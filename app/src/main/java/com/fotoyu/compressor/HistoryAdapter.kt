package com.fotoyu.compressor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fotoyu.compressor.databinding.ItemHistoryBinding

class HistoryAdapter(private var items: List<HistoryItem>) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(val bindingView: ItemHistoryBinding) : RecyclerView.ViewHolder(bindingView.getRoot())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.bindingView
        
        b.txtDate.text = item.date
        b.txtPath.text = item.sourcePath
        b.txtStats.text = "${item.photoCount} foto"
        b.txtSizes.text = "${item.originalSize} \u2192 ${item.resultSize}"
        b.txtSaving.text = "${item.savingPercent}% Hemat"
        
        if (item.isSuccess) {
            b.txtStatus.text = "BERHASIL"
            b.txtStatus.setTextColor(b.getRoot().context.getColor(R.color.gray_600))
            b.iconStatus.setImageResource(R.drawable.ic_check_circle)
        } else {
            b.txtStatus.text = "GAGAL"
            b.txtStatus.setTextColor(b.getRoot().context.getColor(R.color.black))
            b.iconStatus.setImageResource(R.drawable.ic_cancel_circle)
        }
    }

    fun updateItems(newItems: List<HistoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size
}
