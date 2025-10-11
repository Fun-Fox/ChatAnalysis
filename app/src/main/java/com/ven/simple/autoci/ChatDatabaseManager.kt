package com.ven.simple.autoci

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import com.ven.assists.service.AssistsService

/**
 * 聊天记录数据类（包含AI分析结果）
 */
data class ChatRecord(
    val id: Long = 0,
    val groupName: String,
    val daysInterval: Long,
    val chatMessages: String,
    val collectTime: Long = System.currentTimeMillis(),
    val reasoningContent: String? = null,  // AI分析推理过程
    val finalContent: String? = null,      // AI分析最终结果
    val analysisTime: Long? = null         // 分析时间
)

/**
 * 聊天记录数据库管理类
 */
object ChatDatabaseManager {
    private const val DATABASE_NAME = "chat_records.db"
    private const val DATABASE_VERSION = 3  // 更新版本号
    private const val TABLE_NAME = "chat_records"
    
    private object ChatRecordEntry : BaseColumns {
        const val COLUMN_GROUP_NAME = "group_name"
        const val COLUMN_DAYS_INTERVAL = "days_interval"
        const val COLUMN_CHAT_MESSAGES = "chat_messages"
        const val COLUMN_COLLECT_TIME = "collect_time"
        const val COLUMN_REASONING_CONTENT = "reasoning_content"  // 新增列
        const val COLUMN_FINAL_CONTENT = "final_content"          // 新增列
        const val COLUMN_ANALYSIS_TIME = "analysis_time"          // 新增列
    }
    
    private lateinit var dbHelper: ChatDbHelper
    private lateinit var database: SQLiteDatabase
    
    /**
     * 初始化数据库
     */
    fun initialize(context: Context) {
        if (!this::dbHelper.isInitialized) {
            dbHelper = ChatDbHelper(context)
            database = dbHelper.writableDatabase
        }
    }
    
    /**
     * 保存聊天记录到数据库
     */
    fun saveChatRecord(record: ChatRecord): Long {
        val values = ContentValues().apply {
            put(ChatRecordEntry.COLUMN_GROUP_NAME, record.groupName)
            put(ChatRecordEntry.COLUMN_DAYS_INTERVAL, record.daysInterval)
            put(ChatRecordEntry.COLUMN_CHAT_MESSAGES, record.chatMessages)
            put(ChatRecordEntry.COLUMN_COLLECT_TIME, record.collectTime)
            // AI分析结果字段可以为空
            put(ChatRecordEntry.COLUMN_REASONING_CONTENT, record.reasoningContent)
            put(ChatRecordEntry.COLUMN_FINAL_CONTENT, record.finalContent)
            put(ChatRecordEntry.COLUMN_ANALYSIS_TIME, record.analysisTime)
        }
        
        val newRowId = database.insert(TABLE_NAME, null, values)
        println("assists_log: 聊天记录已保存到数据库，行ID: $newRowId")
        return newRowId
    }
    
    /**
     * 获取所有聊天记录
     */
    fun getAllChatRecords(): List<ChatRecord> {
        val records = mutableListOf<ChatRecord>()
        val projection = arrayOf(
            BaseColumns._ID,
            ChatRecordEntry.COLUMN_GROUP_NAME,
            ChatRecordEntry.COLUMN_DAYS_INTERVAL,
            ChatRecordEntry.COLUMN_CHAT_MESSAGES,
            ChatRecordEntry.COLUMN_COLLECT_TIME,
            ChatRecordEntry.COLUMN_REASONING_CONTENT,
            ChatRecordEntry.COLUMN_FINAL_CONTENT,
            ChatRecordEntry.COLUMN_ANALYSIS_TIME
        )
        
        val cursor = database.query(
            TABLE_NAME,
            projection,
            null,
            null,
            null,
            null,
            null
        )
        
        with(cursor) {
            while (moveToNext()) {
                val id = getLong(getColumnIndexOrThrow(BaseColumns._ID))
                val groupName = getString(getColumnIndexOrThrow(ChatRecordEntry.COLUMN_GROUP_NAME))
                val daysInterval = getLong(getColumnIndexOrThrow(ChatRecordEntry.COLUMN_DAYS_INTERVAL))
                val chatMessages = getString(getColumnIndexOrThrow(ChatRecordEntry.COLUMN_CHAT_MESSAGES))
                val collectTime = getLong(getColumnIndexOrThrow(ChatRecordEntry.COLUMN_COLLECT_TIME))
                val reasoningContent = getString(getColumnIndexOrThrow(ChatRecordEntry.COLUMN_REASONING_CONTENT))
                val finalContent = getString(getColumnIndexOrThrow(ChatRecordEntry.COLUMN_FINAL_CONTENT))
                val analysisTime = getLongOrNull(getColumnIndexOrThrow(ChatRecordEntry.COLUMN_ANALYSIS_TIME))
                
                records.add(ChatRecord(id, groupName, daysInterval, chatMessages, collectTime, reasoningContent, finalContent, analysisTime))
            }
        }
        
        cursor.close()
        return records
    }
    
