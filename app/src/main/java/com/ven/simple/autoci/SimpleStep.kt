package com.ven.simple.autoci

import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.click
import com.ven.assists.AssistsCore.findFirstParentClickable
import com.ven.assists.AssistsCore.gesture
import com.ven.assists.AssistsCore.isTextView
import com.ven.assists.AssistsCore.launchApp
import com.ven.assists.AssistsCore.txt
import com.ven.assists.stepper.Step
import com.ven.assists.stepper.StepCollector
import com.ven.assists.stepper.StepImpl
import com.ven.assists.window.AssistsWindowManager.overlayToast
import kotlinx.coroutines.delay

// 数据库相关导入
import com.ven.simple.autoci.ChatDatabaseManager
import com.ven.simple.autoci.ChatRecord
import android.content.Context
import com.ven.assists.AssistsCore.containsText
import com.ven.assists.service.AssistsService

class SimpleStep : StepImpl() {

    override fun onImpl(collector: StepCollector) {
        collector.next(stepTag = 1) { step ->
            val data = step.data
//            "接收到数据：$data".overlayToast()
//            println("assists_log:$data")
            // 第1步：启动小红书应用
            "启动小红书应用".overlayToast()
            delay(1000)
            // 启动小红书应用，包名为 com.xingin.xhs
            launchApp("com.xingin.xhs")

            // 执行第2步
            return@next Step.get(2, delay = 3000, data = data)
        }.next(stepTag = 2) { step ->
            val data = step.data
            //第2步
            //
            "去往消息列表".overlayToast()
            delay(3000)
//            AssistsCore.getAllNodes().forEach { it.logNode() }
//
            AssistsCore.findByTags(
                className = "android.widget.TextView",
                viewId = "com.xingin.xhs:id/0_resource_name_obfuscated",
                text = "消息"
            ).firstOrNull()?.findFirstParentClickable()?.click()

//            执行第3步
            return@next Step.get(3, delay = 2000, data = data)
        }.next(stepTag = 3) { step ->
            val data = step.data
//
            ChatDatabaseManager.initialize(AssistsService.instance!!)

            //第3步
            // 接收群聊名称列表和时间间隔参数
            var groupNames: List<String> = emptyList()
            var daysInterval: Long = 14 // 默认14天

            // 解析传入的 data 参数
            when (data) {
                is Map<*, *> -> {
                    // 安全地提取字段
                    val groupNameList =
                        (data["groupNames"] as? List<*>?)?.filterIsInstance<String>()
                    val interval = data["daysInterval"] as? Number

                    groupNames = groupNameList ?: emptyList()
                    daysInterval = interval?.toLong() ?: 14
                }

                is List<*> -> {
                    groupNames = data.filterIsInstance<String>()
                }

                is Array<*> -> {
                    groupNames = data.filterIsInstance<String>()
                }

                else -> {
                    groupNames = emptyList()
                }
            }

            // 判断群聊列表是否有效
            if (groupNames.isEmpty()) {
                "未接收到有效的群聊列表".overlayToast()
                return@next Step.none
            }
            // 循环处理每个群聊
            groupNames.forEach { groupName ->
                "点击群聊: $groupName".overlayToast()
                delay(5000)

                val allNodes = AssistsCore.getAllNodes()

                // 遍历并打印所有文本节点的文本
//                allNodes.forEach { node ->
//                    val nodeText = node.txt()
//                    if (nodeText.isNotEmpty()) {
////                        println("assists_log: 节点文本: $nodeText")
//                    }
//                }

                // 查找包含groupName的节点
                val targetNode = allNodes.find { it.txt().contains(groupName) }

                if (targetNode != null) {
                    "已找到匹配群聊名称: ${targetNode.txt()}".overlayToast()

                    targetNode.findFirstParentClickable()?.click()

                } else {
                    "未找到包含 '$groupName' 的群聊 $groupName".overlayToast()
                    // 直接返回，继续处理下一个群聊
                    return@forEach
                }
                val chatName = targetNode.txt()
                delay(5000)
                //执行第4步
                // 获取该群聊的聊天信息
                "开始采集聊天信息: $chatName".overlayToast()
                delay(5000)

                // 可配置的时间参数
                val currentTime = System.currentTimeMillis()
                val timeIntervalInMillis = daysInterval * 24 * 60 * 60 * 1000L

                var isReachedTop = false
                var lastScreenTexts = mutableListOf<String>()
                val allCollectedTexts = mutableListOf<String>() // 收集所有屏幕的文本

                while (!isReachedTop) {
                    delay(2000)

                    // 检查当前是否仍在目标应用界面
                    val currentPackage = AssistsCore.getPackageName()
                    "当前包名: $currentPackage".overlayToast()
                    if (currentPackage != "com.xingin.xhs") {
                        "检测到已离开小红书应用，当前应用: $currentPackage".overlayToast()
                        break // 离开循环
                    }

                    // 收集当前屏幕的所有文本
                    val currentScreenTexts = mutableListOf<String>()
                    var hasValidChatsInThisScreen = false
                    var hasRecentChatInThisScreen = false

                    val allNodesForScreen = AssistsCore.getAllNodes()
                    var validTextCount = 0 // 记录已找到的有效文本数量

                    // 遍历所有节点，但只处理前4个满足条件的文本
                    for (i in 0 until allNodesForScreen.size) {
                        val node = allNodesForScreen[i]
                        if (node.txt().isNotEmpty() && node.isTextView()) {
                            validTextCount++
                            // 只处理第4个有效的文本节点（validTextCount == 4）
                            if (validTextCount >= 5) {
                                val text = node.txt()
                                currentScreenTexts.add(text)

                                // 检查是否有在指定时间间隔内的聊天
                                if (isBeyondTimeInterval(
                                        text,
                                        currentTime,
                                        timeIntervalInMillis
                                    )
                                ) {
                                    hasRecentChatInThisScreen = true
                                }
                                hasValidChatsInThisScreen = true
                            }
                        }
                    }


                    // 判断是否到顶：
                    // 1. 当前屏与上一屏的文本完全一致
                    // 2. 当前屏存在在指定时间间隔内的聊天记录
                    if (currentScreenTexts.isNotEmpty() &&
                        lastScreenTexts.isNotEmpty() &&
                        currentScreenTexts == lastScreenTexts
                    ) {
                        isReachedTop = true
                        "已到达顶部，当前屏与上一屏内容完全一致".overlayToast()
                        delay(2000)
                    } else if (hasRecentChatInThisScreen) {
                        // 如果当前屏有在指定时间间隔内的聊天，则认为已到顶
                        isReachedTop = true
                        "已到达顶部，当前屏包含${daysInterval}天内的聊天记录".overlayToast()
                        allCollectedTexts.addAll(0, currentScreenTexts)
                        delay(2000)

                    } else {

                        // 将当前屏幕文本添加到总集合中
                        allCollectedTexts.addAll(0, currentScreenTexts)

                        lastScreenTexts.clear()
                        lastScreenTexts.addAll(currentScreenTexts)

                        // 如果这一屏有有效聊天记录，继续滚动
                        if (hasValidChatsInThisScreen) {
                            // 执行向上滚动
                            val appWidth = AssistsCore.getAppWidthInScreen()
                            val appHeight = AssistsCore.getAppHeightInScreen()

                            val startX = appWidth * 0.5f
                            val startY = appHeight * 0.2f // 从下往上滑

                            val endX = appWidth * 0.5f
                            val endY = appHeight * 0.8f   // 滑动到上方

                            val startLocation = floatArrayOf(startX, startY)
                            val endLocation = floatArrayOf(endX, endY)
                            val duration = 1000L

                            gesture(startLocation, endLocation, 0L, duration)
                            "继续滚动采集更多聊天记录".overlayToast()
                        } else {
                            // 如果这一屏没有找到有效的聊天记录，也停止滚动
                            isReachedTop = true
                            "未找到有效聊天记录，停止滚动".overlayToast()
                        }
                    }
                }

                // 打印所有收集到的文本
                // 在打印前去重
                val uniqueTexts = allCollectedTexts.toSet()
                println("=== 所有收集到的聊天文本 ===")
//                uniqueTexts.forEach { text ->
//                    println("assists_log:$text")
//                }
                println("========================")

                // 确保数据库已初始化
                try {
                    // 将聊天记录保存到数据库
                    val chatMessages = uniqueTexts.joinToString("\n")
                    val chatRecord = ChatRecord(
                        groupName = chatName,
                        daysInterval = daysInterval,
                        chatMessages = chatMessages,
                        collectTime = System.currentTimeMillis()
                    )
                    ChatDatabaseManager.saveChatRecord(chatRecord)
                    "群聊 $chatName 的聊天记录已保存到数据库".overlayToast()
                    delay(2000)
                } catch (e: Exception) {
                    "数据库初始化或保存失败: ${e.message}".overlayToast()
                }
                // 回退回聊天列表
                AssistsCore.back()
            }

            "所有群聊都采样完成了，快回去【查看采集的群聊记录】吧~".overlayToast(delay = 10000)

            //结束执行
            return@next Step.none
        }
    }

