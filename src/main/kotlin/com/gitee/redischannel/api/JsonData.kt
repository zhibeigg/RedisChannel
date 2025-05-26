package com.gitee.redischannel.api

interface JsonData {

    /**
     * 序列化到 Json
     * */
    fun toJson(): String

    /**
     * 从 Json 实例化
     * */
    fun fromJson(json: String): JsonData
}