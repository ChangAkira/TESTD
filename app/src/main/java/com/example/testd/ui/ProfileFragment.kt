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
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.testd.AppConfig
import com.example.testd.MainActivity
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

class ProfileFragment : Fragment() {
    private lateinit var tvStudentId: TextView
    private lateinit var tvName: TextView
    private lateinit var tvUsername: TextView
    private lateinit var etUsername: EditText
    private lateinit var btnUpdate: Button
    private lateinit var btnLogout: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)
        tvStudentId = view.findViewById(R.id.tvStudentId)
        tvName = view.findViewById(R.id.tvName)
        tvUsername = view.findViewById(R.id.tvUsername)
        etUsername = view.findViewById(R.id.etUsername)
        btnUpdate = view.findViewById(R.id.btnUpdateUsername)
        btnLogout = view.findViewById(R.id.btnLogout)

        refreshUserInfo()

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
                    UserSession.save(
                        requireContext(),
                        userInfo.studentId,
                        userInfo.name,
                        updatedUsername
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
