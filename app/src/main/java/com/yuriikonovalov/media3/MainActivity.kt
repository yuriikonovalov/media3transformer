package com.yuriikonovalov.media3

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.yuriikonovalov.media3.databinding.ActivityMainBinding
import com.yuriikonovalov.media3.transformer.Media3Editor

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val editor: Media3Editor = Media3Editor(this)
    private var video1Uri: Uri = Uri.EMPTY
    private var video2Uri: Uri = Uri.EMPTY
    private var imageUri: Uri = Uri.EMPTY
    private var gifFilterUri: Uri = Uri.EMPTY
    private var audioUri: Uri = Uri.EMPTY

    private val video1Picker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        binding.video1TextView.text = uri?.toString()
        log("video1 = $uri")
        video1Uri = uri!!
    }

    private val video2Picker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        binding.video2TextView.text = uri?.toString()
        log("video2 = $uri")
        video2Uri = uri!!
    }

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        log("image = $uri")
        binding.imageTextView.text = uri?.toString()
        imageUri = uri!!
    }


    private val gifFilterPicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        log("gif filter = $uri")
        binding.gifTextView.text = uri?.toString()
        gifFilterUri = uri!!
    }

    private val audioPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        log("audio = $uri")
        binding.audioTextView.text = uri?.toString()
        audioUri = uri!!
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.chooseVideo1Button.setOnClickListener {
            video1Picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }

        binding.chooseVideo2Button.setOnClickListener {
            video2Picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }

        binding.chooseImageButton.setOnClickListener {
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.chooseGifButton.setOnClickListener {
            gifFilterPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.chooseAudioButton.setOnClickListener {
            audioPicker.launch("audio/*")
        }

        binding.startLayoutButton.setOnClickListener {
            editor.processLayout(video1Uri, video2Uri, imageUri)
        }

        binding.startTimelineButton.setOnClickListener {
            editor.processTimeline(video1Uri, video2Uri, imageUri, audioUri)
        }

        binding.startAnimatedFilterButton.setOnClickListener {
            editor.processAnimatedFilter(imageUri, gifFilterUri)
        }

        binding.cancelButton.setOnClickListener {
            editor.cancel()
        }
    }
}