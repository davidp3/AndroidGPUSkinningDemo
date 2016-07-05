package com.deepdownstudios.skinshaderdemo;

import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * The inner "data classes" can be used by serializers to read into simple
 * structures that this class use to construct itself.
 */
public class BasicModel {

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

        /// If vert has at least 'idx' bones then return the bone index, otherwise return 0.
        public int getBone(int idx) {
            return (idx < bones.size()) ? bones.get(idx) : 0;
        }

        /// If vert has at least 'idx' bones then return the bone weight, otherwise return 0.
        public double getBoneWeight(int idx) {
            return (idx < boneWeights.size()) ? boneWeights.get(idx) : 0.0;
        }
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
}
