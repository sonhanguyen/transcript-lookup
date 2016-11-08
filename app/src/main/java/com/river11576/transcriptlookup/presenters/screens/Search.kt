package com.river11576.transcriptlookup.presenters.screens

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import com.river11576.transcriptlookup.domain.QueryResult
import com.river11576.transcriptlookup.domain.UseCases
import com.river11576.transcriptlookup.presenters.*
import flow.Flow
import org.jetbrains.anko.*
import rx.Observable
import rx.Observer
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import javax.inject.Inject
import kotlin.properties.Delegates

class SearchBar: AnkoComponent<Context> {
    data class Query(val query: String, val page: String? = null)

    // this is a read-only stream to consumer, no need be exposed as a Subject
    val queryEvents: Observable<Query> = PublishSubject.create()

    override fun createView(ui: AnkoContext<Context>) = with(ui) {
        linearLayout {
            val query = editText()
                .lparams { width = 0; weight = 1f }
                .editableText

            button(">") {
                gravity = Gravity.END
                onClick {
                    val query = "" + query
                    if(query.isNotBlank()) (queryEvents as Observer<Query>).onNext(Query(query))
                }
            }
        }
    }
}

class ResultList(var resultSource: Observable<QueryResult>): AnkoComponent<Context>, AnkoLogger {

    var data by Delegates.observable<QueryResult?>(null) {
        p, o, n -> renderList()
    }

    override fun createView(ui: AnkoContext<Context>) = with(ui) {
        verticalLayout {
            results = this // "this" is the LinearLayout closure, not the ResultList object
        }
    }

    private var results by Delegates.observable<ViewGroup?>(null) {
        p, o, n -> resultSource.subscribe { data = it }
    }

    private fun renderList() {
        results?.run {
            removeAllViews()
            data?.results?.forEach {
                val track = it
                textView {
                    text = it.title
                    onClick {
                        Flow.get(this).set(PlayerScreen.Props("uri" to track.uri))
                    }
                }
            }
            invalidate()
        }
    }
}

class SearchScreen: AnkoComponent<Activity>, UseCaseFacilitator {
    @Inject override lateinit var interactor: UseCases

    override fun createView(ui: AnkoContext<Activity>) = with(ui) {
        val searchBar = SearchBar()
        val resultStream = searchBar.queryEvents
            .observeOn(Schedulers.io())
            .map { interactor.search(it.query, "en") }
            .observeOn(AndroidSchedulers.mainThread())

        verticalLayout {
            mount(searchBar)
            mount(ResultList(resultStream))
        }
    }
}
