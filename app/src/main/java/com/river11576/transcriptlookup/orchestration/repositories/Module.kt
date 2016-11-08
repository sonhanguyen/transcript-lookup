package com.river11576.transcriptlookup.orchestration.repositories

import com.river11576.transcriptlookup.domain.Transcript
import com.river11576.transcriptlookup.orchestration.MemoizedProvider
import com.river11576.transcriptlookup.orchestration.Provider
import com.river11576.transcriptlookup.orchestration.Store
import com.river11576.transcriptlookup.orchestration.services.youtube.YoutubeTranscriptService
import dagger.Module
import dagger.Provides
import io.realm.Realm
import rx.Single
import javax.inject.Singleton

@Module
class RepositoryModule {
    @Provides @Singleton fun provideTranscriptRepo(realm: Single<Realm>): TranscriptRepository =
        TranscriptRepositoryImpl(YoutubeTranscriptService(), TranscriptCache(realm))
}

class TranscriptRepositoryImpl(service: Provider<Transcript>, cache: Store<Transcript>):
        MemoizedProvider<Transcript>(service, cache), TranscriptRepository {
    override fun lookup(uri: String, lang: String) = get(uri, lang)
}