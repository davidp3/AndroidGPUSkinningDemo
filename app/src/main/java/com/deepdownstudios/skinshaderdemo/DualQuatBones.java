package com.deepdownstudios.skinshaderdemo;

import android.opengl.GLES20;

import com.deepdownstudios.skinshaderdemo.Bones.GLSLBones;

import static com.deepdownstudios.skinshaderdemo.Bones.TransformStorage;
import static com.deepdownstudios.skinshaderdemo.BasicModel.Animation;
import static com.deepdownstudios.skinshaderdemo.BasicModel.RigidTransform;
import static com.deepdownstudios.skinshaderdemo.BasicModel.Skeleton;

/**
 * Animated bones presented as quaternions.
 */
public class DualQuatBones implements GLSLBones, TransformStorage {
    /**
     * Calculate bone pose.
     * @param animation The animation to apply or null for the default pose.
     * @param skeleton  The skeleton of the model to animate
     * @param delta     The time in the animation to set as pose, in seconds.  Ignored if animation == null.
     */
    public DualQuatBones(Animation animation, Skeleton skeleton, double delta) {
        Bones.storeBoneTransforms(animation, skeleton, delta, this);
    }

    @Override
    public void postToGLSLUniform(int boneArrayId) {
        GLES20.glUniform4fv(boneArrayId, mTforms.length / 4, mTforms, 0);
    }

    @Override
    public void allocateStorage(int nTransforms) {
        // We store each dual quaternion as 2 vec4s.
        mTforms = new float[nTransforms * 2 * 4];
    }

    @Override
    public void storeTransform(int transformIdx, RigidTransform transform) {
        DualQuaternion dualQuat = new DualQuaternion(transform);
        // I unwisely put w in the first coordinate in java but it is 4th in GLSL
        // so I need to swizzle here.
        for (int i=0; i<3; i++) {
            mTforms[transformIdx*8+i] = (float)dualQuat.values[i+1];        // x,y,z
            mTforms[transformIdx*8+4+i] = (float)dualQuat.values[i+4+1];    // x,y,z
        }
        mTforms[transformIdx*8+3] = (float)dualQuat.values[0];      // w
        mTforms[transformIdx*8+4+3] = (float)dualQuat.values[4];    // w
    }

    private float[] mTforms;
}
