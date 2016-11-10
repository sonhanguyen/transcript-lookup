package com.river11576.transcriptlookup.domain

interface UseCases {
    fun search(query: String, lang: String, pageSize: Int=0, token: String? = null): QueryResult
    // fun getTrack(uri: String): Track?
    fun getTranscript(uri: String, lang: String): Transcript?
}