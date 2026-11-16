package com.kongjjj.hkpostboxchecker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.kongjjj.hkpostboxchecker.adapter.TrackAdapter
import com.kongjjj.hkpostboxchecker.databinding.BottomSheetTrackHistoryBinding
import com.kongjjj.hkpostboxchecker.model.TrackRecord
import com.kongjjj.hkpostboxchecker.repository.TrackRepository
import kotlinx.coroutines.launch

class TrackHistoryBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetTrackHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var trackRepository: TrackRepository
    private lateinit var trackAdapter: TrackAdapter
    private var isTc: Boolean = true

    var onSelectTrackListener: ((TrackRecord) -> Unit)? = null

    companion object {
        private const val ARG_IS_TC = "arg_is_tc"

        fun newInstance(isTc: Boolean = true): TrackHistoryBottomSheet {
            val fragment = TrackHistoryBottomSheet()
            val args = Bundle().apply {
                putBoolean(ARG_IS_TC, isTc)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            isTc = it.getBoolean(ARG_IS_TC, true)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetTrackHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        trackRepository = TrackRepository(requireContext().applicationContext)

        binding.tvSheetTitle.text = if (isTc) "GPS 軌跡記錄" else "Track History"
        binding.tvEmptyHistory.text = if (isTc) "尚無已儲存的軌跡記錄" else "No saved track records"

        setupRecyclerView()
        loadTracks()
    }

    private fun setupRecyclerView() {
        trackAdapter = TrackAdapter(
            isTc = isTc,
            onShowOnMap = { track ->
                onSelectTrackListener?.invoke(track)
                dismiss()
            },
            onDelete = { track ->
                confirmDeleteTrack(track)
            }
        )

        binding.recyclerTracks.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = trackAdapter
        }
    }

    private fun loadTracks() {
        lifecycleScope.launch {
            val tracks = trackRepository.getAllTracks()
            if (tracks.isEmpty()) {
                binding.tvEmptyHistory.visibility = View.VISIBLE
                binding.recyclerTracks.visibility = View.GONE
            } else {
                binding.tvEmptyHistory.visibility = View.GONE
                binding.recyclerTracks.visibility = View.VISIBLE
                trackAdapter.submitList(tracks)
            }
        }
    }

    private fun confirmDeleteTrack(track: TrackRecord) {
        val title = if (isTc) "刪除軌跡記錄" else "Delete Track"
        val msg = if (isTc) "確定要刪除「${track.title}」嗎？" else "Are you sure to delete '${track.title}'?"
        val posBtn = if (isTc) "刪除" else "Delete"
        val negBtn = if (isTc) "取消" else "Cancel"

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton(posBtn) { _, _ ->
                lifecycleScope.launch {
                    trackRepository.deleteTrack(track.id)
                    loadTracks()
                }
            }
            .setNegativeButton(negBtn, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
