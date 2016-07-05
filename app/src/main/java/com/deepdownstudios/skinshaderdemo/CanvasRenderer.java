package com.deepdownstudios.skinshaderdemo;

import android.annotation.SuppressLint;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import java.lang.ref.WeakReference;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Describes this specific app's behavior.  We load and display one animated model
 * (AnimationRenderer) at a time.  On clicks, we move to the next
 * model/animation/blending mechanism.
 */
class CanvasRenderer implements GLSurfaceView.Renderer, View.OnClickListener {

    public CanvasRenderer(GLSurfaceView glSurfaceView, TextView modelInfoView) {
        mWeakGLSurfaceView = new WeakReference<>(glSurfaceView);
        mModelInfoView = modelInfoView;
        mAnimationRenderer = getAnimRenderer(0);

        String label = mAnimationRenderer.mModelName + " : " +
                mAnimationRenderer.mAnimName + " - " +
                mAnimationRenderer.mAnimator;

        modelInfoView.setText(label);
    }


    public void onDrawFrame(GL10 glUnused) {
        /// This is run on the GLES thread but the mAnimationRenderer is sometimes
        // cleared... on the main thread.  We can still complete this method using the
        // old one but we should make sure that the we don't lose the reference.
        // ...so we assign it to a local variable.
        AnimationRenderer animRenderer = mAnimationRenderer;
        if (animRenderer == null) {
            return;     // We are being (re)-created asynchronously
        }

        if (mCallOnInit) {
            mCallOnInit = false;
            animRenderer.onInit(mVMatrix);
        }

        // Ignore the passed-in GL10 interface, and use the GLES20
        // class's static methods instead.
        GLES20.glClearColor(0.18f, 0.18f, 0.18f, 1.0f);
        GLES20.glClear( GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

        // TODO: Spin the camera or implement a trackball or something.
        float[] tempMat = new float[16];

        // why am I rotating the light...?
        float[] tempLightPos = new float[4];
        // Docs say that, unlike other methods, multiplyMV result cant also be one of the operands.
        // But they say this in a very cryptic way so it may just be bad writing.  It would be
        // weird otherwise.  Still, I respect the docs here.
        Matrix.multiplyMV(tempLightPos, 0, tempMat, 0, mEyeLightPos, 0);

        animRenderer.onDrawFrame(mProjMatrix, mVMatrix, tempLightPos);
    }

    public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float ratio = (float)width / (float)height;
        Matrix.frustumM(mProjMatrix, 0, -ratio, ratio, -1, 1, 0.6f, 100.0f);

        // We lost the GL context.  All of our cached GL objects are gone.
        // We should also recreate the AnimationRenderer since it holds e.g.
        // shader programs.
        ModelData.TEXTURE_CACHE.clear();
        ModelData.MODEL_CACHE.clear();
        setModelInfo(mClickCount);
    }

    public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
        GLES20.glEnable( GLES20.GL_DEPTH_TEST );
        GLES20.glDepthFunc( GLES20.GL_LEQUAL );
        GLES20.glDepthMask( true );

        mEyeLightPos[0] = 5.0f;
        mEyeLightPos[1] = 8.0f;
        mEyeLightPos[2] = -2.0f;
        mEyeLightPos[3] = 1.0f;     // ignored

        Matrix.setLookAtM(mVMatrix, 0,
                mEyeLightPos[0], mEyeLightPos[1], mEyeLightPos[2],      // eye
                0f, 6.0f, 0f,               // lookat point
                0f, 1.0f, 0.0f);            // up
    }

    @Override
    public void onClick(View v) {
        mClickCount += 1;
        setModelInfo(mClickCount);
    }

    public void onTrimMemory(int level) {
        ModelData.MODEL_CACHE.onTrimMemory(level);
        ModelData.TEXTURE_CACHE.onTrimMemory(level);
    }

    @SuppressLint("SetTextI18n")
    private void setModelInfo(final int index) {
        GLSurfaceView glSurfaceView = mWeakGLSurfaceView.get();
        if (glSurfaceView == null) {
            return;     // We are begin destroyed.  Ignore the button click.
        }

        // We do everything asynchronously except nulling out the animation renderer so
        // it can't be used by a draw request.
        final AnimationRenderer oldAnimRenderer = mAnimationRenderer;
        mAnimationRenderer = null;
        glSurfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (oldAnimRenderer != null) {
                    oldAnimRenderer.onRelease();
                }

                mCallOnInit = true;
                mAnimationRenderer = getAnimRenderer(index);
                // For thread safety, build this string before, not in, the post()-ed Runnable.
                final String label = mAnimationRenderer.mModelName + " : " +
                        mAnimationRenderer.mAnimName + " - " +
                        mAnimationRenderer.mAnimator;

                // The TextView is an Android SDK View, not a GLES object.  So it should be handled
                // on the main thread, not the GLES thread (or whatever thread we may be on).
                // You can spot mistakes in GL vs main thread by the fact that they crash with
                // no useful debug information.
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        mModelInfoView.setText(label);
                    }
                });
            }
        });
    }

    public AnimationRenderer getAnimRenderer(int index) {
        int animDescriptorCount = getAnimDescriptorCount();
        index = index % animDescriptorCount;

        int model = 0;
        while (model < mAnimSpecs.length) {
            int nDescs = mAnimSpecs[model].anims.length * Animator.values().length;    // There are 3 blend types
            if (index >= nDescs) {
                index -= nDescs;
                model++;
            } else {
                return new AnimationRenderer(mAnimSpecs[model].cachedSourceModel,
                        mAnimSpecs[model].model,        // model name
                        mAnimSpecs[model].anims[index / Animator.values().length],   // anim name
                        index / Animator.values().length,    // anim index
                        Animator.values()[index % Animator.values().length]); // shader blend mechanism
            }
        }
        throw new IllegalArgumentException("There must be a math bug here or index was too big.");
    }

    public int getAnimDescriptorCount() {
        int model = 0;
        int count = 0;
        while (model < mAnimSpecs.length) {
            count += mAnimSpecs[model].anims.length * Animator.values().length;
            model++;
        }
        return count;
    }

    private static final ModelData.AnimModelSpec[] mAnimSpecs = ModelData.MODEL_ANIMS;

    @SuppressWarnings("unused")
    private static String TAG = "CanvasRenderer";

    private int mClickCount = 0;

    private WeakReference<GLSurfaceView> mWeakGLSurfaceView;
    private TextView mModelInfoView;

    private AnimationRenderer mAnimationRenderer;
    private float[] mProjMatrix = new float[16];
    private float[] mVMatrix = new float[16];
    private float[] mEyeLightPos = new float[4];
    private boolean mCallOnInit = true;
}
