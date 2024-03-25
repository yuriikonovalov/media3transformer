@file:UnstableApi

package com.yuriikonovalov.media3.transformer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.ClippingConfiguration
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextureOverlay
import androidx.media3.effect.VideoCompositorSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import com.yuriikonovalov.media3.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date
import kotlin.math.round
import kotlin.properties.Delegates

class Media3Editor(private val context: Context) {

    private lateinit var transformer: Transformer
    private var startTime by Delegates.notNull<Long>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressJob: Job? = null

    private val transformerListener = object : Transformer.Listener {
        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
            super.onCompleted(composition, exportResult)
            val seconds = (Date().time - startTime) / 1000
            log("DONE -> $seconds seconds")
            progressJob?.cancel()
        }
    }

    fun processLayout(video1Uri: Uri, video2Uri: Uri, imageUri: Uri) {

        // VIDEO 1
        val video1ClippingConfiguration = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(10_000) // start at 10 seconds
            .setEndPositionMs(30_000) // end at 30 seconds
            .build()

        val video1MediaItem = MediaItem.Builder()
            .setUri(video1Uri)
            .setClippingConfiguration(video1ClippingConfiguration)
            .build()

        val video1EditedMediaItem = EditedMediaItem.Builder(video1MediaItem)
            .setEffects(
                Effects(
                    listOf(),
                    listOf(
                        Presentation.createForWidthAndHeight(
                            1080,
                            960,
                            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                        )
                    )
                )
            )
            .build()


        // VIDEO 2 - looping
        val video2ClippingConfiguration = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(0)
            .setEndPositionMs(20_000)
            .build()

        val video2InputMediaItem = MediaItem.Builder()
            .setUri(video2Uri)
            .setClippingConfiguration(video2ClippingConfiguration)
            .build()

        val video2EditedMediaItem = EditedMediaItem.Builder(video2InputMediaItem)
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(
                        Presentation.createForWidthAndHeight(
                            1080,
                            960,
                            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                        )
                    )
                )
            )
            .build()

        // IMAGE
        val imageBitmap = imageBitmap(imageUri, 300, 400)
        val imageOverlay = BitmapOverlay.createStaticBitmapOverlay(imageBitmap)
        val imageOverlayEffect = OverlayEffect(ImmutableList.of(imageOverlay))

        transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(DefaultEncoderFactory.Builder(context.applicationContext).build())
            .addListener(transformerListener)
            .build()

        // composition
        val sequence1 = EditedMediaItemSequence(video1EditedMediaItem)
        val sequence2 = EditedMediaItemSequence(video2EditedMediaItem)


        val compositorSettings = object : VideoCompositorSettings {
            override fun getOutputSize(inputSizes: MutableList<Size>): Size {
                return Size(1080, 1920)
            }

            override fun getOverlaySettings(
                inputId: Int,
                presentationTimeUs: Long
            ): OverlaySettings {
                return when (inputId) {
                    0 -> {
                        OverlaySettings.Builder()
                            .setOverlayFrameAnchor(-1f, 1f)
                            .setBackgroundFrameAnchor(-1f, 0f)
                            .build()
                    }

                    1 -> {
                        OverlaySettings.Builder()
                            .setOverlayFrameAnchor(-1f, -1f)
                            .setBackgroundFrameAnchor(-1f, 0f)
                            .build()
                    }

                    else -> {
                        VideoCompositorSettings.DEFAULT.getOverlaySettings(
                            inputId,
                            presentationTimeUs
                        )
                    }
                }
            }
        }

        val composition = Composition.Builder(sequence2, sequence1)
            .setVideoCompositorSettings(compositorSettings)
            .setEffects(
                Effects(
                    listOf(),
                    listOf(
                        Presentation.createForWidthAndHeight(
                            1080, 1920,
                            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                        ),
                        FrameDropEffect.createDefaultFrameDropEffect(30f),
                        imageOverlayEffect
                    )
                )
            )
            .build()


        val outputFilePath = getOutputPath()
        startTime = Date().time
        transformer.start(composition, outputFilePath)

        startProgressChecking(transformer)
    }

    fun processTimeline(video1Uri: Uri, video2Uri: Uri, imageUri: Uri, audioUri: Uri) {
        val videoDuration = 20_000L

        // VIDEO 1
        val video1ClippingConfiguration = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(10_000) // start at 10 seconds
            .setEndPositionMs(10_000 + videoDuration) // end at 30 seconds
            .build()

        val video1MediaItem = MediaItem.Builder()
            .setUri(video1Uri)
            .setClippingConfiguration(video1ClippingConfiguration)
            .build()

        val video1EditedMediaItem = EditedMediaItem.Builder(video1MediaItem)
            .setRemoveAudio(true)
            .setEffects(
                Effects(
                    listOf(),
                    listOf(
                        Presentation.createForWidthAndHeight(
                            1080,
                            1920,
                            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                        ),
                        FrameDropEffect.createDefaultFrameDropEffect(30f)
                    )
                )
            )
            .build()


        // VIDEO 2
        val video2ClippingConfiguration = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(0)
            .setEndPositionMs(0 + videoDuration)
            .build()

        val video2InputMediaItem = MediaItem.Builder()
            .setUri(video2Uri)
            .setClippingConfiguration(video2ClippingConfiguration)
            .build()

        val video2EditedMediaItem = EditedMediaItem.Builder(video2InputMediaItem)
            .setRemoveAudio(true)
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(
                        Presentation.createForWidthAndHeight(
                            1080,
                            1920,
                            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                        ),
                        FrameDropEffect.createDefaultFrameDropEffect(30f)
                    )
                )
            )
            .build()

        // IMAGE
        val imageDuration = 5_000L //ms
        val imageMediaItem = MediaItem.Builder().setUri(imageUri).build()
        val imageEditedMediaItem = EditedMediaItem.Builder(imageMediaItem)
            .setDurationUs(imageDuration * 1000 /* milliseconds -> microseconds */)
            .setFrameRate(30)
            .build()


        val totalDuration = videoDuration * 2 + imageDuration
        // AUDIO
        val audioMediaItem = MediaItem.Builder()
            .setUri(audioUri)
            .setClippingConfiguration(
                ClippingConfiguration.Builder()
                    .setStartPositionMs(0)
                    .setEndPositionMs(totalDuration)
                    .build()
            )
            .build()
        val audioEditedMediaItem = EditedMediaItem.Builder(audioMediaItem)
            .build()

        transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(DefaultEncoderFactory.Builder(context.applicationContext).build())
            .addListener(transformerListener)
            .build()

        // composition
        val sequenceVideo = EditedMediaItemSequence(
            video1EditedMediaItem,
            video2EditedMediaItem,
            imageEditedMediaItem
        )

        val sequenceAudio = EditedMediaItemSequence(audioEditedMediaItem)

        val composition = Composition.Builder(sequenceVideo, sequenceAudio)
            .setEffects(
                Effects(
                    listOf(),
                    listOf(
                        Presentation.createForWidthAndHeight(
                            1080, 1920,
                            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                        ),
                        FrameDropEffect.createDefaultFrameDropEffect(30f),
                    )
                )
            )
            .build()


        val outputFilePath = getOutputPath()
        startTime = Date().time
        transformer.start(composition, outputFilePath)

        startProgressChecking(transformer)


    }

    fun processAnimatedFilter(imageUri: Uri, gifFilterUri: Uri) {
        // ANIMATED FILTER
        val filterOverlay = GifOverlay(context, gifFilterUri, Size(1080, 1920))
        val overlays = ImmutableList.of<TextureOverlay>(filterOverlay)

        // IMAGE
        val imageMediaItem = MediaItem.Builder().setUri(imageUri).build()
        val imageEditedMediaItem = EditedMediaItem.Builder(imageMediaItem)
            .setDurationUs(6_000_000)
            .setFrameRate(24)
            .setEffects(
                Effects(
                    listOf(),
                    listOf(
                        Presentation.createForWidthAndHeight(
                            1080, 1920,
                            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                        ),
                        OverlayEffect(overlays)
                    )
                )
            )
            .build()

        transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(DefaultEncoderFactory.Builder(context.applicationContext).build())
            .addListener(transformerListener)
            .build()


        val outputFilePath = getOutputPath()
        startTime = Date().time
        transformer.start(imageEditedMediaItem, outputFilePath)

        startProgressChecking(transformer)
    }

    // TODO: GIF Overlay
    /*
        fun process(mediaUri: Uri, filterUri: Uri) {
            val clippingConfiguration = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(20_000) // start at 10 seconds
                .setEndPositionMs(30_000) // end at 20 seconds
                .build()

            val inputMediaItem = MediaItem.Builder()
                .setUri(mediaUri)
                .setClippingConfiguration(clippingConfiguration)
                .build()



            val gifOverlay1 = GifOverlay(context, filterUri)
    //        val gifOverlay2 = GifOverlay(context, filterUri, Size(540,960),true)
            val gifOverlay2 = TextOverlay.createStaticTextOverlay(SpannableString("Hello there"),OverlaySettings.Builder().setScale(5f,5f).build())

            val overlaysBuilder = ImmutableList.Builder<TextureOverlay>()
            overlaysBuilder.add(gifOverlay1)
            val overlayEffect = OverlayEffect(overlaysBuilder.build())
            val overlayEffect2 = OverlayEffect(
                ImmutableList.Builder<TextureOverlay>().add(gifOverlay2).build()
            )

            val backgroundEditedMediaItem = EditedMediaItem.Builder(inputMediaItem)
                .setDurationUs(15_000_000)
                .setFrameRate(30)
                .setEffects(
                    Effects(
                        listOf(),
                        listOf<Effect>(
                            Presentation.createForWidthAndHeight(
                                1080,
                                1920,
                                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                            ),
                            overlayEffect2
                        )

                    )
                )
                .build()


            transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(DefaultEncoderFactory.Builder(context.applicationContext).build())
                .addListener(transformerListener)
                .build()

            // composition
            val backgroundSequence = EditedMediaItemSequence(backgroundEditedMediaItem)

            val compositorSettings = object : VideoCompositorSettings {
                override fun getOutputSize(inputSizes: MutableList<Size>): Size {
                    log("inputSizes = $inputSizes")
                    return Size(1080, 1920)
                }

                override fun getOverlaySettings(
                    inputId: Int,
                    presentationTimeUs: Long
                ): OverlaySettings {
                    return VideoCompositorSettings.DEFAULT.getOverlaySettings(
                        inputId,
                        presentationTimeUs
                    )
                }
            }

            val composition = Composition.Builder(backgroundSequence, backgroundSequence)
                .setVideoCompositorSettings(compositorSettings)
                .setEffects(Effects(listOf(), listOf(overlayEffect)))
                .build()


            val outputFilePath = getOutputPath()
            startTime = Date().time
            transformer.start(composition, outputFilePath)

            startProgressChecking(transformer)
        }
    */

    // TODO: AUDIO OFFSET
    /*
    fun processTimeline(mediaUri: Uri, filterUri: Uri) {

        val clippingConfiguration = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(20_000) // start at 10 seconds
            .setEndPositionMs(30_000) // end at 20 seconds
            .build()

        val inputMediaItem = MediaItem.Builder()
            .setUri(mediaUri)
            .setClippingConfiguration(clippingConfiguration)
            .build()

        val emptySoundEditedMediaItem = EditedMediaItem.Builder(
            MediaItem.Builder()
                .setUri(
                    Uri.Builder()
                        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                        .authority(context.packageName)
                        .path(R.raw.audio5sec.toString())
                        .build()
                )
                .build()
        ).build()


        val foregroundClippingConfiguration = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(30_000)
            .setEndPositionMs(45_000)
            .build()

        val foregroundInputMediaItem = MediaItem.Builder()
            .setUri(filterUri)
//            .setClippingConfiguration(foregroundClippingConfiguration)
            .build()


        val backgroundEditedMediaItem = EditedMediaItem.Builder(inputMediaItem)
            .setRemoveAudio(true)
            .setEffects(
                Effects(
                    listOf(),
                    listOf(
                        Presentation.createForWidthAndHeight(
                            1080,
                            960,
                            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                        )
                    )
                )
            )
            .build()

        val foregroundEditedMediaItem = EditedMediaItem.Builder(foregroundInputMediaItem)
            .setDurationUs(13_000_000)
            .setFrameRate(60)
//            .setEffects(
//                Effects(
//                    emptyList(),
//                    listOf(
//                        Presentation.createForWidthAndHeight(
//                            1080,
//                            960,
//                            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
//                        ),
//                    )
//                )
//            )
            .build()

        transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(DefaultEncoderFactory.Builder(context.applicationContext).build())
            .addListener(transformerListener)
            .build()

        // composition
        val backgroundSequence = EditedMediaItemSequence(listOf(backgroundEditedMediaItem), false)

        val foregroundSequence =
            EditedMediaItemSequence(emptySoundEditedMediaItem, foregroundEditedMediaItem)

        val compositorSettings = object : VideoCompositorSettings {
            override fun getOutputSize(inputSizes: MutableList<Size>): Size {
                return Size(1080, 1920)
            }

            override fun getOverlaySettings(
                inputId: Int,
                presentationTimeUs: Long
            ): OverlaySettings {

                return when (inputId) {
                    0 -> {
                        OverlaySettings.Builder()
                            .setOverlayFrameAnchor(-1f, 1f)
                            .setBackgroundFrameAnchor(-1f, 0f)
                            .setScale(1.4f, 1.4f)
                            .build()
                    }

                    1 -> {
                        OverlaySettings.Builder()
                            .setOverlayFrameAnchor(-1f, -1f)
                            .setBackgroundFrameAnchor(-1f, 0f)
                            .setScale(0.8f, 0.8f)
                            .build()
                    }

                    else -> {
                        VideoCompositorSettings.DEFAULT.getOverlaySettings(
                            inputId,
                            presentationTimeUs
                        )
                    }
                }
            }
        }

        val composition = Composition.Builder(foregroundSequence, backgroundSequence)
//            .setVideoCompositorSettings(compositorSettings)
//            .experimentalSetForceAudioTrack(true)
            .setEffects(
                Effects(
                    listOf(),
                    listOf(FrameDropEffect.createSimpleFrameDropEffect(60f, 30f))
                )
            )
            .build()


        val outputFilePath = getOutputPath()
        startTime = Date().time
        transformer.start(composition, outputFilePath)

        startProgressChecking(transformer)
    }
     */

    fun cancel() {
        transformer.cancel()
    }

    private fun startProgressChecking(transformer: Transformer) {
        val progressHolder = ProgressHolder()
        progressJob?.cancel()
        progressJob = scope.launch {
            var progressState = transformer.getProgress(progressHolder)
            while (progressState != Transformer.PROGRESS_STATE_NOT_STARTED && isActive) {
                progressState = transformer.getProgress(progressHolder)
                log("PROGRESS = ${progressHolder.progress}%")
                delay(500)
            }
        }
    }


    private fun getOutputPath(): String {
        val file = File.createTempFile("output", ".mp4")
        return file.absolutePath
    }

    private fun imageBitmap(uri: Uri, requiredWidth: Int, requiredHeight: Int): Bitmap {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        val inputStream = context.contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream?.close()
        val width = options.outWidth
        val height = options.outHeight
        var inSample = 1
        if (width > requiredWidth || height > requiredHeight) {
            inSample = if (width > height) {
                round(height / requiredHeight.toFloat()).toInt()
            } else {
                round(width / requiredWidth.toFloat()).toInt()
            }
        }

        options.inJustDecodeBounds = false
        options.inSampleSize = inSample
        val inputStream1 = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream1, null, options)
        inputStream1?.close()
        return bitmap!!
    }
}