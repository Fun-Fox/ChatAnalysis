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
import android.widget.RadioGroup
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
    private lateinit var btnSelectPrompt: Button
    private lateinit var btnRegenerate: Button
    private lateinit var btnExport: Button
    private lateinit var svReasoningContent: ScrollView
    private lateinit var svFinalContent: ScrollView
    private lateinit var loadingLayout: LinearLayout
    private lateinit var promptSelectionLayout: LinearLayout
    private lateinit var rgPrompts: RadioGroup
    private lateinit var btnConfirmPrompt: Button
    
    private lateinit var tvCollectTime: String
    private lateinit var tvMessages: String

    private var accumulatedReasoning = ""
    private var accumulatedFinal = ""
    
    private var chatRecordId: Long = -1
    private var groupName: String = ""
    private var selectedPrompt: String = ""
    private var selectedPromptName: String = ""

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
        promptSelectionLayout = findViewById(R.id.prompt_selection_layout)
        rgPrompts = findViewById(R.id.rg_prompts)
        btnSelectPrompt = findViewById(R.id.btn_select_prompt)
        btnRegenerate = findViewById(R.id.btn_regenerate)
        btnExport = findViewById(R.id.btn_export)
        btnConfirmPrompt = findViewById(R.id.btn_confirm_prompt)
        
        btnSelectPrompt.setOnClickListener {
            showPromptSelection()
        }
        
        btnRegenerate.setOnClickListener {
            regenerateAnalysis()
        }
        
        btnExport.setOnClickListener {
            showExportOptions()
        }
        
        btnConfirmPrompt.setOnClickListener {
            confirmPromptAndAnalyze()
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
                // 数据库中没有结果，显示提示词选择界面
                showPromptSelection()
            }
        } else {
            // 没有chatRecordId，显示提示词选择界面
            showPromptSelection()
        }
    }

    private fun showPromptSelection() {
        promptSelectionLayout.visibility = LinearLayout.VISIBLE
        loadingLayout.visibility = LinearLayout.GONE
    }

    private fun confirmPromptAndAnalyze() {
        // 获取选中的提示词
        when (rgPrompts.checkedRadioButtonId) {
            R.id.rb_prompt1 -> {
                selectedPrompt = "你是一个专业的群聊分析师，请分析群聊内容并输出结构化报告(如：热点话题并统计提及次数、话题热度榜、有趣的对话及金句、话题词云)，并深度挖掘用户需求及商业价值。"
                selectedPromptName = "群聊舆情分析报告"
            }
            R.id.rb_prompt2 -> {
                selectedPrompt = "你是一个用户行为分析师，请分析群聊内容，深度挖掘用户行为模式、用户需求和潜在商业价值，输出用户画像和商业建议。"
                selectedPromptName = "用户行为分析"
            }
            R.id.rb_prompt3 -> {
                selectedPrompt = "你是一名玩具产品经理。请分析以下聊天记录，找出其中暗示的3个新产品开发机会或现有产品优化方向。请说明理由并引用用户原话。"
                selectedPromptName = "玩具产品开发机会分析"
            }
            R.id.rb_prompt4 -> {
                selectedPrompt = "你是一名玩具市场分析师。请分析以下聊天记录，找出用户提到的关于玩具的至少5个核心痛点或抱怨，并引用匿名化的用户原话作为例子。"
                selectedPromptName = "玩具核心痛点分析"
            }
            R.id.rb_prompt5 -> {
                selectedPrompt = "你是一名市场情报分析师。请从以下聊天记录中，总结所有关于竞品（非我方品牌）的讨论。列出被提及的竞品品牌，以及用户对它们的正面和负面评价。"
                selectedPromptName = "竞品讨论分析"
            }
            R.id.rb_prompt6 -> {
                selectedPrompt = """

                    ## 1. 角色设定
                    你是一名资深的玩具行业市场分析专家和数据科学家。你的任务是分析以下提供的社群聊天记录，为一家玩具公司提取核心内容、洞察用户痛点、发现潜在商机，并生成一份结构化、逻辑清晰的分析报告。请严格基于提供的文本内容进行分析，避免无根据的猜测。

                    ## 2. 分析维度与要求
                    请按照以下六个维度进行分析，并以报告的形式呈现：

                    ### 第一部分：群聊热点与核心议题分析
                    - **高频关键词提炼：** 提取出现频率最高的20个关键词（已排除“的、了、哈”等无意义助词），并分为以下几类：
                        - **玩具品类/IP：** (例如: 积木, 遥控车, 奥特曼)
                        - **品牌名：** (包括我方品牌和竞品品牌)
                        - **用户行为/情感：** (例如: 推荐, 求助, 坏了, 喜欢, 太贵)
                        - **场景/节日：** (例如: 生日, 旅行, 过年)
                    - **核心讨论话题聚类：** 总结出群内讨论最集中的3-5个核心话题，并对每个话题进行简要描述。例如：产品推荐求助、育儿经验交流、产品使用问题反馈等。

                    ### 第二部分：用户需求与痛点深度挖掘
                    - **识别核心痛点：** 找出用户在使用玩具或购买决策中普遍抱怨或感到困扰的问题点。请列出至少3个主要痛点，并引用1-2句匿名化的用户原话作为证据。
                    - **识别未满足的需求：** 找出用户表达出的“希望有…”、“要是…就好了”这类期待和愿望。请列出至少3个未被满足的需求，并引用原话佐证。

                    ### 第三部分：商业机会与市场洞察
                    - **产品创新/优化建议：** 基于第二部分的痛点和需求，提出具体的产品开发或改进建议。
                    - **竞品分析：** 总结用户对竞品的讨论。提及了哪些竞品？用户赞扬了竞品的哪些优点？吐槽了哪些缺点？这对我们有什么启发？
                    - **营销场景机会：** 用户在哪些特定场景下（如节日、旅行、雨天居家）会讨论玩具？这为我们的场景化营销提供了哪些灵感？

                    ### 第四部分：用户画像与关键人物识别
                    - **核心用户画像(Persona)：** 描述群内最活跃用户的典型特征。他们的孩子大概在什么年龄段？他们最关心什么问题（价格、安全、益智性）？
                    - **意见领袖(KOC)识别：** 找出哪些用户（用[用户A]等匿名标识符）的发言影响力大、推荐被采纳率高、或者回答问题最积极。描述他们的典型特征。

                    ### 第五部分：购买决策链路分析
                    - 总结用户从产生兴趣到决定购买（或放弃）的关键影响因素。他们在购买前会咨询什么？什么因素会促使他们下单（如折扣、好评）？什么因素会让他们犹豫（如价格、安装复杂）？

                    ### 第六部分：总结与行动建议
                    - 对整份报告进行一个高度概括的总结。
                    - 基于以上所有分析，为玩具公司提出3-5条最优先、最具可操作性的行动建议。

                    ## 3. 输入数据
                    以下是需要分析的聊天记录：
                    ---
                    [请在此处粘贴您已脱敏和整理好的聊天记录]
                    ---

                    ## 4. 输出要求
                    请以清晰的Markdown格式生成报告，使用标题、列表和粗体来突出重点，确保报告易于阅读和理解。
                """.trimIndent()
                selectedPromptName = "综合市场分析报告"
            }
            else -> {
                selectedPrompt = "你是一个专业的群聊分析师，请分析群聊内容并输出结构化报告(如：热点话题并统计提及次数、话题热度榜、有趣的对话及金句、话题词云)，并深度挖掘用户需求及商业价值。"
                selectedPromptName = "群聊舆情分析报告"
            }
        }
        
        // 隐藏提示词选择界面
        promptSelectionLayout.visibility = LinearLayout.GONE
        
        // 开始分析
        setupStreamAnalysis()
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
                    put("content", selectedPrompt)
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
        
        // 清空显示内容
        tvReasoningContent.text = ""
        tvFinalContent.text = ""
        accumulatedReasoning = ""
        accumulatedFinal = ""
        
        // 显示提示词选择界面
        showPromptSelection()
    }

    private fun saveAnalysisResultToDatabase() {
        if (chatRecordId != -1L) {
            ChatDatabaseManager.updateAiAnalysisResult(chatRecordId, accumulatedReasoning, accumulatedFinal)
        }
    }

    private fun showExportOptions() {
        // 创建选项对话框让用户选择导出格式
        val options = arrayOf("导出为Markdown", "导出为HTML")
        android.app.AlertDialog.Builder(this)
            .setTitle("选择导出格式")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportAsMarkdown()
                    1 -> exportAsHtml()
                }
            }
            .show()
    }

    private fun exportAsMarkdown() {
        val fileName = "AI分析结果_${groupName}_${selectedPromptName}_${System.currentTimeMillis()}.md"
        
        // 使用存储访问框架让用户选择导出位置
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/markdown"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        
        exportFileLauncher.launch(intent)
    }
    
    private fun exportAsHtml() {
        val fileName = "AI分析结果_${groupName}_${selectedPromptName}_${System.currentTimeMillis()}.html"
        
        // 使用存储访问框架让用户选择导出位置
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/html"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        
        exportHtmlFileLauncher.launch(intent)
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
    
    private val exportHtmlFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.also { uri ->
                try {
                    // 将Markdown转换为HTML
                    val markdownContent = accumulatedFinal.trimIndent()
                    val htmlContent = convertMarkdownToHtml(markdownContent)
                    
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(htmlContent.toByteArray())
                    }
                    
                    runOnUiThread {
                        Toast.makeText(this, "HTML导出成功", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("EXPORT", "HTML导出失败", e)
                    runOnUiThread {
                        Toast.makeText(this, "HTML导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    
    private fun convertMarkdownToHtml(markdown: String): String {
        // 简单的Markdown到HTML转换实现
        val html = StringBuilder()
        html.append("<!DOCTYPE html>\n")
        html.append("<html>\n<head>\n")
        html.append("<meta charset=\"UTF-8\">\n")
        html.append("<title>AI分析结果</title>\n")
        html.append("<style>\n")
        html.append("body { font-family: Arial, sans-serif; margin: 20px; }\n")
        html.append("h1, h2, h3 { color: #333; }\n")
        html.append("code { background-color: #f4f4f4; padding: 2px 4px; border-radius: 3px; }\n")
        html.append("pre { background-color: #f4f4f4; padding: 10px; border-radius: 5px; overflow-x: auto; }\n")
        html.append("blockquote { border-left: 4px solid #ddd; padding-left: 10px; margin-left: 0; color: #666; }\n")
        html.append("table { border-collapse: collapse; width: 100%; }\n")
        html.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n")
        html.append("th { background-color: #f2f2f2; }\n")
        html.append("</style>\n")
        html.append("</head>\n<body>\n")
        
        // 简单的Markdown转换逻辑
        val lines = markdown.lines()
        var inCodeBlock = false
        var inList = false
        
        for (line in lines) {
            var processedLine = line
            
            // 处理代码块
            if (processedLine.startsWith("```")) {
                if (!inCodeBlock) {
                    html.append("<pre><code>")
                    inCodeBlock = true
                } else {
                    html.append("</code></pre>\n")
                    inCodeBlock = false
                }
                continue
            }
            
            if (inCodeBlock) {
                html.append(processedLine).append("\n")
                continue
            }
            
            // 处理标题
            if (processedLine.startsWith("# ")) {
                html.append("<h1>").append(processedLine.substring(2)).append("</h1>\n")
                continue
            } else if (processedLine.startsWith("## ")) {
                html.append("<h2>").append(processedLine.substring(3)).append("</h2>\n")
                continue
            } else if (processedLine.startsWith("### ")) {
                html.append("<h3>").append(processedLine.substring(4)).append("</h3>\n")
                continue
            }
            
            // 处理无序列表
            if (processedLine.startsWith("- ") || processedLine.startsWith("* ")) {
                if (!inList) {
                    html.append("<ul>\n")
                    inList = true
                }
                html.append("<li>").append(processedLine.substring(2)).append("</li>\n")
                continue
            } else {
                if (inList) {
                    html.append("</ul>\n")
                    inList = false
                }
            }
            
            // 处理引用
            if (processedLine.startsWith("> ")) {
                html.append("<blockquote>").append(processedLine.substring(2)).append("</blockquote>\n")
                continue
            }
            
            // 处理粗体
            processedLine = processedLine.replace(Regex("\\*\\*(.*?)\\*\\*"), "<strong>$1</strong>")
            
            // 处理斜体
            processedLine = processedLine.replace(Regex("\\*(.*?)\\*"), "<em>$1</em>")
            
            // 处理行内代码
            processedLine = processedLine.replace(Regex("`([^`]+)`"), "<code>$1</code>")
            
            // 如果是空行
            if (processedLine.isEmpty()) {
                html.append("<br>\n")
                continue
            }
            
            // 普通段落
            html.append("<p>").append(processedLine).append("</p>\n")
        }
        
        // 关闭可能未关闭的标签
        if (inList) {
            html.append("</ul>\n")
        }
        
        html.append("</body>\n</html>")
        return html.toString()
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