package com.deepdownstudios.skinshaderdemo;

import android.opengl.GLES20;

import java.util.ArrayList;
import java.util.List;

import static com.deepdownstudios.skinshaderdemo.BasicModel.Animation;
import static com.deepdownstudios.skinshaderdemo.BasicModel.Bone;
import static com.deepdownstudios.skinshaderdemo.BasicModel.RigidTransform;
import static com.deepdownstudios.skinshaderdemo.BasicModel.Skeleton;

/**
 * Animated bones presented as quaternions.
 */
public class QuatBones extends Bones {
    /**
     * Calculate bone pose.
     * @param animation The animation to apply or null for the default pose.
     * @param skeleton  The skeleton of the model to animate
     * @param delta     The time in the animation to set as pose, in seconds.  Ignored if animation == null.
     */
    public QuatBones(Animation animation, Skeleton skeleton, double delta) {
        List<Bone> bones = skeleton.bones;
        // interleaved as quat, then trans, then next quat, then trans...
        // Note that quats are represented as 3 floats... they are always normalized
        // so the w component is sgn(w)*sqrt(1-x^2-y^2-z^2).  See below for
        // info on sgn(w)
        mTforms = new float[bones.size() * 6];

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
            // MATH ALERT: Since we encode the quat in 3 numbers and get the fourth
            // with a sqrt, we lose the sign (ie sgn(w)).  Since quaternions are
            // identical up to a multiple of -1, we can simply multiply the
            // quaternion by -1 if the w component was negative.  Then we can
            // always assume w is non-negative in the shader.
            float quatCoeff = (product.quat.values[0] >= 0) ? 1.0f : -1.0f;
            for (int j=0; j<3; j++) {
                mTforms[i*6+j] = quatCoeff * (float)product.quat.values[j+1];
                mTforms[i*6+3+j] = (float)product.pos[j];
            }
        }
    }

    @Override
    public void postToGLSLUniform(int boneArrayId) {
        GLES20.glUniform3fv(boneArrayId, mTforms.length / 3, mTforms, 0);
    }

    private float[] mTforms;
}
