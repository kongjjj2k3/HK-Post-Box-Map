package com.kongjjj.hkpostboxchecker.ui

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.kongjjj.hkpostboxchecker.databinding.BottomSheetPostboxDetailBinding
import com.kongjjj.hkpostboxchecker.model.PostBox

class PostBoxDetailBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPostboxDetailBinding? = null
    private val binding get() = _binding!!

    private var postBox: PostBox? = null
    private var isTc: Boolean = true
    private var isEditMode: Boolean = false

    var onDismissListener: (() -> Unit)? = null
    var onAddToPlannedRouteListener: ((PostBox) -> Unit)? = null
    var onEditPostBoxListener: ((PostBox) -> Unit)? = null

    companion object {
        private const val ARG_POST_BOX = "arg_post_box"
        private const val ARG_IS_TC = "arg_is_tc"
        private const val ARG_IS_EDIT_MODE = "arg_is_edit_mode"

        fun newInstance(postBox: PostBox, isTc: Boolean, isEditMode: Boolean = false): PostBoxDetailBottomSheet {
            val fragment = PostBoxDetailBottomSheet()
            val args = Bundle().apply {
                putSerializable(ARG_POST_BOX, postBox)
                putBoolean(ARG_IS_TC, isTc)
                putBoolean(ARG_IS_EDIT_MODE, isEditMode)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            @Suppress("DEPRECATION")
            postBox = it.getSerializable(ARG_POST_BOX) as? PostBox
            isTc = it.getBoolean(ARG_IS_TC, true)
            isEditMode = it.getBoolean(ARG_IS_EDIT_MODE, false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPostboxDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val box = postBox ?: return

        binding.tvPostBoxName.text = box.getDisplayName(isTc)
        binding.tvDistrictTag.text = box.getDisplayDistrict(isTc)
        binding.tvAddress.text = box.getDisplayAddress(isTc)
        binding.tvCollectionTime.text = box.getDisplayCollectionTime(isTc)

        binding.lblAddress.text = if (isTc) "位置" else "Address"
        binding.lblCollectionTime.text = if (isTc) "截郵時間" else "Collection Time"
        binding.lblExtraInfo.text = if (isTc) "分區與經緯度" else "SubDistrict & Coordinates"

        val subDistrict = box.getDisplaySubDistrict(isTc)
        val coordsText = "(${String.format("%.5f", box.latitude)}, ${String.format("%.5f", box.longitude)})"
        binding.tvSubdistrictCoords.text = if (subDistrict.isNotBlank()) "$subDistrict  $coordsText" else coordsText

        if (isEditMode) {
            binding.btnEditPostBox.visibility = View.VISIBLE
            binding.btnEditPostBox.text = if (isTc) "編輯郵箱資訊" else "Edit Post Box Info"
            binding.btnEditPostBox.setOnClickListener {
                onEditPostBoxListener?.invoke(box)
                dismiss()
            }
        } else {
            binding.btnEditPostBox.visibility = View.GONE
        }

        binding.btnAddToRoute.text = if (isTc) "加入預定路徑順序" else "Add to Planned Route"
        binding.btnAddToRoute.setOnClickListener {
            onAddToPlannedRouteListener?.invoke(box)
            dismiss()
        }

        binding.btnNavigate.text = if (isTc) "在 Google 地圖中開啟導航" else "Open Navigation in Google Maps"
        binding.btnNavigate.setOnClickListener {
            val gmmIntentUri = Uri.parse("geo:${box.latitude},${box.longitude}?q=${box.latitude},${box.longitude}(${Uri.encode(box.getDisplayName(isTc))})")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            if (mapIntent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(mapIntent)
            } else {
                val browserUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${box.latitude},${box.longitude}")
                startActivity(Intent(Intent.ACTION_VIEW, browserUri))
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener?.invoke()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
