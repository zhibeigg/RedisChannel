package com.gitee.redischannel.core

import io.lettuce.core.MaintNotificationsConfig

internal object MaintNotifications {

    @JvmStatic
    fun fromEnabled(enabled: Boolean): MaintNotificationsConfig {
        return if (enabled) {
            MaintNotificationsConfig.enabled()
        } else {
            MaintNotificationsConfig.disabled()
        }
    }
}
