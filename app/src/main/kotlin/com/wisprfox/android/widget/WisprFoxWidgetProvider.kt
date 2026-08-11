package com.wisprfox.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.wisprfox.android.MainActivity
import com.wisprfox.android.R

/** Compact, explicit launcher widget; it never adds an always-listening path. */
class WisprFoxWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_wispr_fox)
            views.setOnClickPendingIntent(R.id.widget_speak, activityIntent(context, id, MainActivity.WIDGET_SPEAK))
            views.setOnClickPendingIntent(R.id.widget_upload, activityIntent(context, id, MainActivity.WIDGET_UPLOAD))
            manager.updateAppWidget(id, views)
        }
    }

    private fun activityIntent(context: Context, widgetId: Int, action: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_WIDGET_ACTION, action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            widgetId * 10 + if (action == MainActivity.WIDGET_SPEAK) 1 else 2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
