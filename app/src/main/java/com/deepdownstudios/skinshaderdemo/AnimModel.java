package com.deepdownstudios.skinshaderdemo;

/**
 * Created by davidp on 6/29/16.
 * All methods require an active GLES context on the GLES thread.
 */
public interface AnimModel {
    void jumpTo(double time);

    void draw(float[] modelMatrix, float[] viewMatrix, float[] projMatrix, float[] eyeLightPos);
}
