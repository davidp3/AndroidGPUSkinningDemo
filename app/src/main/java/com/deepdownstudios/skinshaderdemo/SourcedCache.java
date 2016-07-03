package com.deepdownstudios.skinshaderdemo;

/**
 * A Cache that is bound to a Source so that it can construct items
 * that are requested but not found.
 */
public class SourcedCache<V> extends Cache<V> implements Source<V> {

    private final Cache<V> mCache;
    private Source<V> mSource;

    /**
     * Bind an existing cache to an existing source.
     */
    public SourcedCache(Cache<V> cache, Source<V> source) {
        mCache = cache;
        mSource = source;
    }

    /**
     * Bind a new cache to an existing source.
     */
    @SuppressWarnings("unused")
    public SourcedCache(Source<V> source) {
        mCache = new Cache<>();
        mSource = source;
    }

    /**
     * Fetch using the bound source.
     * @param name  Name of the entity to find in the cache or create.
     * @return      The entity.
     */
    public V fetch(String name) {
        return fetch(name, mSource);
    }

    @Override
    public V load() {
        return mSource.load();
    }

    @SuppressWarnings("unused")
    public Cache<V> getCache() {
        return mCache;
    }

    public Source<V> getSource() {
        return mSource;
    }
}
