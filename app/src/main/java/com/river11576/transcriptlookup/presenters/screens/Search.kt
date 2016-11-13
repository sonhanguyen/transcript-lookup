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
    private val mQueryEvents = BehaviorSubject.create<String>(initialQuery)
    val queryEvents = mQueryEvents.filter { it.isNotBlank() }

    override fun createView(ui: AnkoContext<Context>) = with(ui) {
        linearLayout {
            editText(initialQuery).lparams(matchParent, wrapContent).apply {
                isFocusableInTouchMode = true
                requestFocus()
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                inputType = EditorInfo.TYPE_CLASS_TEXT
                setOnEditorActionListener(this@SearchBar)
            }
        }
    }

    private fun fireQueryEvent(query: String) = mQueryEvents.onNext(query)

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
        abstract val clickEvents: Observable<Track>
    }

    fun consume(incomming: List<Track>) {
        data.addAll(incomming)
        notifyLoadingFinished()
    }

    fun notifyLoadingFinished() {
        refreshLayout.apply {
            isRefreshing = false
            notifyDataSetChanged()
            invalidate()
        }
    }

    fun scrollTo(pos: Int) {
        layoutReadyEvents.first().subscribe { it.scrollToPosition(pos) }
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

            recyclerView {
                lparams(matchParent, matchParent)
                layoutManager = LinearLayoutManager(ctx).apply { layoutReadyEvents.onNext(this) }
                adapter = listener
            }
        }
    }

    val navigationEvents: Observable<Int> = PublishSubject.create()
    val requestNextEvents: Observable<Int> = BehaviorSubject.create(50)
    override fun onRefresh(direction: SwipyRefreshLayoutDirection) {
        (requestNextEvents as BehaviorSubject<Int>).onNext(50)
    }

    private var layoutReadyEvents = BehaviorSubject.create<RecyclerView.LayoutManager>()

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int) = itemPresenterFactory().apply {
        clickEvents.subscribe { (navigationEvents as PublishSubject<Int>).onNext(adapterPosition) }
    }

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

    override val clickEvents: Observable<Track> = PublishSubject.create<Track>()

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
                        layoutParams = ViewGroup.LayoutParams(dip(90), wrapContent)
                        aspectRatio = 1.78f
                    }

                    title = textView {
                        setPadding(dip(3), 0, dip(3), 0)
                        onClick {
                            (clickEvents as PublishSubject<Track>).onNext(track)
                            Flow.get(ctx).set(PlayerScreen.Key(track!!.uri))
                        }
                    }
                }
            }
        }
    }

    private lateinit var title: TextView
    private lateinit var img: SimpleDraweeView
}

class SearchScreen(val props: Key): AnkoComponent<Activity>, UseCaseFacilitator, AnkoLogger {
    class Key(
        var query: String="",
        var page: String ?= null,
        val result: ArrayList<Track> = ArrayList<Track>(),
        var lastSelected: Int = 0,
        var total: Int? = null
    )

    @Inject override lateinit var interactor: UseCases

    override fun createView(ui: AnkoContext<Activity>) = with(ui) {
        val searchBar = SearchBar(props.query)
        val resultList = ResultList(props.result) { ResultViewHolder(ctx) }

        resultList.navigationEvents.subscribe { props.lastSelected = it }

        Observable.combineLatest(
            searchBar.queryEvents,
            resultList.requestNextEvents
        ) { searchTerms, pageSize ->
                with(props) {
                    if(searchTerms != query) resultList.refresh()
                    lastSelected.let { if(it > 0) resultList.scrollTo(it) }

                    query = searchTerms

                    pageSize
                }
            }
            .filter {
                (props.total?.let { props.result.size < it }?: true)
                    .apply { if(!this) resultList.notifyLoadingFinished() }
            }
            .observeOn(Schedulers.io())
            .map { interactor.search(props.query, "en", it, props.page) }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext{
                props.page = it.nextPageToken
                props.total = it.total
            }
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
