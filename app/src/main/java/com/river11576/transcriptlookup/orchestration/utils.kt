package com.river11576.transcriptlookup.orchestration

import java.util.*

interface Provider<V> {
    fun get(vararg compositeKey: Any): V?
}

interface Store<V>: Provider<V> {
    fun put(vararg compositeKey: Any, value: V): V?
}

open class MemoizedProvider<V>(private var provider: Provider<V>, private var cache: Store<V>): Provider<V> {
    override fun get(vararg key: Any) =
        cache.get(*key)
            ?:(provider.get(*key)?.let {
                cache.put(*key, value = it); it
            })
}

class MultikeyMap<T>: Store<T> {
    private val map = HashMap<String, T>()
    override fun get(vararg key: Any) = map[key.joinToString()]
    override fun put(vararg key: Any, value: T) = map.put(key.joinToString(), value=value)
}
