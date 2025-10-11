package com.ven.simple.autoci

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.ven.assists.service.AssistsService
import com.ven.assists.utils.CoroutineWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ChatRecordsActivity : ComponentActivity() {
    private lateinit var listView: ListView
    private lateinit var adapter: ChatRecordAdapter
    private val dataList = mutableListOf<ChatRecord>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat_records)
        
        listView = findViewById(R.id.lv_chat_records)
        val btnDeleteAll = findViewById<Button>(R.id.btn_delete_all)
        adapter = ChatRecordAdapter()
        listView.adapter = adapter
        
        loadChatRecords()

        listView.setOnItemClickListener { parent, view, position, id ->
            val selectedRecord = dataList[position]
            showChatMessages(selectedRecord)
        }
        
        btnDeleteAll.setOnClickListener {
            showDeleteAllConfirmationDialog()
        }
    }

    private fun showDeleteAllConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除所有聊天记录吗？此操作不可撤销。")
            .setPositiveButton("删除所有") { dialog, _ ->
                deleteAllChatRecords()
                dialog.dismiss()
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteAllChatRecords() {
        CoroutineWrapper.launch {
            try {
                val deletedCount = withContext(Dispatchers.IO) {
                    ChatDatabaseManager.deleteAllChatRecords()
                }
                
                withContext(Dispatchers.Main) {
                    dataList.clear()
                    adapter.notifyDataSetChanged()
                    Toast.makeText(this@ChatRecordsActivity, "已删除 $deletedCount 条记录", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatRecordsActivity, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private inner class ChatRecordAdapter : BaseAdapter() {
        override fun getCount(): Int = dataList.size

        override fun getItem(position: Int): Any = dataList[position]

        override fun getItemId(position: Int): Long = dataList[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view: View
            val holder: ViewHolder

            if (convertView == null) {
                view = layoutInflater.inflate(R.layout.item_chat_record_with_delete, parent, false)
                holder = ViewHolder()
                holder.tvGroupName = view.findViewById(R.id.tv_group_name)
                holder.tvCollectTime = view.findViewById(R.id.tv_collect_time)
                holder.tvSummary = view.findViewById(R.id.tv_summary)
//                holder.btnDelete = view.findViewById(R.id.btn_delete)
                view.tag = holder
            } else {
                view = convertView
                holder = view.tag as ViewHolder
            }

            val record = dataList[position]
            holder.tvGroupName?.text = record.groupName
            holder.tvCollectTime?.text = formatTime(record.collectTime)
            holder.tvSummary?.text = getSummary(record.chatMessages)
            
//            holder.btnDelete?.setOnClickListener {
//                showDeleteConfirmationDialog(record, position)
//            }

            return view
        }

        private inner class ViewHolder {
            var tvGroupName: TextView? = null
            var tvCollectTime: TextView? = null
            var tvSummary: TextView? = null
            var btnDelete: Button? = null
        }
    }

    private fun showDeleteConfirmationDialog(record: ChatRecord, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除群聊 \"${record.groupName}\" 的记录吗？此操作不可撤销。")
            .setPositiveButton("删除") { dialog, _ ->
                deleteChatRecord(record, position)
                dialog.dismiss()
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteChatRecord(record: ChatRecord, position: Int) {
        CoroutineWrapper.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    ChatDatabaseManager.deleteChatRecordById(record.id)
                }
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        dataList.removeAt(position)
                        adapter.notifyDataSetChanged()
                        Toast.makeText(this@ChatRecordsActivity, "删除成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ChatRecordsActivity, "删除失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatRecordsActivity, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showChatMessages(record: ChatRecord) {
        // 创建一个新的Activity来显示可滚动的聊天消息
        val intent = Intent(this, ChatMessagesActivity::class.java)
        intent.putExtra("groupName", record.groupName)
        intent.putExtra("chatMessages", record.chatMessages)
        intent.putExtra("collectTime", record.collectTime)
        intent.putExtra("chatRecordId", record.id)  // 传递chatRecordId
        startActivity(intent)
    }
    
    private fun loadChatRecords() {
        CoroutineWrapper.launch {
            try {
                // 确保数据库已初始化
                ChatDatabaseManager.initialize(AssistsService.instance ?: this@ChatRecordsActivity)
                
                val records = withContext(Dispatchers.IO) {
                    ChatDatabaseManager.getAllChatRecords()
                }
                
                withContext(Dispatchers.Main) {
                    dataList.clear()
                    dataList.addAll(records)
                    adapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatRecordsActivity, "加载数据失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun formatTime(timeMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }
    
    private fun getSummary(chatMessages: String): String {
        val lines = chatMessages.lines()
        return "共${lines.size}条消息"
    }
}