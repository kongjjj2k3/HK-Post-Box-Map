package com.kongjjj.hkpostboxchecker.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kongjjj.hkpostboxchecker.model.PostBox
import com.kongjjj.hkpostboxchecker.repository.PostBoxRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ViewMode {
    MAP, LIST
}

data class UiState(
    val isLoading: Boolean = true,
    val postBoxes: List<PostBox> = emptyList(),
    val filteredPostBoxes: List<PostBox> = emptyList(),
    val selectedPostBox: PostBox? = null,
    val searchQuery: String = "",
    val selectedDistrict: String = "ALL",
    val errorMessage: String? = null,
    val isTraditionalChinese: Boolean = true,
    val viewMode: ViewMode = ViewMode.MAP,
    val isPlannedRouteActive: Boolean = false,
    val plannedPostBoxes: List<PostBox> = emptyList(),
    val isEditModeEnabled: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PostBoxRepository(application.applicationContext)
    private val prefs = application.getSharedPreferences("planned_route_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    companion object {
        private const val KEY_PLANNED_BOX_IDS = "key_planned_box_ids"
        private const val KEY_PLANNED_ROUTE_ACTIVE = "key_planned_route_active"
    }

    init {
        loadPostBoxes()
    }

    fun loadPostBoxes(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = repository.getPostBoxes(forceRefresh)
            result.onSuccess { list ->
                val filtered = filterList(list, _uiState.value.searchQuery, _uiState.value.selectedDistrict)

                // Restore saved planned route sequence from SharedPreferences
                val (restoredPlannedList, isActive) = loadSavedPlannedRoute(list)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    postBoxes = list,
                    filteredPostBoxes = filtered,
                    plannedPostBoxes = restoredPlannedList,
                    isPlannedRouteActive = isActive && restoredPlannedList.isNotEmpty()
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.localizedMessage ?: "Failed to load data"
                )
            }
        }
    }

    fun toggleEditMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isEditModeEnabled = enabled)
    }

    fun updatePostBox(updatedBox: PostBox) {
        val currentBoxes = _uiState.value.postBoxes.toMutableList()
        val index = currentBoxes.indexOfFirst { it.id == updatedBox.id }
        if (index != -1) {
            currentBoxes[index] = updatedBox
            val filtered = filterList(currentBoxes, _uiState.value.searchQuery, _uiState.value.selectedDistrict)
            val updatedSelected = if (_uiState.value.selectedPostBox?.id == updatedBox.id) updatedBox else _uiState.value.selectedPostBox

            // Also update in planned route if present
            val updatedPlanned = _uiState.value.plannedPostBoxes.map { if (it.id == updatedBox.id) updatedBox else it }

            _uiState.value = _uiState.value.copy(
                postBoxes = currentBoxes,
                filteredPostBoxes = filtered,
                selectedPostBox = updatedSelected,
                plannedPostBoxes = updatedPlanned
            )

            viewModelScope.launch {
                repository.saveUserEditedPostBoxes(currentBoxes)
            }
        }
    }

    fun addPostBox(newBox: PostBox) {
        val currentBoxes = _uiState.value.postBoxes.toMutableList()
        currentBoxes.add(0, newBox)
        val filtered = filterList(currentBoxes, _uiState.value.searchQuery, _uiState.value.selectedDistrict)
        _uiState.value = _uiState.value.copy(
            postBoxes = currentBoxes,
            filteredPostBoxes = filtered,
            selectedPostBox = newBox
        )

        viewModelScope.launch {
            repository.saveUserEditedPostBoxes(currentBoxes)
        }
    }

    fun setSearchQuery(query: String) {
        val current = _uiState.value
        val filtered = filterList(current.postBoxes, query, current.selectedDistrict)
        _uiState.value = current.copy(searchQuery = query, filteredPostBoxes = filtered, selectedPostBox = null)
    }

    fun setDistrictFilter(district: String) {
        val current = _uiState.value
        val filtered = filterList(current.postBoxes, current.searchQuery, district)
        _uiState.value = current.copy(selectedDistrict = district, filteredPostBoxes = filtered, selectedPostBox = null)
    }

    fun selectPostBox(postBox: PostBox?) {
        _uiState.value = _uiState.value.copy(selectedPostBox = postBox)
    }

    fun toggleLanguage() {
        _uiState.value = _uiState.value.copy(isTraditionalChinese = !_uiState.value.isTraditionalChinese)
    }

    fun toggleViewMode() {
        val newMode = if (_uiState.value.viewMode == ViewMode.MAP) ViewMode.LIST else ViewMode.MAP
        _uiState.value = _uiState.value.copy(viewMode = newMode, selectedPostBox = null)
    }

    fun togglePlannedRouteActive() {
        val newActive = !_uiState.value.isPlannedRouteActive
        _uiState.value = _uiState.value.copy(isPlannedRouteActive = newActive)
        persistPlannedRoute(_uiState.value.plannedPostBoxes, newActive)
    }

    fun addPostBoxToPlannedRoute(postBox: PostBox) {
        val currentList = _uiState.value.plannedPostBoxes.toMutableList()
        if (!currentList.any { it.id == postBox.id }) {
            currentList.add(postBox)
            _uiState.value = _uiState.value.copy(plannedPostBoxes = currentList, isPlannedRouteActive = true)
            persistPlannedRoute(currentList, true)
        }
    }

    fun removePostBoxFromPlannedRoute(postBox: PostBox) {
        val currentList = _uiState.value.plannedPostBoxes.toMutableList()
        currentList.removeAll { it.id == postBox.id }
        val newActive = _uiState.value.isPlannedRouteActive && currentList.isNotEmpty()
        _uiState.value = _uiState.value.copy(plannedPostBoxes = currentList, isPlannedRouteActive = newActive)
        persistPlannedRoute(currentList, newActive)
    }

    fun movePlannedPostBox(fromPos: Int, toPos: Int) {
        val currentList = _uiState.value.plannedPostBoxes.toMutableList()
        if (fromPos in currentList.indices && toPos in currentList.indices) {
            val item = currentList.removeAt(fromPos)
            currentList.add(toPos, item)
            _uiState.value = _uiState.value.copy(plannedPostBoxes = currentList)
            persistPlannedRoute(currentList, _uiState.value.isPlannedRouteActive)
        }
    }

    fun clearPlannedRoute() {
        _uiState.value = _uiState.value.copy(plannedPostBoxes = emptyList(), isPlannedRouteActive = false)
        persistPlannedRoute(emptyList(), false)
    }

    private fun persistPlannedRoute(plannedBoxes: List<PostBox>, isActive: Boolean) {
        val idsString = plannedBoxes.joinToString(",") { it.id.toString() }
        prefs.edit()
            .putString(KEY_PLANNED_BOX_IDS, idsString)
            .putBoolean(KEY_PLANNED_ROUTE_ACTIVE, isActive)
            .apply()
    }

    private fun loadSavedPlannedRoute(allPostBoxes: List<PostBox>): Pair<List<PostBox>, Boolean> {
        val idsString = prefs.getString(KEY_PLANNED_BOX_IDS, "") ?: ""
        val isActive = prefs.getBoolean(KEY_PLANNED_ROUTE_ACTIVE, false)
        if (idsString.isBlank()) return Pair(emptyList(), false)

        val idList = idsString.split(",").mapNotNull { it.trim().toIntOrNull() }
        val boxMap = allPostBoxes.associateBy { it.id }
        val restoredList = idList.mapNotNull { boxMap[it] }

        return Pair(restoredList, isActive)
    }

    private fun filterList(list: List<PostBox>, query: String, district: String): List<PostBox> {
        var filtered = list
        if (district != "ALL" && district.isNotBlank()) {
            val dUpper = district.uppercase()
            filtered = filtered.filter { pb ->
                val enUpper = pb.districtEn.uppercase()
                enUpper.equals(dUpper, ignoreCase = true) ||
                        pb.districtTc.contains(district, ignoreCase = true) ||
                        (dUpper.contains("CENTRAL") && (enUpper.contains("CENTRAL") || pb.districtTc.contains("中西區"))) ||
                        (dUpper.contains("ISLANDS") && (enUpper.contains("ISLANDS") || pb.districtTc.contains("離島區")))
            }
        }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            filtered = filtered.filter { pb ->
                pb.nameEn.lowercase().contains(q) ||
                        pb.nameTc.lowercase().contains(q) ||
                        pb.addressEn.lowercase().contains(q) ||
                        pb.addressTc.lowercase().contains(q) ||
                        pb.districtEn.lowercase().contains(q) ||
                        pb.districtTc.lowercase().contains(q) ||
                        pb.subDistrictEn.lowercase().contains(q) ||
                        pb.subDistrictTc.lowercase().contains(q) ||
                        pb.boxNumber.lowercase().contains(q)
            }
        }
        return filtered
    }
}
