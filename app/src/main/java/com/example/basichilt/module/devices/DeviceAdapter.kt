package com.example.basichilt.module.devices


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.basichilt.R

class DeviceAdapter(
    private val onClick: (DeviceItem) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.VH>() {

    private val items = ArrayList<DeviceItem>()

    fun submitList(newList: List<DeviceItem>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return VH(v, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class VH(
        itemView: View,
        private val onClick: (DeviceItem) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvName: TextView = itemView.findViewById(R.id.tv_name)
        private val tvAddr: TextView = itemView.findViewById(R.id.tv_addr)
        private val tvRssi: TextView = itemView.findViewById(R.id.tv_rssi)

        fun bind(item: DeviceItem) {
            tvName.text = item.name ?: "(no name)"
            tvAddr.text = item.address
            tvRssi.text = item.rssi.toString()

            itemView.setOnClickListener { onClick(item) }
        }
    }
}
