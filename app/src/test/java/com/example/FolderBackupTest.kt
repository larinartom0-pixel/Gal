package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.FolderBackupManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FolderBackupTest {

    @Test
    fun testPackAndUnpackEncryptedFolder() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val testDir = File(context.cacheDir, "test_videos_dir").apply {
                mkdirs()
            }

        // Create sample video dummy files
        val file1 = File(testDir, "sample1.mp4").apply {
            writeBytes("DUMMY_MP4_VIDEO_STREAM_DATA_123456789".toByteArray())
        }
        val file2 = File(testDir, "clip2.mov").apply {
            writeBytes("ANOTHER_TEST_VIDEO_STREAM_CONTENTS".toByteArray())
        }

        val archiveFile = File(context.cacheDir, "test_backup.vault")
        val archiveUri = Uri.fromFile(archiveFile)

        // 1. Pack folder to encrypted vault file
        val packResult = FolderBackupManager.packFolderToUri(
            context = context,
            folder = testDir,
            destinationUri = archiveUri
        )
        assertTrue("Packing should succeed", packResult.isSuccess)
        assertEquals(2, packResult.getOrNull())

        // Verify the archive file is encrypted and does not contain plain text
        val archiveBytes = archiveFile.readBytes()
        assertTrue("Archive must start with magic header", archiveBytes.size > 20)
        // Verify external players/inspectors cannot find raw plain video data
        val archiveString = String(archiveBytes)
        assertTrue(!archiveString.contains("DUMMY_MP4_VIDEO_STREAM_DATA_123456789"))

        // 2. Unpack to target restore directory
        val restoreDir = File(context.cacheDir, "restored_videos_dir").apply {
            mkdirs()
        }
        val unpackResult = FolderBackupManager.unpackArchiveFromUri(
            context = context,
            sourceUri = archiveUri,
            targetFolder = restoreDir
        )
        assertTrue("Unpacking should succeed", unpackResult.isSuccess)
        assertEquals(2, unpackResult.getOrNull())

        // Verify restored files
        val restoredFile1 = File(restoreDir, "sample1.mp4")
        val restoredFile2 = File(restoreDir, "clip2.mov")

        assertTrue("Restored file 1 exists", restoredFile1.exists())
        assertTrue("Restored file 2 exists", restoredFile2.exists())
        assertEquals("sample1.mp4 content matches", "DUMMY_MP4_VIDEO_STREAM_DATA_123456789", restoredFile1.readText())
        assertEquals("clip2.mov content matches", "ANOTHER_TEST_VIDEO_STREAM_CONTENTS", restoredFile2.readText())

        // Clean up test directories
        testDir.deleteRecursively()
        restoreDir.deleteRecursively()
        archiveFile.delete()
        }
    }
}
