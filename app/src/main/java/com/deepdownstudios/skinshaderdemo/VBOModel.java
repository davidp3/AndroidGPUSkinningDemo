package com.deepdownstudios.skinshaderdemo;


import android.content.res.Resources;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;
import android.util.Pair;

import com.deepdownstudios.util.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.deepdownstudios.skinshaderdemo.BasicModel.*;
import static com.deepdownstudios.skinshaderdemo.BasicModel.Skeleton;

/**
 * A Model that stores its information in a VBO.
 * All methods require an active GLES context on the GLES thread.
 */
public class VBOModel implements Model {

    private static final int BYTES_PER_SHORT = 2;

    public VBOModel(Resources resources, Cache<GLESTexture> textureCache, ByteBufferModel bbModel) {
        this.mResources = resources;

        Util.Assert(bbModel.mVertByteBuffers.size() == bbModel.mFaceShortBuffers.size());
        Util.Assert(bbModel.mVertByteBuffers.size() == bbModel.mMaterials.size());

        int vbos[] = new int[bbModel.mVertByteBuffers.size()];
        GLES20.glGenBuffers(vbos.length, vbos, 0);
        int ibos[] = new int[bbModel.mVertByteBuffers.size()];
        GLES20.glGenBuffers(ibos.length, ibos, 0);
        for(int i=0; i<bbModel.mVertByteBuffers.size(); i++) {
            Mesh mesh = new Mesh();
            mesh.mVbo = vbos[i];
            mesh.mIbo = ibos[i];
            mesh.mNTris = bbModel.mFaceShortBuffers.get(i).capacity();

            // vbo
            ByteBuffer vertBuffer = bbModel.mVertByteBuffers.get(i);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbos[i]);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertBuffer.capacity(),
                    vertBuffer, GLES20.GL_STATIC_DRAW);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

