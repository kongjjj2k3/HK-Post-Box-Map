package com.kongjjj.hkpostboxchecker.ui

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.kongjjj.hkpostboxchecker.R
import com.kongjjj.hkpostboxchecker.databinding.BottomSheetSettingsBinding
import com.kongjjj.hkpostboxchecker.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class SettingsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    var isRedTrackingLine: Boolean = false
    var onToggleTrackColorListener: ((Boolean) -> Unit)? = null
    var onRefreshDataListener: (() -> Unit)? = null

    companion object {
        fun newInstance(isRedTrackingLine: Boolean): SettingsBottomSheet {
            val sheet = SettingsBottomSheet()
            sheet.isRedTrackingLine = isRedTrackingLine
            return sheet
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTrackColorSwitch()
        setupLanguageRadioGroup()
        setupRefreshButton()
        observeViewModel()
    }

    private fun setupTrackColorSwitch() {
        binding.switchTrackColor.isChecked = isRedTrackingLine
        binding.switchTrackColor.setOnCheckedChangeListener { _, isChecked ->
            onToggleTrackColorListener?.invoke(isChecked)
        }
    }

    private fun setupLanguageRadioGroup() {
        val isTc = viewModel.uiState.value.isTraditionalChinese
        if (isTc) {
            binding.rbTc.isChecked = true
        } else {
            binding.rbEn.isChecked = true
        }

        binding.rgLanguage.setOnCheckedChangeListener { _, checkedId ->
            val shouldBeTc = checkedId == R.id.rb_tc
            if (shouldBeTc != viewModel.uiState.value.isTraditionalChinese) {
                viewModel.toggleLanguage()
            }
        }
    }

    private fun setupRefreshButton() {
        binding.btnRefreshData.setOnClickListener {
            onRefreshDataListener?.invoke()
            dismiss()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val isTc = state.isTraditionalChinese
                    binding.tvSettingsTitle.text = if (isTc) "設定" else "Settings"
                    binding.tvLblLanguage.text = if (isTc) "語言選擇" else "Language"
                    binding.tvLblRefresh.text = if (isTc) "郵箱數據" else "Post Box Data"
                    binding.tvDescRefresh.text = if (isTc) "從 CSDI 平台線上更新全港郵箱最新資料" else "Update all HK post box data online from CSDI"
                    binding.btnRefreshData.text = if (isTc) "更新" else "Refresh"
                    binding.tvLblTrackColor.text = if (isTc) "錄製軌跡顏色" else "Tracking Line Color"
                    binding.tvDescTrackColor.text = if (isTc) "切換紅色 / 綠色軌跡路線" else "Toggle Red / Green tracking line"
                    binding.tvLblAbout.text = if (isTc) "關於" else "About"
                    updateAppVersionDisplay(isTc)
                }
            }
        }
    }

    private fun updateAppVersionDisplay(isTc: Boolean) {
        val versionStr = try {
            val ctx = requireContext()
            val pInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            val vName = pInfo.versionName ?: "1.0"
            @Suppress("DEPRECATION")
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode else pInfo.versionCode.toLong()
            "v$vName (Build $vCode)"
        } catch (e: Exception) {
            "v1.0"
        }
        val appTitle = if (isTc) "香港郵箱地圖 " else "HK Post Box Map "
        binding.tvAppVersion.text = "$appTitle$versionStr  by kongjjj"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
