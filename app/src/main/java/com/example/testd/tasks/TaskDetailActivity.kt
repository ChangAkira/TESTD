package com.example.testd.tasks

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.example.testd.AppConfig
import com.example.testd.R
import com.example.testd.UserSession
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
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
    private lateinit var btnPrimaryAction: MaterialButton
    private lateinit var btnSecondaryAction: MaterialButton
    private lateinit var ivDetailFlag: ImageView
    private lateinit var ivDetailFinish: ImageView
    private lateinit var tvEvidenceLabel: TextView
    private lateinit var layoutEvidenceContainer: View
    private lateinit var ivEvidenceImage: ImageView

    private var currentStatus: String = ""
    private var publisherId: String = ""
    private var acceptorId: String = ""
    private var taskId: Int = -1
    private var source: String = SOURCE_HALL
    private var evidenceUrl: String = ""
    private var pendingEvidenceUri: Uri? = null
    private var pendingEvidenceFile: File? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraForEvidence()
        } else {
            Toast.makeText(this, getString(R.string.task_evidence_camera_denied), Toast.LENGTH_SHORT).show()
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingEvidenceFile?.let { file ->
                uploadEvidence(file)
            }
        } else {
            Toast.makeText(this, getString(R.string.task_evidence_capture_failed), Toast.LENGTH_SHORT).show()
        }
    }

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
        btnPrimaryAction = findViewById(R.id.btnPrimaryAction)
        btnSecondaryAction = findViewById(R.id.btnSecondaryAction)
        ivDetailFlag = findViewById(R.id.ivDetailFlag)
        ivDetailFinish = findViewById(R.id.ivDetailFinish)
        tvEvidenceLabel = findViewById(R.id.tvEvidenceLabel)
        layoutEvidenceContainer = findViewById(R.id.layoutEvidenceContainer)
        ivEvidenceImage = findViewById(R.id.ivEvidenceImage)

        taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
        source = intent.getStringExtra(EXTRA_SOURCE) ?: SOURCE_HALL
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
        publisherId = json.optString("publisher_id")
        acceptorId = json.optString("acceptor_id")
        val publisherName = json.optString("publisher_username")
        val publisherAvatarPath = json.optString("publisher_avatar_url")
        val acceptorName = json.optString("acceptor_username")
        val acceptorAvatarPath = json.optString("acceptor_avatar_url")
        evidenceUrl = if (json.isNull("evidence_url")) "" else json.optString("evidence_url")

        currentStatus = status
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

        tvEvidenceLabel.visibility = View.VISIBLE
        tvEvidenceLabel.text = getString(R.string.task_evidence_title)
        if (evidenceUrl.isNotBlank() && evidenceUrl != "null") {
            layoutEvidenceContainer.visibility = View.VISIBLE
            val fullUrl = if (evidenceUrl.startsWith("http")) {
                evidenceUrl
            } else {
                "${AppConfig.BASE_URL}$evidenceUrl"
            }
            val refreshUrl = if (fullUrl.contains("?")) {
                "$fullUrl&t=${System.currentTimeMillis()}"
            } else {
                "$fullUrl?t=${System.currentTimeMillis()}"
            }
            Glide.with(this)
                .load(refreshUrl)
                .into(ivEvidenceImage)
            ivEvidenceImage.setOnClickListener {
                val intent = android.content.Intent(this, ImagePreviewActivity::class.java)
                intent.putExtra(ImagePreviewActivity.EXTRA_IMAGE_URL, refreshUrl)
                startActivity(intent)
            }
        } else {
            layoutEvidenceContainer.visibility = View.GONE
        }

        when (status) {
            "已送达" -> {
                ivDetailFlag.visibility = View.VISIBLE
                ivDetailFinish.visibility = View.GONE
            }
            "已完成" -> {
                ivDetailFlag.visibility = View.VISIBLE
                ivDetailFinish.visibility = View.VISIBLE
            }
            else -> {
                ivDetailFlag.visibility = View.GONE
                ivDetailFinish.visibility = View.GONE
            }
        }

        updateActionButtons()
    }

    private fun updateActionButtons() {
        btnSecondaryAction.visibility = View.GONE
        btnPrimaryAction.visibility = View.VISIBLE
        btnPrimaryAction.isEnabled = true

        when (source) {
            SOURCE_HALL -> {
                btnPrimaryAction.text = getString(R.string.action_accept)
                btnPrimaryAction.isEnabled = currentStatus == "等待接单"
                btnPrimaryAction.setOnClickListener {
                    val user = UserSession.get(this) ?: return@setOnClickListener
                    updateTaskStatus(
                        newStatus = "进行中",
                        acceptorId = user.studentId,
                        rating = null
                    )
                }
            }
            SOURCE_PUBLISHED -> {
                btnPrimaryAction.text = getString(R.string.action_mark_completed)
                btnPrimaryAction.isEnabled = currentStatus == "已送达"
                btnPrimaryAction.setOnClickListener {
                    if (currentStatus != "已送达") return@setOnClickListener
                    showRatingSheet()
                }
                btnSecondaryAction.visibility = View.VISIBLE
                btnSecondaryAction.text = getString(R.string.action_cancel)
                btnSecondaryAction.isEnabled = currentStatus == "等待接单"
                btnSecondaryAction.setOnClickListener {
                    updateTaskStatus(newStatus = "已取消", acceptorId = null, rating = null)
                }
            }
            SOURCE_ACCEPTED -> {
                btnPrimaryAction.text = if (currentStatus == "已送达") {
                    getString(R.string.action_reupload_evidence)
                } else {
                    getString(R.string.action_mark_delivered)
                }
                btnPrimaryAction.isEnabled = currentStatus == "进行中" || currentStatus == "已送达"
                btnPrimaryAction.setOnClickListener {
                    if (currentStatus == "已完成") return@setOnClickListener
                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                            this,
                            android.Manifest.permission.CAMERA
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        launchCameraForEvidence()
                    } else {
                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    }
                }
            }
        }
    }

    private fun showRatingSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_rating, null)
        val ratingBar = view.findViewById<RatingBar>(R.id.ratingBar)
        val btnConfirm = view.findViewById<Button>(R.id.btnRatingConfirm)
        btnConfirm.setOnClickListener {
            val ratingValue = ratingBar.rating.toInt()
            if (ratingValue <= 0) {
                Toast.makeText(this, getString(R.string.rating_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            updateTaskStatus(newStatus = "已完成", acceptorId = null, rating = ratingValue)
        }
        dialog.setContentView(view as View)
        dialog.show()
    }

    private fun updateTaskStatus(newStatus: String, acceptorId: String?, rating: Int?) {
        val user = UserSession.get(this) ?: return
        val payload = JSONObject()
        payload.put("status", newStatus)
        payload.put("operator_id", user.studentId)
        if (!acceptorId.isNullOrBlank()) {
            payload.put("acceptor_id", acceptorId)
        }
        if (rating != null) {
            payload.put("rating", rating)
        }
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = payload.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("${AppConfig.BASE_URL}/tasks/$taskId/status")
            .put(requestBody)
            .build()

        AppConfig.httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@TaskDetailActivity, getString(R.string.task_action_failed), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                val code = response.code
                response.close()
                if (code == 200) {
                    fetchTaskDetail(taskId)
                } else {
                    runOnUiThread {
                        val error = try {
                            JSONObject(body).optString("error")
                        } catch (_: Exception) {
                            ""
                        }
                        if (error == "insufficient balance") {
                            Toast.makeText(this@TaskDetailActivity, getString(R.string.task_action_insufficient_balance), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@TaskDetailActivity, getString(R.string.task_action_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }

    private fun launchCameraForEvidence() {
        val imageFile = File(cacheDir, "evidence_${taskId}.jpg")
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            imageFile
        )
        pendingEvidenceFile = imageFile
        pendingEvidenceUri = uri
        takePictureLauncher.launch(uri)
    }

    private fun uploadEvidence(file: File) {
        val user = UserSession.get(this) ?: return
        val compressed = compressImage(file) ?: run {
            Toast.makeText(this, getString(R.string.task_evidence_upload_failed), Toast.LENGTH_SHORT).show()
            return
        }
        val fileBody = compressed.toRequestBody("image/jpeg".toMediaType())
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("operator_id", user.studentId)
            .addFormDataPart("evidence", "${taskId}.jpg", fileBody)
            .build()

        val request = Request.Builder()
            .url("${AppConfig.BASE_URL}/tasks/$taskId/evidence")
            .post(multipart)
            .build()

        AppConfig.httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@TaskDetailActivity, getString(R.string.task_evidence_upload_failed), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val code = response.code
                response.close()
                if (code == 200) {
                    if (currentStatus == "进行中") {
                        updateTaskStatus(newStatus = "已送达", acceptorId = null, rating = null)
                    } else {
                        fetchTaskDetail(taskId)
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@TaskDetailActivity, getString(R.string.task_evidence_upload_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun compressImage(file: File): ByteArray? {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val maxSize = 1080
        val width = bitmap.width
        val height = bitmap.height
        val scale = if (width > height) {
            maxSize.toFloat() / width.toFloat()
        } else {
            maxSize.toFloat() / height.toFloat()
        }.coerceAtMost(1.0f)
        val resized = if (scale < 1.0f) {
            android.graphics.Bitmap.createScaledBitmap(
                bitmap,
                (width * scale).toInt(),
                (height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
        if (resized != bitmap) {
            resized.recycle()
        }
        bitmap.recycle()
        return out.toByteArray()
    }

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_SOURCE = "extra_source"
        const val SOURCE_HALL = "hall"
        const val SOURCE_PUBLISHED = "published"
        const val SOURCE_ACCEPTED = "accepted"
    }
}
