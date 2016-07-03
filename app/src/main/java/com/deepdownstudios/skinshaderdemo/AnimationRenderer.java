package com.deepdownstudios.skinshaderdemo;

import android.annotation.SuppressLint;
import android.opengl.Matrix;
import android.os.SystemClock;

import com.deepdownstudios.util.Util;

/**
 * Used to render a single mModelName/animation/blend style.  Similar to an
 * Android GLESRenderer but does not implement that interface.  Instead,
 * it's lifecycle is that onInit(viewMat) is called once, first, on the GLES
 * thread w/ the GLES context, onDrawFrame(projMat, viewMat) is looped
 * afterward, and finally onRelease() is called, possibly followed by
 * a new cycle.
 */
@SuppressLint("Assert")
public class AnimationRenderer {
    public AnimationRenderer(SourcedCache<Model> modelCache, String modelName,
                             String animName,
                             int animIndex,
                             AnimBlendType animBlendType) {
        mModelCache = modelCache;
        mModelName = modelName;
        mAnimName = animName;
        mAnimIndex = animIndex;
        mAnimBlendType = animBlendType;
    }

    @SuppressWarnings("UnusedParameters")
    public void onInit(float[] vMatrix) {
        mModel = mModelCache.fetch(mModelName);
        long animStartTime = SystemClock.uptimeMillis();
        mAnimModel =
                mModel.createAnimModel(mAnimName, mAnimIndex, animStartTime/1000.0, mAnimBlendType);
        Util.Assert(mAnimModel != null);
    }

    public void onRelease() {
        // TODO: Dump shaders and program and VBOs/IBOs/ByteBuffers/Textures
        mModel = null;
        mAnimModel = null;
    }

    public void onDrawFrame(float[] projMatrix, float[] vMatrix, float[] eyeLightPos) {
        Matrix.setIdentityM(mMMatrix, 0);          // model
        long time = SystemClock.uptimeMillis();
        mAnimModel.jumpTo(time/1000.0);
        mAnimModel.draw(mMMatrix, vMatrix, projMatrix, eyeLightPos);
    }

    public enum AnimBlendType {
        NORMAL {
            @Override
            public String toString() { return "matrix"; }
        },
        QUAT {
            @Override
            public String toString() { return "quaternion"; }
        },
        DUAL_QUAT {
            @Override
            public String toString() { return "dual quat"; }
        };

        public Bones getBonesAtTime(ByteBufferModel.Animation animation,
                                    ByteBufferModel.Skeleton skeleton, double delta) {
            switch(this) {
                case NORMAL:
                    return new MatrixBones(animation, skeleton, delta);
                case QUAT:
                    return new QuatBones(animation, skeleton, delta);
            }
            Util.Assert(this.equals(DUAL_QUAT));
            return new DualQuatBones(animation, skeleton, delta);
        }
    }

    private float[] mMMatrix = new float[16];

    public final String mModelName;
    public final String mAnimName;
    private final int mAnimIndex;
    public final SourcedCache<Model> mModelCache;
    public final AnimBlendType mAnimBlendType;

    private Model mModel;
    private AnimModel mAnimModel;
}
