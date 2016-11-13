My learning project in getting back to Android: an app that searches for youtube video and play them with transcript. It surely is lame, but the point is rather to investigate modern [Android development](http://github.com/sonhanguyen/transcript-lookup/blob/master/app/build.gradle):

Kotlin
- Needless to say how awesome it is, the only downside so far is that Android Studio's autocomple really struggles with type inference in my 8G laptop.
- Its stdlib eliminates the need of something like guava or apache-commons.
- As a language I like xtend from Eclipse better but it just doesn't have as much force behind as Kotlin.

Anko
- Look ma, no layout directory!
- Its approach saves the temptation to include a host of libraries: google data binding, butter knife and what not
- If you're familiar with react's jsx, you already know why this is a good idea: collocate code that shares the same concern, eradicate uneccesary layering, towards more atomic components and a more feature-oriented codebase.
- Speaking of react, lattekit and anvil are other projects that do virtual dom, but they're not very mature (neither is this one).
- Realistically you [can't just get rid of xml] (http://maximomussini.com/posts/anko-vs-android-xml), for reasons such as [this annoying implementation of AttributSet](http://reddit.com/r/androiddev/comments/2rmtgm/set_xml_attributes_programmatically_if_no_setter/cni3v3p)

Dagger 2
- Not really need it for such small project, but this is not a production app and I can do whaterver I want so... used to @Binds services' implementation to contracts then @Inject interactor into presenters.
- Have not used view scope or anything like Mortar

Flow
- As this follows Square's approach of single Activity, View based components, I thought I'd need it. Whether it is a good choice is to be validated by more complex usecases.
- The immutable key thing is not followed here. Since there has not been code written to deal with bundle, I just store state in the key which achieves the same effect.

Rx
- I honestly can't imagine going back to handwritten CompositeListener

Implementation notes:
* The architecture is loosely based on VIPER. In the domain layer I however took shortcut and made entities dependent on Realm (one can say this is Realm limitation of having an inheritance-based API)
* For the presentation layer, I took cues from cyclejs, where every presenter has 2 sets of properties:
  * "source": input to render view, may have custom setter for reactivity (of course rxrelay works too but I'm just avoiding too much of rx)
  * "sink", result of view binding: Rx subjects exposed as observables
