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
        return view
    }

    override fun onResume() {
        super.onResume()
        fetchTasks()
    }

    private fun fetchTasks() {
        val userInfo = UserSession.get(requireContext()) ?: return
        val request = Request.Builder()
            .url("${AppConfig.BASE_URL}/tasks?acceptor_id=${userInfo.studentId}")
            .get()
            .build()

        AppConfig.httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
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
                        taskAdapter.updateData(list)
                    }
                } else {
                    requireActivity().runOnUiThread {
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
            items.add(
                TaskItem(
                    id = obj.optInt("id"),
                    title = obj.optString("title"),
                    description = obj.optString("description"),
                    startLocation = obj.optString("start_location"),
                    targetLocation = obj.optString("target_location"),
                    reward = obj.optDouble("reward", 0.0),
                    status = obj.optString("status")
                )
            )
        }
        return items
    }
}
