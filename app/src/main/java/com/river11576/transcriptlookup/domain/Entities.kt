package com.river11576.transcriptlookup.domain

import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

/*
 * Although these may look like what has already been defined in Google's Youtube library
 * they are separated concerns: while these models can have domain logic, Youtube's DTOs are
 * plain and structured according to Google's API design.
 * They will diverse, as domain model can abstract away media sources (of which youtube is just one)
 */
open class Track(): RealmObject() {
    @PrimaryKey open var uri = ""
    open var title = ""
    open var description = ""
    open var thumbnail = ""
    open var transcripts: RealmList<Transcript>? = null

    constructor(uri: String, title: String, desc: String, thumb: String): this() {
        this.uri = uri; this.title = title; description = desc; thumbnail = thumb
    }
}

open class Transcript(): RealmObject() {
    open var lang = "en"
    open var captions: RealmList<Caption>? = null

    constructor(lang: String, captions: Array<Caption>): this() {
        this.lang = lang; this.captions = RealmList(*captions)
    }
}

open class Caption(): RealmObject() {
    var text = ""
    var timestamp: Int = 0
    var duration: Int = 0

    private companion object {
        fun s2ms(second: Float) = (second * 1000).toInt()
    }

    constructor(text: String, start: Float, duration: Float): this() {
        this.text = text; timestamp = s2ms(start); this.duration = s2ms(duration)
    }
}

data class QueryResult(
        var results: List<Track>,
        var total: Int = 0,
        var nextPageToken: String? = null
)
