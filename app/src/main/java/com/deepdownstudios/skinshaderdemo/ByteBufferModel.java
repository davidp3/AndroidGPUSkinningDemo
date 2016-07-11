package com.deepdownstudios.skinshaderdemo;

import android.util.Pair;

import com.deepdownstudios.util.Util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.deepdownstudios.skinshaderdemo.BasicModel.*;

/**
 * Represents a BasicModel as byte buffers.
 * This could implement Model and operate with VertexArrays but...
 * why would I do that when I've got VBOs?  I'm not supporting devices
 * without VBOs (guaranteed on Android since GLES 1.1)
 * Instead, this class is used as a common target for ModelSources.
 * From this, VBOModels (or whatever) can be created.
 */
public class ByteBufferModel {

    public ByteBufferModel(List<Mesh> meshes, Skeleton skeleton) {
        // TThis only knows that the order of the entries in the VBO
        // should be (position3, texCoords2, normal3) because I wrote it
        // that way to correspond with VBOMode.SHADER_ATTRIB_NAMES.
        // A good shader library would do better.

        for(Mesh mesh : meshes) {
            // Vertices
            // nVerts * (3 positions + 2 tex coords + 3 normals + 2 floats representing four bone indices + 4 bone weights) * 4 bytes each
            int nBytes = mesh.verts.length * (3 + 2 + 3 + 2 + 4) * BYTES_PER_FLOAT;

            // These buffers stay synchronized.  The FloatBuffer is just a thin view on the ByteBuffer.
            ByteBuffer vertByteBuffer = ByteBuffer.allocateDirect(nBytes).order(ByteOrder.nativeOrder());
            FloatBuffer vertFloatBuffer = vertByteBuffer.asFloatBuffer();

            for(Vertex vert : mesh.verts) {
                Pair<Integer, Integer> packedBones = packBoneIndices(vert);
                // Batch 3+2+3+2+4 of the nio copy calls without trashing caches and such.
                // Could probably afford increase the batch size...
                float vertValues[] = {
                        (float)vert.pos[0], (float)vert.pos[1], (float)vert.pos[2],
                        (float)vert.texCoords[0], (float)vert.texCoords[1],
                        (float)vert.normal[0], (float)vert.normal[1], (float)vert.normal[2],
                        (float)packedBones.first, (float)packedBones.second,
                        (float)vert.getBoneWeight(0), (float)vert.getBoneWeight(1),
                        (float)vert.getBoneWeight(2), (float)vert.getBoneWeight(3)
                };
                vertFloatBuffer.put(vertValues);
            }

            // Move cursor back to the beginning of the buffer.
            vertByteBuffer.position(0);
            mVertByteBuffers.add(vertByteBuffer);


            // Faces
            // nFaces * 3 verts per face * 2 bytes per vert
            nBytes = mesh.faces.length * 3 * BYTES_PER_SHORT;

            ShortBuffer faceShortBuffer =
                    ByteBuffer.allocateDirect(nBytes).order(ByteOrder.nativeOrder()).asShortBuffer();
            for(short[] face : mesh.faces) {
                faceShortBuffer.put(face);
            }
            faceShortBuffer.position(0);
            mFaceShortBuffers.add(faceShortBuffer);

            // Materials
            Util.Assert(mesh.mRenderPasses.size() > 0);
            mMaterials.add(mesh.mRenderPasses.get(0).material);
        }

        mSkeleton = skeleton;
    }

    /**
     * @return
     * ret.first is first two bones -- last 5 bits are bone 0, next 5 bits are bone 1 (mediump is guaranteed 10 bits)
     * ret.second is the remaining two bones in a similar encoding.
     */
    private Pair<Integer, Integer> packBoneIndices(Vertex vert) {
      int b0 = vert.getBone(0), b1 = vert.getBone(1),
        b2 = vert.getBone(2), b3 = vert.getBone(3);
      return new Pair<>(b1*32 + b0, b3*32 + b2);

    }

    public Skeleton mSkeleton;
    public List<ShortBuffer> mFaceShortBuffers = new ArrayList<>();     // nMeshes
    public List<ByteBuffer> mVertByteBuffers = new ArrayList<>();       // nMeshes
    public List<Material> mMaterials = new ArrayList<>();               // nMeshes

    private static final int BYTES_PER_FLOAT = 4;
    private static final int BYTES_PER_SHORT = 2;
}
