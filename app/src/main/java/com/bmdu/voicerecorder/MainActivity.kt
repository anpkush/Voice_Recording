package com.bmdu.voicerecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.bmdu.voicerecorder.adapter.RecordingsAdapter
import com.bmdu.voicerecorder.databinding.ActivityMainBinding
import com.bmdu.voicerecorder.databinding.ItemRecordBinding
import com.bmdu.voicerecorder.model.Recording
import com.google.android.material.slider.RangeSlider
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), RecordingsAdapter.Listener {

    private lateinit var binding: ActivityMainBinding
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var outputFile: String? = null
    private var isRecording = false
    private var handler: Handler = Handler(Looper.getMainLooper())
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    private lateinit var adapter: RecordingsAdapter
    private val recordings = mutableListOf<Recording>()

    private var lastPosition: Int = 0
    private var currentRecording: Recording? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = RecordingsAdapter(recordings, this)
        binding.rvRecords.layoutManager = LinearLayoutManager(this)
        binding.rvRecords.adapter = adapter

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted = permissions[Manifest.permission.RECORD_AUDIO] == true
            if (granted) toggleRecording()
            else Toast.makeText(this, "Microphone permission denied", Toast.LENGTH_SHORT).show()
        }

        binding.btnRecord.setOnClickListener {
            if (!hasPermissions()) {
                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            } else toggleRecording()
        }

        binding.btnRefresh.setOnClickListener { loadRecordings() }

        if (hasPermissions()) loadRecordings()
    }

    private fun hasPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun toggleRecording() {
        if (isRecording) stopRecording()
        else startRecording()
    }

    private fun startRecording() {
        val file = getOutputFile()
        outputFile = file.absolutePath

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(outputFile)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)

            try {
                prepare()
                start()
                isRecording = true
                binding.btnRecord.text = "Stop Recording"

                binding.tvRecordingStatus.visibility = android.view.View.VISIBLE

                Toast.makeText(this@MainActivity, "Recording started", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "Recording failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getOutputFile(): File {
        val musicDir = getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
        val folder = File(musicDir, "VoiceRecorder")
        if (!folder.exists()) folder.mkdirs()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(folder, "REC_$timeStamp.m4a")
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply { stop(); release() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null
        isRecording = false
        binding.btnRecord.text = "Start Recording"

        binding.tvRecordingStatus.visibility = android.view.View.GONE

        outputFile?.let {
            val file = File(it)
            if (file.exists() && file.length() > 0) {
                val duration = getDuration(it)
                val recording = Recording(file, it, duration.toLong())
                recordings.add(recording)
                adapter.notifyItemInserted(recordings.size - 1)
                Toast.makeText(this, "Recording saved", Toast.LENGTH_SHORT).show()
            }
        }
    }


    override fun onPlayPauseClicked(recording: Recording, position: Int, binding: ItemRecordBinding) {
        if (mediaPlayer != null && mediaPlayer!!.isPlaying) {
            mediaPlayer?.pause()
            binding.btnPlay.setImageResource(android.R.drawable.ic_media_play)
        } else {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(recording.filePath)
                prepare()
                start()
            }

            binding.btnPlay.setImageResource(android.R.drawable.ic_media_pause)

            updateSeekBar(mediaPlayer!!, binding)

            mediaPlayer?.setOnCompletionListener {
                binding.btnPlay.setImageResource(android.R.drawable.ic_media_play)
                binding.seekBar.progress = 0
            }
        }
    }



    override fun onTrimClicked(recording: Recording, position: Int) {
        showTrimDialog(recording)
    }

    override fun onDeleteClicked(recording: Recording, position: Int) {
        if (recording.file.exists()) {
            recording.file.delete()
            recordings.removeAt(position)
            adapter.notifyItemRemoved(position)
            Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onShareClicked(recording: Recording, position: Int) {
        val uri: Uri = FileProvider.getUriForFile(
            this, "${packageName}.provider", recording.file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share audio"))
    }

    private fun stopPlaying() {
        mediaPlayer?.release()
        mediaPlayer = null
        lastPosition = 0
        currentRecording = null
        handler.removeCallbacksAndMessages(null)
    }

    private fun updateSeekBar(player: MediaPlayer, itemBinding: ItemRecordBinding) {
        itemBinding.seekBar.max = player.duration

        handler.postDelayed(object : Runnable {
            override fun run() {
                if (player.isPlaying) {
                    itemBinding.seekBar.progress = player.currentPosition
                    handler.postDelayed(this, 500)
                }
            }
        }, 0)
    }


    private fun getDuration(filePath: String): Int {
        val mp = MediaPlayer()
        return try {
            mp.setDataSource(filePath)
            mp.prepare()
            val duration = mp.duration
            mp.release()
            duration
        } catch (e: Exception) {
            0
        }
    }

    private fun loadRecordings() {
        recordings.clear()
        val dir = getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)?.resolve("VoiceRecorder")
        dir?.listFiles()?.filter { it.extension == "m4a" }?.forEach { file ->
            val rec = Recording(file, file.absolutePath, getDuration(file.absolutePath).toLong())
            recordings.add(rec)
        }
        adapter.notifyDataSetChanged()
    }

    private fun showTrimDialog(recording: Recording) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_trim, null)
        val rangeSlider = dialogView.findViewById<RangeSlider>(R.id.rangeSlider)
        val tvRange = dialogView.findViewById<TextView>(R.id.tvRange)

        val durationMs = getDuration(recording.filePath)

        rangeSlider.valueFrom = 0f
        rangeSlider.valueTo = (durationMs / 1000).toFloat()
        rangeSlider.values = listOf(0f, (durationMs / 1000).toFloat())

        rangeSlider.addOnChangeListener { slider, _, _ ->
            val start = slider.values[0].toInt()
            val end = slider.values[1].toInt()
            tvRange.text = "Start: ${start}s   End: ${end}s"
        }

        AlertDialog.Builder(this)
            .setTitle("Trim Audio")
            .setView(dialogView)
            .setPositiveButton("Trim") { _, _ ->
                val start = (rangeSlider.values[0].toInt()) * 1000
                val end = (rangeSlider.values[1].toInt()) * 1000
                trimAudio(recording.filePath, start, end)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun trimAudio(inputPath: String, startMs: Int, endMs: Int) {
        try {
            val inputFile = File(inputPath)
            if (!inputFile.exists()) return

            val tempFile = File(inputFile.parent, "temp_trim.m4a")

            val extractor = MediaExtractor()
            extractor.setDataSource(inputPath)

            val trackIndex = 0
            extractor.selectTrack(trackIndex)

            val format = extractor.getTrackFormat(trackIndex)
            val muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val newTrackIndex = muxer.addTrack(format)
            muxer.start()

            val bufferSize = 1024 * 1024
            val buffer = java.nio.ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            extractor.seekTo((startMs * 1000).toLong(), MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)

                if (bufferInfo.size < 0) break

                val sampleTime = extractor.sampleTime
                if (sampleTime > endMs * 1000) break

                bufferInfo.presentationTimeUs = sampleTime
                bufferInfo.flags = MediaCodec.BUFFER_FLAG_SYNC_FRAME

                muxer.writeSampleData(newTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            extractor.release()

            if (inputFile.delete()) {
                tempFile.renameTo(inputFile)
            }

            runOnUiThread {
                Toast.makeText(this, "Trimmed successfully!", Toast.LENGTH_SHORT).show()
                loadRecordings()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "Trimming failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }



    override fun onDestroy() {
        super.onDestroy()
        stopPlaying()
        mediaRecorder?.release()
        mediaRecorder = null
    }


}
