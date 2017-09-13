package com.example.retrofitcache.http;

import android.content.Context;
import android.util.Log;

import com.example.retrofitcache.MyApplication;
import com.example.retrofitcache.constants.HttpConstants;
import com.example.retrofitcache.util.NetWorkUtil;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by Administrator on 2017/2/20.
 */

public class HttpRequestsForCache {

    private static HttpRequestsForCache instance;

    //读超时长，单位：毫秒
    public static final int READ_TIME_OUT = 5000;
    //连接时长，单位：毫秒
    public static final int CONNECT_TIME_OUT = 5000;
    //写入时长，单位：毫秒
    public static final int WRITE_TIME_OUT = 5000;

    //短缓存1分钟
    public static final int CACHE_AGE_SHORT = 60;
    //长缓存有效期为1天
    public static final int CACHE_STALE_LONG = 60 * 60 * 24;

    private final Retrofit mRetrofit;
    private final HttpService mHttpService;

    // 是否使用缓存 默认关闭
    private static boolean mUseCache = false;

    public static HttpRequestsForCache getinstance() {
        if (instance == null) {
            synchronized (HttpRequestsForCache.class) {
                if (instance == null) {
                    instance = new HttpRequestsForCache();
                }
            }
        }
        return instance;
    }

    public static RequestParams createRequestParams() {
        //请求参数
        RequestParams localRequestParams = new RequestParams();
//        localRequestParams.put("t", (System.currentTimeMillis() + "").substring(0, 10));
        return localRequestParams;
    }

    public static RequestParams createRequestParams(Context context) {
        //请求参数
        RequestParams localRequestParams = new RequestParams();
//        localRequestParams.put("t", (System.currentTimeMillis() + "").substring(0, 10));
        return localRequestParams;
    }

    private HttpRequestsForCache() {

        File httpCacheDirectory = new File(MyApplication.context.getCacheDir(), "HttpCache");
        int cacheSize = 10 * 1024 * 1024; // 10 MiB
        Cache cache = new Cache(httpCacheDirectory, cacheSize);

        //开启Log
        HttpLoggingInterceptor logInterceptor = new HttpLoggingInterceptor();
        logInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        //手动创建一个OkHttpClient并设置超时时间
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .readTimeout(READ_TIME_OUT, TimeUnit.MILLISECONDS)
                .connectTimeout(CONNECT_TIME_OUT, TimeUnit.MILLISECONDS)
                .writeTimeout(WRITE_TIME_OUT, TimeUnit.MILLISECONDS)
                .addInterceptor(logInterceptor)
                .cache(cache)
//                .addNetworkInterceptor(REWRITE_CACHE_CONTROL_INTERCEPTOR)
                .addNetworkInterceptor(REWRITE_CACHE_CONTROL_INTERCEPTOR_)
                .build();

        mRetrofit = new Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl(HttpConstants.BASE_URL)
                //增加返回值为Gson的支持(以实体类返回)
                .addConverterFactory(GsonConverterFactory.create())
                //增加返回值为String的支持
//                .addConverterFactory(ScalarsConverterFactory.create())
                .build();
        mHttpService = mRetrofit.create(HttpService.class);
    }

    public HttpService getHttpService() {
        return mHttpService;
    }


    //方案一
    private final Interceptor REWRITE_CACHE_CONTROL_INTERCEPTOR = new Interceptor() {
        @Override
        public okhttp3.Response intercept(Chain chain) throws IOException {
            okhttp3.Response response = chain.proceed(chain.request());
            if (NetWorkUtil.isNetworkAvailable(MyApplication.context)) {
                Log.e("Interceptor", "response: " + response.toString());
                int maxAge = 60*60; // read from cache for 1 minute
                return response.newBuilder()
                        .removeHeader("Pragma")//清除头信息，因为服务器如果不支持，会返回一些干扰信息，不清除下面无法生效
                        .header("Cache-Control", "public, max-age=" + maxAge)
                        .build();
            } else {
                Log.e("Interceptor", "net not connect");
                int maxStale = 60 * 60 * 24 * 28; // tolerate 4-weeks stale
                return response.newBuilder()
                        .removeHeader("Pragma")//清除头信息，因为服务器如果不支持，会返回一些干扰信息，不清除下面无法生效
                        .header("Cache-Control", "public, only-if-cached, max-stale=" + maxStale)
                        .build();
            }
        }
    };



    // 响应头拦截器，用来配置缓存策略
    private Interceptor REWRITE_CACHE_CONTROL_INTERCEPTOR_ = new Interceptor() {
        @Override
        public Response intercept(Chain chain) throws IOException {

            CacheControl.Builder cacheBuilder = new CacheControl.Builder();
            cacheBuilder.maxAge(0, TimeUnit.SECONDS);
            cacheBuilder.maxStale(365, TimeUnit.DAYS);
            CacheControl cacheControl = cacheBuilder.build();

            Request request = chain.request();
            if (!NetWorkUtil.isNetworkAvailable(MyApplication.context)) {
                Log.e("Interceptor", "request: " + request.toString());
                //没网时只使用缓存
                //自定义请求头，可以在响应头对请求头的header进行拦截，配置不同的缓存策略
                request = request.newBuilder()
                        .header("head-request", request.toString())
                        .cacheControl(CacheControl.FORCE_CACHE)
//                        .cacheControl(cacheControl)
                        .build();
            }
            Response response = chain.proceed(request);
            if (NetWorkUtil.isNetworkAvailable(MyApplication.context)) {
                //有网的时候读接口上的@Headers里的配置，你可以在这里进行统一的设置
                Log.e("Interceptor", "response: " + response.toString());
                //添加头信息，配置Cache-Control
                //removeHeader("Pragma") 使缓存生效
                String cache = request.cacheControl().toString();
                return response.newBuilder()
                        .removeHeader("Pragma")
                        .header("Cache-Control", "public, max-age=" + CACHE_AGE_SHORT)
//                        .header("Cache-Control", cache)
                        .build();
            } else {
                Log.e("Interceptor", "net not connect");
                return response.newBuilder()
                        .removeHeader("Pragma")
                        .header("Cache-Control", "public,only-if-cached, max-stale=" + CACHE_STALE_LONG)
                        .build();
            }
        }
    };


}
