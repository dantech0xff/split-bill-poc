package com.example.splitbill

import android.app.Application

class SplitBillApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
