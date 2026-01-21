package com.example.testd // 保持你自己的包名

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.testd.R
import com.example.testd.AppConfig
import com.example.testd.UserSession
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var tvStatus: TextView
    private lateinit var tvResult: TextView

    private val loginUrl = "http://ids.xidian.edu.cn/authserver/login"
    private val targetUrlKeyword = "personalInfo"
    private var hasSubmitted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!UserSession.isLoggedOut(this)) {
            val cachedUser = UserSession.get(this)
            if (cachedUser != null) {
                navigateToHome()
                return
            }
        }

        webView = findViewById(R.id.webView)
        tvStatus = findViewById(R.id.tvStatus)
        tvResult = findViewById(R.id.tvResult)

        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(WebAppInterface(), "Android")

        // 【新增】WebChromeClient 用于让网页里的 console.log 能打印到 Android Studio 的 Logcat 中
        // 这对于调试非常重要！
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d("WebViewConsole", consoleMessage?.message() ?: "")
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                // 只要包含 personalInfo 且不在登录页，就开始抓
                if (url != null && url.contains(targetUrlKeyword) && !url.contains("login")) {
                    //tvStatus.text = "检测到登录成功，正在尝试抓取..."
                    startGrabData()
                    CookieManager.getInstance().flush()
                }
            }
        }

        webView.loadUrl(loginUrl)
    }

    private fun startGrabData() {
        // 【关键修改】为了防止 JS 语法错误，我删除了所有的双斜杠注释
        // 并且把代码压缩了一下，确保能运行
        val jsCode = """
            (function() {
                console.log('开始尝试抓取...');
                var checkCount = 0;
                var timer = setInterval(function() {
                    try {
                        var idElem = document.querySelector('.character_info .account'); 
                        var nameElem = document.querySelector('.character_info .name'); 
                        
                        if (!idElem) idElem = document.querySelector('.userNo');
                        if (!nameElem) nameElem = document.querySelector('.userinfo-cn');

                        if (idElem && nameElem) {
                            var id = idElem.innerText;
                            var name = nameElem.innerText;
                            
                            if(id && name) {
                                console.log('抓取成功:' + id + ' ' + name);
                                window.Android.onDataReceived(id, name);
                                clearInterval(timer); 
                            }
                        }
                    } catch(e) {
                        console.log('Error:' + e);
                    }
                    
                    checkCount++;
                    if(checkCount > 100) { 
                        clearInterval(timer);
                    }
                }, 500);
            })();
        """.trimIndent()

        webView.loadUrl("javascript:$jsCode")
        CookieManager.getInstance().flush()
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun onDataReceived(id: String, name: String) {
            val studentId = id.trim()
            val studentName = name.trim()
            if (hasSubmitted) {
                return
            }
            hasSubmitted = true
            runOnUiThread {
                tvStatus.text = "登录认证成功！"
                tvResult.text = "学号：$studentId\n姓名：$studentName"
                //Toast.makeText(this@MainActivity, "抓取成功", Toast.LENGTH_SHORT).show()

                // 打印日志，确认收到
                Log.d("GrabData", "收到数据: $studentId, $studentName")
            }
            postUserInfo(studentId, studentName)
        }
    }

    private fun postUserInfo(studentId: String, name: String) {
        val payload = JSONObject()
        payload.put("student_id", studentId)
        payload.put("name", name)
        payload.put("username", studentId)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = payload.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("${AppConfig.BASE_URL}/users")
            .post(requestBody)
            .build()

        AppConfig.httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("UserUpload", "提交失败", e)
                hasSubmitted = false
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "提交失败，请检查网络或后端服务",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val code = response.code
                response.close()
                if (code == 201 || code == 409) {
                    fetchUsernameAndProceed(studentId, name)
                } else {
                    hasSubmitted = false
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "提交失败，状态码: $code",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    private fun fetchUsernameAndProceed(studentId: String, name: String) {
        val request = Request.Builder()
            .url("${AppConfig.BASE_URL}/users/$studentId")
            .get()
            .build()

        AppConfig.httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("UserFetch", "获取用户名失败", e)
                UserSession.save(this@MainActivity, studentId, name, studentId)
                runOnUiThread {
                    navigateToHome()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                val code = response.code
                response.close()
                if (code == 200) {
                    val json = JSONObject(body)
                    val username = json.optString("username", studentId).ifBlank { studentId }
                    val updatedName = json.optString("name", name).ifBlank { name }
                    UserSession.save(this@MainActivity, studentId, updatedName, username)
                } else if (code == 404) {
                    UserSession.save(this@MainActivity, studentId, name, studentId)
                } else {
                    UserSession.save(this@MainActivity, studentId, name, studentId)
                }
                runOnUiThread {
                    navigateToHome()
                }
            }
        })
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }
}