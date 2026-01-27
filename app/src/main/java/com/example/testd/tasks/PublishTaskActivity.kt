package com.example.testd.tasks

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
    private var startLat: Double? = null
    private var startLng: Double? = null
    private var targetLat: Double? = null
    private var targetLng: Double? = null

    private val mapSelectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val name = data.getStringExtra(MapSelectionActivity.EXTRA_SELECTED_NAME).orEmpty()
        val lat = data.getDoubleExtra(MapSelectionActivity.EXTRA_SELECTED_LAT, Double.NaN)
        val lng = data.getDoubleExtra(MapSelectionActivity.EXTRA_SELECTED_LNG, Double.NaN)
        val selectType = data.getStringExtra(MapSelectionActivity.EXTRA_SELECT_TYPE)
            ?: MapSelectionActivity.TYPE_START
        if (lat.isNaN() || lng.isNaN() || name.isBlank()) return@registerForActivityResult
        if (selectType == MapSelectionActivity.TYPE_START) {
            etStartLocation.setText(name)
            startLat = lat
            startLng = lng
        } else {
            etTargetLocation.setText(name)
            targetLat = lat
            targetLng = lng
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_publish_task)

        etTitle = findViewById(R.id.etTaskTitle)
        etDescription = findViewById(R.id.etTaskDescription)
        etStartLocation = findViewById(R.id.etTaskStartLocation)
        etTargetLocation = findViewById(R.id.etTaskTargetLocation)
        etReward = findViewById(R.id.etTaskReward)
        btnSubmit = findViewById(R.id.btnSubmitTask)

        etStartLocation.setOnClickListener {
            launchMapSelection(MapSelectionActivity.TYPE_START)
        }
        etTargetLocation.setOnClickListener {
            launchMapSelection(MapSelectionActivity.TYPE_TARGET)
        }
        btnSubmit.setOnClickListener { submitTask() }
    }

    private fun launchMapSelection(type: String) {
        val intent = Intent(this, MapSelectionActivity::class.java)
        intent.putExtra(MapSelectionActivity.EXTRA_SELECT_TYPE, type)
        mapSelectionLauncher.launch(intent)
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

        if (title.isEmpty() || rewardText.isEmpty() || startLocation.isEmpty() || targetLocation.isEmpty()) {
            Toast.makeText(this, "请填写标题、酬劳和地点", Toast.LENGTH_SHORT).show()
            return
        }

        val reward = rewardText.toDoubleOrNull()
        if (reward == null) {
            Toast.makeText(this, "酬劳必须是数字", Toast.LENGTH_SHORT).show()
            return
        }

        if (startLat == null || startLng == null || targetLat == null || targetLng == null) {
            Toast.makeText(this, "请通过地图选择起点和终点", Toast.LENGTH_SHORT).show()
            return
        }

        val payload = JSONObject()
        payload.put("publisher_id", userInfo.studentId)
        payload.put("title", title)
        payload.put("description", description)
        payload.put("start_location", startLocation)
        payload.put("target_location", targetLocation)
        payload.put("start_lat", startLat)
        payload.put("start_lng", startLng)
        payload.put("target_lat", targetLat)
        payload.put("target_lng", targetLng)
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
