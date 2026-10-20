package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.DetectionMode
import com.example.data.VideoDuplicateDetector
import com.example.data.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("gal", appName)
    }

    @Test
    fun `flexible duplicate detection finds exact, name-similar and size-proximate duplicates`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempDir = File(context.cacheDir, "dup_test_dir").apply {
            deleteRecursively()
            mkdirs()
        }

        val payloadA = ByteArray(120_000) { i -> ((i * 31 + 7) % 251).toByte() }
        val payloadB = ByteArray(120_000) { i -> ((i * 17 + 3) % 251).toByte() }

        // Exact copy
        val f1 = File(tempDir, "holiday_trip.mp4").apply { writeBytes(payloadA) }
        val f2 = File(tempDir, "holiday_trip (1).mp4").apply { writeBytes(payloadA) }

        // Similar name with tiny size difference
        val payloadA_slight = ByteArray(120_500) { i -> ((i * 31 + 7) % 251).toByte() }
        val f3 = File(tempDir, "holiday_trip_copy.mp4").apply { writeBytes(payloadA_slight) }

        // Unrelated video
        val f4 = File(tempDir, "lecture_math.webm").apply { writeBytes(payloadB) }

        val items = listOf(f1, f2, f3, f4).mapIndexed { idx, file ->
            VideoItem(
                id = idx + 1,
                file = file,
                name = file.name,
                path = file.absolutePath,
                size = file.length(),
                lastModified = file.lastModified(),
                durationMs = 30_000L,
                resolution = "1920x1080"
            )
        }

        val groups = VideoDuplicateDetector.findDuplicatesSync(items, DetectionMode.FLEXIBLE_MATCH)
        assertEquals(1, groups.size)
        assertTrue(groups.first().items.size >= 2)
        assertTrue(groups.first().items.any { it.name.startsWith("holiday_trip") })
    }

    @Test
    fun `normalization cleans names effectively`() {
        val n1 = VideoDuplicateDetector.normalizeVideoName("My_Cool_Video (Copy) (1).mp4")
        val n2 = VideoDuplicateDetector.normalizeVideoName("my cool video [1080p].webm")
        assertTrue(n1.contains("mycoolvideo"))
        assertTrue(n2.contains("mycoolvideo"))
    }
}
