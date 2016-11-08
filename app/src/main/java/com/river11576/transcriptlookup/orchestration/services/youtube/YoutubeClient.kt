package com.river11576.transcriptlookup.orchestration.services.youtube

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.SearchListResponse
import com.river11576.transcriptlookup.domain.*
import com.river11576.transcriptlookup.orchestration.Provider
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.error
import rx.android.schedulers.AndroidSchedulers

class YoutubeClient (val apiKey: String) {
    enum class CaptionFilter(val availability: String) {
        ANY("any"), CC("closedCaption"), NONE("none")
    }

    fun search(query: String,
               lang: String? = "en",
               videoCaption: CaptionFilter = lang?.let { CaptionFilter.CC } ?: CaptionFilter.ANY,
               cc: Boolean = false,
               pageToken: String? = null
    ): SearchListResponse {
        var search = youtubeClient.search()
                .list(PART)
                .setKey(apiKey)
                .set("type", "video")
                .set("q", if (cc) "\"$query\", cc" else query)

        if (CaptionFilter.CC == videoCaption) search = search
                .set("videoCaption", videoCaption.availability)

        lang?.run { search = search.set("relevanceLanguage", this) }

        return search.execute()
    }

    fun listCaptions(videoId: String) =
        youtubeClient.captions()
            .list(PART, videoId)
            .setKey(apiKey)
            .execute().items

    private val youtubeClient = YouTube(NetHttpTransport(), JacksonFactory(), { })

    companion object {
        private val PART = "snippet"
    }
}

class YoutubeTranscriptService: Provider<Transcript>, AnkoLogger {
    override fun get(vararg key: Any): Transcript? {
        val (id, lang) = key
        return TimedTextService.CLIENT.transcriptForVideoId("" + id, "" + lang)
            .map { it.lines.map { Caption(it.text, it.start, it.dur) } }
            .onErrorReturn {
                AnkoLogger@this.error(it)
                listOf<Caption>()
            }
            .map { Transcript(lang as String, it.toTypedArray()) }
            .observeOn(AndroidSchedulers.mainThread()) // for the RealmObject creation
            .toBlocking().first()
    }
}