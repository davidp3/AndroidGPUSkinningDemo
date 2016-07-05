package com.deepdownstudios.skinshaderdemo;

/**
 * All methods require an active GLES context on the GLES thread.
 */
public interface Model {
    /**
     * Request an instance of a playing animation from the model.
     * @param animName If the model indexes its animations by name then this
     *                  will be the name of the animation to look up
     * @param animIndex If the model indexes its animations by number then this
     *                   will be the index of the animation to look up
     * @param animStartTime Time in seconds, relative to now, to start the animation.
     *                      Use SystemClock.uptimeMillis()/1000.0 to start it "now".
     *                      Add/subtract from that to start at different points in the animation.
     * @param animator     One of the types of animator we are using.
     * @return The animated model, playing the animation.
     */
    AnimModel createAnimModel(String animName, int animIndex, double animStartTime, Animator animator);
}
