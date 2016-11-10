package com.river11576.transcriptlookup.presenters

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewManager
import android.widget.FrameLayout
import org.jetbrains.anko.AnkoComponent
import org.jetbrains.anko.AnkoContext
import org.jetbrains.anko.setContentView
import org.jetbrains.anko.wrapContent
import rx.Completable
import rx.subjects.AsyncSubject

fun <T> AnkoContext<T>.render(component: AnkoComponent<out T>) =
    (component as AnkoComponent<T>).createView(this)

fun <T> Context.render(component: AnkoComponent<T>, owner: T) =
    component.createView(AnkoContext.createReusable(this, owner))

fun <T: Context> Context.render(component: AnkoComponent<T>) =
    render(component as AnkoComponent<Context>, this)

fun <T: AnkoComponent<out Context>> ViewGroup.mount(component: T, onMount: (T.() -> Unit) ?= null) =
    context.render(component as AnkoComponent<Context>).apply {
        addView(this)
        onMount?.invoke(component)
    }

class EventEmitterWrapper<T: View>(wrapped: T): FrameLayout(wrapped.context) {
    init {
        layoutParams = wrapped.layoutParams
            ?:LayoutParams(wrapContent, wrapContent)

        super.addView(wrapped, layoutParams)
    }

    val detachEvent by lazy { Completable.fromObservable(mDetachEvent) }
    fun interceptKeyEvent(handle: (KeyEvent) -> Boolean) { keyEventCallback = handle }
    var keyEventCallback: (KeyEvent) -> Boolean = { false }; private set

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mDetachEvent.onCompleted()
    }
    override fun onKeyPreIme(code: Int, e: KeyEvent): Boolean = keyEventCallback(e)

    private val mDetachEvent by lazy { AsyncSubject.create<Nothing>() }
}

fun <T: View> ViewManager.withHooks(view: T, provideHook: EventEmitterWrapper<T>.() -> Unit): View {
    try { removeView(view) } // undo anko dsl method's side effect in case view was created that way
    catch(notCritical: Exception) { }
    return EventEmitterWrapper(view).let {
        addView(it, it.layoutParams)
        it.apply(provideHook)
    }
}

abstract class ComponentPreview<T: Activity>(val component: AnkoComponent<T>)
        : AnkoComponent<ComponentPreview<T>>, Activity()
    {
    private val mComponent = component as AnkoComponent<Activity>

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        mComponent.setContentView(this)
    }

    override fun createView(ui: AnkoContext<ComponentPreview<T>>) =
        mComponent.createView(ui as AnkoContext<Activity>)
}
