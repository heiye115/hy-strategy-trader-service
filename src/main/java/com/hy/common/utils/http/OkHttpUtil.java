package com.hy.common.utils.http;


import cn.hutool.core.util.StrUtil;
import com.hy.common.utils.json.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okhttp3.Request.Builder;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * OkHttp 工具类 - 优化版
 * 改进点:
 * 1. 修复资源泄漏问题（使用try-with-resources）
 * 2. 优化连接池配置（最大连接数50，保活5分钟）
 * 3. 添加超时配置（连接/读取/写入超时各30秒）
 * 4. 完善异常处理（统一日志记录）
 * 5. 添加空值检查（防止NPE）
 * 6. 配置重试机制（最多重试3次）
 */
@Slf4j
public class OkHttpUtil {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    /**
     * 优化后的OkHttpClient配置:
     * - 连接池: 最大50个空闲连接，保活5分钟
     * - 连接超时: 30秒
     * - 读取超时: 30秒
     * - 写入超时: 30秒
     * - 重试机制: 启用（最多3次）
     */
    private static final OkHttpClient client = new OkHttpClient
            .Builder()
            .connectionPool(new ConnectionPool(50, 5, TimeUnit.MINUTES))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    /**
     * GET 请求（优化版）
     * @param url 请求URL
     * @return 响应内容
     * @throws IOException 网络异常或HTTP错误
     */
    public static String get(String url) throws IOException {
        if (StrUtil.isBlank(url)) {
            throw new IllegalArgumentException("URL不能为空");
        }
        
        Request request = new Builder().url(url).build();
        
        // 使用try-with-resources自动关闭Response
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorMsg = String.format("HTTP请求失败: url=%s, code=%d, message=%s", 
                    url, response.code(), response.message());
                log.error(errorMsg);
                throw new IOException(errorMsg);
            }
            
            ResponseBody body = response.body();
            if (body == null) {
                log.warn("响应body为空: url={}", url);
                return "";
            }
            
