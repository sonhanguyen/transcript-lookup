
package com.river11576.transcriptlookup.presenters.screens

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.jaedongchicken.ytplayer.YoutubePlayerView
import com.jaedongchicken.ytplayer.YoutubePlayerView.YouTubeListener
import com.river11576.transcriptlookup.domain.Caption
import com.river11576.transcriptlookup.domain.Transcript
import com.river11576.transcriptlookup.domain.UseCases
import com.river11576.transcriptlookup.presenters.UseCaseFacilitator
import com.river11576.transcriptlookup.presenters.mount
import com.river11576.transcriptlookup.presenters.withHooks
import org.jetbrains.anko.*
import org.jetbrains.anko.custom.ankoView
import org.jetbrains.anko.recyclerview.v7.recyclerView
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import java.util.*
import javax.inject.Inject
import kotlin.properties.Delegates

class TranscriptPlayer(playbackTimeEvents: Observable<Double>, val createViewHolder: () -> CaptionViewHolder)
        : AnkoComponent<Context>, RecyclerView.Adapter<RecyclerView.ViewHolder>(), AnkoLogger
    {
    init {
        playbackTimeEvents.subscribe {
            transcript?.captions?.indexOfFirst {
                caption -> caption.timestamp >= 1000 * it
            }?.let {
                var viewHolder = captionPresenter(highlight)
                viewHolder?.highlighted = false
                highlight = it

                listView.layoutManager.scrollToPosition(it)
                viewHolder = captionPresenter(it)
                viewHolder?.highlighted = true
            }
        }
    }
    var transcript by Delegates.observable(null as Transcript?) {
        p, o, n -> notifyDataSetChanged()
    }

    var highlight: Int = 0; private set

    override fun createView(ui: AnkoContext<Context>) = with(ui) {
        recyclerView {
            listView = this // because assignment is not an expression in kotlin
            layoutManager = LinearLayoutManager(ctx)
            adapter = this@TranscriptPlayer
        }
    }

    private lateinit var listView: RecyclerView
    private fun captionPresenter(i: Int) = listView.findViewHolderForAdapterPosition(i) as CaptionPresenter?

    private fun caption(i: Int) = transcript?.captions?.get(i)

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder?, i: Int) {
        (holder as CaptionPresenter).apply {
            caption = caption(i)
            highlighted = highlight == i
        }
    }

    val captionSelectedEvents: Observable<Caption> = PublishSubject.create()
    override fun onCreateViewHolder(parent: ViewGroup?, type: Int) = createViewHolder().apply {
        itemView.onClick {
            (captionSelectedEvents as PublishSubject).onNext(caption(adapterPosition))
        }
    }

    override fun getItemCount() = transcript?.run { captions?.size } ?: 0
}

abstract class CaptionPresenter(v: View): RecyclerView.ViewHolder(v) {
    abstract var caption: Caption?
    abstract var highlighted: Boolean
}

class CaptionViewHolder(ctx: Context): CaptionPresenter(LinearLayout(ctx)) {
    init {
        (itemView as ViewGroup).apply {
            title = textView()
        }
    }

    private var title: TextView? = null

    override var highlighted by Delegates.observable(false) {
        p, o, it -> itemView.backgroundColor = if(it) Color.YELLOW else Color.WHITE
    }

    override var caption by Delegates.observable(null as Caption?) {
        p, old, new -> title?.text = new!!.text
    }
}

class YoutubePlayer(val uri: String, val startAt: Double? = null): AnkoComponent<Activity>, YouTubeListener, AnkoLogger {
    override fun createView(ui: AnkoContext<Activity>) = AnkoContext.createReusable(ui.ctx, ui.owner).run {
        withHooks(
            verticalLayout {
                ankoView(::YoutubePlayerView, 0) {
                    yt = lparams(matchParent, wrapContent)
                    setAutoPlayerHeight(ctx)
                    initialize(uri, this@YoutubePlayer)
                }
            }, { detachEvent.subscribe {
                    yt.pause()
                    (timeEvents as PublishSubject).onCompleted()
                }
            }
        )
    }

    private lateinit var yt: YoutubePlayerView
    override fun onReady() {
        startAt?.run {
            yt.post{
                yt.play()
                seek(startAt)
            }
        }
    }

    fun seek(position: Double) {
        yt.seekToMillis(position/1000) //3rd library's naming is great, isn't it?
    }

    val timeEvents: Observable<Double> = PublishSubject.create()

    override fun onCurrentSecond(second: Double) {
        yt.post { (timeEvents as PublishSubject).onNext(second) }
    }

    override fun onStateChange(state: YoutubePlayerView.STATE?) { }

    override fun onDuration(duration: Double) { }
    override fun onPlaybackQualityChange(arg: String?) { }
    override fun onApiChange(arg: String?) { }
    override fun onPlaybackRateChange(arg: String?) { }

    override fun onError(log: String?) { error(log) }
    override fun logs(log: String?) { debug(log) }
}

class PlayerScreen(props: Props): AnkoComponent<Activity>, UseCaseFacilitator {
    class Props(vararg tuples: Pair<String, Any>): HashMap<String, Any>(mapOf(*tuples))
    val uri: String by props

    @Inject override lateinit var interactor: UseCases

    override fun createView(ui: AnkoContext<Activity>) = with(ui) {
        verticalLayout {
            var youtubePlayer = YoutubePlayer(uri, .0)
            var transcriptPlayer = TranscriptPlayer(youtubePlayer.timeEvents) { CaptionViewHolder(context) }

            mount(youtubePlayer)
            mount(transcriptPlayer)

            Observable.fromCallable { interactor.getTranscript(uri, "en") }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { transcriptPlayer.transcript = it }

            transcriptPlayer.captionSelectedEvents
                .subscribe {
                    youtubePlayer.seek(it.timestamp.toDouble())
                }
        }
    }
}

