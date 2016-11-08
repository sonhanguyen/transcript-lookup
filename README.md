My learning project in getting back to Android development: an app that searches for youtube video and play them with transcript. It surely is lame, but the point is rather to investigate modern Android libraries.

Kotlin
- Needless to say how awesome it is, the only downside so far is that Android Studio's autocomple really struggles with type inference in my 8G laptop.
- Its stdlib eliminates the need of something like guava, or apache-commons.
- As a language I like xtend from Eclipse better but it just doesn't have as much force behind as Kotlin.

Anko
- Look ma, no layout directory!
- Its approach saves the temptation to include a host of libraries: google data binding, butter knife and what not
- If you're familiar with react's jsx, you already know why this is a good idea: collocate code that shares the same concern, eradicate uneccesary layering, towards a more feature-oriented codebase.
- Speaking of react, lattekit and anvil are other projects that do virtual dom, but they're not very mature (neither is this one).
- Undestand that realistically you can't just get rid of xml, I don't see how they won't work together.

Dagger 2
- Not really need it for such small project, but this is not a production app and I can do whaterver I want so... used to inject interactor into presenters.
- Have not used view scope or anything like Mortar

Flow
- As this follows Square's approach of single Activity, View based components, I thought I'd need it. Whether it is a good choice is to be validated by more complex usecases.

Rx
- I honestly can't imagine going back to handwritten CompositeListener

Realm
- For caching

Implementation notes:
- The architecture is loosely based on VIPER.
- For the presentation layer, I took cues from cyclejs, where every presenter has 2 sets of properties:
-- "source": input to render view, may have custom setter for reactivity (of course subject/observer works too but that seems to much)
-- "sink", which is result of view binding: Rx subjects exposed as observables
