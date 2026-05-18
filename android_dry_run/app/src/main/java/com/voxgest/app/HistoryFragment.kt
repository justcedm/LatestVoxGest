package com.voxgest.app

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.voxgest.app.adapter.HistoryAdapter
import com.voxgest.app.adapter.VoxHistoryStore
import com.voxgest.dryrun.R

class HistoryFragment : Fragment(R.layout.fragment_history) {
    private val adapter = HistoryAdapter()
    private lateinit var emptyState: View
    private val listener = { render() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        emptyState = view.findViewById(R.id.historyEmptyState)
        view.findViewById<RecyclerView>(R.id.historyRecyclerView).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HistoryFragment.adapter
            itemAnimator = DefaultItemAnimator().apply { addDuration = 150L }
        }
        view.findViewById<View>(R.id.clearHistoryButton).setOnClickListener { confirmClear() }
        VoxHistoryStore.addListener(listener)
        render()
    }

    override fun onDestroyView() {
        VoxHistoryStore.removeListener(listener)
        super.onDestroyView()
    }

    private fun render() {
        adapter.submit(VoxHistoryStore.entries())
        emptyState.visibility = if (VoxHistoryStore.entries().isEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.history_clear_title)
            .setMessage(R.string.history_clear_body)
            .setNegativeButton(R.string.history_clear_cancel, null)
            .setPositiveButton(R.string.history_clear_confirm) { _, _ -> VoxHistoryStore.clear() }
            .show()
    }
}
