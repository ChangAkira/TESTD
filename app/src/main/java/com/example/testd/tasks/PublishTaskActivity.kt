package com.example.testd.tasks

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.testd.AppConfig
import com.example.testd.R
import com.example.testd.UserSession
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class PublishTaskActivity : AppCompatActivity() {
    private lateinit var etTitle: EditText
    private lateinit var etDescription: EditText
    private lateinit var etStartLocation: EditText
    private lateinit var etTargetLocation: EditText
    private lateinit var etReward: EditText
    private lateinit var btnSubmit: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_publish_task)

        etTitle = findViewById(R.id.etTaskTitle)
        etDescription = findViewById(R.id.etTaskDescription)
        etStartLocation = findViewById(R.id.etTaskStartLocation)
        etTargetLocation = findViewById(R.id.etTaskTargetLocation)
        etReward = findViewById(R.id.etTaskReward)
        btnSubmit = findViewById(R.id.btnSubmitTask)

        btnSubmit.setOnClickListener { submitTask() }
    }

    private fun submitTask() {
        val userInfo = UserSession.get(this)
        if (userInfo == null) {
            Toast.makeText(this, "用户信息缺失", Toast.LENGTH_SHORT).show()
            return
        }
        val title = etTitle.text.toString().trim()
        val description = etDescription.text.toString().trim()
        val startLocation = etStartLocation.text.toString().trim()
        val targetLocation = etTargetLocation.text.toString().trim()
        val rewardText = etReward.text.toString().trim()

        if (title.isEmpty() || rewardText.isEmpty()) {
            Toast.makeText(this, "请填写标题和酬劳", Toast.LENGTH_SHORT).show()
            return
        }

        val reward = rewardText.toDoubleOrNull()
        if (reward == null) {
            Toast.makeText(this, "酬劳必须是数字", Toast.LENGTH_SHORT).show()
            return
        }

        val payload = JSONObject()
        payload.put("publisher_id", userInfo.studentId)
        payload.put("title", title)
        payload.put("description", description)
        payload.put("start_location", startLocation)
        payload.put("target_location", targetLocation)
        payload.put("reward", reward)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = payload.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("${AppConfig.BASE_URL}/tasks")
            .post(requestBody)
            .build()

        AppConfig.httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@PublishTaskActivity, "发布失败", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val code = response.code
                response.close()
                if (code == 201) {
                    runOnUiThread {
                        Toast.makeText(this@PublishTaskActivity, "发布成功", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@PublishTaskActivity, "发布失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}
