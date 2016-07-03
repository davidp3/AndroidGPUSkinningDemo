package com.deepdownstudios.skinshaderdemo;

import android.content.res.Resources;
import android.util.Pair;

import com.deepdownstudios.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * MilkShape 3D importer.
 */
public class Ms3dModelSource implements Source<Model> {
    private Resources mResources;
    private Cache<GLESTexture> mTextureCache;
    private int mResourceId;
    private List<Pair<Integer, Integer>> mAnimFrameRanges;

    /**
     * Milkshape skinned import.
     * @param textureCache
     * @param animFrameRanges a list of [start,end] frame ranges (inclusive)
     */
    public Ms3dModelSource(Resources resources, Cache<GLESTexture> textureCache, int resourceId, List<Pair<Integer, Integer>> animFrameRanges) {
        mResources = resources;
        mTextureCache = textureCache;
        mResourceId = resourceId;
        mAnimFrameRanges = animFrameRanges;
    }

    @Override
    public Model load() {
        InputStream inputStream = mResources.openRawResource(mResourceId);
        // available() is correct for Android resource streams.  See
        // http://stackoverflow.com/questions/6049926/get-the-size-of-an-android-file-resource
        byte[] buffer;
        try {
            int streamLen = inputStream.available();
            buffer = new byte[streamLen];
            int read = inputStream.read(buffer);
            Util.Assert(read == streamLen);
        } catch (IOException e) {
            throw new IllegalStateException("Trouble reading resource : " + mResourceId, e);
        }
        ByteBufferModel bbModel = loadBB(buffer);
        return new VBOModel(mResources, mTextureCache, bbModel);
    }

    private ByteBufferModel loadBB(byte[] ms3dFile) {
        Util.Assert(false);
        return null;
    }
}
