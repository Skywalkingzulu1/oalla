package com.doctorsonwheels.wheelmd

import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.lang.reflect.Field

fun ViewPager2.reduceSwipeSensitivity(factor: Int = 3) {
    try {
        val recyclerViewField: Field = ViewPager2::class.java.getDeclaredField("mRecyclerView")
        recyclerViewField.isAccessible = true
        val recyclerView = recyclerViewField.get(this) as RecyclerView

        val touchSlopField: Field = RecyclerView::class.java.getDeclaredField("mTouchSlop")
        touchSlopField.isAccessible = true
        val touchSlop = touchSlopField.get(recyclerView) as Int
        touchSlopField.set(recyclerView, touchSlop * factor)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
