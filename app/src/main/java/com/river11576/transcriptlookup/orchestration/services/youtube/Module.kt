package com.river11576.transcriptlookup.orchestration.services.youtube

import com.river11576.transcriptlookup.domain.QueryResult
import com.river11576.transcriptlookup.domain.Track
import com.river11576.transcriptlookup.domain.Transcript
import com.river11576.transcriptlookup.domain.UseCases
import com.river11576.transcriptlookup.orchestration.repositories.TranscriptRepository
import io.realm.Realm
import rx.Single
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject

class Youtube @Inject constructor(var transcripts: TranscriptRepository, var realm: Single<Realm>): UseCases {
    companion object {
        val client = YoutubeClient("AIzaSyD_tyTzzuvYLBlLsAilZsQGIsloVtAFE04")
    }

    override fun search(query: String, lang: String, pageSize: Int, page: String?): QueryResult {
        val result = client.search(query, pageSize, lang, pageToken = page, cc = true)
        return realm
            .observeOn(AndroidSchedulers.mainThread())
            .map { realm ->
                realm.beginTransaction()
                result.items
                    .map {
                        val id = it.id.videoId
                        realm.where(Track::class.java)
                            .equalTo("uri", id)
                            .findFirst()
                            ?:run {
                                it.snippet
                                    .run { Track(id, title, description, thumbnails.default.url) }
                                    .apply { realm.copyToRealm(this) }
                            }
                    }
                    .apply { realm.commitTransaction() }
            }
            .map { QueryResult(it, result.pageInfo.totalResults, result.nextPageToken) }
            .toBlocking()
            .value()
    }

    override fun getTranscript(uri: String, lang: String): Transcript? = transcripts.lookup(uri, lang)
}

