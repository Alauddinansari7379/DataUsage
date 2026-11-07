package dev.jahidhasanco.datausage.presentation.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.alauddin.datausagen.databinding.ItemDailyUsagesBinding
import dev.jahidhasanco.datausage.data.model.UsagesData

class DataUsagesAdapter : ListAdapter<UsagesData, DataUsagesAdapter.DataUsagesViewHolder>(DIFF) {

    class DataUsagesViewHolder(
        private val binding: ItemDailyUsagesBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: UsagesData) {
            binding.apply {
                tvDate.text = item.date
                mobileData.text = item.mobile
                wifiData.text = item.wifi
                dayBar.progress = item.sharePercent
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = DataUsagesViewHolder(
        ItemDailyUsagesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: DataUsagesViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<UsagesData>() {
            override fun areItemsTheSame(oldItem: UsagesData, newItem: UsagesData) =
                oldItem.date == newItem.date

            override fun areContentsTheSame(oldItem: UsagesData, newItem: UsagesData) =
                oldItem == newItem
        }
    }
}
