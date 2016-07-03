package com.deepdownstudios.skinshaderdemo;

import android.opengl.GLES20;

import java.util.ArrayList;
import java.util.List;

import static com.deepdownstudios.skinshaderdemo.ByteBufferModel.Animation;
import static com.deepdownstudios.skinshaderdemo.ByteBufferModel.Bone;
import static com.deepdownstudios.skinshaderdemo.ByteBufferModel.RigidTransform;
import static com.deepdownstudios.skinshaderdemo.ByteBufferModel.Skeleton;

/**
 * Animated bones presented as matricies.
 */
public class MatrixBones extends Bones {
    /**
     * Calculate bone pose.
     * @param animation The animation to apply or null for the default pose.
     * @param skeleton  The skeleton of the model to animate
     * @param delta     The time in the animation to set as pose, in seconds.  Ignored if animation == null.
     */
    public MatrixBones(Animation animation, Skeleton skeleton, double delta) {
        List<Bone> bones = skeleton.bones;
        // Calculate the (potentially animated) model-to-bone matricies.
        mTforms = new float[bones.size() * 16];

        List<Bone> bonesCopy = new ArrayList<>();
        for (Bone bone: bones)
            bonesCopy.add(bone.copy());

        calculatePose(bonesCopy, animation, delta);

        // MATH ALERT: Multiply the invBindPose transformations (which map bone-to-model-space, or model/bone)
        // by mTforms (model-to-bone or bone/model).  So
        // invBindPose * mTforms = model/bone * bone/model = model/model = a "unitless"
        // transformation that has no inherent coordinate system.  Which is good because
        // the skeletal transformation maps from a model space back into itself.
        // In the shader, you then also apply MVP as you do to position any object.
        for (int i=0; i<bonesCopy.size(); i++) {
            Bone bone = bonesCopy.get(i);
            Bone invBone = skeleton.invBindPose.get(i);
            RigidTransform product = bone.transform.multiply(invBone.transform);
            System.arraycopy(product.asMatrix(), 0, mTforms, 16*i, 16);
        }
    }

    @Override
    public void postToGLSLUniform(int boneArrayId) {
        GLES20.glUniformMatrix4fv(boneArrayId, mTforms.length / 16, false, mTforms, 0);
    }

    private float[] mTforms;
}
