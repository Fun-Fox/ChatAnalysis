package com.ven.simple.autoci

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.blankj.utilcode.util.AppUtils
import com.ven.assists.AssistsCore
import com.ven.assists.service.AssistsService
import com.ven.assists.service.AssistsServiceListener
import com.ven.assists.utils.CoroutineWrapper
import kotlinx.coroutines.delay
import com.ven.assists.stepper.StepManager
import com.ven.assists.window.AssistsWindowManager
import com.ven.simple.wx_auto_login.Overlay

class MainActivity : ComponentActivity(), AssistsServiceListener {
    private lateinit var etGroupNames: EditText
    private lateinit var etDaysInterval: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 初始化视图
        initViews()

        AssistsService.listeners.add(object : AssistsServiceListener {
            override fun onServiceConnected(service: AssistsService) {
                CoroutineWrapper.launch {
                    while (true) {
                        AssistsCore.back()
                        delay(500)
                        if (AssistsCore.getPackageName() == AppUtils.getAppPackageName()) {
                            break
                        }
                    }
                }
            }
        })
    }

    private fun initViews() {
        val btnOpenAccessibility = findViewById<Button>(R.id.btn_open_accessibility)
        etGroupNames = findViewById(R.id.et_group_names)
        etDaysInterval = findViewById(R.id.et_days_interval)
        val btnSave = findViewById<Button>(R.id.btn_save)
        val btnExecute = findViewById<Button>(R.id.btn_execute)
        val btnViewRecords = findViewById<Button>(R.id.btn_view_records)

        // 设置默认值
        etGroupNames.setText("做点什么 不务正业")
        etDaysInterval.setText("14")

        // 设置按钮点击事件
        btnOpenAccessibility.setOnClickListener {
            AssistsCore.openAccessibilitySetting()
        }

        btnSave.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        }

        btnExecute.setOnClickListener {

            saveSettings()
            Overlay.show()
//            if (AssistsCore.isAccessibilityServiceEnabled()) {
//
//            } else {
//                Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
//                AssistsCore.openAccessibilitySetting()
//            }
        }

        btnViewRecords.setOnClickListener {
            val intent = Intent(this, ChatRecordsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun saveSettings() {
        val groupNames = etGroupNames.text.toString()
        val daysInterval = etDaysInterval.text.toString()

        // 使用SharedPreferences保存设置
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        with(prefs.edit()) {
            putString("group_names", groupNames)
            putString("days_interval", daysInterval)
            apply()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val groupNames = prefs.getString("group_names", "做点什么呢 不务正业")
        val daysInterval = prefs.getString("days_interval", "14")

        etGroupNames.setText(groupNames)
        etDaysInterval.setText(daysInterval)
    }


    override fun onResume() {
        super.onResume()
        loadSettings()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 确保在Activity销毁时隐藏日志悬浮窗
        if (OverlayLog.showed) {
            OverlayLog.hide()
        }
    }
}