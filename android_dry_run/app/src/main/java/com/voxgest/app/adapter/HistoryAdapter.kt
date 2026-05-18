package com.voxgest.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.voxgest.dryrun.MotionSettings
import com.voxgest.dryrun.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HistoryType { Sign, Speech, Phrase }

data class HistoryEntry(
    val type: HistoryType,
    val text: String,
    val timestamp: String,
    val detail: String,
    val section: String
)

object VoxHistoryStore {
    private val listeners = mutableSetOf<() -> Unit>()
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.US)
    private val items = mutableListOf(
        HistoryEntry(HistoryType.Sign, "HELLO, I NEED WATER", "10:45 AM", "Spoken", "Today"),
        HistoryEntry(HistoryType.Speech, "Hello, how can I help you?", "10:42 AM", "Shown in signs", "Today"),
        HistoryEntry(HistoryType.Phrase, "I need help", "10:40 AM", "Shown in signs", "Today"),
        HistoryEntry(HistoryType.Sign, "THANK YOU", "08:15 PM", "Spoken", "Yesterday"),
        HistoryEntry(HistoryType.Speech, "Please wait a moment.", "07:50 PM", "Shown in signs", "Yesterday"),
        HistoryEntry(HistoryType.Phrase, "Call a doctor", "07:30 PM", "Shown in signs", "Yesterday")
    )

    fun entries(): List<HistoryEntry> = items.toList()

    fun add(type: HistoryType, text: String, detail: String) {
        val clean = text.trim()
        if (clean.isBlank() || clean.equals("NOTHING", ignoreCase = true)) return
        items.add(0, HistoryEntry(type, clean, timeFormat.format(Date()), detail, "Today"))
        notifyChanged()
    }

    fun addPhrase(text: String, emergency: Boolean) {
        val clean = text.trim()
        if (clean.isBlank() || clean.equals("NOTHING", ignoreCase = true)) return
        add(if (emergency) HistoryType.Phrase else HistoryType.Phrase, clean, "Shown in signs")
    }

    fun clear() {
        items.clear()
        notifyChanged()
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        listeners.toList().forEach { it.invoke() }
    }
}

class HistoryAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private sealed class Row {
        data class Header(val title: String) : Row()
        data class Item(val entry: HistoryEntry) : Row()
    }

    private val rows = mutableListOf<Row>()

    fun submit(entries: List<HistoryEntry>) {
        rows.clear()
        var lastSection = ""
        entries.forEach { entry ->
            if (entry.section != lastSection) {
                rows.add(Row.Header(entry.section))
                lastSection = entry.section
            }
            rows.add(Row.Item(entry))
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is Row.Header -> VIEW_HEADER
        is Row.Item -> VIEW_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_HEADER) {
            HeaderHolder(TextView(parent.context).apply {
                setPadding(dp(16), dp(12), dp(16), dp(8))
                textSize = 12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            })
        } else {
            ItemHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_history_entry, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderHolder).bind(row.title)
            is Row.Item -> (holder as ItemHolder).bind(row.entry)
        }
    }

    override fun getItemCount(): Int = rows.size

    private class HeaderHolder(private val textView: TextView) : RecyclerView.ViewHolder(textView) {
        fun bind(title: String) {
            textView.text = title
        }
    }

    private class ItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: View = itemView.findViewById(R.id.historyCard)
        private val iconCircle: FrameLayout = itemView.findViewById(R.id.typeIconCircle)
        private val icon: ImageView = itemView.findViewById(R.id.typeIcon)
        private val typeLabel: TextView = itemView.findViewById(R.id.typeLabel)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timestampText: TextView = itemView.findViewById(R.id.timestampText)
        private val metaText: TextView = itemView.findViewById(R.id.metaText)
        private val metaIcon: ImageView = itemView.findViewById(R.id.metaIcon)

        fun bind(entry: HistoryEntry) {
            val context = itemView.context
            val (iconRes, circleBg, color) = when (entry.type) {
                HistoryType.Sign -> Triple(R.drawable.ic_hand_gesture, R.drawable.bg_icon_teal, R.color.history_sign_icon)
                HistoryType.Speech -> Triple(R.drawable.ic_waveform, R.drawable.bg_icon_yellow, R.color.history_speech_icon)
                HistoryType.Phrase -> Triple(R.drawable.ic_chat, R.drawable.bg_icon_orange, R.color.history_phrase_icon)
            }
            iconCircle.setBackgroundResource(circleBg)
            icon.setImageResource(iconRes)
            icon.setColorFilter(ContextCompat.getColor(context, color))
            typeLabel.text = entry.type.name
            typeLabel.setTextColor(ContextCompat.getColor(context, color))
            messageText.text = entry.text
            timestampText.text = entry.timestamp
            metaText.text = entry.detail
            metaIcon.setImageResource(if (entry.detail.contains("Spoken", true)) R.drawable.ic_volume_up else R.drawable.ic_play_arrow)
            metaIcon.setColorFilter(ContextCompat.getColor(context, R.color.text_muted))
            card.contentDescription = "${entry.type.name} at ${entry.timestamp}: ${entry.text}"
            animateAppear()
        }

        private fun animateAppear() {
            if (!MotionSettings.animationsEnabled(itemView.context)) return
            itemView.alpha = 0f
            itemView.translationY = 12f * itemView.resources.displayMetrics.density
            itemView.animate().alpha(1f).translationY(0f).setDuration(150L).setInterpolator(DecelerateInterpolator()).start()
        }
    }

    private fun View.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val VIEW_HEADER = 0
        const val VIEW_ITEM = 1
    }
}
