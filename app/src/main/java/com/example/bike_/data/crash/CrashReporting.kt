package com.example.bike_.data.crash

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Thin wrapper around Crashlytics so callers never have to care about SDK availability.
 * All reporting is best-effort: analytics must never affect tracking or data storage.
 */
object CrashReporting {
    fun initialize(collectionEnabled: Boolean, appVersion: String) {
        try {
            FirebaseCrashlytics.getInstance().apply {
                setCrashlyticsCollectionEnabled(collectionEnabled)
                setCustomKey("app_version", appVersion)
            }
        } catch (_: Throwable) {
        }
    }

    fun record(throwable: Throwable, context: String? = null) {
        try {
            FirebaseCrashlytics.getInstance().apply {
                if (!context.isNullOrBlank()) log(context.take(256))
                recordException(throwable)
            }
        } catch (_: Throwable) {
        }
    }

    fun log(message: String) {
        try {
            FirebaseCrashlytics.getInstance().log(message.take(256))
        } catch (_: Throwable) {
        }
    }
}
