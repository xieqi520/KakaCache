package com.im4j.kakacache.core.cache.memory;

import com.im4j.kakacache.common.exception.CacheException;
import com.im4j.kakacache.common.utils.Utils;
import com.im4j.kakacache.core.cache.CacheEntry;
import com.im4j.kakacache.core.cache.memory.journal.IMemoryJournal;
import com.im4j.kakacache.core.cache.memory.storage.IMemoryStorage;

/**
 * 内存缓存
 * @version 0.1 king 2016-04
 */
public final class MemoryCache {

    private final IMemoryStorage mStorage;
    private final IMemoryJournal mJournal;
    private final long mMaxSize;
    private final long mMaxQuantity;

    public MemoryCache(IMemoryStorage storage,
                       IMemoryJournal journal,
                       long maxSize,
                       long maxQuantity) {
        this.mStorage = storage;
        this.mJournal = journal;
        this.mMaxSize = maxSize;
        this.mMaxQuantity = maxQuantity;
    }

    /**
     * 读取
     * @param key
     * @param <T>
     * @return
     */
    public <T> T load(String key) throws CacheException {
        Utils.checkNotNull(key);

        CacheEntry entry = mJournal.get(key);
        if (entry == null) {
            return null;
        }

        // 过期自动清理
        if (entry.isExpiry()) {
            remove(key);
            return null;
        }

        // 读取缓存
        return (T) mStorage.load(key);
    }

    /**
     * 保存
     * @param expires 有效期（单位：秒）
     */
    public <T> void save(String key, T value, int expires) throws CacheException {
        Utils.checkNotNull(key);

        if (value == null) {
            remove(key);
            return;
        }

        // TODO 先写入，后清理。会超出限定条件，需要一定交换空间

        // 写入缓存
        mStorage.save(key, value);
        long createTime = System.currentTimeMillis();
        long expiresTime = createTime + expires;
        mJournal.put(key, new CacheEntry(key, createTime, expiresTime));

        // 清理无用数据
        clearUnused();
    }

    /**
     * 是否包含
     * @param key
     * @return
     */
    public boolean containsKey(String key) {
        return mJournal.containsKey(key);
    }

    /**
     * 删除缓存
     * @param key
     */
    public void remove(String key) throws CacheException {
        mStorage.remove(key);
        mJournal.remove(key);
    }

    /**
     * 清空缓存
     */
    public void clear() throws CacheException {
        mStorage.clear();
        mJournal.clear();
    }

    /**
     * 清理无用缓存
     */
    public void clearUnused() {
        // 清理过期
        for (CacheEntry entry : mJournal.snapshot()) {
            if (entry.isExpiry()) {
                remove(entry.getKey());
            }
        }

        // 清理超出缓存
        if (mMaxSize != 0) {
            while (mMaxSize < getTotalSize()) {
                remove(mJournal.getLoseKey());
            }
        }
        if (mMaxQuantity != 0) {
            while (mMaxQuantity < getTotalQuantity()) {
                remove(mJournal.getLoseKey());
            }
        }
    }

    /**
     * 缓存大小
     * @return 单位:byte
     */
    public long getTotalSize() {
        long size = mStorage.getTotalSize();
        Utils.checkNotLessThanZero(size);
        return size;
    }

    /**
     * 缓存个数
     * @return 单位:个数
     */
    public long getTotalQuantity() {
        long quantity = mStorage.getTotalQuantity();
        Utils.checkNotLessThanZero(quantity);
        return quantity;
    }

}