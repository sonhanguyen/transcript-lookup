package com.river11576.transcriptlookup.orchestration.services.youtube;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.util.List;

import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.simplexml.SimpleXmlConverterFactory;
import retrofit2.http.Query;
import rx.Observable;

import retrofit2.http.GET;

public abstract class TimedTextService {
    @Root public static class Transcript {
        @Root public static class Text {
            @Attribute public float start;
            @Attribute public float dur;
            @org.simpleframework.xml.Text public String text;
        }

        @ElementList(inline=true) public List<Text> lines;
    }

    public interface Api {
        @GET("api/timedtext")
        Observable<Transcript> transcriptForVideoId(@Query("v") String id, @Query("lang") String lang);
    }

    public static final Api CLIENT = new Retrofit.Builder()
        .baseUrl("http://www.youtube.com")
        .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
        .addConverterFactory(SimpleXmlConverterFactory.create())
        .build()
        .create(Api.class);
}
