@file:OptIn(UnstableApi::class)

package com.yuriikonovalov.media3.transformer

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import com.bumptech.glide.gifdecoder.GifHeaderParser
import com.bumptech.glide.gifdecoder.StandardGifDecoder
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPoolAdapter
import com.bumptech.glide.load.engine.bitmap_recycle.LruArrayPool
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator
import com.bumptech.glide.load.resource.gif.GifBitmapProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer

class GifOverlay(
    context: Context,
    uri: Uri,
    private val size: Size = Size(1080, 1920)
) : BitmapOverlay() {

    private val defaultBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    private val file = getFile(context, uri)

    private val standardGifDecoder: StandardGifDecoder
    private val frameCount: Int
    private var numberOfShownFrames = 0

    init {
        val data = file.readBytes()
        val byteBuffer = ByteBuffer.wrap(data)
        val memorySizeCalculator = MemorySizeCalculator.Builder(context).build()
        val bitmapPoolSize = memorySizeCalculator.bitmapPoolSize.toLong()
        val bitmapPool =
            if (bitmapPoolSize > 0) LruBitmapPool(bitmapPoolSize) else BitmapPoolAdapter()
        val arrayPool = LruArrayPool(memorySizeCalculator.arrayPoolSizeInBytes)
        val gifBitmapProvider = GifBitmapProvider(bitmapPool, arrayPool)
        val header = GifHeaderParser().setData(byteBuffer).parseHeader()
        standardGifDecoder = StandardGifDecoder(gifBitmapProvider, header, byteBuffer, 1)
        frameCount = standardGifDecoder.frameCount
        standardGifDecoder.advance()

    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        // 33_333 millis each frame is shown (when the framerate is 30)

        if (numberOfShownFrames < frameCount - 1) {
            numberOfShownFrames++
            val bitmap = standardGifDecoder.nextFrame
            standardGifDecoder.advance()

            if (numberOfShownFrames == frameCount - 1) {
                //reset
                numberOfShownFrames = 0
                standardGifDecoder.resetFrameIndex()
                standardGifDecoder.advance()
            }

            return bitmap ?: defaultBitmap
        } else {
            return defaultBitmap
        }

        // TODO: WITH A TIME SEGMENT WHEN TO SHOW
//        if (presentationTimeUs in 5_000_000..10_000_000) {
//            // 33_333 millis frame
//            if (numberOfShownFrames < frameCount - 1) {
//                numberOfShownFrames++
//                log("DELAY = ${standardGifDecoder.nextDelay}")
//                val bitmap = standardGifDecoder.nextFrame
//                standardGifDecoder.advance()
//
//                if (numberOfShownFrames == frameCount - 1) {
//                    //reset
//                    numberOfShownFrames = 0
//                    standardGifDecoder.resetFrameIndex()
//                    standardGifDecoder.advance()
//                }
//
//                return bitmap ?: defaultBitmap
//            } else {
//                return defaultBitmap
//            }
//        } else {
//            return defaultBitmap
//        }
    }

    override fun getTextureSize(presentationTimeUs: Long): Size {
        return size
    }

    private fun getFile(context: Context, uri: Uri): File {
        val destinationFilename =
            File(context.filesDir.path + File.separatorChar + queryName(context, uri))
        try {
            context.contentResolver.openInputStream(uri).use { ins ->
                createFileFromStream(
                    ins!!,
                    destinationFilename
                )
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return destinationFilename
    }

    private fun createFileFromStream(ins: InputStream, destination: File?) {
        try {
            FileOutputStream(destination).use { os ->
                val buffer = ByteArray(100000000)
                var length: Int
                while (ins.read(buffer).also { length = it } > 0) {
                    os.write(buffer, 0, length)
                }
                os.flush()
            }
        } catch (ex: java.lang.Exception) {
            ex.printStackTrace()
        }
    }

    private fun queryName(context: Context, uri: Uri): String {
        val returnCursor: Cursor = context.contentResolver.query(uri, null, null, null, null)!!
        val nameIndex: Int = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        returnCursor.moveToFirst()
        val name: String = returnCursor.getString(nameIndex)
        returnCursor.close()
        return name
    }
}