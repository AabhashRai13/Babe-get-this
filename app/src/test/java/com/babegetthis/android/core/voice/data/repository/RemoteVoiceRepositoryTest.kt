package com.babegetthis.android.core.voice.data.repository

import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.network.NetworkMonitor
import com.babegetthis.android.core.voice.data.remote.TranscribeApiService
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import java.io.File

// Real Retrofit over a MockWebServer, so serialization, multipart assembly and
// HTTP status mapping are all genuinely exercised.
class RemoteVoiceRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: RemoteVoiceRepository
    private val networkMonitor = mockk<NetworkMonitor>()

    private lateinit var audio: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        every { networkMonitor.isOnline() } returns true

        val json = Json { ignoreUnknownKeys = true }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TranscribeApiService::class.java)

        repository = RemoteVoiceRepository(api, networkMonitor)

        audio = File.createTempFile("voice", ".m4a").apply { writeBytes(ByteArray(64)) }
    }

    @After
    fun tearDown() {
        server.shutdown()
        audio.delete()
    }

    private fun respond(code: Int, body: String = "{}") {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    private fun <T> Result<T>.data(): T = (this as Result.Success).data
    private fun <T> Result<T>.error(): AppError = (this as Result.Error).error

    // --- happy path ---

    @Test
    fun `a transcribed response maps to drafts`() = runTest {
        respond(
            200,
            """{"transcript":"milk and eggs","items":[
                {"name":"Milk","quantity":2,"unit":"litres","category":"dairy","location":"Aldi","note":"semi"},
                {"name":"Eggs"}
            ]}""",
        )

        val drafts = repository.transcribeAndParse(audio).data()

        assertEquals(2, drafts.size)
        assertEquals("Milk", drafts[0].name)
        assertEquals("2 litres", drafts[0].quantity)
        assertEquals("dairy", drafts[0].category)
        assertEquals("Aldi", drafts[0].shop)
        assertEquals("semi", drafts[0].note)
    }

    @Test
    fun `an empty item list comes back as an empty success`() = runTest {
        respond(200, """{"transcript":"","items":[]}""")

        assertTrue(repository.transcribeAndParse(audio).data().isEmpty())
    }

    @Test
    fun `unknown response fields are ignored`() = runTest {
        respond(200, """{"items":[{"name":"Milk","somethingNew":true}],"futureField":1}""")

        assertEquals("Milk", repository.transcribeAndParse(audio).data().single().name)
    }

    @Test
    fun `the audio is uploaded as multipart under the audio part name`() = runTest {
        respond(200, """{"items":[]}""")

        repository.transcribeAndParse(audio)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/transcribe", request.path)
        val body = request.body.readUtf8()
        assertTrue("expected multipart", request.getHeader("Content-Type")!!.startsWith("multipart/"))
        assertTrue("expected an 'audio' part", body.contains("name=\"audio\""))
        assertTrue("expected the container mime", body.contains("audio/mp4"))
    }

    // --- offline guard ---

    // Fails fast rather than waiting for an upload to time out.
    @Test
    fun `offline is refused without touching the network`() = runTest {
        every { networkMonitor.isOnline() } returns false

        val error = repository.transcribeAndParse(audio).error()

        assertTrue(error is AppError.NetworkError)
        assertEquals(0, server.requestCount)
    }

    // --- status mapping ---

    // A 4xx here is about the recording, not authentication, so the copy is
    // overridden away from safeCall's auth-flavoured default.
    @Test
    fun `a 400 blames the recording rather than the credentials`() = runTest {
        respond(400)

        val error = repository.transcribeAndParse(audio).error()

        assertTrue(error is AppError.ServerError)
        assertEquals("Couldn't process that recording. Please try again.", error.message)
    }

    @Test
    fun `a 413 is also treated as a recording problem`() = runTest {
        respond(413)

        assertEquals(
            "Couldn't process that recording. Please try again.",
            repository.transcribeAndParse(audio).error().message,
        )
    }

    // 401 keeps safeCall's default: the Supabase token really is stale, and
    // AuthAuthenticator will end the session.
    @Test
    fun `a 401 stays an unauthorized error`() = runTest {
        respond(401)

        assertTrue(repository.transcribeAndParse(audio).error() is AppError.UnauthorizedError)
    }

    @Test
    fun `a 500 is a server error`() = runTest {
        respond(500)

        val error = repository.transcribeAndParse(audio).error()

        assertTrue(error is AppError.ServerError)
        assertEquals(500, (error as AppError.ServerError).code)
    }

    @Test
    fun `a dropped connection is a network error`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertTrue(repository.transcribeAndParse(audio).error() is AppError.NetworkError)
    }

    @Test
    fun `a malformed body does not escape as an exception`() = runTest {
        respond(200, "this is not json")

        assertTrue(repository.transcribeAndParse(audio) is Result.Error)
    }

    // Regression for the safeCall leak fixed in task 10.2: a missing cache file
    // used to surface its full path to the user.
    @Test
    fun `a missing audio file does not leak its path`() = runTest {
        respond(200, """{"items":[]}""")
        val gone = File("/tmp/definitely-not-here-${System.nanoTime()}.m4a")

        val error = repository.transcribeAndParse(gone).error()

        assertTrue("leaked path: ${error.message}", !error.message.contains(".m4a"))
        assertTrue("leaked path: ${error.message}", !error.message.contains("/tmp"))
    }
}
