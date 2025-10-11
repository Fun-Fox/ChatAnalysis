package com.ven.simple.autoci

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.ven.assists.utils.CoroutineWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

class ChatMessagesActivity : ComponentActivity() {
    private lateinit var tvGroupName: TextView
    private lateinit var tvCollectTime: TextView
    private lateinit var tvMessages: TextView
    private lateinit var tvAiResult: TextView
    private lateinit var btnAiAnalyze: Button
    
    private var chatRecordId: Long = -1  // 添加chatRecordId变量
    
    private val client = OkHttpClient()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat_messages)
        
        initViews()
        setupData()
        setupClickListeners()
    }
    
    private fun initViews() {
        tvGroupName = findViewById(R.id.tv_group_name)
        tvCollectTime = findViewById(R.id.tv_collect_time)
        tvMessages = findViewById(R.id.tv_messages)
        tvAiResult = findViewById(R.id.tv_ai_result)
        btnAiAnalyze = findViewById(R.id.btn_ai_analyze)
    }
    
    private fun setupData() {
        val groupName = intent.getStringExtra("groupName") ?: ""
        val chatMessages = intent.getStringExtra("chatMessages") ?: ""
        val collectTime = intent.getLongExtra("collectTime", System.currentTimeMillis())
        chatRecordId = intent.getLongExtra("chatRecordId", -1)  // 正确获取chatRecordId
        
        tvGroupName.text = groupName
        tvCollectTime.text = formatTime(collectTime)
        tvMessages.text = chatMessages
        
        // 设置标题为群聊名称
        title = groupName
    }
    
    private fun setupClickListeners() {
        btnAiAnalyze.setOnClickListener {
            val intent = Intent(this, AiAnalysisResultActivity::class.java)
            intent.putExtra("groupName", tvGroupName.text.toString())
            intent.putExtra("chatMessages", tvMessages.text.toString())
            intent.putExtra("collectTime", tvCollectTime.text.toString())
            intent.putExtra("chatRecordId", chatRecordId)  // 正确传递chatRecordId
            startActivity(intent)
        }
    }

    
    private fun formatTime(timeMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }
}