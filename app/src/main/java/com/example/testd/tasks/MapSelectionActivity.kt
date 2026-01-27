package com.example.testd.tasks

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.MapView
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeAddress
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch
import com.example.testd.R
import com.example.testd.UserSession
import org.json.JSONArray
import org.json.JSONObject

class MapSelectionActivity : AppCompatActivity(),
    GeocodeSearch.OnGeocodeSearchListener,
    PoiSearch.OnPoiSearchListener {

    private lateinit var mapView: MapView
    private lateinit var aMap: AMap
    private lateinit var geocodeSearch: GeocodeSearch
    private lateinit var tvSelectedAddress: TextView
    private lateinit var etSearchKeyword: EditText
    private lateinit var btnSearch: ImageButton
    private lateinit var btnShowFavorites: Button
    private lateinit var btnSaveFavorite: Button
    private lateinit var btnConfirm: Button
    private lateinit var rvPlaceList: RecyclerView
    private lateinit var placeAdapter: PlaceAdapter

    private var locationClient: AMapLocationClient? = null
    private var currentLatLng: LatLng? = null
    private var currentAddressName: String? = null
    private var currentAddressDetail: String? = null
    private var currentCity: String? = null
    private var searchResults: List<PlaceItem> = emptyList()
    private var showingFavorites = false
    private var ignoreNextCamera = false
    private var hasCentered = false
    private var isLocating = true

    private val selectType: String by lazy {
        intent.getStringExtra(EXTRA_SELECT_TYPE) ?: TYPE_START
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.any { it }
        if (granted) {
            startLocation()
        } else {
            isLocating = false
            updateSelectedAddress()
            Toast.makeText(this, "需要定位权限以获取当前位置", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_selection)

        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)
        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)

        mapView = findViewById(R.id.mapView)
        tvSelectedAddress = findViewById(R.id.tvSelectedAddress)
        etSearchKeyword = findViewById(R.id.etSearchKeyword)
        btnSearch = findViewById(R.id.btnSearch)
        btnShowFavorites = findViewById(R.id.btnShowFavorites)
        btnSaveFavorite = findViewById(R.id.btnSaveFavorite)
        btnConfirm = findViewById(R.id.btnConfirm)
        rvPlaceList = findViewById(R.id.rvPlaceList)

        mapView.onCreate(savedInstanceState)
        aMap = mapView.map
        aMap.uiSettings.isZoomControlsEnabled = false
        aMap.uiSettings.isMyLocationButtonEnabled = true
        val myLocationStyle = MyLocationStyle().apply {
            myLocationType(MyLocationStyle.LOCATION_TYPE_SHOW)
        }
        aMap.myLocationStyle = myLocationStyle
        aMap.isMyLocationEnabled = true

        geocodeSearch = GeocodeSearch(this)
        geocodeSearch.setOnGeocodeSearchListener(this)

        placeAdapter = PlaceAdapter { item ->
            showingFavorites = false
            isLocating = false
            currentLatLng = LatLng(item.lat, item.lng)
            currentAddressName = item.name
            currentAddressDetail = item.address
            updateSelectedAddress()
            ignoreNextCamera = true
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 17f))
        }
        rvPlaceList.layoutManager = LinearLayoutManager(this)
        rvPlaceList.adapter = placeAdapter

        btnSearch.setOnClickListener { performSearch() }
        btnShowFavorites.setOnClickListener { showFavorites() }
        btnSaveFavorite.setOnClickListener { saveCurrentAsFavorite() }
        btnConfirm.setOnClickListener { confirmSelection() }

        aMap.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
            override fun onCameraChange(position: com.amap.api.maps.model.CameraPosition) {}

            override fun onCameraChangeFinish(position: com.amap.api.maps.model.CameraPosition) {
                if (ignoreNextCamera) {
                    ignoreNextCamera = false
                    return
                }
                val target = position.target
                currentLatLng = target
                isLocating = false
                requestReverseGeocode(target)
            }
        })

        ignoreNextCamera = true
        aMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(DEFAULT_CAMPUS_LAT, DEFAULT_CAMPUS_LNG),
                16f
            )
        )

        requestLocationPermission()
        updateSelectedAddress()
    }

    private fun requestLocationPermission() {
        val hasFine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            startLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun startLocation() {
        if (locationClient != null) return
        locationClient = AMapLocationClient(applicationContext)
        val option = AMapLocationClientOption().apply {
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            isOnceLocation = true
            isNeedAddress = true
        }
        locationClient?.setLocationOption(option)
        val cached = locationClient?.lastKnownLocation
        if (cached != null && cached.errorCode == 0) {
            val cachedLatLng = LatLng(cached.latitude, cached.longitude)
            currentCity = cached.city
            currentLatLng = cachedLatLng
            isLocating = false
            if (!hasCentered) {
                aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(cachedLatLng, 16f))
                hasCentered = true
            }
            requestReverseGeocode(cachedLatLng)
        }
        locationClient?.setLocationListener { location ->
            if (location != null && location.errorCode == 0) {
                val latLng = LatLng(location.latitude, location.longitude)
                currentCity = location.city
                currentLatLng = latLng
                isLocating = false
                if (!hasCentered) {
                    aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                    hasCentered = true
                }
                requestReverseGeocode(latLng)
            }
            locationClient?.stopLocation()
        }
        locationClient?.startLocation()
    }

    private fun requestReverseGeocode(latLng: LatLng) {
        val point = LatLonPoint(latLng.latitude, latLng.longitude)
        val query = RegeocodeQuery(point, 200f, GeocodeSearch.AMAP)
        geocodeSearch.getFromLocationAsyn(query)
    }

    override fun onRegeocodeSearched(result: com.amap.api.services.geocoder.RegeocodeResult?, rCode: Int) {
        if (rCode != 1000 || result == null) return
        val address = result.regeocodeAddress ?: return
        currentCity = address.city
        val preferredName = pickPreferredPoiName(address)
        currentAddressName = preferredName.ifEmpty { address.formatAddress }
        currentAddressDetail = address.formatAddress
        isLocating = false
        updateSelectedAddress()
    }

    override fun onGeocodeSearched(result: com.amap.api.services.geocoder.GeocodeResult?, rCode: Int) {
        // no-op
    }

    private fun pickPreferredPoiName(address: RegeocodeAddress): String {
        val preferredKeywords = listOf("教育", "餐饮", "食堂", "宿舍", "住宿", "生活", "科教")
        val poiList = address.pois ?: return ""
        for (poi in poiList) {
            val title = poi.title ?: ""
            val snippet = poi.snippet ?: ""
            if (preferredKeywords.any { title.contains(it) || snippet.contains(it) }) {
                return title
            }
        }
        return poiList.firstOrNull()?.title ?: ""
    }

    private fun updateSelectedAddress() {
        val name = when {
            isLocating -> getString(R.string.selecting_address)
            currentAddressName != null -> currentAddressName
            else -> getString(R.string.map_selected_address_placeholder)
        }
        val detail = currentAddressDetail
        tvSelectedAddress.text = if (detail.isNullOrBlank() || detail == name) {
            name
        } else {
            "$name\n$detail"
        }
    }

    private fun performSearch() {
        val keyword = etSearchKeyword.text.toString().trim()
        if (keyword.isEmpty()) {
            Toast.makeText(this, "请输入关键词", Toast.LENGTH_SHORT).show()
            return
        }
        showingFavorites = false
        isLocating = false
        val query = PoiSearch.Query(keyword, "", currentCity)
        query.pageSize = 20
        query.pageNum = 0
        val poiSearch = PoiSearch(this, query)
        poiSearch.setOnPoiSearchListener(this)
        poiSearch.searchPOIAsyn()
    }

    override fun onPoiSearched(result: PoiResult?, rCode: Int) {
        if (rCode != 1000 || result == null) {
            Toast.makeText(this, "搜索失败", Toast.LENGTH_SHORT).show()
            return
        }
        searchResults = result.pois?.map { poi ->
            PlaceItem(
                name = poi.title ?: "",
                address = poi.snippet ?: "",
                lat = poi.latLonPoint.latitude,
                lng = poi.latLonPoint.longitude
            )
        } ?: emptyList()
        placeAdapter.submitList(searchResults)
    }

    override fun onPoiItemSearched(item: com.amap.api.services.core.PoiItem?, rCode: Int) {
        // no-op
    }

    private fun showFavorites() {
        showingFavorites = true
        isLocating = false
        placeAdapter.submitList(loadFavorites())
    }

    private fun saveCurrentAsFavorite() {
        val latLng = currentLatLng
        val name = currentAddressName
        val address = currentAddressDetail
        if (latLng == null || name.isNullOrBlank()) {
            Toast.makeText(this, "请先选择位置", Toast.LENGTH_SHORT).show()
            return
        }
        val favorites = loadFavorites().toMutableList()
        val exists = favorites.any { it.lat == latLng.latitude && it.lng == latLng.longitude }
        if (!exists) {
            favorites.add(
                PlaceItem(
                    name = name,
                    address = address ?: "",
                    lat = latLng.latitude,
                    lng = latLng.longitude
                )
            )
            saveFavorites(favorites)
            Toast.makeText(this, "已保存常用地址", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "常用地址已存在", Toast.LENGTH_SHORT).show()
        }
        if (showingFavorites) {
            placeAdapter.submitList(loadFavorites())
        }
    }

    private fun confirmSelection() {
        val latLng = currentLatLng
        val name = currentAddressName
        if (latLng == null || name.isNullOrBlank()) {
            Toast.makeText(this, "请先选择位置", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent().apply {
            putExtra(EXTRA_SELECTED_NAME, name)
            putExtra(EXTRA_SELECTED_ADDRESS, currentAddressDetail ?: "")
            putExtra(EXTRA_SELECTED_LAT, latLng.latitude)
            putExtra(EXTRA_SELECTED_LNG, latLng.longitude)
            putExtra(EXTRA_SELECT_TYPE, selectType)
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun loadFavorites(): List<PlaceItem> {
        val prefs = getFavoritesPrefs()
        val raw = prefs.getString(getFavoritesKey(), "[]") ?: "[]"
        val array = JSONArray(raw)
        val result = mutableListOf<PlaceItem>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            result.add(
                PlaceItem(
                    name = obj.optString("name"),
                    address = obj.optString("address"),
                    lat = obj.optDouble("lat"),
                    lng = obj.optDouble("lng")
                )
            )
        }
        return result
    }

    private fun saveFavorites(list: List<PlaceItem>) {
        val array = JSONArray()
        list.forEach { item ->
            val obj = JSONObject()
            obj.put("name", item.name)
            obj.put("address", item.address)
            obj.put("lat", item.lat)
            obj.put("lng", item.lng)
            array.put(obj)
        }
        getFavoritesPrefs().edit()
            .putString(getFavoritesKey(), array.toString())
            .apply()
    }

    private fun getFavoritesPrefs(): SharedPreferences {
        return getSharedPreferences(PREFS_FAVORITES, MODE_PRIVATE)
    }

    private fun getFavoritesKey(): String {
        val userId = UserSession.get(this)?.studentId ?: "guest"
        return "favorites_$userId"
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
        locationClient?.onDestroy()
    }

    companion object {
        const val EXTRA_SELECT_TYPE = "extra_select_type"
        const val EXTRA_SELECTED_NAME = "extra_selected_name"
        const val EXTRA_SELECTED_ADDRESS = "extra_selected_address"
        const val EXTRA_SELECTED_LAT = "extra_selected_lat"
        const val EXTRA_SELECTED_LNG = "extra_selected_lng"
        const val TYPE_START = "type_start"
        const val TYPE_TARGET = "type_target"
        private const val PREFS_FAVORITES = "prefs_favorites"
        private const val DEFAULT_CAMPUS_LAT = 34.123186
        private const val DEFAULT_CAMPUS_LNG = 108.833538
    }
}
