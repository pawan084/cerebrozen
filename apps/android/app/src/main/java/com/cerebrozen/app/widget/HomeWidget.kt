package com.cerebrozen.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.cerebrozen.app.EXTRA_ROUTE
import com.cerebrozen.app.MainActivity

/**
 * The Today widget (HOME_SPEC #22): a one-tap "Talk it through" / "Breathe" launch straight
 * from the home screen. This first version is DELIBERATELY action-only, not a live mirror of
 * Today's streak/mood state — a widget runs in its own process on its own refresh model, and
 * making it track [com.cerebrozen.app.net.HomeCache] live would need a periodic Worker plus a
 * durable cross-process store, which is a real second project. Shipping the always-correct,
 * zero-navigation actions now and layering live data on later is the honest scoping call
 * (see docs/HOME_SPEC.md #22) — a widget that shows a STALE streak is worse than one that
 * shows no streak at all.
 */
private val WidgetNight = androidx.compose.ui.graphics.Color(0xFF0B0E24)
private val WidgetCoral = androidx.compose.ui.graphics.Color(0xFFF56B6B)
private val WidgetCream = androidx.compose.ui.graphics.Color(0xFFF3EDE7)

class HomeWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content() }
    }

    @Composable
    private fun Content() {
        Column(
            modifier = GlanceModifier.fillMaxSize()
                .background(ColorProvider(WidgetNight))
                .padding(14.dp),
        ) {
            Text(
                "CereBro",
                style = TextStyle(color = ColorProvider(WidgetCream), fontWeight = FontWeight.Bold),
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                "A moment for you",
                style = TextStyle(color = ColorProvider(WidgetCream)),
            )
            Spacer(modifier = GlanceModifier.height(10.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                WidgetButton("Talk it through", "coach", modifier = GlanceModifier.defaultWeight())
                Spacer(modifier = GlanceModifier.width(8.dp))
                WidgetButton("Breathe", "breathe/reset", modifier = GlanceModifier.defaultWeight())
            }
        }
    }

    @Composable
    private fun WidgetButton(label: String, route: String, modifier: GlanceModifier) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setClass(androidx.glance.LocalContext.current, MainActivity::class.java)
            putExtra(EXTRA_ROUTE, route)
        }
        Row(
            modifier = modifier
                .background(ColorProvider(WidgetCoral))
                .padding(10.dp)
                .clickable(actionStartActivity(intent)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = TextStyle(color = ColorProvider(WidgetNight), fontWeight = FontWeight.Medium))
        }
    }
}

/** The system talks to a Receiver, not the [GlanceAppWidget] directly — see
 *  res/xml/home_widget_info.xml for the provider metadata. */
class HomeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HomeWidget()
}
