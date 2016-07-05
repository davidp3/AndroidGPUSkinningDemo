package com.deepdownstudios.skinshaderdemo;

import android.opengl.GLES20;

import java.util.ArrayList;
import java.util.List;

import static com.deepdownstudios.skinshaderdemo.BasicModel.Animation;
import static com.deepdownstudios.skinshaderdemo.BasicModel.Bone;
import static com.deepdownstudios.skinshaderdemo.BasicModel.RigidTransform;
import static com.deepdownstudios.skinshaderdemo.BasicModel.Skeleton;

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
        mTforms = new float[bones.size() * 16];

        List<Bone> bonesCopy = new ArrayList<>();
        for (Bone bone: bones)
            bonesCopy.add(bone.copy());

        // Calculate the (potentially animated) bone-to-model matricies.
        calculatePose(bonesCopy, animation, delta);

        // MATH ALERT: Multiply the invBindPose transformations (which map model-to-bone-space, or bone/model)
        // by mTforms (bone-to-model or model/bone).  So
        // mTforms * invBindPose = model/bone * bone/model = a "unitless"
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
