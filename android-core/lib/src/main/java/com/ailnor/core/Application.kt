/* 
 * Copyright Erkinjanov Anaskhan, 14/02/2022.
 */

package com.ailnor.core

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import java.io.File

@SuppressLint("StaticFieldLeak")
open class Application: Application() {

    companion object{
        lateinit var context: Context
        lateinit var handler: Handler

        @JvmStatic
        fun getFilesDirFixed(): File {
            for (a in 0..9) {
                val path: File =
                    context.filesDir
                if (path != null) {
                    return path
                }
            }
            try {
                val info: ApplicationInfo = context.applicationInfo
                val path = File(info.dataDir, "files")
                path.mkdirs()
                return path
            } catch (e: Exception) {
//                FileLog.e(e)
            }
            return File("/data/data/uz.unical.bito/files")
        }

    }

    override fun onCreate() {
        super.onCreate()
        handler = Handler(applicationContext.mainLooper)
        context = applicationContext
    }

}