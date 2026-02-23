package com.example.entryplayer.ent

import android.content.Context
import android.net.Uri
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Utility for extracting `.ent` project files.
 *
 * An `.ent` file can be a zipped or tarred archive containing a `project.json` and asset files. This
 * extractor uses magic bytes to determine the archive type and extracts accordingly. Tar archives may
 * optionally be gzipped.
 */
object EntExtractor {

    /**
     * Extract an `.ent` file from the provided URI into the given output directory. The resulting
     * project root will be returned (normalized to the directory containing `project.json`).
     */
    fun extractFromUri(context: Context, uri: Uri, outputDir: File): File {
        val tempFile = File(outputDir, "input.ent")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { out -> input.copyTo(out) }
        } ?: error("파일을 열 수 없습니다.")

        extractSmart(tempFile, outputDir)
        return normalizeRoot(outputDir)
    }

    /**
     * Determine archive type by magic bytes and extract accordingly.
     */
    private fun extractSmart(file: File, outputDir: File) {
        val header = ByteArray(8)
        FileInputStream(file).use { fis -> fis.read(header) }

        val isZip = header.size >= 4 &&
            header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
        val isGzip = header.size >= 2 &&
            header[0] == 0x1F.toByte() && header[1] == 0x8B.toByte()

        when {
            isZip -> unzip(file, outputDir)
            isGzip -> untarGz(file, outputDir)
            else -> {
                try {
                    untar(file, outputDir)
                } catch (e: Exception) {
                    throw IllegalStateException("지원되지 않거나 손상된 .ent 포맷입니다: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Extract a ZIP archive.
     */
    private fun unzip(file: File, outputDir: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = safeFile(outputDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /**
     * Extract a gzipped tar archive.
     */
    private fun untarGz(file: File, outputDir: File) {
        FileInputStream(file).use { fis ->
            BufferedInputStream(fis).use { bis ->
                GzipCompressorInputStream(bis).use { gis ->
                    TarArchiveInputStream(gis).use { tis ->
                        extractTarEntries(tis, outputDir)
                    }
                }
            }
        }
    }

    /**
     * Extract a tar archive.
     */
    private fun untar(file: File, outputDir: File) {
        FileInputStream(file).use { fis ->
            BufferedInputStream(fis).use { bis ->
                val ais: ArchiveInputStream = ArchiveStreamFactory().createArchiveInputStream("tar", bis)
                (ais as TarArchiveInputStream).use { tis ->
                    extractTarEntries(tis, outputDir)
                }
            }
        }
    }

    /**
     * Extract entries from a Tar archive into the given directory.
     */
    private fun extractTarEntries(tis: TarArchiveInputStream, outputDir: File) {
        var entry = tis.nextEntry
        while (entry != null) {
            val outFile = safeFile(outputDir, entry.name)
            if (entry.isDirectory) {
                outFile.mkdirs()
            } else {
                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { fos -> tis.copyTo(fos) }
            }
            entry = tis.nextEntry
        }
    }

    /**
     * Ensure no path traversal by verifying the extracted file is within the output directory.
     */
    private fun safeFile(baseDir: File, name: String): File {
        val outFile = File(baseDir, name)
        val basePath = baseDir.canonicalPath
        val outPath = outFile.canonicalPath
        if (!outPath.startsWith(basePath)) {
            throw SecurityException("Zip Slip 차단: $name")
        }
        return outFile
    }

    /**
     * Find the `project.json` inside the extracted directory tree.
     */
    fun findProjectJson(root: File): File? {
        root.walkTopDown().forEach {
            if (it.isFile && it.name.equals("project.json", ignoreCase = true)) {
                return it
            }
        }
        return null
    }

    /**
     * Normalize the project root so that it directly contains `project.json`.
     */
    private fun normalizeRoot(outputDir: File): File {
        val pj = findProjectJson(outputDir) ?: return outputDir
        return pj.parentFile ?: outputDir
    }
}