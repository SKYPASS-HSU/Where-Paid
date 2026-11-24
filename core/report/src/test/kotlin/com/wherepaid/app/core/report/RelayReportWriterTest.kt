package com.wherepaid.app.core.report

import com.wherepaid.app.core.model.CategoryTotal
import com.wherepaid.app.core.model.MonthlyReportRequest
import com.wherepaid.app.core.model.ReportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.time.YearMonth
import kotlin.concurrent.thread

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

    /** 요청 하나에 정해진 응답 하나를 주는 아주 작은 HTTP 서버. 받은 요청 원문을 [received]에 남긴다. */
    private lateinit var server: ServerSocket
    private var status = "200 OK"
    private var reply = """{"title":"카페 순례자","summary":"요약"}"""
    @Volatile private var received: String = ""

    @Before
    fun setUp() {
        server = ServerSocket(0)
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (e: java.io.IOException) {
                    break
                }
                socket.use { s ->
                    val input = s.getInputStream()
                    // 머리(빈 줄까지)는 바이트로 읽는다. 본문은 Content-Length 바이트만큼 (한글은 글자 수와 바이트 수가 다르다)
                    val headBytes = ByteArrayOutputStream()
                    var tail = 0
                    while (true) {
                        val b = input.read()
                        if (b < 0) break
                        headBytes.write(b)
                        tail = (tail shl 8) or b
                        if (tail == END_OF_HEAD) break
                    }
                    val head = headBytes.toString(Charsets.UTF_8.name()).split("\r\n").filter { it.isNotEmpty() }
                    val length = head.firstOrNull { it.startsWith("content-length:", ignoreCase = true) }
                        ?.substringAfter(':')?.trim()?.toInt() ?: 0
                    val body = ByteArray(length)
                    var read = 0
                    while (read < length) {
                        val n = input.read(body, read, length - read)
                        if (n < 0) break
                        read += n
                    }
                    received = head.joinToString("\n") + "\n\n" + String(body, 0, read, Charsets.UTF_8)
                    val bytes = reply.toByteArray(Charsets.UTF_8)
                    val out = s.getOutputStream()
                    out.write(
                        ("HTTP/1.1 $status\r\nContent-Type: application/json\r\n" +
                            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.UTF_8),
                    )
                    out.write(bytes)
                    out.flush()
                }
            }
        }
    }

    @After
    fun tearDown() = server.close()

    private fun writer(token: String = "test-token") =
        RelayReportWriter("http://127.0.0.1:${server.localPort}/", token, Dispatchers.Unconfined)

    @Test
    fun `요청 본문은 서버 README 형식의 월 집계만 담는다`() {
        val json = JSONObject(RelayReportWriter.encodeRequest(request))

        val keys = json.keys().asSequence().toSet()
        assertEquals(setOf("month", "offlineTotal", "onlineTotal", "paymentCount", "categories", "previousMonthTotal"), keys)
        assertEquals("2026-10", json.getString("month"))
        assertEquals(312_400L, json.getLong("offlineTotal"))
        assertEquals("카페", json.getJSONArray("categories").getJSONObject(0).getString("name"))
        assertTrue(json.isNull("previousMonthTotal"))
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
        assertTrue(received.startsWith("POST ${RelayReportWriter.PATH} "))
        assertTrue(received.lines().any { it.equals("x-app-token: test-token", ignoreCase = true) })
        assertEquals(31, JSONObject(received.substringAfter("\n\n")).getInt("paymentCount"))
    }

    @Test
    fun `서버 오류와 연결 실패는 Failed`() = runTest {
        status = "401 Unauthorized"
        reply = """{"error":"unauthorized"}"""
        assertEquals(ReportResult.Failed, writer().write(request))

        // 아무도 듣지 않는 포트 (연결 거부)
        val closedPort = ServerSocket(0).use { it.localPort }
        val unreachable = RelayReportWriter("http://127.0.0.1:$closedPort", "t", Dispatchers.Unconfined)
        assertEquals(ReportResult.Failed, unreachable.write(request))
    }

    private companion object {
        /** "\r\n\r\n" */
        const val END_OF_HEAD = 0x0D0A0D0A
    }
}
