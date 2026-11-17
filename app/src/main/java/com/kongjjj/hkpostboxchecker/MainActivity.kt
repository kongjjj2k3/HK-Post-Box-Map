package com.kongjjj.hkpostboxchecker

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.kongjjj.hkpostboxchecker.adapter.PostBoxAdapter
import com.kongjjj.hkpostboxchecker.databinding.ActivityMainBinding
import com.kongjjj.hkpostboxchecker.model.PostBox
import com.kongjjj.hkpostboxchecker.model.TrackRecord
import com.kongjjj.hkpostboxchecker.repository.TrackRepository
import com.kongjjj.hkpostboxchecker.service.GpsTrackingService
import com.kongjjj.hkpostboxchecker.service.TrackingState
import com.kongjjj.hkpostboxchecker.ui.PostBoxDetailBottomSheet
import com.kongjjj.hkpostboxchecker.ui.SequenceListBottomSheet
import com.kongjjj.hkpostboxchecker.ui.SettingsBottomSheet
import com.kongjjj.hkpostboxchecker.ui.TrackHistoryBottomSheet
import com.kongjjj.hkpostboxchecker.viewmodel.MainViewModel
import com.kongjjj.hkpostboxchecker.viewmodel.UiState
import com.kongjjj.hkpostboxchecker.viewmodel.ViewMode
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var trackRepository: TrackRepository

    private lateinit var mapView: MapView
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private lateinit var postBoxAdapter: PostBoxAdapter
    private var optionsMenu: Menu? = null

    // Foreground GPS Service variables
    private var trackingService: GpsTrackingService? = null
    private var isServiceBound = false

    private var useRedTrackingLine = false
    private var activePolyline: Polyline? = null
    private var displayedHistoryPolyline: Polyline? = null
    private val historyMarkers = mutableListOf<Marker>()

    // Planned Route Overlay
    private var plannedRoutePolyline: Polyline? = null
    private val plannedRouteMarkers = mutableListOf<Marker>()

    // Hong Kong Center Coordinates
    private val hkCenterPoint = GeoPoint(22.3193, 114.1694)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as GpsTrackingService.LocalBinder
            trackingService = binder.getService()
            isServiceBound = true
            observeTrackingService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            trackingService = null
            isServiceBound = false
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            enableMyLocation()
        } else {
            Toast.makeText(this, if (viewModel.uiState.value.isTraditionalChinese) "需要定位權限以顯示位置及記錄軌跡" else "Location permission required", Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // osmdroid configuration
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))

        trackRepository = TrackRepository(applicationContext)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)

        setupMapView()
        setupRecyclerView()
        setupSearchView()
        setupDistrictChips(true)
        setupFabButtons()
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, GpsTrackingService::class.java)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    private fun setupMapView() {
        mapView = binding.contentMain.mapView
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setBuiltInZoomControls(false)

        mapView.minZoomLevel = 10.0
        mapView.maxZoomLevel = 19.0
        mapView.controller.setZoom(12.5)
        mapView.controller.setCenter(hkCenterPoint)

        checkLocationPermissionAndEnable()
    }

    private fun setupRecyclerView() {
        postBoxAdapter = PostBoxAdapter(isTc = true) { postBox ->
            viewModel.selectPostBox(postBox)
            focusOnPostBox(postBox)
        }
        binding.contentMain.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = postBoxAdapter
        }
    }

    private fun setupSearchView() {
        binding.contentMain.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.setSearchQuery(query.orEmpty())
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText.orEmpty())
                return true
            }
        })
    }

    private fun setupDistrictChips(isTc: Boolean) {
        val districts = listOf(
            "ALL" to if (isTc) "全部地區" else "All Districts",
            "CENTRAL AND WESTERN" to if (isTc) "中西區" else "Central & Western",
            "EASTERN" to if (isTc) "東區" else "Eastern",
            "SOUTHERN" to if (isTc) "南區" else "Southern",
            "WAN CHAI" to if (isTc) "灣仔區" else "Wan Chai",
            "KOWLOON CITY" to if (isTc) "九龍城區" else "Kowloon City",
            "KWUN TONG" to if (isTc) "觀塘區" else "Kwun Tong",
            "SHAM SHUI PO" to if (isTc) "深水埗區" else "Sham Shui Po",
            "WONG TAI SIN" to if (isTc) "黃大仙區" else "Wong Tai Sin",
            "YAU TSIM MONG" to if (isTc) "油尖旺區" else "Yau Tsim Mong",
            "ISLANDS DISTRICT" to if (isTc) "離島區" else "Islands",
            "KWAI TSING" to if (isTc) "葵青區" else "Kwai Tsing",
            "NORTH" to if (isTc) "北區" else "North",
            "SAI KUNG" to if (isTc) "西貢區" else "Sai Kung",
            "SHA TIN" to if (isTc) "沙田區" else "Sha Tin",
            "TAI PO" to if (isTc) "大埔區" else "Tai Po",
            "TSUEN WAN" to if (isTc) "荃灣區" else "Tsuen Wan",
            "TUEN MUN" to if (isTc) "屯門區" else "Tuen Mun",
            "YUEN LONG" to if (isTc) "元朗區" else "Yuen Long"
        )

        val chipGroup = binding.contentMain.chipGroupDistrict
        val selectedCode = viewModel.uiState.value.selectedDistrict
        chipGroup.removeAllViews()

        districts.forEach { (code, label) ->
            val chipBackgroundColorStateList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()
                ),
                intArrayOf(
                    Color.parseColor("#D32F2F"),
                    Color.parseColor("#2A2A2A")
                )
            )
            val chipTextColorStateList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()
                ),
                intArrayOf(
                    Color.parseColor("#FFFFFF"),
                    Color.parseColor("#E0E0E0")
                )
            )

            val chip = Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = code == selectedCode
                chipBackgroundColor = chipBackgroundColorStateList
                setTextColor(chipTextColorStateList)
                chipStrokeWidth = 2f
                chipStrokeColor = ColorStateList.valueOf(Color.parseColor("#383838"))
                setOnClickListener {
                    viewModel.setDistrictFilter(code)
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun setupFabButtons() {
        // Bottom-Left FABs
        binding.fabPlannedRoute.setOnClickListener {
            viewModel.togglePlannedRouteActive()
            val isActive = viewModel.uiState.value.isPlannedRouteActive
            val isTc = viewModel.uiState.value.isTraditionalChinese
            val count = viewModel.uiState.value.plannedPostBoxes.size
            val msg = if (isActive) {
                if (isTc) "已開啟預定路徑模式 (${count} 個目標)" else "Planned route ON (${count} targets)"
            } else {
                if (isTc) "已關閉預定路徑模式" else "Planned route OFF"
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        binding.fabSequenceList.setOnClickListener {
            val sheet = SequenceListBottomSheet.newInstance()
            sheet.show(supportFragmentManager, "sequence_list")
        }

        // Bottom-Right FABs
        binding.fabTracking.setOnClickListener {
            val isCurrentlyTracking = trackingService?.trackingState?.value?.isTracking ?: false
            if (isCurrentlyTracking) {
                stopTrackingAndSave()
            } else {
                startTracking()
            }
        }

        binding.fabLocation.setOnClickListener {
            if (myLocationOverlay?.isMyLocationEnabled == true && myLocationOverlay?.myLocation != null) {
                mapView.controller.animateTo(myLocationOverlay!!.myLocation, 16.0, 1000L)
            } else {
                checkLocationPermissionAndEnable()
            }
        }

        binding.fabViewMode.setOnClickListener {
            viewModel.toggleViewMode()
        }
    }

    private fun startTracking() {
        if (!hasLocationPermission()) {
            checkLocationPermissionAndEnable()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val intent = Intent(this, GpsTrackingService::class.java).apply {
            action = GpsTrackingService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)

        val isTc = viewModel.uiState.value.isTraditionalChinese
        val msg = if (isTc) "已開啟背景 GPS 行山/路徑軌跡記錄..." else "Background GPS tracking started..."
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun stopTrackingAndSave() {
        val isTc = viewModel.uiState.value.isTraditionalChinese
        val record = trackingService?.stopTrackingInternal()

        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        trackingService = null

        activePolyline?.let { mapView.overlays.remove(it) }
        activePolyline = null
        mapView.invalidate()

        if (record == null || record.points.size < 2) {
            val msg = if (isTc) "記錄點或移動距離過少，未儲存" else "Track points too few to save"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            return
        }

        val defaultTitle = if (isTc) {
            "軌跡 " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(record.startTime))
        } else {
            "Track " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(record.startTime))
        }
        val input = EditText(this).apply {
            setText(defaultTitle)
            setSelection(defaultTitle.length)
        }

        val title = if (isTc) "儲存 GPS 行山軌跡" else "Save Track"
        val message = if (isTc) {
            "總距離: ${record.getFormattedDistance()}\n花費時間: ${record.getFormattedDuration()}\n記錄點: ${record.points.size} 個\n請輸入軌跡名稱:"
        } else {
            "Distance: ${record.getFormattedDistance()}\nDuration: ${record.getFormattedDuration()}\nPoints: ${record.points.size}\nEnter track title:"
        }
        val posBtn = if (isTc) "儲存" else "Save"
        val negBtn = if (isTc) "取消" else "Cancel"

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setView(input)
            .setPositiveButton(posBtn) { _, _ ->
                val titleStr = input.text.toString().ifBlank { defaultTitle }
                val finalRecord = record.copy(title = titleStr)

                lifecycleScope.launch {
                    trackRepository.saveTrack(finalRecord)
                    val toastMsg = if (isTc) "軌跡已成功儲存！(${finalRecord.getFormattedDistance()})" else "Track saved! (${finalRecord.getFormattedDistance()})"
                    Toast.makeText(this@MainActivity, toastMsg, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(negBtn, null)
            .show()
    }

    private fun observeTrackingService() {
        val service = trackingService ?: return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                service.trackingState.collect { state ->
                    updateTrackingUiAndOverlay(state)
                }
            }
        }
    }

    private fun updateTrackingUiAndOverlay(state: TrackingState) {
        if (state.isTracking) {
            binding.fabTracking.setImageResource(R.drawable.ic_record_stop)
            binding.fabTracking.imageTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))

            val trackColor = if (useRedTrackingLine) Color.parseColor("#FF1744") else Color.parseColor("#00E676")
            val geoPoints = state.points.map { GeoPoint(it.latitude, it.longitude) }

            if (activePolyline == null) {
                activePolyline = Polyline(mapView).apply {
                    outlinePaint.color = trackColor
                    outlinePaint.strokeWidth = 14f
                }
                mapView.overlays.add(activePolyline)
            } else {
                activePolyline?.outlinePaint?.color = trackColor
            }
            activePolyline?.setPoints(geoPoints)
            mapView.invalidate()
        } else {
            binding.fabTracking.setImageResource(R.drawable.ic_record_start)
            binding.fabTracking.imageTintList = ColorStateList.valueOf(Color.parseColor("#D32F2F"))
        }
    }

    private fun showTrackOnMap(track: TrackRecord) {
        if (track.points.isEmpty()) return

        val isTc = viewModel.uiState.value.isTraditionalChinese

        // Clear previous displayed history polyline & markers
        displayedHistoryPolyline?.let { mapView.overlays.remove(it) }
        historyMarkers.forEach { mapView.overlays.remove(it) }
        historyMarkers.clear()

        val geoPoints = track.points.map { GeoPoint(it.latitude, it.longitude) }

        // Create cyan history polyline
        displayedHistoryPolyline = Polyline(mapView).apply {
            outlinePaint.color = Color.parseColor("#40C4FF")
            outlinePaint.strokeWidth = 14f
            setPoints(geoPoints)
        }
        mapView.overlays.add(displayedHistoryPolyline)

        val startDrawable = AppCompatResources.getDrawable(this, R.drawable.ic_marker_start)
        val endDrawable = AppCompatResources.getDrawable(this, R.drawable.ic_marker_end)

        // Start marker (Green Pin)
        val startTitle = if (isTc) "起點: ${track.title}" else "Start: ${track.title}"
        val startMarker = Marker(mapView).apply {
            position = geoPoints.first()
            title = startTitle
            icon = startDrawable
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        // End marker (Red Pin)
        val endTitle = if (isTc) "終點: ${track.getFormattedDistance()}" else "End: ${track.getFormattedDistance()}"
        val endMarker = Marker(mapView).apply {
            position = geoPoints.last()
            title = endTitle
            icon = endDrawable
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }

        mapView.overlays.add(startMarker)
        mapView.overlays.add(endMarker)
        historyMarkers.add(startMarker)
        historyMarkers.add(endMarker)

        // Zoom map to fit the track
        val boundingBox = BoundingBox.fromGeoPoints(geoPoints)
        mapView.zoomToBoundingBox(boundingBox, true, 100)

        val toastMsg = if (isTc) "已在地圖上顯示軌跡：${track.title}" else "Showing track on map: ${track.title}"
        Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
    }

    private fun clearDisplayedHistoryTrack() {
        val isTc = viewModel.uiState.value.isTraditionalChinese
        displayedHistoryPolyline?.let { mapView.overlays.remove(it) }
        displayedHistoryPolyline = null
        historyMarkers.forEach { mapView.overlays.remove(it) }
        historyMarkers.clear()
        mapView.invalidate()
        val msg = if (isTc) "已隱藏地圖顯示的歷史軌跡" else "Hidden displayed track from map"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkLocationPermissionAndEnable() {
        if (hasLocationPermission()) {
            enableMyLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun enableMyLocation() {
        if (myLocationOverlay == null) {
            myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView)
            mapView.overlays.add(myLocationOverlay)
        }
        myLocationOverlay?.enableMyLocation()
        myLocationOverlay?.enableFollowLocation()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUiState(state)
                }
            }
        }
    }

    private fun renderUiState(state: UiState) {
        // 1. Update Monolingual Language Texts
        updateLanguageUi(state.isTraditionalChinese)

        // 2. Loading
        binding.contentMain.progressIndicator.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        // 3. View Mode
        if (state.viewMode == ViewMode.MAP) {
            binding.contentMain.mapView.visibility = View.VISIBLE
            binding.contentMain.recyclerView.visibility = View.GONE
            binding.fabViewMode.setImageResource(R.drawable.ic_list)
        } else {
            binding.contentMain.mapView.visibility = View.GONE
            binding.contentMain.recyclerView.visibility = View.VISIBLE
            binding.fabViewMode.setImageResource(R.drawable.ic_map)
        }

        // 4. Status Count
        val countText = if (state.isTraditionalChinese) {
            "已顯示 ${state.filteredPostBoxes.size} 個郵箱"
        } else {
            "Showing ${state.filteredPostBoxes.size} boxes"
        }
        binding.contentMain.tvStatusCount.text = countText

        // 5. Update List & Map Markers
        postBoxAdapter.updateLanguage(state.isTraditionalChinese)
        postBoxAdapter.submitList(state.filteredPostBoxes)

        updateMapMarkers(state.filteredPostBoxes, state.isTraditionalChinese)
        updatePlannedRouteOverlay(state.plannedPostBoxes, state.isPlannedRouteActive, state.isTraditionalChinese)

        // 6. Error message snackbar
        state.errorMessage?.let { msg ->
            val retryText = if (state.isTraditionalChinese) "重試" else "Retry"
            Snackbar.make(binding.main, msg, Snackbar.LENGTH_LONG)
                .setAction(retryText) { viewModel.loadPostBoxes(forceRefresh = true) }
                .show()
        }

        // 7. Selected PostBox Bottom Sheet
        state.selectedPostBox?.let { postBox ->
            val existingSheet = supportFragmentManager.findFragmentByTag("post_box_detail") as? PostBoxDetailBottomSheet
            if (existingSheet == null) {
                val sheet = PostBoxDetailBottomSheet.newInstance(postBox, state.isTraditionalChinese).apply {
                    onDismissListener = {
                        viewModel.selectPostBox(null)
                    }
                    onAddToPlannedRouteListener = { box ->
                        viewModel.addPostBoxToPlannedRoute(box)
                        val num = viewModel.uiState.value.plannedPostBoxes.size
                        val name = box.getDisplayName(state.isTraditionalChinese)
                        val toastMsg = if (state.isTraditionalChinese) "已加入預定順序 #${num}：${name}" else "Added to planned sequence #${num}: ${name}"
                        Toast.makeText(this@MainActivity, toastMsg, Toast.LENGTH_SHORT).show()
                    }
                }
                sheet.show(supportFragmentManager, "post_box_detail")
            }
        } ?: run {
            val existingSheet = supportFragmentManager.findFragmentByTag("post_box_detail") as? PostBoxDetailBottomSheet
            existingSheet?.dismissAllowingStateLoss()
        }
    }

    private fun updateLanguageUi(isTc: Boolean) {
        supportActionBar?.title = if (isTc) "香港郵箱地圖" else "HK Post Box Map"
        binding.contentMain.searchView.queryHint = if (isTc) "搜尋郵箱編號、地址或地區..." else "Search box no., address, district..."

        optionsMenu?.findItem(R.id.action_track_history)?.title = if (isTc) "查看軌跡記錄" else "Track History"
        optionsMenu?.findItem(R.id.action_settings)?.title = if (isTc) "設定" else "Settings"

        setupDistrictChips(isTc)
    }

    private fun updateMapMarkers(postBoxes: List<PostBox>, isTc: Boolean) {
        // Clear previous markers except location overlay, active polyline, history polyline and history/planned markers
        val overlaysToRemove = mapView.overlays.filterIsInstance<Marker>().filter {
            it !in historyMarkers && it !in plannedRouteMarkers
        }
        mapView.overlays.removeAll(overlaysToRemove.toSet())

        val postboxDrawable = AppCompatResources.getDrawable(this, R.drawable.ic_postbox)

        for (postBox in postBoxes) {
            val marker = Marker(mapView).apply {
                position = GeoPoint(postBox.latitude, postBox.longitude)
                title = postBox.getDisplayName(isTc)
                snippet = postBox.getDisplayAddress(isTc)
                icon = postboxDrawable
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { m, _ ->
                    viewModel.selectPostBox(postBox)
                    m.showInfoWindow()
                    true
                }
            }
            mapView.overlays.add(marker)
        }

        // Re-ensure displayed history polyline & markers stay persistent on map
        displayedHistoryPolyline?.let {
            if (!mapView.overlays.contains(it)) {
                mapView.overlays.add(it)
            }
        }
        historyMarkers.forEach {
            if (!mapView.overlays.contains(it)) {
                mapView.overlays.add(it)
            }
        }

        mapView.invalidate()
    }

    private fun updatePlannedRouteOverlay(plannedBoxes: List<PostBox>, isActive: Boolean, isTc: Boolean) {
        plannedRoutePolyline?.let { mapView.overlays.remove(it) }
        plannedRouteMarkers.forEach { mapView.overlays.remove(it) }
        plannedRouteMarkers.clear()

        if (!isActive || plannedBoxes.isEmpty()) {
            binding.fabPlannedRoute.imageTintList = ColorStateList.valueOf(Color.parseColor("#757575"))
            mapView.invalidate()
            return
        }

        binding.fabPlannedRoute.imageTintList = ColorStateList.valueOf(Color.parseColor("#FF6F00"))

        val geoPoints = plannedBoxes.map { GeoPoint(it.latitude, it.longitude) }

        if (geoPoints.size >= 2) {
            plannedRoutePolyline = Polyline(mapView).apply {
                outlinePaint.color = Color.parseColor("#FFB300")
                outlinePaint.strokeWidth = 14f
                setPoints(geoPoints)
            }
            mapView.overlays.add(plannedRoutePolyline)
        }

        // Add sequence badges on map
        plannedBoxes.forEachIndexed { index, postBox ->
            val sequenceMarker = Marker(mapView).apply {
                position = GeoPoint(postBox.latitude, postBox.longitude)
                title = if (isTc) "目標 #${index + 1}: ${postBox.getDisplayName(isTc)}" else "Target #${index + 1}: ${postBox.getDisplayName(isTc)}"
                snippet = postBox.getDisplayAddress(isTc)
                icon = createSequenceMarkerIcon(index + 1)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { m, _ ->
                    viewModel.selectPostBox(postBox)
                    m.showInfoWindow()
                    true
                }
            }
            mapView.overlays.add(sequenceMarker)
            plannedRouteMarkers.add(sequenceMarker)
        }
        mapView.invalidate()
    }

    private fun createSequenceMarkerIcon(sequenceNum: Int): Drawable {
        val size = (36 * resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFB300")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f * resources.displayMetrics.density
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2f, borderPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#121212")
            textSize = 16f * resources.displayMetrics.density
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val yPos = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText("$sequenceNum", size / 2f, yPos, textPaint)

        return BitmapDrawable(resources, bitmap)
    }

    private fun focusOnPostBox(postBox: PostBox) {
        mapView.controller.animateTo(GeoPoint(postBox.latitude, postBox.longitude), 16.0, 800L)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        optionsMenu = menu
        updateLanguageUi(viewModel.uiState.value.isTraditionalChinese)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val isTc = viewModel.uiState.value.isTraditionalChinese
        return when (item.itemId) {
            R.id.action_settings -> {
                val sheet = SettingsBottomSheet.newInstance(useRedTrackingLine).apply {
                    onToggleTrackColorListener = { isRed ->
                        useRedTrackingLine = isRed
                        val color = if (useRedTrackingLine) Color.parseColor("#FF1744") else Color.parseColor("#00E676")
                        activePolyline?.outlinePaint?.color = color
                        mapView.invalidate()
                        updateLanguageUi(viewModel.uiState.value.isTraditionalChinese)
                    }
                    onRefreshDataListener = {
                        viewModel.loadPostBoxes(forceRefresh = true)
                        val msg = if (isTc) "正在線上更新 CSDI 郵箱數據..." else "Refreshing CSDI data online..."
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }
                sheet.show(supportFragmentManager, "settings")
                true
            }
            R.id.action_track_history -> {
                val sheet = TrackHistoryBottomSheet.newInstance(isTc).apply {
                    onSelectTrackListener = { track ->
                        showTrackOnMap(track)
                    }
                    onClearDisplayedTrackListener = {
                        clearDisplayedHistoryTrack()
                    }
                }
                sheet.show(supportFragmentManager, "track_history")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}
