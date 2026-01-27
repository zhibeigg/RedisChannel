package com.gitee.redischannel.core

import io.lettuce.core.MaintNotificationsConfig
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class MaintNotificationsConfigTest {

    @Test
    fun defaultsMaintenanceNotificationsDisabledWhenFalse() {
        val clazz = Class.forName("com.gitee.redischannel.core.MaintNotifications")
        val method = clazz.getMethod("fromEnabled", Boolean::class.javaPrimitiveType)
        val config = method.invoke(null, false) as MaintNotificationsConfig
        assertFalse(config.maintNotificationsEnabled(), "Expected maintenance notifications to be disabled when false")
    }

    @Test
    fun enablesMaintenanceNotificationsWhenTrue() {
        val clazz = Class.forName("com.gitee.redischannel.core.MaintNotifications")
        val method = clazz.getMethod("fromEnabled", Boolean::class.javaPrimitiveType)
        val config = method.invoke(null, true) as MaintNotificationsConfig
        assertTrue(config.maintNotificationsEnabled(), "Expected maintenance notifications to be enabled when true")
    }

    @Test
    fun configTemplateDefaultsToFalse() {
        val content = Files.readString(Paths.get("src/main/resources/config.yml"))
        val hasDefault = Regex("(?m)^\\s*maintNotifications:\\s*false\\s*$").containsMatchIn(content)
        assertTrue(hasDefault, "Expected maintNotifications default to be false in config.yml")
    }
}
