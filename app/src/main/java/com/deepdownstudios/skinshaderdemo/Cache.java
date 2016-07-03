package com.deepdownstudios.skinshaderdemo;

import android.content.ComponentCallbacks2;
import android.os.Build;
import android.util.LruCache;

/**
 * Simple (lame) cache of data that respects low-memory warnings.
 */
public class Cache<V> {
    // Don't really care about this.  Eviction should be done based on low-memory indications.
    private static final int MAX_NUM_ITEMS = 128;

    private final LruCache<String, V> mLruCache = new LruCache<>(MAX_NUM_ITEMS);

    /**
     * Load from source or retrieve item from the cache.
     * Callers to this method must be prepared for the source to load the item.  For
     * example, if this is Android View-based then it must run on the main thread or the
     * source should asynchronously do that.
     * If the source is GLES-based then this method requires an active GLES
     * context on the GLES thread.
     * @param name          Key to cache/load the item with
     * @param itemSource    Source capable of loading the item in case it is not in the cache
     * @return  The item or null if it was not cached and failed to load
     */
    public V fetch(String name, Source<V> itemSource) {
        V item = mLruCache.get(name);
        if (item!= null) {
            return item;
        }
        item = itemSource.load();
        mLruCache.put(name, item);
        return item;
    }

    /**
     * Extra-lame policy for eviction.  This is more than enough for our dumb sample tho.
     * @param level See ComponentCallbacks2.onTrimMemory()
     */
    public void onTrimMemory(int level) {
        switch (level) {
            case ComponentCallbacks2.TRIM_MEMORY_BACKGROUND:
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE:
                trimToSize(2);
                break;
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW:
            case ComponentCallbacks2.TRIM_MEMORY_COMPLETE:
                trimToSize(1);
                break;
        }
    }

    // LRUCache is garbage before API 17, when trimToSize was introduced.
    // Before that, I just evict everything and don't give a f#$!
    private void trimToSize(int newSize) {
        // Pet peeve: Don't use Build-VERSION_CODES.  Its an extra layer of indirection that
        // obscures what we are doing, which is checking that we have at least API
        // level 17.  VERSION_CODES is marketing, not useful.
        if (Build.VERSION.SDK_INT >= 17) {
            mLruCache.trimToSize(newSize);
        } else {
            mLruCache.evictAll();
        }
    }

    public void clear() {
        mLruCache.evictAll();
    }
}
