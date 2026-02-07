package com.example.testd.ui

import android.Manifest
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import android.content.pm.PackageManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.example.testd.AppConfig
import com.example.testd.R
import com.example.testd.tasks.TaskAdapter
import com.example.testd.tasks.TaskDetailActivity
import com.example.testd.tasks.TaskItem
import com.google.android.material.floatingactionbutton.FloatingActionButton
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.math.min

class Column1Fragment : Fragment() {
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var fabRecommend: FloatingActionButton

    private val tasks = mutableListOf<TaskItem>()
    private var currentPage = 1
    private var isLoading = false
    private var hasMore = true
    private val pageSize = 15

    private var locationClient: AMapLocationClient? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.any { it }
        if (granted) {
            requestLocationAndRecommend()
        } else {
            runOnUiThreadIfAlive {
                showToast(getString(R.string.task_recommend_no_permission))
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_column1, container, false)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        recyclerView = view.findViewById(R.id.taskRecyclerView)
        fabRecommend = view.findViewById(R.id.fabRecommend)

        taskAdapter = TaskAdapter(mutableListOf())
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = taskAdapter
        taskAdapter.onItemClick = { task ->
            val intent = android.content.Intent(requireContext(), TaskDetailActivity::class.java)
            intent.putExtra(TaskDetailActivity.EXTRA_TASK_ID, task.id)
            startActivity(intent)
        }

        swipeRefresh.setOnRefreshListener {
            refreshFirstPage()
        }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val total = layoutManager.itemCount
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (!isLoading && hasMore && total > 0 && lastVisible >= total - 3) {
                    loadNextPage()
                }
            }
        })

        fabRecommend.setOnClickListener {
            if (hasLocationPermission()) {
                requestLocationAndRecommend()
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }

        swipeRefresh.isRefreshing = true
        refreshFirstPage()
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        locationClient?.stopLocation()
        locationClient?.onDestroy()
        locationClient = null
    }

    private fun refreshFirstPage() {
        currentPage = 1
        hasMore = true
        fetchOpenTasks(page = 1, isRefresh = true)
    }

    private fun loadNextPage() {
        fetchOpenTasks(page = currentPage + 1, isRefresh = false)
    }

    private fun fetchOpenTasks(page: Int, isRefresh: Boolean) {
        if (isLoading) return
        isLoading = true
        val request = Request.Builder()
            .url("${AppConfig.BASE_URL}/tasks/open?page=$page&page_size=$pageSize")
            .get()
            .build()

        AppConfig.httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThreadIfAlive {
                    isLoading = false
                    swipeRefresh.isRefreshing = false
                    showToast("任务加载失败")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                val code = response.code
                response.close()
                if (code == 200) {
                    val json = JSONObject(body)
                    val list = parseTasks(json.optJSONArray("tasks") ?: JSONArray())
                    runOnUiThreadIfAlive {
                        isLoading = false
                        swipeRefresh.isRefreshing = false
                        if (isRefresh) {
                            tasks.clear()
                            tasks.addAll(list)
                            taskAdapter.updateData(tasks)
                        } else {
                            tasks.addAll(list)
                            taskAdapter.appendData(list)
                        }
                        hasMore = list.size >= pageSize
                        if (list.isNotEmpty()) {
                            currentPage = page
                        } else if (isRefresh) {
                            currentPage = 1
                        }
                    }
                } else {
                    runOnUiThreadIfAlive {
                        isLoading = false
                        swipeRefresh.isRefreshing = false
                        showToast("任务加载失败")
                    }
                }
            }
        })
    }

    private fun hasLocationPermission(): Boolean {
        val context = context ?: return false
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return hasFine || hasCoarse
    }

    private fun requestLocationAndRecommend() {
        val context = context ?: return
        AMapLocationClient.updatePrivacyShow(context, true, true)
        AMapLocationClient.updatePrivacyAgree(context, true)
        if (locationClient == null) {
            locationClient = AMapLocationClient(context.applicationContext)
        }
        val option = AMapLocationClientOption().apply {
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            isOnceLocation = true
            isNeedAddress = false
        }
        locationClient?.setLocationOption(option)

        val cached = locationClient?.lastKnownLocation
        if (cached != null && cached.errorCode == 0) {
            recommendByLocation(cached.latitude, cached.longitude)
            return
        }

        locationClient?.setLocationListener { location ->
            if (location != null && location.errorCode == 0) {
                recommendByLocation(location.latitude, location.longitude)
            } else {
                runOnUiThreadIfAlive {
                    showToast(getString(R.string.task_recommend_location_failed))
                }
            }
            locationClient?.stopLocation()
        }
        locationClient?.startLocation()
    }

    private fun recommendByLocation(userLat: Double, userLng: Double) {
        if (tasks.isEmpty()) {
            runOnUiThreadIfAlive {
                showToast(getString(R.string.task_recommend_done))
            }
            return
        }
        val sorted = tasks.sortedBy { task ->
            distanceToTask(task, userLat, userLng)
        }
        tasks.clear()
        tasks.addAll(sorted)
        runOnUiThreadIfAlive {
            taskAdapter.updateData(tasks)
            showToast(getString(R.string.task_recommend_done))
        }
    }

    private fun distanceToTask(task: TaskItem, userLat: Double, userLng: Double): Double {
        val startDist = if (task.startLat != null && task.startLng != null) {
            distanceMeters(userLat, userLng, task.startLat, task.startLng)
        } else {
            Double.MAX_VALUE
        }
        val targetDist = if (task.targetLat != null && task.targetLng != null) {
            distanceMeters(userLat, userLng, task.targetLat, task.targetLng)
        } else {
            Double.MAX_VALUE
        }
        return min(startDist, targetDist)
    }

    private fun distanceMeters(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results[0].toDouble()
    }

    private fun runOnUiThreadIfAlive(action: () -> Unit) {
        val hostActivity = activity ?: return
        hostActivity.runOnUiThread {
            if (!isAdded) return@runOnUiThread
            action()
        }
    }

    private fun showToast(message: String) {
        val ctx = context ?: return
        Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
    }

    private fun parseTasks(array: JSONArray): List<TaskItem> {
        val items = mutableListOf<TaskItem>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val startLat = if (obj.has("start_lat") && !obj.isNull("start_lat")) {
                obj.optDouble("start_lat")
            } else null
            val startLng = if (obj.has("start_lng") && !obj.isNull("start_lng")) {
                obj.optDouble("start_lng")
            } else null
            val targetLat = if (obj.has("target_lat") && !obj.isNull("target_lat")) {
                obj.optDouble("target_lat")
            } else null
            val targetLng = if (obj.has("target_lng") && !obj.isNull("target_lng")) {
                obj.optDouble("target_lng")
            } else null
            items.add(
                TaskItem(
                    id = obj.optInt("id"),
                    title = obj.optString("title"),
                    description = obj.optString("description"),
                    startLocation = obj.optString("start_location"),
                    targetLocation = obj.optString("target_location"),
                    reward = obj.optDouble("reward", 0.0),
                    status = obj.optString("status"),
                    startLat = startLat,
                    startLng = startLng,
                    targetLat = targetLat,
                    targetLng = targetLng
                )
            )
        }
        return items
    }
}
