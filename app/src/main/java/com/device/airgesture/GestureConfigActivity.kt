package com.device.airgesture

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.device.airgesture.action.Action
import com.device.airgesture.action.Gesture
import com.device.airgesture.action.GestureActionManager
import com.device.airgesture.utils.LogUtil

class GestureConfigActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GestureConfigAdapter
    private lateinit var resetButton: Button
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gesture_config)

        setupViews()
        loadCurrentConfig()
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.gestureRecyclerView)
        resetButton = findViewById(R.id.resetButton)
        saveButton = findViewById(R.id.saveButton)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = GestureConfigAdapter()
        recyclerView.adapter = adapter

        resetButton.setOnClickListener {
            resetToDefaults()
        }

        saveButton.setOnClickListener {
            saveConfiguration()
        }
    }

    private fun loadCurrentConfig() {
        val manager = GestureActionManager.getInstance()
        val configList = mutableListOf<GestureConfig>()

        // 只显示有意义的手势
        val gesturesInOrder = listOf(
            Gesture.SWIPE_LEFT,
            Gesture.SWIPE_RIGHT,
            Gesture.SWIPE_UP,
            Gesture.SWIPE_DOWN,
            Gesture.OK_SIGN,
            Gesture.FIST,
            Gesture.PALM_OPEN,
            Gesture.PEACE_SIGN,
            Gesture.CIRCLE_CW,
            Gesture.L_SHAPE
        )

        for (gesture in gesturesInOrder) {
            val action = manager.getGestureAction(gesture) ?: Action.NONE
            configList.add(GestureConfig(gesture, action))
        }

        adapter.setItems(configList)
    }

    private fun resetToDefaults() {
        GestureActionManager.getInstance().resetToDefaults()
        loadCurrentConfig()
        Toast.makeText(this, "已恢复默认设置", Toast.LENGTH_SHORT).show()
    }

    private fun saveConfiguration() {
        val manager = GestureActionManager.getInstance()
        for (config in adapter.getItems()) {
            manager.setGestureAction(config.gesture, config.action)
        }
        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show()
        finish()
    }

    data class GestureConfig(
        val gesture: Gesture,
        var action: Action
    )

    inner class GestureConfigAdapter : RecyclerView.Adapter<GestureConfigAdapter.ViewHolder>() {

        private val items = mutableListOf<GestureConfig>()
        private val actionList = listOf(
            Action.NONE,
            Action.NEXT_PAGE,
            Action.PREV_PAGE,
            Action.SCROLL_UP,
            Action.SCROLL_DOWN,
            Action.SCREENSHOT,
            Action.VOLUME_UP,
            Action.VOLUME_DOWN,
            Action.MUTE,
            Action.RECENT_APPS,
            Action.BACK,
            Action.HOME,
            Action.PLAY_PAUSE
        )

        fun setItems(list: List<GestureConfig>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        fun getItems(): List<GestureConfig> = items

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_gesture_config, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val gestureText: TextView = itemView.findViewById(R.id.gestureText)
            private val actionSpinner: Spinner = itemView.findViewById(R.id.actionSpinner)

            fun bind(config: GestureConfig) {
                gestureText.text = getGestureName(config.gesture)

                val actionNames = actionList.map { getActionName(it) }
                val adapter = ArrayAdapter(
                    itemView.context,
                    android.R.layout.simple_spinner_item,
                    actionNames
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                actionSpinner.adapter = adapter

                val currentIndex = actionList.indexOf(config.action)
                if (currentIndex >= 0) {
                    actionSpinner.setSelection(currentIndex)
                }

                actionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        config.action = actionList[position]
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }

            private fun getGestureName(gesture: Gesture): String {
                return when (gesture) {
                    Gesture.SWIPE_LEFT -> "左滑"
                    Gesture.SWIPE_RIGHT -> "右滑"
                    Gesture.SWIPE_UP -> "上滑"
                    Gesture.SWIPE_DOWN -> "下滑"
                    Gesture.OK_SIGN -> "OK手势"
                    Gesture.FIST -> "握拳"
                    Gesture.PALM_OPEN -> "张开手掌"
                    Gesture.PEACE_SIGN -> "剪刀手"
                    Gesture.CIRCLE_CW -> "顺时针画圈"
                    Gesture.CIRCLE_CCW -> "逆时针画圈"
                    Gesture.L_SHAPE -> "L形手势"
                    else -> gesture.name
                }
            }

            private fun getActionName(action: Action): String {
                return when (action) {
                    Action.NONE -> "无动作"
                    Action.NEXT_PAGE -> "下一页"
                    Action.PREV_PAGE -> "上一页"
                    Action.SCROLL_UP -> "向上滚动"
                    Action.SCROLL_DOWN -> "向下滚动"
                    Action.SCROLL_STOP -> "停止滚动"
                    Action.SCREENSHOT -> "截图"
                    Action.VOLUME_UP -> "音量增加"
                    Action.VOLUME_DOWN -> "音量减小"
                    Action.MUTE -> "静音"
                    Action.RECENT_APPS -> "最近任务"
                    Action.BACK -> "返回"
                    Action.HOME -> "主页"
                    Action.PLAY_PAUSE -> "播放/暂停"
                    Action.NEXT_TRACK -> "下一曲"
                    Action.PREV_TRACK -> "上一曲"
                    else -> action.name
                }
            }
        }
    }

    companion object {
        private const val TAG = "GestureConfigActivity"
    }
}