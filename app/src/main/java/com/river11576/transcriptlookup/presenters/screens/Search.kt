package com.river11576.transcriptlookup.presenters.screens

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
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
import org.jetbrains.anko.custom.ankoView
import com.facebook.drawee.view.*
import com.river11576.transcriptlookup.domain.Track
import org.jetbrains.anko.recyclerview.v7.recyclerView
import rx.subjects.BehaviorSubject
import android.view.inputmethod.InputMethodManager


class SearchBar(val initialQuery: String=""): AnkoComponent<Context>, AnkoLogger, TextView.OnEditorActionListener {
    // this is a read-only stream to consumer, no need be exposed as a Subject
    val queryEvents: Observable<String> = PublishSubject.create()

    override fun createView(ui: AnkoContext<Context>) = with(ui) {
        linearLayout {
            editText(initialQuery).lparams(matchParent, wrapContent).apply {
                if(initialQuery.isNotEmpty()) fireQueryEvent(initialQuery)
                isFocusableInTouchMode = true
                requestFocus()
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                inputType = EditorInfo.TYPE_CLASS_TEXT
                setOnEditorActionListener(this@SearchBar)
            }
        }
    }

    private fun fireQueryEvent(query: String) = (queryEvents as Observer<String>).onNext(query)

    override fun onEditorAction(v: TextView, action: Int, e: KeyEvent?): Boolean {
        val query = "" + (v as EditText).editableText
        if(action == IME_ACTION_SEARCH && query.isNotBlank()) {
            fireQueryEvent(query)
            val imm = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0)
            return true
        }
        return false
    }
}

class ResultList(resultSource: Observable<QueryResult>, val itemPresenterFactory: () -> ResultPresenter):
        AnkoComponent<Context>,
        RecyclerView.Adapter<ResultList.ResultPresenter>(),
        ViewTreeObserver.OnGlobalLayoutListener,
        AnkoLogger
    {
    abstract class ResultPresenter(v: View) : RecyclerView.ViewHolder(v) {
        abstract var track: Track?
    }

    init { resultSource.subscribe { data = it } }

    var data by Delegates.observable<QueryResult?>(null) {
        p, o, n -> (listView.parent as View).invalidate(); notifyDataSetChanged()
    }

    override fun createView(ui: AnkoContext<Context>) = with(ui) {
        verticalLayout {
            listView = recyclerView {
                layoutManager = LinearLayoutManager(ctx)
                val owner= this@ResultList
                adapter = owner
                viewTreeObserver.addOnGlobalLayoutListener(owner)
            }.lparams { height = 0; weight = 1f }
            button("Next") {
                gravity = Gravity.END
            }
        }
    }


    private lateinit var listView: RecyclerView
    private var listHeight = PublishSubject.create<Int>()

    val pageSizeReadyEvents = listHeight.map {
        itemPresenterFactory().itemView.run {
            visibility = View.INVISIBLE
            val vg = listView.parent as ViewGroup
            vg.addView(this)
            vg.measure(0, 0)
            val pageSize = it / measuredHeight
            vg.removeView(this)
            pageSize
        }
    }

    override fun onGlobalLayout() {
        listView.viewTreeObserver.removeOnGlobalLayoutListener(this)
        listHeight.onNext(listView.measuredHeight)
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int) = itemPresenterFactory()

    override fun getItemCount() = data?.run { results?.size } ?:0

    override fun onBindViewHolder(presenter: ResultPresenter, i: Int) {
        presenter.track = data?.results!![i]
    }
}

class ResultViewHolder(ctx: Context): ResultList.ResultPresenter(LinearLayout(ctx)) {
    override var track by Delegates.observable<Track?>(null) { p, o, new ->
        title.text = new!!.title
        img.imageURI = Uri.parse(new!!.thumbnail)
    }

    init {
        with(itemView as ViewGroup) {
            img = ankoView(::SimpleDraweeView, 0) {
                layoutParams = ViewGroup.LayoutParams(dip(80), wrapContent)
                aspectRatio = 1.33f
            }

            title = textView {
                onClick {
                    Flow.get(this).set(PlayerScreen.Key(track!!.uri))
                }
            }
        }
    }

    private lateinit var title: TextView
    private lateinit var img: SimpleDraweeView
}

class SearchScreen(val props: Key): AnkoComponent<Activity>, UseCaseFacilitator {
    class Key(var query: String="", var page: String ?= null)

    @Inject override lateinit var interactor: UseCases

    override fun createView(ui: AnkoContext<Activity>) = with(ui) {
        val searchBar = SearchBar(props.query)
        val pageSize = BehaviorSubject.create<Int>()

        val resultStream = searchBar.queryEvents
            .doOnNext{ props.query = it }
            .flatMap { query -> pageSize
                .observeOn(Schedulers.io())
                .map { interactor.search(query, "en", it, props.page) }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext{ props.page = it.nextPageToken }


        val resultList = ResultList(resultStream){ ResultViewHolder(ctx) }.apply {
            pageSizeReadyEvents.subscribe(pageSize)
        }

        verticalLayout {
            mount(searchBar)
            mount(resultList).lparams { height = 0; weight = 1f }
        }
    }
}
