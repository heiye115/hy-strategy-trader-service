package com.hy.common.utils.http;


import cn.hutool.core.util.StrUtil;
import com.hy.common.utils.json.JsonUtil;
import okhttp3.*;
import okhttp3.Request.Builder;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class OkHttpUtil {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");


    private static final OkHttpClient client = new OkHttpClient
            .Builder()
            .connectionPool(new ConnectionPool(5, 10, TimeUnit.SECONDS))
            .build();

    public static String get(String url) throws IOException {
        Request request = new Builder().url(url).build();
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("Unexpected code " + response);
        }
        return response.body().string();
    }

    public static String get(String httpUrl, String name, String value) {
        String result = null;
        try {
            Builder builder = new Builder().url(httpUrl);
            Response execute;
            if (name != null) {
                builder.addHeader(name, value);
            }
            execute = client.newCall(builder.build()).execute();
            if (execute.isSuccessful()) {
                result = execute.body().string();
                execute.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static String get(String httpUrl, Map<String, String> headerMap) {
        String result = null;
        try {
            Builder builder = new Builder().url(httpUrl);
            Response execute = null;
            if (headerMap != null && !headerMap.isEmpty()) {
                headerMap.forEach((k, v) -> builder.addHeader(k, v));
            }
            execute = client.newCall(builder.build()).execute();
            if (execute.isSuccessful()) {
                result = execute.body().string();
                execute.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static String post(String httpUrl, Map<String, Object> maps) throws IOException {
        return post(httpUrl, maps, null, null);
    }

    public static String post(String httpUrl, Map<String, Object> maps, String name, String value)
            throws IOException {
        String result = null;
        FormBody.Builder builder = new FormBody.Builder();
        for (Map.Entry<String, Object> entry : maps.entrySet()) {
            if (entry.getValue() != null) {
                builder.add(entry.getKey(), entry.getValue().toString());
            }
        }
        Builder post = new Builder().url(httpUrl).post(builder.build());
        Response execute = null;
        if (StrUtil.isNotBlank(name) && StrUtil.isNotBlank(value)) {
            post.addHeader(name, value);
            execute = client.newCall(post.build()).execute();
        } else {
            execute = client.newCall(post.build()).execute();
        }
        if (execute.isSuccessful()) {
            result = execute.body().string();
            execute.close();
        }
        return result;
    }


    public static String postJson(String url, String json, Map<String, String> header) throws IOException {
        RequestBody requestBody = RequestBody.create(json, JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .headers(Headers.of(header))
                .build();
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("Unexpected code " + response);
        }
        return response.body().string();
    }

//    public static String postJson(String url, Map<String, String> map) throws IOException {
//        return postJson(url, JsonUtil.toJson(map), new HashMap<>());
//    }

    public static <T> T postJson(String url, Map<String, String> map, Map<String, String> header, Class<T> objClass) throws IOException {
        return JsonUtil.toBean(postJson(url, JsonUtil.toJson(map), header), objClass);
    }
}
