package com.kongjjj.hkpostboxchecker.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kongjjj.hkpostboxchecker.databinding.ItemTrackRecordBinding
import com.kongjjj.hkpostboxchecker.model.TrackRecord

class TrackAdapter(
    private val isTc: Boolean = true,
    private val onShowOnMap: (TrackRecord) -> Unit,
    private val onDelete: (TrackRecord) -> Unit
) : ListAdapter<TrackRecord, TrackAdapter.TrackViewHolder>(TrackDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val binding = ItemTrackRecordBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TrackViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TrackViewHolder(private val binding: ItemTrackRecordBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(track: TrackRecord) {
            binding.tvTrackTitle.text = track.title

            val startPrefix = if (isTc) "開始時間: " else "Start: "
            val distPrefix = if (isTc) "距離: " else "Distance: "
            val timePrefix = if (isTc) "時間: " else "Duration: "

            binding.tvTrackDate.text = "$startPrefix${track.getFormattedDate()}"
            binding.tvTrackDistance.text = "$distPrefix${track.getFormattedDistance()}"
            binding.tvTrackDuration.text = "$timePrefix${track.getFormattedDuration()}"

            binding.btnShowOnMap.text = if (isTc) "在地圖上顯示軌跡" else "Show Track on Map"
            binding.btnShowOnMap.setOnClickListener {
                onShowOnMap(track)
            }

            binding.btnDeleteTrack.setOnClickListener {
                onDelete(track)
            }
        }
    }

    private class TrackDiffCallback : DiffUtil.ItemCallback<TrackRecord>() {
        override fun areItemsTheSame(oldItem: TrackRecord, newItem: TrackRecord): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TrackRecord, newItem: TrackRecord): Boolean {
            return oldItem == newItem
        }
    }
}
