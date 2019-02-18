package com.ximimax.androidtools

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.support.v4.content.FileProvider
import android.view.View
import android.view.inputmethod.InputMethodManager
import java.lang.reflect.InvocationTargetException
import java.util.*


/**
 * "" AndroidTools
 * create by DuYang
 * e-mail:duyangs1994@gmail.com
 * update time 2019/2/18.
 */
object Tools {

    init {
        UnsupportedOperationException("Can`t init")
    }

    private val ACTIVITY_LIFECYCLE = ActivityLifecycleImpl()
    private var application: Application? = null
    private const val PERMISSION_ACTIVITY_CLASS_NAME = "com.ximimax.androidtools.util.PermissionUtils\$PermissionActivity"//todo 更换为自己的

    fun init(context: Context?) {
        if (context == null) {
            Tools.init(getApplicationByReflect())
            return
        }
        Tools.init(context.applicationContext)
    }

    fun init(app: Application?) {
        if (application == null) {
            application = app ?: getApplicationByReflect()
            application!!.registerActivityLifecycleCallbacks(ACTIVITY_LIFECYCLE)
        } else {
            if (app != null) {
                if (app.javaClass != application!!.javaClass) {
                    application!!.unregisterActivityLifecycleCallbacks(ACTIVITY_LIFECYCLE)
                    ACTIVITY_LIFECYCLE.mActivityList.clear()
                    application = app
                    application!!.registerActivityLifecycleCallbacks(ACTIVITY_LIFECYCLE)
                }
            }
        }
    }

    fun getApplication(): Application {
        if (application != null) {
            return application as Application
        }
        val app = getApplicationByReflect()
        Tools.init(app)
        return app
    }


    private fun getApplicationByReflect(): Application {
        try {
            @SuppressLint("PrivateApi")
            val activityThread = Class.forName("android.app.ActivityThread")
            val thread = activityThread.getMethod("currentActivityThread").invoke(null)
            val app = activityThread.getMethod("getApplication").invoke(thread)
                    ?: throw NullPointerException("initialize the Tools first")
            return app as Application
        } catch (e: NoSuchMethodException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        }
        throw NullPointerException("initialize the Tools first")
    }

    fun getActivityLifecycle(): ActivityLifecycleImpl = ACTIVITY_LIFECYCLE

    fun getActivityList(): LinkedList<Activity> = ACTIVITY_LIFECYCLE.mActivityList

    fun getTopActivityOrApp(): Context = if (isAppForeground()) {
        val topActivity = ACTIVITY_LIFECYCLE.getTopActivity()
        topActivity ?: getApplication()
    } else {
        getApplication()
    }

    fun isAppForeground(): Boolean {
        val activityManager: ActivityManager = getApplication().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningAppProcessInfo = activityManager.runningAppProcesses
        if (runningAppProcessInfo.size == 0) return false
        for (info in runningAppProcessInfo) {
            if (info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                return info.processName == getApplication().packageName
            }
        }
        return false
    }

    class ActivityLifecycleImpl : Application.ActivityLifecycleCallbacks {

        val mActivityList = LinkedList<Activity>()
        val mStatusListenerMap = mutableMapOf<Any, OnAppStatusChangedListener>()
        val mDestroyedListener = mutableMapOf<Activity, Set<OnActivityDestroyedListener>>()

        private var mForegroundCount = 0
        private var mConfigCount = 0
        private var mIsBackground = false

        override fun onActivityCreated(p0: Activity?, p1: Bundle?) {
            if (p0 != null) {
                setTopActivity(p0)
            }
        }

        override fun onActivityStarted(p0: Activity?) {
            if (!mIsBackground) {
                if (p0 != null) {
                    setTopActivity(p0)
                }
            }
            if (mConfigCount < 0) {
                ++mConfigCount
            } else {
                ++mForegroundCount
            }
        }

        override fun onActivityResumed(p0: Activity?) {
            if (p0 != null){
                setTopActivity(p0)
            }
            if (mIsBackground){
                mIsBackground = false
                postStatus(true)
            }
        }

        override fun onActivityPaused(p0: Activity?) {

        }

