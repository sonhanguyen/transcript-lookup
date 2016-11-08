package com.river11576.transcriptlookup.presenters

import android.app.Activity
import android.content.Context
import com.river11576.transcriptlookup.domain.UseCases
import com.river11576.transcriptlookup.orchestration.OrchestrationModule
import com.river11576.transcriptlookup.presenters.screens.PlayerScreen
import com.river11576.transcriptlookup.presenters.screens.SearchScreen
import dagger.Component
import dagger.Provides
import flow.Dispatcher
import flow.Flow
import flow.Traversal
import flow.TraversalCallback
import org.jetbrains.anko.AnkoComponent
import org.jetbrains.anko.setContentView
import javax.inject.Singleton

class Main: Activity() {
    override fun attachBaseContext(baseContext: Context) {
        super.attachBaseContext(Router(this).install(baseContext))
    }

    override fun onBackPressed() {
        if(!Flow.get(this).goBack()) super.onBackPressed()
    }
}

class Router(private val activity: Activity): Dispatcher {
    private val injector by lazy {
        DaggerRootComponent.builder().appModule(AppModule(activity)).build()
    }

    fun install(baseContext: Context) =
        Flow.configure(baseContext, activity)
            .dispatcher(this)
            .defaultKey(null)
            .install()

    override fun dispatch(traversal: Traversal, callback: TraversalCallback) {
        val key = traversal.destination.top<Any>()
        val component: AnkoComponent<Activity> = when(key) {
            is PlayerScreen.Props -> PlayerScreen(key).apply { injector.inject(this) }
            else -> SearchScreen().apply { injector.inject(this) }
        }

        component.setContentView(activity)
        // alternatively, setup animation/delegate it to master view/presenter
        callback.onTraversalCompleted()
    }
}

@dagger.Module
class AppModule(val activity: Activity) {
    @Provides fun mainActivity(): Context = activity
}

interface UseCaseFacilitator {
    var interactor: UseCases // VIPER's terminology, I don't really care for it
}

@Singleton
@Component(modules = arrayOf(OrchestrationModule::class, AppModule::class))
interface RootComponent: UseCaseFacilitator {
    /*
     * even though it's a bit annoying to list all possible injections,
     * the plus side is visibility, as you can see which presenters have access to the component
     */
    fun inject(presenter: SearchScreen)
    fun inject(presenter: PlayerScreen)
}
