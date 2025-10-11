package com.ven.simple.wx_auto_login

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import com.blankj.utilcode.util.ScreenUtils
import com.ven.assists.service.AssistsService
import com.ven.assists.service.AssistsServiceListener
import com.ven.assists.stepper.StepManager
import com.ven.assists.window.AssistsWindowManager
import com.ven.assists.window.AssistsWindowWrapper
import com.ven.simple.autoci.SimpleStep
import com.ven.simple.autoci.databinding.OverlayBinding

object Overlay : AssistsServiceListener {

    @SuppressLint("StaticFieldLeak")
    var viewBinding: OverlayBinding? = null
        private set
        get() {
            if (field == null) {
                field = OverlayBinding.inflate(LayoutInflater.from(AssistsService.instance)).apply {
                    btnStop.setOnClickListener {
                        StepManager.isStop = true
                    }
                    btnClick.setOnClickListener {
                        // 从 SharedPreferences 获取设置数据
                        val sharedPrefs = AssistsService.instance?.getSharedPreferences(
                            "app_settings",
                            android.content.Context.MODE_PRIVATE
                        )
                        val groupNamesStr =
                            sharedPrefs?.getString("group_names", "") ?: ""
                        val daysIntervalStr = sharedPrefs?.getString("days_interval", "14") ?: "14"

                        // 处理群聊名称数据
                        val groupNames = if (groupNamesStr.isNotBlank()) {
                            groupNamesStr.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
                        } else {
                            listOf("")
                        }

                        // 处理时间间隔数据
                        val daysInterval = if (daysIntervalStr.isNotBlank()) {
                            daysIntervalStr.toLongOrNull() ?: 14
                        } else {
                            14L
                        }

                        // 准备参数
                        val params = mapOf(
                            "groupNames" to groupNames,
                            "daysInterval" to daysInterval
                        )

                        StepManager.execute(
                            SimpleStep::class.java,
                            stepTag = 1,
                            begin = true,
                            data = params
                        )
                    }

                }
            }
            return field
        }

    var onClose: ((parent: View) -> Unit)? = null

    var showed = false
        private set
        get() {
            assistWindowWrapper?.let {
                return AssistsWindowManager.isVisible(it.getView())
            } ?: return false
        }

    var assistWindowWrapper: AssistsWindowWrapper? = null
        private set
        get() {
            viewBinding?.let {
                if (field == null) {
                    field = AssistsWindowWrapper(
                        it.root,
                        wmLayoutParams = AssistsWindowManager.createLayoutParams().apply {
                            width = (ScreenUtils.getScreenWidth() * 0.3).toInt()
                            height = (ScreenUtils.getScreenHeight() * 0.3).toInt()
                        },
                        onClose = this.onClose
                    ).apply {
                        minWidth = (ScreenUtils.getScreenWidth() * 0.3).toInt()
                        minHeight = (ScreenUtils.getScreenHeight() * 0.2).toInt()
                        initialX = (ScreenUtils.getScreenWidth() * 0.7).toInt()
                        initialY = ScreenUtils.getScreenHeight() / 2
                        wmlp.x = initialX
                        wmlp.y = initialY
                        viewBinding.tvTitle.text = ""

                    }
                }
            }
            return field
        }

    fun show() {
        if (!AssistsService.listeners.contains(this)) {
            AssistsService.listeners.add(this)
        }
        AssistsWindowManager.add(assistWindowWrapper)
    }

    fun hide() {
        AssistsWindowManager.removeView(assistWindowWrapper?.getView())
    }

    override fun onUnbind() {
        viewBinding = null
        assistWindowWrapper = null
    }
}