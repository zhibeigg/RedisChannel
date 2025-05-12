package com.gitee.redischannel.api

interface JsonData {

    fun toJson(): String

    fun fromJson(json: String): JsonData
}