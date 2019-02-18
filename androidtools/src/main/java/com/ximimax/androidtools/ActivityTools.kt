package com.ximimax.androidtools

import android.app.Activity
import android.view.View
import android.content.ContextWrapper
import android.content.Intent


/**
 * "" AndroidTools
 * create by DuYang
 * e-mail:duyangs1994@gmail.com
 * update time 2019/2/18.
 */
object ActivityTools {

    /**
     * Return the activity by view.
     *
     * @param view The view.
     * @return the activity by view.
     */
    fun getActivity(view: View): Activity? {
        var context = view.context
        while (context is ContextWrapper) {
            if (context is Activity) {
                return context
            }
            context = context.baseContext
        }
        return null
    }

    fun isActivityExisits(packageName: String, className: String):Boolean{
        val intent = Intent()
        intent.setClassName(packageName, className)
        return
    }
}