package com.deepdownstudios.skinshaderdemo;

import android.util.Pair;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a model as byte buffers.
 * This could implement Model and operate with VertexArrays but...
 * why would I do that when I've got VBOs?  I'm not supporting devices
 * without VBOs (guaranteed on Android since GLES 1.1)
 * Instead, this class is used as a common target for ModelSources.
 * From this, VBOModels (or whatever) can be created.
 *
 * The inner "data classes" can be used by serializers to read into simple
 * structures that this class use to construct itself.
 */
public class ByteBufferModel {

    public ByteBufferModel(List<Mesh> meshes, Skeleton skeleton) {
        // TThis only knows that the order of the entries in the VBO
        // should be (position3, texCoords2, normal3) because I wrote it
        // that way to correspond with VBOMode.SHADER_ATTRIB_NAMES.
        // A good shader library would do better.

        // These buffers stay synchronized.  The FloatBuffer is just a thin view on the ByteBuffer.
        for(Mesh mesh : meshes) {
            // Vertices
            // nVerts * (3 positions + 2 tex coords + 3 normals + 4 bone indices + 4 bone weights) * 4 bytes each
            int nBytes = mesh.verts.length * (3 + 2 + 3 + 4 + 4) * BYTES_PER_FLOAT;

            ByteBuffer vertByteBuffer = ByteBuffer.allocateDirect(nBytes).order(ByteOrder.nativeOrder());
            FloatBuffer vertFloatBuffer = vertByteBuffer.asFloatBuffer();

            for(Vertex vert : mesh.verts) {
                // Batch 3+2+3+4+4 of the nio copy calls without trashing caches and such.
                // Could probably afford increase the batch size...
                float vertValues[] = {
                        (float)vert.pos[0], (float)vert.pos[1], (float)vert.pos[2],
                        (float)vert.texCoords[0], (float)vert.texCoords[1],
                        (float)vert.normal[0], (float)vert.normal[1], (float)vert.normal[2],
                        (float)getBone(vert,0), (float)getBone(vert,1),
                        (float)getBone(vert,2), (float)getBone(vert,3),
                        (float)getBoneWeight(vert,0), (float)getBoneWeight(vert,1),
                        (float)getBoneWeight(vert,2), (float)getBoneWeight(vert,3)
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
            mMaterials.add(mesh.mMaterial);
        }

        mSkeleton = skeleton;
    }

    public static class Mesh {
        public short faces[][];       // nFaces x 3 verts-per-face
        public Vertex verts[];        // nVerts
        public Material mMaterial = new Material();
    }

    public static class Vertex {
        public double pos[] = new double[3];
        public double normal[] = new double[3];
        public double texCoords[] = new double[2];
        public List<Integer> bones = new ArrayList<>();            // up to four.  Will get packed into 4 bytes
        public List<Double> boneWeights = new ArrayList<>();    // Aligned with "bones"
        // there were also tangents for the bump map...
    }

    public static class RigidTransform {
        public double pos[] = new double[3];
        public Quaternion quat = new Quaternion(0, 1.0, 0, 0);      // A 0-degree rotation

        public float[] asMatrix() {
            float[] ret = quat.asMatrix();
            for (int i=0; i<3; i++)
                ret[12+i] = (float)pos[i];         // set translation
            return ret;
        }

        public RigidTransform copy() {
            RigidTransform ret = new RigidTransform();
            System.arraycopy(pos, 0, ret.pos, 0, 3);
            ret.quat = new Quaternion(quat);
            return ret;
        }

        // this * o (meaning apply o, then apply this)
        public RigidTransform multiply(RigidTransform o) {
            RigidTransform result = new RigidTransform();
            result.quat = quat.multiply(o.quat).normalize();
            double[] posDisp = quat.transformPoint(o.pos);
            for (int j=0; j<3; j++) {
                result.pos[j] = pos[j] + posDisp[j];
            }
            return result;
        }

        // Similar to this * o (see multiply) but specifically for composing
        // Ogre animation frames.  The cross-rotation-translaton term that
        // is part of multiply is cut from this.
        public RigidTransform animCompose(RigidTransform o) {
            RigidTransform result = new RigidTransform();
            result.quat = quat.multiply(o.quat).normalize();
            double[] posDisp = o.pos;       // compare to multiply()
            for (int j=0; j<3; j++) {
                result.pos[j] = pos[j] + posDisp[j];
            }
            return result;
        }
    }

    public static class Bone {
        public String name;
        public int parentIdx;
        public RigidTransform transform;
        public Bone copy() {
            Bone ret = new Bone();
            ret.name = name;
            ret.parentIdx = parentIdx;
            ret.transform = transform.copy();
            return ret;
        }
    }

    public static class Animation {
        public String name;
        public double duration;      // in seconds
        // One transform list per joint. Float is time of frame.
        // Some entries may be null meaning no frames.
        // This ArrayList must be sorted by time.
        ArrayList<Pair<Double, RigidTransform>> keyframes[];     // nKeyFrames
    }

    public static class Skeleton {
        public List<Bone> bones = new ArrayList<>();                // nBones
        public List<Animation> animations = new ArrayList<>();      // nAnimations
        public List<Bone> invBindPose;                              // nBones
    }

    public static class Material {
        public int textureResourceId;
        public int bumpResourceId;
    }

    public Skeleton mSkeleton;
    public List<ShortBuffer> mFaceShortBuffers = new ArrayList<>();     // nMeshes
    public List<ByteBuffer> mVertByteBuffers = new ArrayList<>();       // nMeshes
    public List<Material> mMaterials = new ArrayList<>();               // nMeshes


    /// If vert has at least 'idx' bones then return the bone index, otherwise return 0.
    private int getBone(Vertex vert, int idx) {
        return (idx < vert.bones.size()) ? vert.bones.get(idx) : 0;
    }

    /// If vert has at least 'idx' bones then return the bone weight, otherwise return 0.
    private double getBoneWeight(Vertex vert, int idx) {
        return (idx < vert.boneWeights.size()) ? vert.boneWeights.get(idx) : 0.0;
    }

    private static final int BYTES_PER_FLOAT = 4;
    private static final int BYTES_PER_SHORT = 2;
}
