package com.radley.homefit

import android.app.Application
import com.radley.homefit.di.ServiceLocator
import com.radley.homefit.platform.Notifications

class HomeFitApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        Notifications.createChannels(this)
    }
}
