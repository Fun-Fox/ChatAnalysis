package com.ven.simple.autoci

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import android.widget.ScrollView
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.File
import java.io.FileWriter
import java.util.concurrent.TimeUnit

class AiAnalysisResultActivity : ComponentActivity() {
    private lateinit var tvGroupName: TextView
    private lateinit var tvReasoningContent: TextView
    private lateinit var tvFinalContent: TextView
    private lateinit var btnRegenerate: Button
    private lateinit var btnExport: Button
    private lateinit var svReasoningContent: ScrollView
    private lateinit var svFinalContent: ScrollView
    private lateinit var loadingLayout: LinearLayout
    
    private lateinit var tvCollectTime: String
    private lateinit var tvMessages: String

    private var accumulatedReasoning = ""
    private var accumulatedFinal = ""
    
    private var chatRecordId: Long = -1
    private var groupName: String = ""

    private val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true) // 启用连接失败重试
            .build()

    private var eventSource: EventSource? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_ai_analysis_result)

        initViews()
        setupData()
        
        // 检查数据库中是否已有分析结果
        checkAndLoadAnalysisResult()
    }

    private fun initViews() {
        tvGroupName = findViewById(R.id.tv_group_name)
        tvReasoningContent = findViewById(R.id.tv_reasoning_content)
        tvFinalContent = findViewById(R.id.tv_final_content)
        svReasoningContent = findViewById(R.id.sv_reasoning_content)
        svFinalContent = findViewById(R.id.sv_final_content)
        loadingLayout = findViewById(R.id.loading_layout)
        btnRegenerate = findViewById(R.id.btn_regenerate)
        btnExport = findViewById(R.id.btn_export)
        
        btnRegenerate.setOnClickListener {
            regenerateAnalysis()
        }
        
        btnExport.setOnClickListener {
            exportAnalysisResult()
        }
    }

    private fun setupData() {
        groupName = intent.getStringExtra("groupName") ?: ""
        val chatMessages = intent.getStringExtra("chatMessages") ?: ""
        val collectTimeStr = intent.getStringExtra("collectTime") ?: ""
        chatRecordId = intent.getLongExtra("chatRecordId", -1)

        tvGroupName.text = "AI群聊舆情解析报告"
        tvCollectTime = collectTimeStr
        tvMessages = chatMessages

        // 设置标题为群聊名称
        title = groupName
    }

    private fun checkAndLoadAnalysisResult() {
        if (chatRecordId != -1L) {
            val record = ChatDatabaseManager.getChatRecordById(chatRecordId)
            if (record != null && !record.reasoningContent.isNullOrEmpty() && !record.finalContent.isNullOrEmpty()) {
                // 数据库中有结果，直接显示
                tvReasoningContent.text = record.reasoningContent
                tvFinalContent.text = record.finalContent
                accumulatedReasoning = record.reasoningContent ?: ""
                accumulatedFinal = record.finalContent ?: ""
                Log.d("AI_API", "从数据库加载分析结果")
            } else {
                // 数据库中没有结果，发起网络请求
                setupStreamAnalysis()
            }
        } else {
            // 没有chatRecordId，直接发起网络请求
            setupStreamAnalysis()
        }
    }

    private fun setupStreamAnalysis() {
        val chatMessages = tvMessages
        val collectTime = tvCollectTime

        // 构建请求体
        val prompt = """
            群聊名称：$groupName    
            采集时间：$collectTime
            群聊内容：$chatMessages 
        """
            .trimIndent()

        val jsonBody = JSONObject()
            .put("model", "deepgeminipro")
            .put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "你是一个专业的群聊分析师，请分析群聊内容并输出结构化报告(如：热点话题并统计提及次数、话题热度榜、有趣的对话及金句、话题词云)，并深度挖掘用户需求及商业价值。 ")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            .put("stream", true)
            .put("temperature", 1)
            .put("top_p", 1)
            .toString()

        val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
        val apiKey = getApiToken()
        val request = Request.Builder()
            .url("https://openai.weavex.tech/v1/chat/completions")
            .header("X-Api-Key", apiKey)
            .header("X-Stainless-Retry-Count", "0")
            .header("X-Stainless-Timeout", "600")
            .header("X-Title", "Cherry Studio")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) CherryStudio/1.5.11 Chrome/138.0.7204.243 Electron/37.4.0 Safari/537.36")
            .header("Authorization", "Bearer $apiKey")
            .header(
                "Content-Type",
                "application/json"
            )
            .post(body)
            .build()

        // 打印请求信息
        Log.d("AI_API", "Request URL: ${request.url}")
        Log.d("AI_API", "Request Method: ${request.method}")
        Log.d("AI_API", "Request Headers: ${request.headers}")
        Log.d("AI_API", "Request Body: $jsonBody")

        // 创建事件源工厂
        val factory = EventSources.createFactory(client)
        
        // 清空之前的内容
        accumulatedReasoning = ""
        accumulatedFinal = ""
        runOnUiThread {
            tvReasoningContent.text = ""
            tvFinalContent.text = ""
            // 显示加载指示器
            loadingLayout.visibility = LinearLayout.VISIBLE
        }
        
        // 创建事件监听器
        val sseListener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                Log.d("AI_API", "SSE连接已打开: ${response.code}")
                runOnUiThread {
                    Toast.makeText(this@AiAnalysisResultActivity, "正在分析中...", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                Log.d("AI_API", "Event received - id: $id, type: $type, data: $data")
                
                // 处理SSE结束标记
                if (data == "[DONE]") {
                    // 保存结果到数据库
                    saveAnalysisResultToDatabase()
                    runOnUiThread {
                        Toast.makeText(this@AiAnalysisResultActivity, "分析完成", Toast.LENGTH_SHORT).show()
                        // 隐藏加载指示器
                        loadingLayout.visibility = LinearLayout.GONE
                    }
                    return
                }
                
                try {
                    val jsonObject = JSONObject(data)
                    val choices = jsonObject.getJSONArray("choices")
                    if (choices.length() > 0) {
                        val choice = choices.getJSONObject(0)
                        val delta = choice.getJSONObject("delta")
                        if (delta.has("reasoning_content")) {
                            val reasoningContent = delta.getString("reasoning_content")
                            accumulatedReasoning += reasoningContent
                            runOnUiThread {
                                tvReasoningContent.text = accumulatedReasoning
                                // 自动滚动到推理内容底部
                                scrollToBottom(svReasoningContent)
                            }
                        } else if (delta.has("content")) {
                            val content = delta.getString("content")
                            accumulatedFinal += content
                            runOnUiThread {
                                // 检查内容是否为Markdown格式
                                tvFinalContent.text = accumulatedFinal
                                // 自动滚动到最终结果底部
                                scrollToBottom(svFinalContent)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AI_API", "解析SSE事件数据时出错: ${e.message}")
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                Log.e("AI_API", "SSE连接失败: ${t?.message}", t)
                response?.let { 
                    Log.e("AI_API", "响应码: ${it.code}")
                }
                
                runOnUiThread {
                    Toast.makeText(this@AiAnalysisResultActivity, "分析失败: ${t?.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                    // 隐藏加载指示器
                    loadingLayout.visibility = LinearLayout.GONE
                }
            }

            override fun onClosed(eventSource: EventSource) {
                Log.d("AI_API", "SSE连接正常关闭")
                // 保存结果到数据库
                saveAnalysisResultToDatabase()
                runOnUiThread {
                    // 隐藏加载指示器
                    loadingLayout.visibility = LinearLayout.GONE
                }
            }
        }

        // 创建并启动事件源
        eventSource = factory.newEventSource(request, sseListener)
    }

    private fun scrollToBottom(scrollView: ScrollView) {
        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun regenerateAnalysis() {
        // 取消之前的连接（如果有的话）
        eventSource?.cancel()
        
        // 清空旧的分析结果
        if (chatRecordId != -1L) {
            ChatDatabaseManager.updateAiAnalysisResult(chatRecordId, "", "")
        }
        
        // 重新发起分析
        setupStreamAnalysis()
    }

    private fun saveAnalysisResultToDatabase() {
        if (chatRecordId != -1L) {
            ChatDatabaseManager.updateAiAnalysisResult(chatRecordId, accumulatedReasoning, accumulatedFinal)
        }
    }

    private fun exportAnalysisResult() {
        val fileName = "AI分析结果_${groupName}_${System.currentTimeMillis()}.md"
        
        // 使用存储访问框架让用户选择导出位置
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/markdown"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        
        exportFileLauncher.launch(intent)
    }
    
    private val exportFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.also { uri ->
                try {
                    val content = accumulatedFinal.trimIndent()
                    
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(content.toByteArray())
                    }
                    
                    runOnUiThread {
                        Toast.makeText(this, "导出成功", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("EXPORT", "导出失败", e)
                    runOnUiThread {
                        Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    
    private fun getApiToken(): String {
        try {
            val inputStream = resources.openRawResource(R.raw.api_config)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val json = reader.use { it.readText() }
            val jsonObject = JSONObject(json)
            val apiKey = jsonObject.getString("openai_api_key")
            Log.d("AI_API", "Loaded API Key: $apiKey")
            return apiKey
        } catch (e: Exception) {
            throw RuntimeException("Failed to read API key from config file", e)
        }
    }

    private fun formatTime(timeMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 取消事件源连接，避免内存泄漏
        eventSource?.cancel()
        // 关闭OkHttpClient以释放资源
        client.dispatcher.executorService.shutdown()
    }
}