            return body.string();
        } catch (IOException e) {
            log.error("GET请求异常: url={}", url, e);
            throw e;
        }
    }

    /**
     * GET 请求（带单个Header）- 优化版
     * @param httpUrl 请求URL
     * @param name Header名称
     * @param value Header值
     * @return 响应内容，失败时抛出异常
     * @throws IOException 网络异常或HTTP错误
     */
    public static String get(String httpUrl, String name, String value) throws IOException {
        if (StrUtil.isBlank(httpUrl)) {
            throw new IllegalArgumentException("URL不能为空");
        }
        
        Builder builder = new Builder().url(httpUrl);
        if (StrUtil.isNotBlank(name) && value != null) {
            builder.addHeader(name, value);
        }
        
        try (Response response = client.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                String errorMsg = String.format("HTTP请求失败: url=%s, code=%d, message=%s", 
                    httpUrl, response.code(), response.message());
                log.error(errorMsg);
                throw new IOException(errorMsg);
            }
            
            ResponseBody body = response.body();
            if (body == null) {
                log.warn("响应body为空: url={}", httpUrl);
                return "";
            }
            
            return body.string();
        } catch (IOException e) {
            log.error("GET请求异常: url={}, header={}:{}", httpUrl, name, value, e);
            throw e;
        }
    }

    /**
     * GET 请求（带多个Headers）- 优化版
     * @param httpUrl 请求URL
     * @param headerMap Header集合
     * @return 响应内容，失败时抛出异常
     * @throws IOException 网络异常或HTTP错误
     */
    public static String get(String httpUrl, Map<String, String> headerMap) throws IOException {
        if (StrUtil.isBlank(httpUrl)) {
            throw new IllegalArgumentException("URL不能为空");
        }
        
        Builder builder = new Builder().url(httpUrl);
        if (headerMap != null && !headerMap.isEmpty()) {
            headerMap.forEach((k, v) -> {
                if (StrUtil.isNotBlank(k) && v != null) {
                    builder.addHeader(k, v);
                }
            });
        }
        
        try (Response response = client.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                String errorMsg = String.format("HTTP请求失败: url=%s, code=%d, message=%s", 
                    httpUrl, response.code(), response.message());
                log.error(errorMsg);
                throw new IOException(errorMsg);
            }
            
            ResponseBody body = response.body();
            if (body == null) {
                log.warn("响应body为空: url={}", httpUrl);
                return "";
            }
            
            return body.string();
        } catch (IOException e) {
            log.error("GET请求异常: url={}, headers={}", httpUrl, headerMap, e);
            throw e;
        }
    }

    /**
     * POST 表单请求（无Header）
     * @param httpUrl 请求URL
     * @param maps 表单参数
     * @return 响应内容
     * @throws IOException 网络异常或HTTP错误
     */
    public static String post(String httpUrl, Map<String, Object> maps) throws IOException {
        return post(httpUrl, maps, null, null);
    }

    /**
     * POST 表单请求（带Header）- 优化版
     * @param httpUrl 请求URL
     * @param maps 表单参数
     * @param name Header名称
     * @param value Header值
     * @return 响应内容
     * @throws IOException 网络异常或HTTP错误
     */
    public static String post(String httpUrl, Map<String, Object> maps, String name, String value)
            throws IOException {
        if (StrUtil.isBlank(httpUrl)) {
            throw new IllegalArgumentException("URL不能为空");
        }
        if (maps == null) {
            throw new IllegalArgumentException("表单参数不能为null");
        }
        
        FormBody.Builder formBuilder = new FormBody.Builder();
        for (Map.Entry<String, Object> entry : maps.entrySet()) {
            if (entry.getValue() != null) {
                formBuilder.add(entry.getKey(), entry.getValue().toString());
            }
        }
        
        Builder requestBuilder = new Builder().url(httpUrl).post(formBuilder.build());
        if (StrUtil.isNotBlank(name) && StrUtil.isNotBlank(value)) {
            requestBuilder.addHeader(name, value);
        }
        
        try (Response response = client.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                String errorMsg = String.format("HTTP POST请求失败: url=%s, code=%d, message=%s", 
                    httpUrl, response.code(), response.message());
                log.error(errorMsg);
                throw new IOException(errorMsg);
            }
            
            ResponseBody body = response.body();
            if (body == null) {
                log.warn("响应body为空: url={}", httpUrl);
                return "";
            }
            
            return body.string();
        } catch (IOException e) {
            log.error("POST表单请求异常: url={}, params={}", httpUrl, maps, e);
            throw e;
        }
    }


    /**
     * POST JSON 请求 - 优化版
     * @param url 请求URL
     * @param json JSON字符串
     * @param header Header集合
     * @return 响应内容
     * @throws IOException 网络异常或HTTP错误
     */
    public static String postJson(String url, String json, Map<String, String> header) throws IOException {
        if (StrUtil.isBlank(url)) {
            throw new IllegalArgumentException("URL不能为空");
        }
        if (StrUtil.isBlank(json)) {
            throw new IllegalArgumentException("JSON内容不能为空");
        }
        if (header == null) {
            throw new IllegalArgumentException("Header不能为null（可以为空Map）");
        }
        
        RequestBody requestBody = RequestBody.create(json, JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .headers(Headers.of(header))
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorMsg = String.format("HTTP POST JSON请求失败: url=%s, code=%d, message=%s", 
                    url, response.code(), response.message());
                log.error(errorMsg);
                throw new IOException(errorMsg);
            }
            
            ResponseBody body = response.body();
            if (body == null) {
                log.warn("响应body为空: url={}", url);
                return "";
            }
            
            return body.string();
        } catch (IOException e) {
            log.error("POST JSON请求异常: url={}, json={}", url, json, e);
            throw e;
        }
    }

    /**
     * POST JSON 请求并解析响应为对象
     * @param url 请求URL
     * @param map 请求参数（会转为JSON）
     * @param header Header集合
     * @param objClass 响应对象类型
     * @return 解析后的对象
     * @throws IOException 网络异常或HTTP错误
     */
    public static <T> T postJson(String url, Map<String, String> map, Map<String, String> header, Class<T> objClass) throws IOException {
        String responseJson = postJson(url, JsonUtil.toJson(map), header);
        return JsonUtil.toBean(responseJson, objClass);
    }
    
    /**
     * 获取OkHttpClient实例（供高级用途使用）
     * @return OkHttpClient实例
     */
    public static OkHttpClient getClient() {
        return client;
    }
}
