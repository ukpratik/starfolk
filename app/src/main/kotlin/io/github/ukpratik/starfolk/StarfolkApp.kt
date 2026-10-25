// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk

import android.app.Application

class StarfolkApp : Application() {
    /** Manual DI (ADR-004): every long-lived object hangs off this container, created lazily. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        io.github.ukpratik.starfolk.notify.Reminders.createChannels(this)
    }
}
