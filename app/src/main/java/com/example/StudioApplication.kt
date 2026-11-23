package com.example

import android.app.Application
import com.example.service.StudioController

class StudioApplication : Application() {
  lateinit var studioController: StudioController
    private set

  override fun onCreate() {
    super.onCreate()
    studioController = StudioController(this)
  }
}