    /**
     * 根据ID获取聊天记录（包含AI分析结果）
     */
    fun getChatRecordById(id: Long): ChatRecord? {
        val projection = arrayOf(
            BaseColumns._ID,
            ChatRecordEntry.COLUMN_GROUP_NAME,
            ChatRecordEntry.COLUMN_DAYS_INTERVAL,
            ChatRecordEntry.COLUMN_CHAT_MESSAGES,
            ChatRecordEntry.COLUMN_COLLECT_TIME,
            ChatRecordEntry.COLUMN_REASONING_CONTENT,
            ChatRecordEntry.COLUMN_FINAL_CONTENT,
            ChatRecordEntry.COLUMN_ANALYSIS_TIME
        )
        
        val selection = "${BaseColumns._ID} = ?"
        val selectionArgs = arrayOf(id.toString())
        
        val cursor = database.query(
            TABLE_NAME,
            projection,
            selection,
            selectionArgs,
            null,
            null,
            null
        )
        
        var record: ChatRecord? = null
        with(cursor) {
            if (moveToFirst()) {
                val recordId = getLong(getColumnIndexOrThrow(BaseColumns._ID))
                val groupName = getString(getColumnIndexOrThrow(ChatRecordEntry.COLUMN_GROUP_NAME))
                val daysInterval = getLong(getColumnIndexOrThrow(ChatRecordEntry.COLUMN_DAYS_INTERVAL))
                val chatMessages = getString(getColumnIndexOrThrow(ChatRecordEntry.COLUMN_CHAT_MESSAGES))
                val collectTime = getLong(getColumnIndexOrThrow(ChatRecordEntry.COLUMN_COLLECT_TIME))
                val reasoningContent = getString(getColumnIndexOrThrow(ChatRecordEntry.COLUMN_REASONING_CONTENT))
                val finalContent = getString(getColumnIndexOrThrow(ChatRecordEntry.COLUMN_FINAL_CONTENT))
                val analysisTime = getLongOrNull(getColumnIndexOrThrow(ChatRecordEntry.COLUMN_ANALYSIS_TIME))
                
                record = ChatRecord(recordId, groupName, daysInterval, chatMessages, collectTime, reasoningContent, finalContent, analysisTime)
            }
        }
        
        cursor.close()
        return record
    }
    
    /**
     * 更新聊天记录的AI分析结果
     */
    fun updateAiAnalysisResult(recordId: Long, reasoningContent: String, finalContent: String): Boolean {
        val values = ContentValues().apply {
            put(ChatRecordEntry.COLUMN_REASONING_CONTENT, reasoningContent)
            put(ChatRecordEntry.COLUMN_FINAL_CONTENT, finalContent)
            put(ChatRecordEntry.COLUMN_ANALYSIS_TIME, System.currentTimeMillis())
        }
        
        val selection = "${BaseColumns._ID} = ?"
        val selectionArgs = arrayOf(recordId.toString())
        
        val count = database.update(TABLE_NAME, values, selection, selectionArgs)
        return count > 0
    }
    
    /**
     * 根据ID删除聊天记录
     */
    fun deleteChatRecordById(id: Long): Boolean {
        val whereClause = "${BaseColumns._ID} = ?"
        val whereArgs = arrayOf(id.toString())
        val deletedRows = database.delete(TABLE_NAME, whereClause, whereArgs)
        return deletedRows > 0
    }
    
    /**
     * 删除所有聊天记录
     */
    fun deleteAllChatRecords(): Int {
        return database.delete(TABLE_NAME, null, null)
    }
    
    /**
     * 获取Long类型值，如果为null则返回null
     */
    private fun android.database.Cursor.getLongOrNull(columnIndex: Int): Long? {
        return if (isNull(columnIndex)) null else getLong(columnIndex)
    }
    
    /**
     * 数据库帮助类
     */
    private class ChatDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        
        override fun onCreate(db: SQLiteDatabase) {
            val createTableSql = """
                CREATE TABLE $TABLE_NAME (
                    ${BaseColumns._ID} INTEGER PRIMARY KEY,
                    ${ChatRecordEntry.COLUMN_GROUP_NAME} TEXT NOT NULL,
                    ${ChatRecordEntry.COLUMN_DAYS_INTERVAL} INTEGER NOT NULL,
                    ${ChatRecordEntry.COLUMN_CHAT_MESSAGES} TEXT NOT NULL,
                    ${ChatRecordEntry.COLUMN_COLLECT_TIME} INTEGER NOT NULL,
                    ${ChatRecordEntry.COLUMN_REASONING_CONTENT} TEXT,
                    ${ChatRecordEntry.COLUMN_FINAL_CONTENT} TEXT,
                    ${ChatRecordEntry.COLUMN_ANALYSIS_TIME} INTEGER
                )
            """.trimIndent()
            
            db.execSQL(createTableSql)
            println("assists_log: 聊天记录数据库表创建成功")
        }
        
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // 数据库升级逻辑
            if (oldVersion < 3) {
                // 添加AI分析结果相关列
                db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN ${ChatRecordEntry.COLUMN_REASONING_CONTENT} TEXT")
                db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN ${ChatRecordEntry.COLUMN_FINAL_CONTENT} TEXT")
                db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN ${ChatRecordEntry.COLUMN_ANALYSIS_TIME} INTEGER")
            }
        }
    }
}