            // ibo
            ShortBuffer faceBuffer = bbModel.mFaceShortBuffers.get(i);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibos[i]);
            GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, faceBuffer.capacity() * BYTES_PER_SHORT,
                    faceBuffer, GLES20.GL_STATIC_DRAW);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

            // textures.  Use string rendering of ID as name (ha!)
            mesh.mTexture = textureCache.fetch(
                    String.valueOf(bbModel.mMaterials.get(i).textureResourceId),
                    new TextureSource(mResources, bbModel.mMaterials.get(i).textureResourceId));

            // its ready.
            mMeshes.add(mesh);
        }

        mSkeleton = bbModel.mSkeleton;
    }

    @Override
    public AnimModel createAnimModel(String animName, int animIndex, double startTime,
                                     Animator animator) {
        return new SkinnedVBOAnimModel(this, getAnim(animName, animIndex), startTime, animator);
    }

    public void setShaderProgram(int vShaderResource, int pShaderResource) {
        BufferedReader vsReader =
                new BufferedReader(new InputStreamReader(mResources.openRawResource(vShaderResource)));
        BufferedReader psReader =
                new BufferedReader(new InputStreamReader(mResources.openRawResource(pShaderResource)));
        String line;
        StringBuilder vShader = new StringBuilder(), pShader = new StringBuilder();
        try {
            while ((line = vsReader.readLine()) != null) {
                vShader.append(line).append("\n");
            }
            while ((line = psReader.readLine()) != null) {
                pShader.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "Initializing shaders.  Vertex: " + vShaderResource + "  -  Pixel : " + pShaderResource);
        setShaderProgram(vShader.toString(), pShader.toString());
    }

    public void setShaderProgram(String vShader, String pShader) {
        // Create program from shaders
        mProgram = createProgram(vShader, pShader);
        Util.Assert(mProgram != 0);

        // Fetch handles to the shaders' uniform and attribute parameters.
        int runningTotal = 0;
        for(String name : SHADER_ATTRIB_NAMES) {
            int handle = GLES20.glGetAttribLocation(mProgram, name);
            checkGlError("glGetAttribLocation " + name);
            if (handle == -1) {
                throw new RuntimeException("Could not get attrib location for " + name);
            }
            mAttribHandles.put(name, handle);
            mAttribOffsets.put(name, runningTotal);
            Pair<Integer, Integer> attribInfo = SHADER_ATTRIB_INFO.get(name);
            runningTotal += attribInfo.first * glTypeSize(attribInfo.second);
        }

        Util.Assert(runningTotal == mShaderStride);

        for(String name : SHADER_UNIFORM_NAMES) {
            int handle = GLES20.glGetUniformLocation(mProgram, name);
            checkGlError("glGetUniformLocation " + name);
            if (handle == -1) {
                throw new RuntimeException("Could not get uniformlocation for " + name);
            }
            mUniformHandles.put(name, handle);
        }
    }

    public void draw(float[] modelMatrix, float[] viewMatrix, float[] projMatrix,
                     float[] eyeLightPos, Bones bones) {
        GLES20.glUseProgram(mProgram);
        checkGlError("glUseProgram");

        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);

        float[] mvMatrix = new float[16];
        float[] mvpMatrix = new float[16];
        Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0);      // mv = view * model
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, mvMatrix, 0);        // mvp = proj * mv = proj * view * model

        float[] vtimMatrix = new float[16];
        float[] tempMat = new float[16];
        Matrix.invertM(vtimMatrix, 0, modelMatrix, 0);                      // vtim = model^-1
        Matrix.transposeM(tempMat, 0, vtimMatrix, 0);                    // tempMat = vtim^T = (model^-1)^T
        Matrix.multiplyMM(vtimMatrix, 0, viewMatrix, 0, tempMat, 0);     // vtim = view * tempMat = view * (model^-1)^T

        // Set the 'global' uniform shader variables.  These are the same
        // for all of the model's meshes.
        // Texture samplers are configured per-mesh.
        GLES20.glUniformMatrix4fv(mUniformHandles.get("uMVPMatrix"), 1, false, mvpMatrix, 0);
        checkGlError("glUniformMatrix4fv mvpMatrix");
        GLES20.glUniformMatrix4fv(mUniformHandles.get("uMVMatrix"), 1, false, mvMatrix, 0);
        checkGlError("glUniformMatrix4fv mvMatrix");
        GLES20.glUniformMatrix4fv(mUniformHandles.get("uVTIMMatrix"), 1, false, vtimMatrix, 0);
        checkGlError("glUniformMatrix4fv vtimMatrix");
        GLES20.glUniform3fv(mUniformHandles.get("uLightPos"), 1, eyeLightPos, 0);
        checkGlError("glUniform3fv uLightPos");
        bones.postToGLSLUniform(mUniformHandles.get("uBoneTforms[0]"));
        checkGlError("postToGLSLUniform");

        for (Mesh mesh : mMeshes) {
            // Set the uniform shader texture samplers
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mesh.mTexture.mId);
            GLES20.glUniform1i(mUniformHandles.get("uTexture"), 0);     // bind to sampler #0
            checkGlError("glUniform1i uTexture");

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.mVbo);
            checkGlError("glBindBuffer mVbo");
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mesh.mIbo);
            checkGlError("glBindBuffer mIbo");

            for (String attribName : SHADER_ATTRIB_INFO.keySet()) {
                Pair<Integer, Integer> sizes = SHADER_ATTRIB_INFO.get(attribName);
                int attribHandle = mAttribHandles.get(attribName);
                int attribOffset = mAttribOffsets.get(attribName);
                GLES20.glVertexAttribPointer(attribHandle, sizes.first, sizes.second, false,
                        mShaderStride, attribOffset);
                checkGlError("glVertexAttribPointer " + attribName);
                GLES20.glEnableVertexAttribArray(attribHandle);
                checkGlError("glEnableVertexAttribArray " + attribName);
            }

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

            // Draw
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.mNTris, GLES20.GL_UNSIGNED_SHORT, 0);

            // Reset GLES state
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        }

        GLES20.glDisable(GLES20.GL_CULL_FACE);
    }

    public Skeleton getSkeleton() {
        return mSkeleton;
    }


    /******************************************************************************/

    private class Mesh {
        /// GLES index of the primary texture
        public GLESTexture mTexture;
        /// GLES index of the vertex buffer object
        public int mVbo;
        /// GLES index of the index buffer object
        public int mIbo;
        /// Number of triangles in the mesh
        public int mNTris;
    }

    /******************************************************************************/


    private int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader == 0) {
            throw new IllegalStateException("Failed to create "
                    + ((shaderType == GLES20.GL_VERTEX_SHADER) ? "vertex" : "fragment")
                    + " shader program : " + source);
        }
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "Could not compile shader " + shaderType + ":");
            Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("Failed to compile "
                    + ((shaderType == GLES20.GL_VERTEX_SHADER) ? "vertex" : "fragment")
                    + " shader program : " + source);
        }
        return shader;
    }

    private int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GLES20.glCreateProgram();
        if (program == 0) {
            throw new IllegalStateException("Failed to create shader program.");
        }

        GLES20.glAttachShader(program, vertexShader);
        checkGlError("create Program attachShader vertex");
        GLES20.glAttachShader(program, pixelShader);
        checkGlError("create Program attachShader pixel");
        GLES20.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link program: ");
            Log.e(TAG, GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            throw new IllegalStateException("Failed to link shader program.");
        }
        return program;
    }

    /**
     * Get and log ALL current GL errors.  Throw the last one as an exception.
     * @param message Message to log if there are errors.
     */
    private void checkGlError(String message) {
        int error = GLES20.glGetError();
        while (error != GLES20.GL_NO_ERROR) {
            Log.e(TAG, message + ".  GlError : " + error);
            error = GLES20.glGetError();
            if (error == GLES20.GL_NO_ERROR) {
                throw new RuntimeException(message + ". GlError : " + error);
            }
        }
    }

    @SuppressWarnings("unused")
    private void logShaderVariables(int program)
    {
        int[] count = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_ACTIVE_ATTRIBUTES, count, 0);

        for (int i=0; i<count[0]; i++) {
            int[] tmp = {0, 0, 0};
            byte[] nameb = new byte[64];
            GLES20.glGetActiveAttrib(program, i, 64, tmp, 0, tmp, 1, tmp, 2, nameb, 0);
            String name = new String(nameb, 0, tmp[0]);
            Log.i(TAG, "Program " + program + " : Variable " + i + " : name = " + name);
        }
    }

    private Animation getAnim(String animName, int animIndex) {
        if (!animName.isEmpty()) {
            for (Animation anim : mSkeleton.animations) {
                if (anim.name.equals(animName)) {
                    return anim;
                }
            }
        }
        if (animIndex != -1) {
            return mSkeleton.animations.get(animIndex);
        }
        throw new IllegalArgumentException("Unrecognized animation name = " + animName);
    }

    /// This is the order the values must appear in the VBO.
    /// It does not matter if they use this order in the shader itself.
    private static final String SHADER_ATTRIB_NAMES[] = {
            "aPosition", "aTexCoord", "aNormal", "aBoneIndices", "aBoneWeights"
    };

    /// uVTIMMatrix is view * ((model^-1)^T), for deforming normals even if model has scale.
    /// Use [0] for array members.  This shouldn't be necessary (although it is valid)
    /// but GLES driver writers have been deviating from the spec this way for years.
    private static final String SHADER_UNIFORM_NAMES[] = {
            "uMVPMatrix", "uMVMatrix",  "uVTIMMatrix", "uBoneTforms[0]", "uLightPos", "uTexture"
    };

    private Map<String, Integer> mAttribHandles = new HashMap<>();
    private Map<String, Integer> mAttribOffsets = new HashMap<>();
    private Map<String, Pair<Integer,Integer>> SHADER_ATTRIB_INFO = new HashMap<>();
    private int mShaderStride;

    // Returns size  of type in bytes
    private int glTypeSize(int glType) {
        switch(glType) {
            case GLES20.GL_FLOAT:
            case GLES20.GL_UNSIGNED_INT:
            case GLES20.GL_INT:
                return 4;
            case GLES20.GL_SHORT:
            case GLES20.GL_UNSIGNED_SHORT_4_4_4_4:
            case GLES20.GL_UNSIGNED_SHORT_5_5_5_1:
            case GLES20.GL_UNSIGNED_SHORT_5_6_5:
            case GLES20.GL_UNSIGNED_SHORT:
                return 2;
            case GLES20.GL_BYTE:
            case GLES20.GL_UNSIGNED_BYTE:
                return 1;
        }
        throw new IllegalArgumentException("glTypeSize called with " + glType + " which is not a legal type.");
    }

    {
        SHADER_ATTRIB_INFO.put("aPosition", new Pair<>(3, GLES20.GL_FLOAT));
        SHADER_ATTRIB_INFO.put("aTexCoord", new Pair<>(2, GLES20.GL_FLOAT));
        SHADER_ATTRIB_INFO.put("aNormal", new Pair<>(3, GLES20.GL_FLOAT));
        SHADER_ATTRIB_INFO.put("aBoneIndices", new Pair<>(2, GLES20.GL_FLOAT)); // 2 bones per float
        SHADER_ATTRIB_INFO.put("aBoneWeights", new Pair<>(4, GLES20.GL_FLOAT));
        mShaderStride = 0;
        for (Pair<Integer, Integer> info : SHADER_ATTRIB_INFO.values()) {
            mShaderStride += info.first * glTypeSize(info.second);
        }
    }

    private Map<String, Integer> mUniformHandles = new HashMap<>();

    private int mProgram;

    private static String TAG = "VBOModel";

    private Resources mResources;
    private List<Mesh> mMeshes = new ArrayList<>();
    private Skeleton mSkeleton;
}
