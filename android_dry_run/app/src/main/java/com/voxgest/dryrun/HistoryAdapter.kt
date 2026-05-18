package com.voxgest.dryrun

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.MessageViewHolder>() {
    private var items: List<ConversationMessage> = emptyList()
    private val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())

    fun submit(messages: List<ConversationMessage>) {
        items = messages
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].source == ConversationSource.SIGNED) VIEW_SIGNED else VIEW_HEARD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layout = if (viewType == VIEW_SIGNED) R.layout.item_message_sign else R.layout.item_message_hear
        return MessageViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false)
        )
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(items[position], timeFormat)
    }

    override fun getItemCount(): Int = items.size

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: View = itemView.findViewById(R.id.messageCard)
        private val sourceChip: TextView = itemView.findViewById(R.id.sourceChip)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timestampText: TextView = itemView.findViewById(R.id.timestampText)

        fun bind(message: ConversationMessage, formatter: DateFormat) {
            val context = itemView.context
            val sourceLabel = when (message.source) {
                ConversationSource.SIGNED -> context.getString(R.string.history_sender_sign)
                ConversationSource.HEARD -> context.getString(R.string.history_sender_hear)
                ConversationSource.SHORTCUT -> context.getString(R.string.history_sender_shortcut)
                ConversationSource.EMERGENCY -> context.getString(R.string.history_sender_emergency)
            }
            val chipBackground = when (message.source) {
                ConversationSource.SIGNED -> R.drawable.bg_chip_signed
                ConversationSource.HEARD -> R.drawable.bg_chip_heard
                ConversationSource.SHORTCUT -> R.drawable.bg_chip_heard
                ConversationSource.EMERGENCY -> R.drawable.bg_chip_emergency
            }
            val time = formatter.format(Date(message.timestampMillis))
            sourceChip.text = sourceLabel
            sourceChip.background = ContextCompat.getDrawable(context, chipBackground)
            messageText.text = message.text
            timestampText.text = "$time - ${message.detail}"
            card.contentDescription = "$sourceLabel at $time: ${message.text}"
            animateAppearIfNeeded()
        }

        private fun animateAppearIfNeeded() {
            if (!MotionSettings.animationsEnabled(itemView.context)) return
            itemView.alpha = 0f
            itemView.translationY = 12f * itemView.resources.displayMetrics.density
            itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(150L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private companion object {
        const val VIEW_SIGNED = 1
        const val VIEW_HEARD = 2
    }
}
