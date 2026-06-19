package com.ulysses.app

import android.app.Application
import com.ulysses.app.data.db.UlyssesDatabase
import com.ulysses.app.network.DhtNetworkManager

class UlyssesApp : Application() {

    val database: UlyssesDatabase by lazy {
        UlyssesDatabase.getInstance(this)
    }

    val networkManager: DhtNetworkManager by lazy {
        DhtNetworkManager(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Start network manager if device is in a group
        if (networkManager.isInGroup()) {
            networkManager.start()
        }
    }

    companion object {
        lateinit var instance: UlyssesApp
            private set
    }
}
