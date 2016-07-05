package com.deepdownstudios.skinshaderdemo;

/**
 * A Cache that is bound to a Source so that it can construct items
 * that are requested but not found.
 */
public class CachedSource<V> extends Cache<V> implements Source<V> {

    private final Cache<V> mCache;
    private Source<V> mSource;

    /**
     * Bind an existing cache to an existing source.
     */
    public CachedSource(Cache<V> cache, Source<V> source) {
        mCache = cache;
        mSource = source;
    }

    /**
     * Bind a new cache to an existing source.
     */
    @SuppressWarnings("unused")
    public CachedSource(Source<V> source) {
        mCache = new Cache<>();
        mSource = source;
    }

    /**
     * Fetch using the bound source.
     * @param name  Name of the entity to find in the cache or create.
     * @return      The entity.
     */
    public V fetch(String name) {
        return mCache.fetch(name, mSource);
    }

    public V fetch(String name, Source<V> source) {
        return mCache.fetch(name, source);      // ignores mSource
    }

    @Override
    public V load() {
        return mSource.load();
    }

    @Override
    public void onTrimMemory(int level) {
        mCache.onTrimMemory(level);
    }

    public void clear() {
        mCache.clear();
    }

    @SuppressWarnings("unused")
    public Cache<V> getCache() {
        return mCache;
    }

    public Source<V> getSource() {
        return mSource;
    }
}
