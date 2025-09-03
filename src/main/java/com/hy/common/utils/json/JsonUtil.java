package com.hy.common.utils.json;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.SimpleType;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class JsonUtil {

    public final static ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 多余的值不解析
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 解析器支持解析单引号
        mapper.configure(Feature.ALLOW_SINGLE_QUOTES, true);
        // 解析器支持解析结束符
        mapper.configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);
        // mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        mapper.configure(Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        // Include.NON_EMPTY 属性为 空（“”） 或者为 NULL 都不序列化
        // Include.NON_NULL 属性为NULL 不序列化
        mapper.setSerializationInclusion(Include.NON_EMPTY);

    }

    public static String toJson(Object obj) {
        String json = null;
        if (obj == null) {
            return json;
        }
        try {
            json = mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("转换JSON异常： ", e);
            throw new RuntimeException(e);
        }
        return json;
    }

    public static <T> T toBean(String jsonStr, Class<T> objClass) {
        if (jsonStr == null) {
            return null;
        }
        T t;
        try {
            t = mapper.readValue(jsonStr, objClass);
        } catch (Exception e) {
            log.error("objClass转换BEAN异常详情：" + jsonStr, e);
            throw new RuntimeException(e);
        }
        return t;
    }

    public static <T> T toBean(Reader reader, Type type) {
        T t;
        try {
            t = mapper.readValue(reader, mapper.constructType(type));
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return t;
    }

    public static <T> T toBean(InputStream in, Type type) {
        T t = null;
        try {
            t = mapper.readValue(in, mapper.constructType(type));

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return t;
    }

    private static <T> T toBean(String jsonStr, JavaType type) {
        if (jsonStr == null) {
            return null;
        }
        T t;
        try {
            t = mapper.readValue(jsonStr, type);
        } catch (Exception e) {
            log.error("JavaType转换BEAN异常详情：" + jsonStr, e);
            throw new RuntimeException(e);
        }
        return t;
    }

    public static <T> T toBean(String jsonStr, Class<?> c1, Class<?> c2, Class<?> c3) {
        return toBean(jsonStr, mapper.getTypeFactory().constructParametricType(c1,
                mapper.getTypeFactory().constructParametricType(c2, c3)));
    }

    public static <T> T toBean(String jsonStr, Class<?> collectionClass, Class<?>... elementClasses) {
        return toBean(jsonStr, mapper.getTypeFactory().constructParametricType(collectionClass, elementClasses));
    }

    private static <T> T toCollection(String jsonStr, Class<?> collectionClass, Class<?>... elementClasses) {
        return toBean(jsonStr, mapper.getTypeFactory().constructParametricType(collectionClass, elementClasses));
    }

    public static <T> T toSet(String jsonStr, Class<?>... elementClasses) {
        return toCollection(jsonStr, Set.class, elementClasses);
    }

    public static <T> T toList(String jsonStr, Class<?>... elementClasses) {
        return toCollection(jsonStr, List.class, elementClasses);
    }

    public static <T> T toMap(String jsonStr) {
        return toBean(jsonStr, mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
    }

    public static <T> T toMap(String jsonStr, Class<?> c1, Class<?> c2, Class<?>... c3) {
        JavaType keyType = SimpleType.constructUnsafe(c1);
        JavaType valueType = mapper.getTypeFactory().constructParametricType(c2, c3);
        return toBean(jsonStr, mapper.getTypeFactory().constructMapType(Map.class, keyType, valueType));
    }

    public static <T> T convertValue(Object obj, Class<T> clazz) {
        return mapper.convertValue(obj, clazz);
    }

    public static <T> T convertValue(Object obj, Type type) {
        return mapper.convertValue(obj, mapper.constructType(type));
    }

}
