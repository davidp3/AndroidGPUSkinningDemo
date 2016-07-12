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
public class QuatBones implements GLSLBones, TransformStorage {
    /**
     * Calculate bone pose.
     * @param animation The animation to apply or null for the default pose.
     * @param skeleton  The skeleton of the model to animate
     * @param delta     The time in the animation to set as pose, in seconds.  Ignored if animation == null.
     */
    public QuatBones(Animation animation, Skeleton skeleton, double delta) {
        Bones.storeBoneTransforms(animation, skeleton, delta, this);
    }

    @Override
    public void postToGLSLUniform(int boneArrayId) {
        GLES20.glUniform3fv(boneArrayId, mTforms.length / 3, mTforms, 0);
    }

    @Override
    public void allocateStorage(int nTransforms) {
        // Interleaved as quat, then trans, then next quat, then trans...
        // Note that quats are represented as 3 floats... they are always normalized
        // so the w component is sgn(w)*sqrt(1-x^2-y^2-z^2).  See storeTransform for
        // info on sgn(w)
        mTforms = new float[nTransforms * 6];
    }

    @Override
    public void storeTransform(int transformIdx, RigidTransform transform) {
        // MATH ALERT: Since we encode the quat in 3 numbers and get the fourth
        // with a sqrt, we lose the sign (ie sgn(w)).  Since quaternions are
        // identical up to a multiple of -1, we can simply multiply the
        // quaternion by -1 if the w component was negative.  Then we can
        // always assume w is non-negative in the shader.
        float quatCoeff = (transform.quat.values[0] >= 0) ? 1.0f : -1.0f;
        for (int i=0; i<3; i++) {
            mTforms[transformIdx*6+i] = quatCoeff * (float)transform.quat.values[i+1];
            mTforms[transformIdx*6+3+i] = (float)transform.pos[i];
        }
    }

    private float[] mTforms;
}
