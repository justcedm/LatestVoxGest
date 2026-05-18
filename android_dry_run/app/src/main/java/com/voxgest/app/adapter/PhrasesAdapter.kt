package com.voxgest.app.adapter

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.voxgest.dryrun.MotionSettings
import com.voxgest.dryrun.R

data class PhraseItem(
    val text: String,
    val iconRes: Int,
    val circleBackground: Int,
    val iconColor: Int,
    val emergency: Boolean = false
)

class PhrasesAdapter(
    private val items: List<PhraseItem>,
    private val onPhraseClick: (PhraseItem) -> Unit
) : RecyclerView.Adapter<PhrasesAdapter.PhraseHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhraseHolder {
        return PhraseHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_phrase_button, parent, false), onPhraseClick)
    }

    override fun onBindViewHolder(holder: PhraseHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class PhraseHolder(itemView: View, private val onPhraseClick: (PhraseItem) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val card: View = itemView.findViewById(R.id.phraseCard)
        private val circle: FrameLayout = itemView.findViewById(R.id.phraseIconCircle)
        private val icon: ImageView = itemView.findViewById(R.id.phraseIcon)
        private val text: TextView = itemView.findViewById(R.id.phraseText)
        private var current: PhraseItem? = null

        init {
            card.setOnTouchListener { view, event ->
                if (MotionSettings.animationsEnabled(view.context)) {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80L).start()
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f)
                            .setInterpolator(OvershootInterpolator(2f)).setDuration(200L).start()
                    }
                }
                false
            }
            card.setOnClickListener { current?.let(onPhraseClick) }
        }

        fun bind(item: PhraseItem) {
            current = item
            text.text = item.text
            circle.setBackgroundResource(item.circleBackground)
            icon.setImageResource(item.iconRes)
            icon.setColorFilter(ContextCompat.getColor(itemView.context, item.iconColor))
            card.contentDescription = if (item.emergency) "Emergency: ${item.text}" else item.text
        }
    }
}
