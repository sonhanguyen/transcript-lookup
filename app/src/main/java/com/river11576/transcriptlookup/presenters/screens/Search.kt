package com.river11576.transcriptlookup.presenters.screens

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.support.v7.widget.CardView
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
import android.widget.EditText
import android.widget.TextView
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
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import com.orangegangsters.github.swipyrefreshlayout.library.*
import rx.subjects.BehaviorSubject
import java.util.*

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

class ResultList(private val data: ArrayList<Track>, val itemPresenterFactory: () -> ResultPresenter):
        AnkoComponent<Context>,
        RecyclerView.Adapter<ResultList.ResultPresenter>(),
        SwipyRefreshLayout.OnRefreshListener,
        AnkoLogger
    {
    abstract class ResultPresenter(v: View): RecyclerView.ViewHolder(v) {
        abstract var track: Track?
    }

    fun consume(incomming: List<Track>) {
        data.addAll(incomming)
        refreshLayout.apply {
            isRefreshing = false
            notifyDataSetChanged()
            invalidate()
        }
    }

    fun refresh() {
        data.clear()
        refreshLayout.isRefreshing = true
    }

    private lateinit var refreshLayout: SwipyRefreshLayout

    override fun createView(ui: AnkoContext<Context>) = with(ui) {
        val listener = this@ResultList
        ankoView(::SwipyRefreshLayout, 0) {
            direction = SwipyRefreshLayoutDirection.BOTTOM
            setOnRefreshListener(listener)
            refreshLayout = this

            listView = recyclerView {
                lparams(matchParent, matchParent)
                layoutManager = LinearLayoutManager(ctx)
                adapter = listener
            }
        }
    }

    val requestNextEvents: Observable<Int> = BehaviorSubject.create(50)
    override fun onRefresh(direction: SwipyRefreshLayoutDirection) {
        (requestNextEvents as BehaviorSubject<Int>).onNext(50)
    }

    private lateinit var listView: RecyclerView

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int) = itemPresenterFactory()

    override fun getItemCount() = data.size

    override fun onBindViewHolder(presenter: ResultPresenter, i: Int) {
        presenter.track = data[i]
    }
}

class ResultViewHolder(ctx: Context): ResultList.ResultPresenter(FrameLayout(ctx)) {
    override var track by Delegates.observable<Track?>(null) { p, o, new ->
        title.text = new!!.title
        img.imageURI = Uri.parse(new!!.thumbnail)
    }

    init {
        with(itemView as FrameLayout) {
            setPadding(0, dip(8), 0, 0)
            layoutParams = ViewGroup.LayoutParams(matchParent, wrapContent)

            ankoView(::CardView, 0) {
                layoutParams = ViewGroup.LayoutParams(matchParent, wrapContent)
                cardElevation = dip(3).toFloat()
                radius = dip(12).toFloat()

                linearLayout {
                    img = ankoView(::SimpleDraweeView, 0) {
                        layoutParams = ViewGroup.LayoutParams(dip(100), wrapContent)
                        aspectRatio = 1.33f
                    }

                    title = textView {
                        setPadding(dip(3), 0, dip(3), 0)
                        onClick {
                            Flow.get(this).set(PlayerScreen.Key(track!!.uri))
                        }
                    }
                }
            }
        }
    }

    private lateinit var title: TextView
    private lateinit var img: SimpleDraweeView
}

class SearchScreen(val props: Key): AnkoComponent<Activity>, UseCaseFacilitator {
    class Key(val result: ArrayList<Track> = ArrayList<Track>(), var query: String="", var page: String ?= null)

    @Inject override lateinit var interactor: UseCases

    override fun createView(ui: AnkoContext<Activity>) = with(ui) {
        val searchBar = SearchBar(props.query)
        val resultList = ResultList(props.result) { ResultViewHolder(ctx) }

        Observable.combineLatest(
            searchBar.queryEvents, resultList.requestNextEvents
        ) { query, pageSize ->
                if(props.query != query) resultList.refresh()
                props.query = query
                pageSize
            }
            .observeOn(Schedulers.io())
            .map { interactor.search(props.query, "en", it, props.page) }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext{ props.page = it.nextPageToken }
            .subscribe {
                resultList.consume(it.results)
            }

        verticalLayout {
            setPadding(dip(8), 0, dip(8), 0)

            mount(searchBar)
            mount(resultList).lparams { height = 0; weight = 1f }
        }
    }
}