        override fun onActivityStopped(p0: Activity?) {
            if (p0!!.isChangingConfigurations){
                --mConfigCount
            }else{
                --mForegroundCount
                if (mForegroundCount <= 0){
                    mIsBackground = true
                    postStatus(false)
                }
            }
        }

        override fun onActivitySaveInstanceState(p0: Activity?, p1: Bundle?) {

        }

        override fun onActivityDestroyed(p0: Activity?) {
            mActivityList.remove(p0!!)
            consumeOnActivityDestroyedListener(p0)
            fixSoftInputLeaks(p0)
        }

        private fun consumeOnActivityDestroyedListener(activity: Activity) {
            val iterator = mDestroyedListener.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key === activity) {
                    val value = entry.value
                    for (listener in value) {
                        listener.onActivityDestroyed(activity)
                    }
                    iterator.remove()
                }
            }
        }

        private fun fixSoftInputLeaks(activity: Activity?) {
            if (activity == null) return
            val imm = getApplication().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val leakViews = arrayOf("mLastSrvView", "mCurRootView", "mServedView", "mNextServedView")
            for (leakView in leakViews) {
                try {
                    val declaredField = InputMethodManager::class.java.getDeclaredField(leakView)
                            ?: continue
                    if (!declaredField.isAccessible) {
                        declaredField.isAccessible = true
                    }
                    val view = declaredField.get(imm) as? View ?: continue
                    if (view.rootView === activity.window.decorView.rootView) {
                        declaredField[imm] = null
                    }
                } catch (th: Throwable) {
                    th.printStackTrace()
                }

            }
        }


        private fun postStatus(isForeground: Boolean) {
            if (mStatusListenerMap.isEmpty()) return
            for (onAppStatusChangedListener in mStatusListenerMap.values) {
                if (isForeground) {
                    onAppStatusChangedListener.onForeground()
                } else {
                    onAppStatusChangedListener.onBackground()
                }
            }
        }

        fun getTopActivity(): Activity? {
            if (!mActivityList.isEmpty()) {
                return mActivityList.last
            }
            val topActivity = getTopActivityByReflect()
            if (topActivity != null) {
                setTopActivity(topActivity)
            }
            return topActivity
        }

        private fun setTopActivity(activity: Activity) {
            if (PERMISSION_ACTIVITY_CLASS_NAME == activity.javaClass.name) return
            if (mActivityList.contains(activity)) {
                if (mActivityList.last != activity) {
                    mActivityList.remove(activity)
                    mActivityList.addLast(activity)
                }
            } else {
                mActivityList.addLast(activity)
            }
        }


        private fun getTopActivityByReflect(): Activity? {
            try {
                @SuppressLint("PrivateApi")
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null)
                val activitiesField = activityThreadClass.getDeclaredField("mActivityList")
                activitiesField.isAccessible = true
                val activities = activitiesField.get(activityThread) as Map<*, *>
                if (activities.isEmpty()) return null
                for (activityRecord in activities.values) {
                    val activityRecordClass = activityRecord!!.javaClass
                    val pausedField = activityRecordClass.getDeclaredField("paused")
                    pausedField.isAccessible = true
                    if (!pausedField.getBoolean(activityRecord)) {
                        val activityField = activityRecordClass.getDeclaredField("activity")
                        activityField.isAccessible = true
                        return activityField.get(activityRecord) as Activity
                    }
                }
            } catch (e: ClassNotFoundException) {
                e.printStackTrace()
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            } catch (e: InvocationTargetException) {
                e.printStackTrace()
            } catch (e: NoSuchMethodException) {
                e.printStackTrace()
            } catch (e: NoSuchFieldException) {
                e.printStackTrace()
            }
            return null
        }

    }

    class FileProviderForTools : FileProvider() {
        override fun onCreate(): Boolean {
            Tools.init(context)
            return true
        }
    }

    interface OnAppStatusChangedListener {
        fun onForeground()

        fun onBackground()
    }

    interface OnActivityDestroyedListener {
        fun onActivityDestroyed(activity: Activity)
    }
}