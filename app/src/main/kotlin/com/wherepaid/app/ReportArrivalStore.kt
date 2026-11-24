package com.wherepaid.app

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.YearMonth

/**
 * 마지막으로 본 월간 리포트의 달을 기억한다. 지도 홈의 "○월 리포트가 도착했어요" 배너가 쓴다.
 * 리포트 화면(S-08)을 열면 [markSeen]을 부른다. 기기에만 저장하는 작은 설정값이라 SharedPreferences로 둔다.
 */
class ReportArrivalStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("app_state", Context.MODE_PRIVATE)
    private val seen = MutableStateFlow(prefs.getString(KEY_LAST_SEEN, null)?.let(YearMonth::parse))

    /** 마지막으로 본 리포트의 달. 한 번도 안 봤으면 null. */
    val lastSeen: StateFlow<YearMonth?> = seen.asStateFlow()

    fun markSeen(month: YearMonth) {
        if (seen.value?.let { it >= month } == true) return
        prefs.edit { putString(KEY_LAST_SEEN, month.toString()) }
        seen.value = month
    }

    /** 데이터 초기화 때 같이 지운다. */
    fun clear() {
        prefs.edit { remove(KEY_LAST_SEEN) }
        seen.value = null
    }

    private companion object {
        const val KEY_LAST_SEEN = "report_last_seen_month"
    }
}
