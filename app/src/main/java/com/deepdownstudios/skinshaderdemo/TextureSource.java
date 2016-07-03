package com.deepdownstudios.skinshaderdemo;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

/**
 * Loads textures from the application resources folder.  This is pretty
 * crappy -- you would really want to use smarter formats (and sometimes
 * compressed GPU-specific formats) instead of the ones that BitmapFactory
 * uses.
 * (This may actually be fine for simple applications.)
 */
public class TextureSource implements Source<GLESTexture> {
    private Resources mResources;
    private int mTextureResourceId;

    public TextureSource(Resources resources, int textureResourceId) {
        mResources = resources;
        mTextureResourceId = textureResourceId;
    }

    @Override
    public GLESTexture load() {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        final Bitmap bitmap = BitmapFactory.decodeResource(mResources, mTextureResourceId, options);

        int[] glTexId = new int[1];
        GLES20.glGenTextures(1, glTexId, 0);
        checkGlError("glGenTextures : " + mTextureResourceId);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, glTexId[0]);
        checkGlError("glBindTexture : " + mTextureResourceId);
        for(int[] texParam : DEFAULT_TEXTURE_PARAMETERS) {
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, texParam[0], texParam[1]);
        }
        checkGlError("glTexParameteri : " + mTextureResourceId);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        checkGlError("GLUtils.texImage2D : " + mTextureResourceId);
        bitmap.recycle();

        return new GLESTexture(glTexId[0]);
    }

    private static final int DEFAULT_TEXTURE_PARAMETERS[][] = {
            {GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST},
            {GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR},
            {GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT},
            {GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT}
    };

    /**
     * Get and log ALL current GL errors.  Throw the last one as an exception.
     * @param message Message to log if there are errors.
     */
    private void checkGlError(String message) {
        int error = GLES20.glGetError();
        while (error != GLES20.GL_NO_ERROR) {
            Log.e(TAG, message + ".  GlError : " + error);
            error = GLES20.glGetError();
            if (error == GLES20.GL_NO_ERROR) {
                throw new RuntimeException(message + ". GlError : " + error);
            }
        }
    }

    private static final String TAG = "TextureSource";
}
