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
    public static Cache<GLESTexture> TEXTURE_CACHE = new Cache<>();

    public static final AnimDescriptions.AnimSpec[] MODEL_ANIMS = new AnimDescriptions.AnimSpec[] {
              new AnimDescriptions.AnimSpec("M. Chief",
                    new String[] { "idle", "something" },
                    new OgreModelSource(CanvasApplication.getInstance().getResources(),
                            TEXTURE_CACHE,
                            R.raw.m_chief_mesh, R.raw.m_chief_skeleton,
                            R.drawable.masterchief_base, R.drawable.masterchief_bump_displacement) )
/*
            , new AnimDescriptions.AnimSpec("Ninja",
                    new String[] { "stealth", "sword", "death", "idle" },
                    new Ms3dModelSource(CanvasApplication.getInstance().getResources(),
                            TEXTURE_CACHE,
                            R.raw.ninja,
                            Arrays.asList(new Pair<>(15, 30), new Pair<>(45, 59),
                                            new Pair<>(166, 173), new Pair<>(206, 250) )) )
*/ /*
            , new AnimDescriptions.AnimSpec("Alien", new String[] { "belly", "licking" },
                  new Ms3dModelSource(CanvasApplication.getInstance().getResources(),
                          TEXTURE_CACHE,
                          Arrays.asList(new Pair<>(240, 330), new Pair<>(1430, 1530) )) )
*/
    };
}
