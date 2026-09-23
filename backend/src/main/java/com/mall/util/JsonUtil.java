package com.mall.util;

import com.alibaba.fastjson2.JSON;

public class JsonUtil {

    public static String toJson(Object obj) {
        return JSON.toJSONString(obj);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return JSON.parseObject(json, clazz);
    }
}
