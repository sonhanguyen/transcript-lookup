package com.river11576.transcriptlookup.orchestration

import android.content.Context
import com.river11576.transcriptlookup.domain.UseCases
import com.river11576.transcriptlookup.orchestration.repositories.RepositoryModule
import com.river11576.transcriptlookup.orchestration.services.youtube.Youtube
import dagger.Binds
import dagger.Module
import dagger.Provides
import io.realm.Realm
import io.realm.RealmConfiguration
import rx.Completable
import rx.Observable
import rx.Scheduler
import rx.Single
import rx.android.schedulers.AndroidSchedulers
import rx.subjects.AsyncSubject
import javax.inject.Singleton

@Module(includes = arrayOf(RepositoryModule::class, StorageModule::class))
interface OrchestrationModule {
    @Binds fun bindToService(service: Youtube): UseCases
}

@Module
class StorageModule {
    enum class Stores { INMEMORY }

    @Provides fun provideRealm(context: Context): Single<Realm> =
        RealmProvider(context).getRealm(AndroidSchedulers.mainThread(), Stores.INMEMORY)!!
}

class RealmProvider(private val context: Context): Provider<Single<Realm>> {
    val stores = MemoizedProvider<Single<Realm>>(this, MultikeyMap<Single<Realm>>())

    val realmInitialized by lazy {
        Completable.fromAction { Realm.init(context) }
    }

    override fun get(vararg keys: Any): Single<Realm>? {
        val (scheduler, db) = keys

        val promise: AsyncSubject<Realm> = AsyncSubject.create()

        realmInitialized.andThen(
            Observable.fromCallable {
                val config = RealmConfiguration.Builder().name("" + db)
                Realm.getInstance(config.inMemory().build())
            }
        )
            // because realm makes threading such a big deal
            .subscribeOn(scheduler as Scheduler)
            .subscribe(promise)

        return promise.toSingle()
    }

    fun getRealm(scheduler: Scheduler, store: StorageModule.Stores) = stores.get(scheduler, store)
}
