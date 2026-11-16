package com.kongjjj.hkpostboxchecker.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kongjjj.hkpostboxchecker.databinding.ItemPostBoxBinding
import com.kongjjj.hkpostboxchecker.model.PostBox

class PostBoxAdapter(
    private var isTc: Boolean = true,
    private val onItemClick: (PostBox) -> Unit
) : ListAdapter<PostBox, PostBoxAdapter.PostBoxViewHolder>(PostBoxDiffCallback()) {

    fun updateLanguage(isTc: Boolean) {
        this.isTc = isTc
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostBoxViewHolder {
        val binding = ItemPostBoxBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PostBoxViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostBoxViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PostBoxViewHolder(private val binding: ItemPostBoxBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(postBox: PostBox) {
            binding.tvItemTitle.text = postBox.getDisplayName(isTc)
            binding.tvItemAddress.text = postBox.getDisplayAddress(isTc)
            binding.tvItemDistrict.text = postBox.getDisplayDistrict(isTc)
            
            val collectionTime = postBox.getDisplayCollectionTime(isTc)
            val prefix = if (isTc) "截郵時間: " else "Collection: "
            binding.tvItemCollection.text = if (collectionTime.isNotBlank()) "$prefix$collectionTime" else ""

            binding.root.setOnClickListener {
                onItemClick(postBox)
            }
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
