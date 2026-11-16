package com.kongjjj.hkpostboxchecker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.kongjjj.hkpostboxchecker.adapter.SequenceAdapter
import com.kongjjj.hkpostboxchecker.databinding.BottomSheetSequenceListBinding
import com.kongjjj.hkpostboxchecker.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class SequenceListBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSequenceListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var sequenceAdapter: SequenceAdapter

    companion object {
        fun newInstance(): SequenceListBottomSheet {
            return SequenceListBottomSheet()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSequenceListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isTc = viewModel.uiState.value.isTraditionalChinese
        binding.tvSeqTitle.text = if (isTc) "郵箱預定順序列表" else "Planned Route Sequence"
        binding.btnClearSeq.text = if (isTc) "清空" else "Clear"
        binding.tvEmptySeq.text = if (isTc) "尚未加入任何郵箱目標\n在地圖點擊郵箱即可加入預定順序" else "No post boxes added\nClick any post box on the map to add"

        setupRecyclerView()
        setupButtons(isTc)
        observeViewModel()
    }

    private fun setupRecyclerView() {
        sequenceAdapter = SequenceAdapter(
            isTc = viewModel.uiState.value.isTraditionalChinese,
            onMoveUp = { pos -> viewModel.movePlannedPostBox(pos, pos - 1) },
            onMoveDown = { pos -> viewModel.movePlannedPostBox(pos, pos + 1) },
            onRemove = { postBox -> viewModel.removePostBoxFromPlannedRoute(postBox) }
        )

        binding.recyclerSeq.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = sequenceAdapter
        }
    }

    private fun setupButtons(isTc: Boolean) {
        binding.btnClearSeq.setOnClickListener {
            if (viewModel.uiState.value.plannedPostBoxes.isEmpty()) return@setOnClickListener
            val title = if (isTc) "清空預定路徑" else "Clear Planned Route"
            val msg = if (isTc) "確定要清空所有預定郵箱目標嗎？" else "Are you sure to clear all planned post boxes?"
            val posBtn = if (isTc) "清空" else "Clear"
            val negBtn = if (isTc) "取消" else "Cancel"

            AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton(posBtn) { _, _ ->
                    viewModel.clearPlannedRoute()
                }
                .setNegativeButton(negBtn, null)
                .show()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val list = state.plannedPostBoxes
                    if (list.isEmpty()) {
                        binding.tvEmptySeq.visibility = View.VISIBLE
                        binding.recyclerSeq.visibility = View.GONE
                    } else {
                        binding.tvEmptySeq.visibility = View.GONE
                        binding.recyclerSeq.visibility = View.VISIBLE
                        sequenceAdapter.submitList(list)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
