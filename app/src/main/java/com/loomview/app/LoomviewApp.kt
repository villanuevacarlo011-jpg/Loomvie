package com.loomview.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * App 進入點。目前僅掛載 Hilt 供依賴注入使用；
 * 不在此處做任何跟 HTML 內容相關的初始化 —— App 對內容保持無知，
 * 所有內容解讀都交給被載入的 HTML 自己。
 */
@HiltAndroidApp
class LoomviewApp : Application()
