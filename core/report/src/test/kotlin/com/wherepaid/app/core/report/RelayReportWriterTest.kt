package com.wherepaid.app.core.report

import com.sun.net.httpserver.HttpServer
import com.wherepaid.app.core.model.CategoryTotal
import com.wherepaid.app.core.model.MonthlyReportRequest
import com.wherepaid.app.core.model.ReportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.time.YearMonth

/** 가짜 집계로 시험한다. 실제 결제 기록이 아니다. */
class RelayReportWriterTest {
    private val request = MonthlyReportRequest(
        month = YearMonth.of(2026, 10),
        offlineTotal = 312_400,
        onlineTotal = 88_900,
        paymentCount = 31,
        categories = listOf(CategoryTotal("카페", 98_000, 14), CategoryTotal("편의점", 72_000, 11)),
        previousMonthTotal = null,
    )

    private lateinit var server: HttpServer
    private var status = 200
    private var reply = """{"title":"카페 순례자","summary":"요약"}"""
    private var receivedBody: String? = null
    private var receivedToken: String? = null

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext(RelayReportWriter.PATH) { exchange ->
            receivedBody = exchange.requestBody.bufferedReader().readText()
            receivedToken = exchange.requestHeaders.getFirst("x-app-token")
            val bytes = reply.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @After
    fun tearDown() = server.stop(0)

    private fun writer(token: String = "test-token") =
        RelayReportWriter("http://127.0.0.1:${server.address.port}/", token, Dispatchers.Unconfined)

    @Test
    fun `요청 본문은 서버 README 형식의 월 집계만 담는다`() {
        val json = JSONObject(RelayReportWriter.encodeRequest(request))

        assertEquals(setOf("month", "offlineTotal", "onlineTotal", "paymentCount", "categories", "previousMonthTotal"), json.keySet())
        assertEquals("2026-10", json.getString("month"))
        assertEquals(312_400L, json.getLong("offlineTotal"))
        assertEquals("카페", json.getJSONArray("categories").getJSONObject(0).getString("name"))
        assertEquals(true, json.isNull("previousMonthTotal"))
    }

    @Test
    fun `응답 해석`() {
        assertEquals(ReportResult.Success("칭호", "요약"), RelayReportWriter.parseResponse(200, """{"title":" 칭호 ","summary":"요약"}"""))
        assertEquals(ReportResult.Failed, RelayReportWriter.parseResponse(200, """{"title":"칭호"}"""))
        assertEquals(ReportResult.Failed, RelayReportWriter.parseResponse(200, "그냥 글"))
        assertEquals(ReportResult.Failed, RelayReportWriter.parseResponse(502, """{"error":"upstream_error"}"""))
        assertEquals(ReportResult.Failed, RelayReportWriter.parseResponse(200, null))
    }

    @Test
    fun `서버를 불러 칭호와 요약을 받는다`() = runTest {
        assertEquals(ReportResult.Success("카페 순례자", "요약"), writer().write(request))
        assertEquals("test-token", receivedToken)
        assertEquals(31, JSONObject(receivedBody!!).getInt("paymentCount"))
    }

    @Test
    fun `서버 오류와 연결 실패는 Failed`() = runTest {
        status = 401
        reply = """{"error":"unauthorized"}"""
        assertEquals(ReportResult.Failed, writer().write(request))

        // 아무도 듣지 않는 포트 (연결 거부)
        val closedPort = ServerSocket(0).use { it.localPort }
        val unreachable = RelayReportWriter("http://127.0.0.1:$closedPort", "t", Dispatchers.Unconfined)
        assertEquals(ReportResult.Failed, unreachable.write(request))
    }
}
