package com.example.testd.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.testd.AppConfig
import com.example.testd.MainActivity
import com.example.testd.R
import com.example.testd.UserSession
import com.bumptech.glide.Glide
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class ProfileFragment : Fragment() {
    private val defaultAvatars = listOf(
        "default (1).png",
        "default (2).png",
        "default (3).png",
        "default (4).png",
        "default (5).png"
    )

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val mimeType = requireContext().contentResolver.getType(uri)
        if (mimeType != "image/jpeg" && mimeType != "image/jpg" && mimeType != "image/png" && mimeType != "image/gif") {
            Toast.makeText(requireContext(), getString(R.string.only_image_allowed), Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        val userInfo = UserSession.get(requireContext()) ?: return@registerForActivityResult
        val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return@registerForActivityResult
        val bytes = inputStream.readBytes()
        inputStream.close()
        uploadAvatar(userInfo.studentId, bytes, mimeType)
    }

    private lateinit var ivAvatar: ImageView
    private lateinit var tvStudentId: TextView
    private lateinit var tvName: TextView
    private lateinit var tvUsername: TextView
    private lateinit var tvBalance: TextView
    private lateinit var etUsername: EditText
    private lateinit var btnUpdate: Button
    private lateinit var btnLogout: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)
        ivAvatar = view.findViewById(R.id.ivAvatar)
        tvStudentId = view.findViewById(R.id.tvStudentId)
        tvName = view.findViewById(R.id.tvName)
        tvUsername = view.findViewById(R.id.tvUsername)
        tvBalance = view.findViewById(R.id.tvBalance)
        etUsername = view.findViewById(R.id.etUsername)
        btnUpdate = view.findViewById(R.id.btnUpdateUsername)
        btnLogout = view.findViewById(R.id.btnLogout)

        refreshUserInfo()

        ivAvatar.setOnClickListener { showAvatarOptions() }
        btnUpdate.setOnClickListener { updateUsername() }
        btnLogout.setOnClickListener { logout() }
        return view
    }

    private fun refreshUserInfo() {
        val userInfo = UserSession.get(requireContext())
        if (userInfo == null) {
            tvStudentId.text = "--"
            tvName.text = "--"
            tvUsername.text = "--"
            return
        }
        tvStudentId.text = userInfo.studentId
        tvName.text = userInfo.name
        tvUsername.text = userInfo.username
        tvBalance.text = String.format("%.2f", userInfo.balance)
        loadAvatar(userInfo.avatarUrl)
    }

    override fun onResume() {
        super.onResume()
        fetchLatestBalance()
    }

    private fun fetchLatestBalance() {
        val userInfo = UserSession.get(requireContext()) ?: return
        val request = Request.Builder()
            .url("${AppConfig.BASE_URL}/users/${userInfo.studentId}")
            .get()
            .build()

        AppConfig.httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    refreshUserInfo()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                val code = response.code
                response.close()
                if (code == 200) {
                    val json = JSONObject(body)
                    val username = json.optString("username", userInfo.username)
                    val name = json.optString("name", userInfo.name)
                    val avatarFilename = json.optString("avatar_filename", userInfo.avatarFilename)
                    val avatarPath = json.optString("avatar_url", "")
                    val avatarUrl = if (avatarPath.isNotBlank()) {
                        "${AppConfig.BASE_URL}$avatarPath"
                    } else {
                        userInfo.avatarUrl
                    }
                    val balance = json.optDouble("balance", userInfo.balance)
                    UserSession.save(
                        requireContext(),
                        userInfo.studentId,
                        name,
                        username,
                        avatarFilename,
                        avatarUrl,
                        balance
                    )
                }
                requireActivity().runOnUiThread {
                    refreshUserInfo()
                }
            }
        })
    }

    private fun loadAvatar(avatarUrl: String) {
        if (avatarUrl.isBlank()) {
            Glide.with(this)
                .load(android.R.drawable.sym_def_app_icon)
                .into(ivAvatar)
            return
        }
        // 添加强制刷新：在URL后添加时间戳参数，避免Glide缓存旧图片
        // 这样可以确保每次加载时都获取最新图片
        val urlWithTimestamp = if (avatarUrl.contains("?")) {
            "$avatarUrl&t=${System.currentTimeMillis()}"
        } else {
            "$avatarUrl?t=${System.currentTimeMillis()}"
        }
        Glide.with(this)
            .load(urlWithTimestamp)
            .placeholder(android.R.drawable.sym_def_app_icon)
            .into(ivAvatar)
    }

    private fun showAvatarOptions() {
        val options = arrayOf(
            getString(R.string.choose_default_avatar),
            getString(R.string.choose_from_gallery)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.change_avatar))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showDefaultAvatarPicker()
                    1 -> pickImageLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun showDefaultAvatarPicker() {
        val labels = defaultAvatars.mapIndexed { index, _ -> "默认${index + 1}" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.choose_default_avatar))
            .setItems(labels) { _, which ->
                val userInfo = UserSession.get(requireContext()) ?: return@setItems
                val filename = defaultAvatars[which]
                setDefaultAvatar(userInfo.studentId, filename)
            }
            .show()
    }

    private fun updateUsername() {
        val userInfo = UserSession.get(requireContext())
        if (userInfo == null) {
            Toast.makeText(requireContext(), "用户信息缺失", Toast.LENGTH_SHORT).show()
            return
        }
        val newUsername = etUsername.text.toString().trim()
        if (newUsername.isEmpty()) {
            Toast.makeText(requireContext(), "请输入新的用户名", Toast.LENGTH_SHORT).show()
            return
        }

        val payload = JSONObject()
        payload.put("username", newUsername)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = payload.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("${AppConfig.BASE_URL}/users/${userInfo.studentId}")
            .put(requestBody)
            .build()

        AppConfig.httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "更新失败，请检查网络", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                val code = response.code
                response.close()
                if (code == 200) {
                    val json = JSONObject(body)
                    val updatedUsername = json.optString("username", newUsername)
                    val avatarFilename = json.optString("avatar_filename", userInfo.avatarFilename)
                    val avatarPath = json.optString("avatar_url", "")
                    val avatarUrl = if (avatarPath.isNotBlank()) {
                        "${AppConfig.BASE_URL}$avatarPath"
                    } else {
                        userInfo.avatarUrl
                    }
                    UserSession.save(
                        requireContext(),
                        userInfo.studentId,
                        userInfo.name,
                        updatedUsername,
                        avatarFilename,
                        avatarUrl,
                        userInfo.balance
                    )
                    requireActivity().runOnUiThread {
                        etUsername.setText("")
                        refreshUserInfo()
                        Toast.makeText(requireContext(), "更新成功", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    requireActivity().runOnUiThread {
                        Toast.makeText(
                            requireContext(),
                            "更新失败，状态码: $code",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    private fun setDefaultAvatar(studentId: String, avatarFilename: String) {
        val payload = JSONObject()
        payload.put("avatar_filename", avatarFilename)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = payload.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("${AppConfig.BASE_URL}/users/$studentId/avatar/default")
            .put(requestBody)
            .build()

        AppConfig.httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "更新头像失败", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                val code = response.code
                response.close()
                if (code == 200) {
                    val json = JSONObject(body)
                    val avatarFilenameResp = json.optString("avatar_filename", avatarFilename)
                    val avatarPath = json.optString("avatar_url", "")
                    val avatarUrl = if (avatarPath.isNotBlank()) {
                        "${AppConfig.BASE_URL}$avatarPath"
                    } else {
                        ""
                    }
                    val userInfo = UserSession.get(requireContext())
                    if (userInfo != null) {
                        UserSession.save(
                            requireContext(),
                            userInfo.studentId,
                            userInfo.name,
                            userInfo.username,
                            avatarFilenameResp,
                            avatarUrl,
                            userInfo.balance
                        )
                    }
                    requireActivity().runOnUiThread {
                        // 清除Glide缓存以确保显示最新头像
                        Glide.get(requireContext()).clearMemory()
                        // 在后台线程清除磁盘缓存
                        Thread {
                            Glide.get(requireContext()).clearDiskCache()
                        }.start()
                        fetchLatestAvatar()
                    }
                } else {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "更新头像失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun uploadAvatar(studentId: String, data: ByteArray, mimeType: String) {
        val mediaType = mimeType.toMediaType()
        val fileBody = data.toRequestBody(mediaType)
        val extension = when (mimeType) {
            "image/png" -> "png"
            "image/gif" -> "gif"
            else -> "jpg"
        }
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("avatar", "$studentId.$extension", fileBody)
            .build()

        val request = Request.Builder()
            .url("${AppConfig.BASE_URL}/users/$studentId/avatar")
            .post(multipart)
            .build()

        AppConfig.httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "上传失败", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                val code = response.code
                response.close()
                if (code == 200) {
                    val json = JSONObject(body)
                    val avatarFilename = json.optString("avatar_filename", "")
                    val avatarPath = json.optString("avatar_url", "")
                    val avatarUrl = if (avatarPath.isNotBlank()) {
                        "${AppConfig.BASE_URL}$avatarPath"
                    } else {
                        ""
                    }
                    val userInfo = UserSession.get(requireContext())
                    if (userInfo != null) {
                        UserSession.save(
                            requireContext(),
                            userInfo.studentId,
                            userInfo.name,
                            userInfo.username,
                            avatarFilename,
                            avatarUrl,
                            userInfo.balance
                        )
                    }
                    requireActivity().runOnUiThread {
                        // 清除Glide缓存以确保显示最新头像
                        Glide.get(requireContext()).clearMemory()
                        // 在后台线程清除磁盘缓存
                        Thread {
                            Glide.get(requireContext()).clearDiskCache()
                        }.start()
                        fetchLatestAvatar()
                        Toast.makeText(requireContext(), "上传成功", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "上传失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun fetchLatestAvatar() {
        val userInfo = UserSession.get(requireContext()) ?: return
        val request = Request.Builder()
            .url("${AppConfig.BASE_URL}/users/${userInfo.studentId}/avatar")
            .get()
            .build()

        AppConfig.httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    refreshUserInfo()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                val code = response.code
                response.close()
                if (code == 200) {
                    val json = JSONObject(body)
                    val avatarFilename = json.optString("avatar_filename", userInfo.avatarFilename)
                    val avatarPath = json.optString("avatar_url", "")
                    val avatarUrl = if (avatarPath.isNotBlank()) {
                        "${AppConfig.BASE_URL}$avatarPath"
                    } else {
                        userInfo.avatarUrl
                    }
                    UserSession.save(
                        requireContext(),
                        userInfo.studentId,
                        userInfo.name,
                        userInfo.username,
                        avatarFilename,
                        avatarUrl,
                        userInfo.balance
                    )
                }
                requireActivity().runOnUiThread {
                    refreshUserInfo()
                }
            }
        })
    }

    private fun logout() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        UserSession.markLoggedOut(requireContext())
        UserSession.clear(requireContext())

        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }
}
