package com.voxgest.dryrun

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class HistoryFragment : Fragment(R.layout.fragment_history) {
    private val adapter = HistoryAdapter()
    private lateinit var emptyState: View
    private val historyListener = { renderHistory() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val history = view.findViewById<RecyclerView>(R.id.historyRecyclerView)
        emptyState = view.findViewById(R.id.historyEmptyState)
        history.layoutManager = LinearLayoutManager(requireContext())
        history.adapter = adapter
        history.itemAnimator = DefaultItemAnimator().apply {
            addDuration = 150L
        }
        view.findViewById<MaterialButton>(R.id.clearHistoryButton).setOnClickListener {
            confirmClearHistory()
        }
        ConversationHistoryManager.addListener(historyListener)
        renderHistory()
    }

    override fun onDestroyView() {
        ConversationHistoryManager.removeListener(historyListener)
        super.onDestroyView()
    }

    private fun renderHistory() {
        val messages = ConversationHistoryManager.messages()
        adapter.submit(messages)
        emptyState.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirmClearHistory() {
        if (ConversationHistoryManager.messages().isEmpty()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.history_clear_title)
            .setMessage(R.string.history_clear_body)
            .setNegativeButton(R.string.history_clear_cancel, null)
            .setPositiveButton(R.string.history_clear_confirm) { _, _ ->
                ConversationHistoryManager.clear()
            }
            .show()
    }
}
