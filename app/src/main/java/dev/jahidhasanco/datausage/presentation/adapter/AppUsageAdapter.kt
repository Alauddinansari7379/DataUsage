package dev.jahidhasanco.datausage.presentation.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.alauddin.datausagen.R
import com.alauddin.datausagen.databinding.ItemAppUsageBinding
import dev.jahidhasanco.datausage.DataFormatter
import dev.jahidhasanco.datausage.data.model.AppUsageUi

class AppUsageAdapter : ListAdapter<AppUsageUi, AppUsageAdapter.AppViewHolder>(DIFF) {

    class AppViewHolder(
        private val binding: ItemAppUsageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppUsageUi) {
            binding.apply {
                appName.text = item.label
                appTotal.text = DataFormatter.format(item.total)
                appBar.progress = item.sharePercent
                appDetail.text = root.context.getString(
                    R.string.app_usage_detail,
                    DataFormatter.format(item.downloads),
                    DataFormatter.format(item.uploads)
                )
                if (item.icon != null) {
                    appIcon.setImageDrawable(item.icon)
                } else {
                    appIcon.setImageResource(R.drawable.ic_apps)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = AppViewHolder(
        ItemAppUsageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<AppUsageUi>() {
            override fun areItemsTheSame(oldItem: AppUsageUi, newItem: AppUsageUi) =
                oldItem.uid == newItem.uid

            override fun areContentsTheSame(oldItem: AppUsageUi, newItem: AppUsageUi) =
                oldItem.uid == newItem.uid &&
                        oldItem.total == newItem.total &&
                        oldItem.sharePercent == newItem.sharePercent &&
                        oldItem.label == newItem.label
        }
    }
}
