package com.wherepaid.app.core.report

import com.wherepaid.app.core.model.MonthlyReportRequest
import com.wherepaid.app.core.model.ReportResult
import com.wherepaid.app.core.model.ReportWriter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * ④ LLM 중계 서버(`server/llm-relay`)를 부르는 [ReportWriter]. 요청·응답 형식은 그 폴더의 README가 기준이다.
 * 보내는 것은 [MonthlyReportRequest]의 월 집계뿐이다 (NFR-03). 요청·응답 본문을 로그로 남기지 않는다.
 */
class RelayReportWriter(
    private val baseUrl: String,
    private val token: String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ReportWriter {

    override suspend fun write(request: MonthlyReportRequest): ReportResult = withContext(io) {
        try {
            val connection = URL(baseUrl.trimEnd('/') + PATH).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                // 서버는 Gemini를 10초까지 기다린다. 앱은 그보다 길게 기다린다.
                connection.readTimeout = READ_TIMEOUT_MS
                connection.doOutput = true
                connection.setRequestProperty("content-type", "application/json; charset=utf-8")
                if (token.isNotEmpty()) connection.setRequestProperty("x-app-token", token)
                connection.outputStream.use { it.write(encodeRequest(request).toByteArray(Charsets.UTF_8)) }

                val code = connection.responseCode
                val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                parseResponse(code, body)
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            // 인터넷 없음, 시간 초과, 연결 실패
            ReportResult.Failed
        }
    }

    companion object {
        const val PATH = "/v1/monthly-report"
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 15_000

        /** 서버 README의 요청 본문. */
        fun encodeRequest(request: MonthlyReportRequest): String = JSONObject()
            .put("month", request.month.toString()) // "2026-10"
            .put("offlineTotal", request.offlineTotal)
            .put("onlineTotal", request.onlineTotal)
            .put("paymentCount", request.paymentCount)
            .put(
                "categories",
                JSONArray().apply {
                    request.categories.forEach {
                        put(JSONObject().put("name", it.name).put("total", it.total).put("count", it.count))
                    }
                },
            )
            .put("previousMonthTotal", request.previousMonthTotal ?: JSONObject.NULL)
            .toString()

        /** 200이고 칭호·요약이 모두 있을 때만 성공. 나머지는 실패. */
        fun parseResponse(code: Int, body: String?): ReportResult {
            if (code != 200 || body == null) return ReportResult.Failed
            return try {
                val json = JSONObject(body)
                val title = json.optString("title").trim()
                val summary = json.optString("summary").trim()
                if (title.isEmpty() || summary.isEmpty()) ReportResult.Failed else ReportResult.Success(title, summary)
            } catch (e: JSONException) {
                ReportResult.Failed
            }
        }
    }
}

/** 중계 서버 주소가 설정돼 있으면 실제 구현, 없으면 null (AppContainer가 가짜를 쓴다). */
fun createReportWriter(): ReportWriter? =
    BuildConfig.RELAY_URL.takeIf { it.isNotEmpty() }?.let { RelayReportWriter(it, BuildConfig.RELAY_TOKEN) }
