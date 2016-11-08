package com.river11576.transcriptlookup.orchestration.repositories

import com.river11576.transcriptlookup.domain.*
import com.river11576.transcriptlookup.orchestration.*
import io.realm.Realm
import rx.Single
import rx.android.schedulers.AndroidSchedulers

class TranscriptCache(private val realm: Single<Realm>): Store<Transcript> {
    override fun get(vararg key: Any): Transcript? {
        val (id, lang) = key
        return realm
            .observeOn(AndroidSchedulers.mainThread())
            .map {
                it.where(Track::class.java)
                    .equalTo("uri", id as String)
                    .findFirst()
                    ?.transcripts
                    ?.find { it.lang == lang }
            }
            .toBlocking().value()
    }

    override fun put(vararg key: Any, transcript: Transcript): Transcript? {
        val (id, lang) = key
        return realm
            .observeOn(AndroidSchedulers.mainThread())
            .map {
                val track = it.where(Track::class.java)
                    .equalTo("uri", id as String)
                    .findFirst()

                it.beginTransaction()
                track.transcripts?.add(transcript)
                it.commitTransaction()
                track
                    .transcripts
                    ?.find { it.lang == lang }
            }
            .toBlocking()
            .value()
    }
}

interface TranscriptRepository {
    fun lookup(uri: String, lang: String): Transcript?
}