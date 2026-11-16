package com.kongjjj.hkpostboxchecker.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kongjjj.hkpostboxchecker.databinding.ItemSequenceBoxBinding
import com.kongjjj.hkpostboxchecker.model.PostBox

class SequenceAdapter(
    private var isTc: Boolean = true,
    private val onMoveUp: (Int) -> Unit,
    private val onMoveDown: (Int) -> Unit,
    private val onRemove: (PostBox) -> Unit
) : ListAdapter<PostBox, SequenceAdapter.SequenceViewHolder>(PostBoxDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SequenceViewHolder {
        val binding = ItemSequenceBoxBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SequenceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SequenceViewHolder, position: Int) {
        holder.bind(getItem(position), position, itemCount)
    }

    inner class SequenceViewHolder(private val binding: ItemSequenceBoxBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(postBox: PostBox, position: Int, totalCount: Int) {
            binding.tvSequenceNum.text = "${position + 1}"
            binding.tvSeqBoxName.text = postBox.getDisplayName(isTc)
            binding.tvSeqBoxAddress.text = postBox.getDisplayAddress(isTc)

            binding.btnMoveUp.isEnabled = position > 0
            binding.btnMoveUp.alpha = if (position > 0) 1.0f else 0.3f
            binding.btnMoveUp.setOnClickListener { onMoveUp(position) }

            binding.btnMoveDown.isEnabled = position < totalCount - 1
            binding.btnMoveDown.alpha = if (position < totalCount - 1) 1.0f else 0.3f
            binding.btnMoveDown.setOnClickListener { onMoveDown(position) }

            binding.btnRemoveSeq.setOnClickListener { onRemove(postBox) }
        }
    }

    private class PostBoxDiffCallback : DiffUtil.ItemCallback<PostBox>() {
        override fun areItemsTheSame(oldItem: PostBox, newItem: PostBox): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PostBox, newItem: PostBox): Boolean {
            return oldItem == newItem
        }
    }
}
