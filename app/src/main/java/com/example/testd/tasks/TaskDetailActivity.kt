package com.example.testd.tasks

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.testd.AppConfig
import com.example.testd.R
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

class TaskDetailActivity : AppCompatActivity() {
    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvDescription: TextView
    private lateinit var tvLocations: TextView
    private lateinit var tvReward: TextView
    private lateinit var tvCreatedAt: TextView
    private lateinit var ivPublisherAvatar: ImageView
    private lateinit var tvPublisherName: TextView
    private lateinit var acceptorSection: View
    private lateinit var ivAcceptorAvatar: ImageView
    private lateinit var tvAcceptorName: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_detail)

        tvTitle = findViewById(R.id.tvDetailTitle)
        tvStatus = findViewById(R.id.tvDetailStatus)
        tvDescription = findViewById(R.id.tvDetailDescription)
        tvLocations = findViewById(R.id.tvDetailLocations)
        tvReward = findViewById(R.id.tvDetailReward)
        tvCreatedAt = findViewById(R.id.tvDetailCreatedAt)
        ivPublisherAvatar = findViewById(R.id.ivPublisherAvatar)
        tvPublisherName = findViewById(R.id.tvPublisherName)
        acceptorSection = findViewById(R.id.layoutAcceptorSection)
        ivAcceptorAvatar = findViewById(R.id.ivAcceptorAvatar)
        tvAcceptorName = findViewById(R.id.tvAcceptorName)

        val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
        if (taskId <= 0) {
            Toast.makeText(this, getString(R.string.task_detail_invalid), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        fetchTaskDetail(taskId)
    }

    private fun fetchTaskDetail(taskId: Int) {
        val request = Request.Builder()
            .url("${AppConfig.BASE_URL}/tasks/$taskId")
            .get()
            .build()

        AppConfig.httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@TaskDetailActivity, getString(R.string.task_detail_load_failed), Toast.LENGTH_SHORT).show()
                    finish()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                val code = response.code
                response.close()
                if (code != 200) {
                    runOnUiThread {
                        Toast.makeText(this@TaskDetailActivity, getString(R.string.task_detail_load_failed), Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    return
                }
                val json = JSONObject(body)
                runOnUiThread {
                    bindDetail(json)
                }
            }
        })
    }

    private fun bindDetail(json: JSONObject) {
        val title = json.optString("title")
        val status = json.optString("status")
        val description = json.optString("description")
        val startLocation = json.optString("start_location")
        val targetLocation = json.optString("target_location")
        val reward = json.optDouble("reward", 0.0)
        val createdAt = json.optString("created_at")
        val publisherName = json.optString("publisher_username")
        val publisherAvatarPath = json.optString("publisher_avatar_url")
        val acceptorName = json.optString("acceptor_username")
        val acceptorAvatarPath = json.optString("acceptor_avatar_url")

        tvTitle.text = title
        tvStatus.text = getString(R.string.task_detail_status, status)
        tvDescription.text = description
        tvLocations.text = getString(R.string.task_detail_locations, startLocation, targetLocation)
        tvReward.text = String.format(Locale.getDefault(), getString(R.string.task_detail_reward_format), reward)
        tvCreatedAt.text = getString(R.string.task_detail_created_at, createdAt)
        tvPublisherName.text = publisherName.ifBlank { getString(R.string.task_detail_unknown_user) }

        val publisherAvatarUrl = if (publisherAvatarPath.isNotBlank()) {
            "${AppConfig.BASE_URL}$publisherAvatarPath"
        } else ""
        Glide.with(this)
            .load(publisherAvatarUrl)
            .placeholder(R.mipmap.ic_launcher)
            .error(R.mipmap.ic_launcher)
            .into(ivPublisherAvatar)

        if (acceptorName.isNotBlank() || acceptorAvatarPath.isNotBlank()) {
            acceptorSection.visibility = View.VISIBLE
            tvAcceptorName.text = acceptorName.ifBlank { getString(R.string.task_detail_unknown_user) }
            val acceptorAvatarUrl = if (acceptorAvatarPath.isNotBlank()) {
                "${AppConfig.BASE_URL}$acceptorAvatarPath"
            } else ""
            Glide.with(this)
                .load(acceptorAvatarUrl)
                .placeholder(R.mipmap.ic_launcher)
                .error(R.mipmap.ic_launcher)
                .into(ivAcceptorAvatar)
        } else {
            acceptorSection.visibility = View.GONE
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
    }
}
