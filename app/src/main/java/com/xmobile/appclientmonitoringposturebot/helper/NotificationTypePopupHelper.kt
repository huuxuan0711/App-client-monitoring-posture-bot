package com.xmobile.appclientmonitoringposturebot.helper

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import com.xmobile.appclientmonitoringposturebot.R

object NotificationTypePopupHelper {
    fun show(
        context: Context,
        anchorView: View,
        currentOption: String,
        onSelect: (selectedId: Int) -> Unit
    ) {
        val layout = LayoutInflater.from(context).inflate(R.layout.layout_type_notification, null)
        val popup = PopupWindow(
            layout,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)
        popup.showAsDropDown(
            anchorView,
            0,
            15
        )

        val ids = listOf(
            R.id.txtAll,
            R.id.txtMild,
            R.id.txtModerate,
            R.id.txtSevere
        )

        for (id in ids) {
            val tv = layout.findViewById<TextView>(id)
            // highlight option hiện tại
            if (tv.text.toString() == currentOption) {
                tv.setTextColor(context.resources.getColor(R.color.dark_blue, context.theme))
            } else {
                tv.setTextColor(context.resources.getColor(R.color.gray, context.theme))
            }
            tv.setOnClickListener {
                onSelect(id)
                popup.dismiss()
            }
        }
    }
}