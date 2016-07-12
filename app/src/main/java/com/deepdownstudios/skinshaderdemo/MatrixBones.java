package com.deepdownstudios.skinshaderdemo;

import android.opengl.GLES20;

import com.deepdownstudios.skinshaderdemo.Bones.GLSLBones;
import com.deepdownstudios.skinshaderdemo.Bones.TransformStorage;

import static com.deepdownstudios.skinshaderdemo.BasicModel.Animation;
import static com.deepdownstudios.skinshaderdemo.BasicModel.RigidTransform;
import static com.deepdownstudios.skinshaderdemo.BasicModel.Skeleton;

/**
 * Animated bones presented as matrices.
 */
public class MatrixBones implements GLSLBones, TransformStorage {
    /**
     * Calculate bone pose.
     * @param animation The animation to apply or null for the default pose.
     * @param skeleton  The skeleton of the model to animate
     * @param delta     The time in the animation to set as pose, in seconds.  Ignored if animation == null.
     */
    public MatrixBones(Animation animation, Skeleton skeleton, double delta) {
        Bones.storeBoneTransforms(animation, skeleton, delta, this);
    }

    @Override
    public void postToGLSLUniform(int boneArrayId) {
        GLES20.glUniformMatrix4fv(boneArrayId, mTforms.length / 16, false, mTforms, 0);
    }

    @Override
    public void allocateStorage(int nTransforms) {
        mTforms = new float[nTransforms * 16];
    }

    @Override
    public void storeTransform(int transformIdx, RigidTransform transform) {
        System.arraycopy(transform.asMatrix(), 0, mTforms, 16*transformIdx, 16);
    }

    private float[] mTforms;
}
