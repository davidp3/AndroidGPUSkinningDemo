package com.deepdownstudios.skinshaderdemo;

import android.util.Pair;

import java.util.Arrays;

/**
 * Specifies all of the models and animations, as well as serializers and meta-data.
 * This could be serialized from JSON or whatever.
 */
public class ModelData {
    /**
     * Caches GLES representations of texture resources.  Remember never to request the
     * texture unless you are on the GLES thread!
     */
    public static final Cache<GLESTexture> TEXTURE_CACHE = new Cache<>();

    /**
     * Cache of Models that should be used by each of the AnimModelSpecs in MODEL_ANIMS.
     */
    public static final Cache<Model> MODEL_CACHE = new Cache<>();

    public static final AnimModelSpec[] MODEL_ANIMS = new AnimModelSpec[] {
              new AnimModelSpec("M. Chief",
                    new String[] { "idle", "something" },
                    new CachedSource<>(MODEL_CACHE,
                            new OgreModelSource(CanvasApplication.getInstance().getResources(),
                                TEXTURE_CACHE,
                                R.raw.m_chief_mesh, R.raw.m_chief_skeleton) ))
            , new AnimModelSpec("Ninja",
                    new String[] { "walk", "stealth", "punch", "sword", "swipe", "spin", "death", "idle" },
                    new CachedSource<>(MODEL_CACHE,
                            new Ms3dModelSource(CanvasApplication.getInstance().getResources(),
                                TEXTURE_CACHE,
                                R.raw.ninja,
                                Arrays.asList(new Pair<>(1, 14), new Pair<>(15, 30),
                                              new Pair<>(32, 44), new Pair<>(45, 59),
                                              new Pair<>(60, 68), new Pair<>(134, 145),
                                              new Pair<>(166, 173), new Pair<>(206, 250)),
                                1.0/8.0 /* speed coefficient */) ))
/*
            , new AnimModelSpec("Alien", new String[] { "belly", "licking" },
                  new Ms3dModelSource(CanvasApplication.getInstance().getResources(),
                          TEXTURE_CACHE,
                          Arrays.asList(new Pair<>(240, 330), new Pair<>(1430, 1530) )) )
*/
    };

    /**
     * An AnimModelSpec defines a set of animations for one model.
     */
    public static class AnimModelSpec {
        public String model;
        public String[] anims;
        public CachedSource<Model> cachedSourceModel;

        /**
         * @param model     Visual name of the model.
         * @param anims     Array of names of animations found in the model.  Alternatively,
         *                  these are visual names given to a list of unnamed animations (for
         *                  formats without named animations).
         * @param cachedSourceModel   A CachedSource<Model> that loads this model.
         */
        public AnimModelSpec(String model, String[] anims, CachedSource<Model> cachedSourceModel) {
            this.model = model;
            this.anims = anims;
            this.cachedSourceModel = cachedSourceModel;
        }
    }
}
