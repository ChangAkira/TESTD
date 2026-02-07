package com.example.testd.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.testd.AppConfig
import com.example.testd.R
import com.example.testd.UserSession
import com.example.testd.tasks.TaskAdapter
import com.example.testd.tasks.TaskDetailActivity
import com.example.testd.tasks.TaskItem
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class MyAcceptedFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var taskAdapter: TaskAdapter
    private var currentPage = 1
    private var isLoading = false
    private var hasMore = true
    private val pageSize = 15

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_my_accepted, container, false)
        recyclerView = view.findViewById(R.id.taskRecyclerView)

        taskAdapter = TaskAdapter(mutableListOf())
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = taskAdapter
        taskAdapter.onItemClick = { task ->
            val intent = android.content.Intent(requireContext(), TaskDetailActivity::class.java)
            intent.putExtra(TaskDetailActivity.EXTRA_TASK_ID, task.id)
            intent.putExtra(TaskDetailActivity.EXTRA_SOURCE, TaskDetailActivity.SOURCE_ACCEPTED)
            startActivity(intent)
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
        return view
    }

    override fun onResume() {
        super.onResume()
        refreshFirstPage()
    }

    private fun refreshFirstPage() {
        currentPage = 1
        hasMore = true
        fetchTasks(page = 1, isRefresh = true)
    }

    private fun loadNextPage() {
        fetchTasks(page = currentPage + 1, isRefresh = false)
    }

    private fun fetchTasks(page: Int, isRefresh: Boolean) {
        if (isLoading) return
        isLoading = true
        val userInfo = UserSession.get(requireContext()) ?: return
        val request = Request.Builder()
            .url("${AppConfig.BASE_URL}/tasks?acceptor_id=${userInfo.studentId}&page=$page&page_size=$pageSize")
            .get()
            .build()

        AppConfig.httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    isLoading = false
                    Toast.makeText(requireContext(), "任务加载失败", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                val code = response.code
                response.close()
                if (code == 200) {
                    val json = JSONObject(body)
                    val list = parseTasks(json.optJSONArray("tasks") ?: JSONArray())
                    requireActivity().runOnUiThread {
                        isLoading = false
                        if (isRefresh) {
                            taskAdapter.updateData(list)
                        } else {
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
                    requireActivity().runOnUiThread {
                        isLoading = false
                        Toast.makeText(requireContext(), "任务加载失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
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
