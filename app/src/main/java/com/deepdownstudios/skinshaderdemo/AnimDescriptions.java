package com.deepdownstudios.skinshaderdemo;

/**
 * To use this, you create AnimDescriptions.AnimSpecs manually and pass
 * them at construction-time.  An AnimSpec defines a "family" of animations.
 * AnimDescriptions use an array of AnimSpecs to define a
 * "family of a family" of AnimSpecs.  AnimDescriptions enumerate
 * all members of this family so that clients can pull a specific one
 * and get a renderer for it.
 */
public class AnimDescriptions {

    public AnimDescriptions(AnimSpec[] animSpecs, Cache<Model> modelCache) {
        mAnimSpecs = animSpecs;
        mModelCache = modelCache;
    }

    public static class AnimSpec {
        public String model;
        public String[] anims;
        public Source<Model> modelSource;

        public AnimSpec(String model, String[] anims, Source<Model> modelSource) {
            this.model = model;
            this.anims = anims;
            this.modelSource = modelSource;
        }
    }

    public AnimationRenderer getAnimRenderer(int index) {
        int animDescriptorCount = getAnimDescriptorCount();
        index = index % animDescriptorCount;

        int model = 0;
        while (model < mAnimSpecs.length) {
            int nDescs = mAnimSpecs[model].anims.length * AnimationRenderer.AnimBlendType.values().length;    // There are 3 blend types
            if (index >= nDescs) {
                index -= nDescs;
                model++;
            } else {
                return new AnimationRenderer(new SourcedCache<>(mModelCache, mAnimSpecs[model].modelSource),
                        mAnimSpecs[model].model,        // model name
                        mAnimSpecs[model].anims[index / AnimationRenderer.AnimBlendType.values().length],   // anim name
                        index / AnimationRenderer.AnimBlendType.values().length,    // anim index
                        AnimationRenderer.AnimBlendType.values()[index % AnimationRenderer.AnimBlendType.values().length]); // shader blend mechanism
            }
        }
        throw new IllegalArgumentException("There must be a math bug here or index was too big.");
    }

    public int getAnimDescriptorCount() {
        int model = 0;
        int count = 0;
        while (model < mAnimSpecs.length) {
            count += mAnimSpecs[model].anims.length * AnimationRenderer.AnimBlendType.values().length;
            model++;
        }
        return count;
    }

    private AnimSpec[] mAnimSpecs;
    private Cache<Model> mModelCache;
}
