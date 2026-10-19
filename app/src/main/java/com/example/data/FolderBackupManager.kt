package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles encrypted packaging and unpacking of the video folder.
 * 
 * Guarantees:
 * - Streamed I/O to avoid OutOfMemoryError on large video folders.
 * - AES encryption with custom container format: external file managers,
 *   zip utilities, and media players CANNOT view or preview the contents.
 * - Restores files with their original names and timestamps.
 */
object FolderBackupManager {
    private const val TAG = "FolderBackupManager"
    private const val MAGIC_HEADER = "VAULT_ENC_V1.0"
    private const val MASTER_SALT = "VaultMediaMasterSecretKeySalt_2026!#"

    private fun getSecretKey(): SecretKeySpec {
        val md = MessageDigest.getInstance("SHA-256")
        val keyBytes = md.digest(MASTER_SALT.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    data class BackupProgress(
        val currentFile: Int,
        val totalFiles: Int,
        val currentFileName: String,
        val isPacking: Boolean // true for pack, false for unpack
    )

    /**
     * Packs all video files in [folder] into an encrypted vault archive at [destinationUri].
     */
    suspend fun packFolderToUri(
        context: Context,
        folder: File,
        destinationUri: Uri,
        onProgress: (BackupProgress) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val files = folder.listFiles { file ->
                file.isFile && !file.name.endsWith(".tmp") && !file.name.endsWith(".vault")
            }?.filter { VideoFolderScanner.isVideoFile(it) || it.name.startsWith(".") } ?: emptyList()

            if (files.isEmpty()) {
                return@withContext Result.failure(Exception("Папка порожня або не містить відеофайлів"))
            }

            val outStream = context.contentResolver.openOutputStream(destinationUri)
                ?: return@withContext Result.failure(Exception("Не вдалося відкрити сховище для запису"))

            BufferedOutputStream(outStream).use { rawOut ->
                val dataOut = DataOutputStream(rawOut)
                // 1. Write Magic Header
                dataOut.writeUTF(MAGIC_HEADER)

                // 2. Generate and write 16-byte random IV
                val iv = ByteArray(16)
                SecureRandom().nextBytes(iv)
                dataOut.writeInt(iv.size)
                dataOut.write(iv)

                // 3. Write number of files
                dataOut.writeInt(files.size)
                dataOut.flush()

                // Setup Cipher for streaming encryption
                val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), IvParameterSpec(iv))

                val cipherOut = CipherOutputStream(rawOut, cipher)
                val buffer = ByteArray(64 * 1024)

                files.forEachIndexed { index, file ->
                    onProgress(
                        BackupProgress(
                            currentFile = index + 1,
                            totalFiles = files.size,
                            currentFileName = file.name,
                            isPacking = true
                        )
                    )

                    // Write file header (inside encrypted stream)
                    val encDataOut = DataOutputStream(cipherOut)
                    encDataOut.writeUTF(file.name)
                    encDataOut.writeLong(file.length())
                    encDataOut.writeLong(file.lastModified())
                    encDataOut.flush()

                    // Stream file bytes through cipher
                    BufferedInputStream(FileInputStream(file)).use { fileIn ->
                        var read: Int
                        while (fileIn.read(buffer).also { read = it } != -1) {
                            cipherOut.write(buffer, 0, read)
                        }
                    }
                    cipherOut.flush()
                }

                cipherOut.close()
            }

            Result.success(files.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error packing folder", e)
            Result.failure(e)
        }
    }

    /**
     * Unpacks and decrypts an encrypted vault archive from [sourceUri] into [targetFolder].
     */
    suspend fun unpackArchiveFromUri(
        context: Context,
        sourceUri: Uri,
        targetFolder: File,
        onProgress: (BackupProgress) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!targetFolder.exists()) {
                targetFolder.mkdirs()
            }

            val inStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(Exception("Не вдалося відкрити архів для читання"))

            var extractedCount = 0

            BufferedInputStream(inStream).use { rawIn ->
                val dataIn = DataInputStream(rawIn)

                // 1. Check Magic Header
                val header = dataIn.readUTF()
                if (header != MAGIC_HEADER) {
                    return@withContext Result.failure(Exception("Невірний або несумісний формат архіву Vault"))
                }

                // 2. Read IV
                val ivSize = dataIn.readInt()
                if (ivSize != 16) {
                    return@withContext Result.failure(Exception("Пошкоджений заголовок шифрування"))
                }
                val iv = ByteArray(16)
                dataIn.readFully(iv)

                // 3. Read total files
                val totalFiles = dataIn.readInt()
                if (totalFiles <= 0) {
                    return@withContext Result.failure(Exception("Архів порожній"))
                }

                // Setup Cipher for streaming decryption
                val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), IvParameterSpec(iv))

                val cipherIn = CipherInputStream(rawIn, cipher)
                val encDataIn = DataInputStream(cipherIn)
                val buffer = ByteArray(64 * 1024)

                for (i in 0 until totalFiles) {
                    val fileName = encDataIn.readUTF()
                    val fileSize = encDataIn.readLong()
                    val lastModified = encDataIn.readLong()

                    onProgress(
                        BackupProgress(
                            currentFile = i + 1,
                            totalFiles = totalFiles,
                            currentFileName = fileName,
                            isPacking = false
                        )
                    )

                    // Write to target folder safely
                    val safeName = fileName.replace("/", "_").replace("\\", "_")
                    val destFile = File(targetFolder, safeName)
                    val tempDest = File(targetFolder, "$safeName.unpacking")

                    BufferedOutputStream(FileOutputStream(tempDest)).use { fileOut ->
                        var remaining = fileSize
                        while (remaining > 0) {
                            val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                            val read = cipherIn.read(buffer, 0, toRead)
                            if (read == -1) break
                            fileOut.write(buffer, 0, read)
                            remaining -= read
                        }
                        fileOut.flush()
                    }

                    if (destFile.exists()) {
                        destFile.delete()
                    }
                    tempDest.renameTo(destFile)
                    if (lastModified > 0) {
                        destFile.setLastModified(lastModified)
                    }

                    extractedCount++
                }
            }

            Result.success(extractedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Error unpacking archive", e)
            Result.failure(e)
        }
    }
}
