package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.DetectionMode
import com.example.data.VideoDuplicateDetector
import com.example.data.VideoFingerprint
import com.example.data.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `fast hash and deep scan detect exact and header-modified duplicates`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempDir = File(context.cacheDir, "dup_test_dir").apply {
            deleteRecursively()
            mkdirs()
        }

        val payloadA = ByteArray(120_000) { i -> ((i * 31 + 7) % 251).toByte() }
        val payloadB = ByteArray(120_000) { i -> ((i * 17 + 3) % 251).toByte() }

        // Exact copy pair
        val f1 = File(tempDir, "video_orig.mp4").apply { writeBytes(payloadA) }
        val f2 = File(tempDir, "video_copy.mp4").apply { writeBytes(payloadA) }

        // Remuxed copy (different MP4 header bytes at start, identical media stream in middle)
        val remuxedBytes = payloadA.clone()
        for (i in 0 until 256) {
            remuxedBytes[i] = (remuxedBytes[i] + 1).toByte()
        }
        val f3 = File(tempDir, "video_remuxed.mp4").apply { writeBytes(remuxedBytes) }

        // Completely different video
        val f4 = File(tempDir, "video_unique.mp4").apply { writeBytes(payloadB) }

        val items = listOf(f1, f2, f3, f4).mapIndexed { idx, file ->
            VideoItem(
                id = idx + 1,
                file = file,
                name = file.name,
                path = file.absolutePath,
                size = file.length(),
                lastModified = file.lastModified(),
                durationMs = 10_000L
            )
        }

        val fastGroups = VideoDuplicateDetector.findDuplicatesSync(items, DetectionMode.FAST_HASH_ONLY)
        assertEquals(1, fastGroups.size)
        assertEquals(3, fastGroups.first().items.size)

        val deepGroups = VideoDuplicateDetector.findDuplicatesSync(items, DetectionMode.DEEP_FRAME_SCAN)
        assertEquals(1, deepGroups.size)
        assertEquals(3, deepGroups.first().items.size)
    }

    @Test
    fun `perceptual frame comparison matches visual clones and modified videos but rejects unrelated videos`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dummyFileA = File(context.cacheDir, "a.mp4").apply { writeBytes(ByteArray(1024) { 1 }) }
        val dummyFileB = File(context.cacheDir, "b.mp4").apply { writeBytes(ByteArray(800) { 2 }) }
        val dummyFileC = File(context.cacheDir, "c.mp4").apply { writeBytes(ByteArray(900) { 3 }) }

        fun makePatternBitmap(seed: Int, brightnessShift: Int = 0): Bitmap {
            val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    val r = ((x * 7 + y * seed + 20 + brightnessShift).coerceIn(0, 255))
                    val g = ((y * 6 + x * (seed + 1) + 30 + brightnessShift).coerceIn(0, 255))
                    val b = (((x + y) * 4 + seed * 10 + brightnessShift).coerceIn(0, 255))
                    bmp.setPixel(x, y, Color.rgb(r, g, b))
                }
            }
            return bmp
        }

        val sigsOrig = (1..6).map { i ->
            VideoDuplicateDetector.computeFrameSignature(makePatternBitmap(seed = i * 3, brightnessShift = 0), i / 6f)
        }
        // Slightly compressed/brightened version of the same frames
        val sigsModified = (1..6).map { i ->
            VideoDuplicateDetector.computeFrameSignature(makePatternBitmap(seed = i * 3, brightnessShift = 8), i / 6f)
        }
        // Completely different visual pattern (inverted gradients)
        val sigsDifferent = (1..6).map { i ->
            val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    val l = if ((x / 4 + y / 4) % 2 == 0) 240 else 20
                    bmp.setPixel(31 - x, 31 - y, Color.rgb(l, 255 - l, (x * 8).coerceIn(0, 255)))
                }
            }
            VideoDuplicateDetector.computeFrameSignature(bmp, i / 6f)
        }

        val fpOrig = VideoFingerprint(
            item = VideoItem(1, dummyFileA, "a.mp4", dummyFileA.path, 5_000_000L, 0L, durationMs = 15_000L),
            durationMs = 15_000L,
            width = 1920,
            height = 1080,
            frameHashes = sigsOrig.map { it.hHash },
            contentHash = "hashA",
            bodyHash = "bodyA",
            frameSignatures = sigsOrig
        )

        val fpModified = VideoFingerprint(
            item = VideoItem(2, dummyFileB, "b.mp4", dummyFileB.path, 1_200_000L, 0L, durationMs = 13_500L),
            durationMs = 13_500L,
            width = 1280,
            height = 720,
            frameHashes = sigsModified.map { it.hHash },
            contentHash = "hashB",
            bodyHash = "bodyB",
            frameSignatures = sigsModified
        )

        val fpDifferent = VideoFingerprint(
            item = VideoItem(3, dummyFileC, "c.mp4", dummyFileC.path, 4_000_000L, 0L, durationMs = 15_000L),
            durationMs = 15_000L,
            width = 1920,
            height = 1080,
            frameHashes = sigsDifferent.map { it.hHash },
            contentHash = "hashC",
            bodyHash = "bodyC",
            frameSignatures = sigsDifferent
        )

        val matchRes = VideoDuplicateDetector.compareFingerprints(fpOrig, fpModified)
        assertTrue("Modified copy should match original", matchRes.isMatch)

        val diffRes = VideoDuplicateDetector.compareFingerprints(fpOrig, fpDifferent)
        assertFalse("Unrelated video should not match", diffRes.isMatch)
    }
}
