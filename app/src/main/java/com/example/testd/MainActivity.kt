package com.example.testd // 保持你自己的包名

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.testd.R

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var tvStatus: TextView
    private lateinit var tvResult: TextView

    private val loginUrl = "http://ids.xidian.edu.cn/authserver/login"
    private val targetUrlKeyword = "personalInfo"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun onDataReceived(id: String, name: String) {
            runOnUiThread {
                tvStatus.text = "登录认证成功！"
                tvResult.text = "学号：${id.trim()}\n姓名：${name.trim()}"
                //Toast.makeText(this@MainActivity, "抓取成功", Toast.LENGTH_SHORT).show()

                // 打印日志，确认收到
                Log.d("GrabData", "收到数据: $id, $name")
            }
        }
    }
}