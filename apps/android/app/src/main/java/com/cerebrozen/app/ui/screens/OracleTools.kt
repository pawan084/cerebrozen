package com.cerebrozen.app.ui.screens

import androidx.annotation.StringRes
import com.cerebrozen.app.R

/**
 * The Oracle's tool names, mapped to what a person should read.
 *
 * The server streams a `tool` frame the moment the agent decides to call a
 * tool — name only, never arguments, because arguments routinely quote the
 * user's own words (a journal title, a mood note). The name is a wire value
 * from `backend/app/agent/tools.py` (`TOOLS`); this map is the display half,
 * duplicated by hand like every cross-stack contract in this repo.
 *
 * An unknown name falls back to the generic label rather than showing the raw
 * identifier: `get_weekly_insights` on screen would read as a leak, and a new
 * backend tool must degrade to "your data", not to snake_case.
 *
 * Pure and JVM-tested ([OracleToolsTest]) — the composables only look up.
 */
@StringRes
internal fun oracleToolLabelRes(name: String): Int = when (name) {
    "get_weekly_insights" -> R.string.oracle_tool_weekly_insights
    "suggest_activity" -> R.string.oracle_tool_suggest_activity
    "log_mood" -> R.string.oracle_tool_log_mood
    "save_journal" -> R.string.oracle_tool_save_journal
    "log_sleep" -> R.string.oracle_tool_log_sleep
    else -> R.string.oracle_tool_generic
}
