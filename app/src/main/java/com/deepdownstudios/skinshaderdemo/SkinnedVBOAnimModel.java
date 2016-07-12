package com.deepdownstudios.skinshaderdemo;

import android.annotation.SuppressLint;

import com.deepdownstudios.skinshaderdemo.Bones.GLSLBones;

import static com.deepdownstudios.skinshaderdemo.BasicModel.*;

/**
 * Created by davidp on 6/29/16.
 * The SkinnedVBOAnimModel uses the VBOModel for its data and rendering but
 * sets bone matrix/quat/dual-quat values for uniform shader variables.
 */
@SuppressLint("Assert")
public class SkinnedVBOAnimModel implements AnimModel {

    public SkinnedVBOAnimModel(VBOModel vboModel, Animation animation, double startTime,
                               Animator animator) {
        mVboModel = vboModel;
        mAnimation = animation;
        mStartTime = startTime;
        mAnimator = animator;

        mVboModel.setShaderProgram(getVShader(animator), R.raw.frag_shader);
    }

    @Override
    public void jumpTo(double time) {
        double delta = (time - mStartTime) % mAnimation.duration;        // Loop the animation.
//        long delta = Math.min((time - mStartTime), duration);        // Stop and freeze.
        mBones = mAnimator.getBonesAtTime(mAnimation, mVboModel.getSkeleton(), delta);
    }

    @Override
    public void draw(float[] modelMatrix, float[] viewMatrix, float[] projMatrix, float[] eyeLightPos) {
        if (mBones == null) {
            jumpTo(mStartTime);
        }
        mVboModel.draw(modelMatrix, viewMatrix, projMatrix, eyeLightPos, mBones);
    }

    private int getVShader(Animator animator) {
        switch (animator) {
            case NORMAL:
                return R.raw.vert_shader_matrix;
            case QUAT:
                return R.raw.vert_shader_quat;
            case DUAL_QUAT:
                return R.raw.vert_shader_dualquat;
        }
        throw new IllegalArgumentException("Invalid Animator : " + animator);
    }

    private VBOModel mVboModel;         // the model we are an instance of
    private Animation mAnimation;     // keyframes object for bones.  May or may not be uniform.
    private double mStartTime;            // value to consult as time of animation start, in seconds
    private Animator mAnimator;     // Type of bone matrix blending for this animation instance
    private GLSLBones mBones = null;        // bones at "current" time

    @SuppressWarnings("unused")
    private static String TAG = "SkinnedVBOAnimModel";
}
