package com.bmdu.voicerecorder.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bmdu.voicerecorder.databinding.ItemRecordBinding
import com.bmdu.voicerecorder.model.Recording

class RecordingsAdapter(
    private val recordings: List<Recording>,
    private val listener: Listener
) : RecyclerView.Adapter<RecordingsAdapter.RecordViewHolder>() {

    interface Listener {
        fun onPlayPauseClicked(recording: Recording, position: Int, binding: ItemRecordBinding)
        fun onTrimClicked(recording: Recording, position: Int)
        fun onDeleteClicked(recording: Recording, position: Int)
        fun onShareClicked(recording: Recording, position: Int)
    }

    inner class RecordViewHolder(val binding: ItemRecordBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val binding =
            ItemRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecordViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        val recording = recordings[position]
        holder.binding.tvName.text = recording.file.name
        holder.binding.tvDuration.text = "${recording.durationMs / 1000}s"

        holder.binding.btnPlay.setOnClickListener {
            listener.onPlayPauseClicked(recording, position, holder.binding)
        }

        holder.binding.btnTrim.setOnClickListener {
            listener.onTrimClicked(recording, position)
        }
        holder.binding.btnDelete.setOnClickListener {
            listener.onDeleteClicked(recording, position)
        }
        holder.binding.btnShare.setOnClickListener {
            listener.onShareClicked(recording, position)
        }
    }

    override fun getItemCount(): Int = recordings.size
}