    fun isBeyondTimeInterval(text: String, currentTime: Long, timeIntervalInMillis: Long): Boolean {
        // 匹配 "MM-dd HH:mm" 格式的正则表达式
        val timePattern = Regex("""(\d{2})-(\d{2})\s(\d{2}):(\d{2})""")
        val matchResult = timePattern.find(text)

        if (matchResult != null) {
            try {
                val (month, day, hour, minute) = matchResult.destructured
                // 构造年份（假设为当前年份）
                val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

                // 创建 SimpleDateFormat 来解析时间
                val sdf =
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                sdf.timeZone = java.util.TimeZone.getDefault()

                // 构造完整的时间字符串
                val timeString = "$currentYear-$month-$day $hour:$minute"
                val parsedTime = sdf.parse(timeString)?.time ?: return false

                // 判断解析的时间是否超出指定时间间隔
                val timeDiff = currentTime - parsedTime
                val isBeyondRange = timeDiff < 0 || timeDiff > timeIntervalInMillis

                if (isBeyondRange) {
                    println("assists_log:找到时间戳: $month-$day $hour:$minute，超出${timeIntervalInMillis / (24 * 60 * 60 * 1000L)}天范围")
                }

                return isBeyondRange
            } catch (e: Exception) {
                // 解析失败，不是有效的时间格式
                println("assists_log:时间解析失败: $text, 错误: ${e.message}")
            }
        }
        return false
    }
}