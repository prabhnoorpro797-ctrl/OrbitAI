package com.orbitai.assistant.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.orbitai.assistant.R

class ConversationAdapter : RecyclerView.Adapter<ConversationAdapter.VH>() {

    data class Item(val type: Type, val text: String) {
        enum class Type { USER, ASSISTANT, SYSTEM }
    }

    private val items = mutableListOf<Item>()

    fun add(item: Item) {
        items.add(item)
        notifyItemInserted(items.size - 1)
    }

    override fun getItemViewType(position: Int): Int = items[position].type.ordinal

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = when (Item.Type.entries[viewType]) {
            Item.Type.USER -> R.layout.item_message_user
            Item.Type.ASSISTANT -> R.layout.item_message_assistant
            Item.Type.SYSTEM -> R.layout.item_message_system
        }
        val v = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.text.text = items[position].text
    }

    override fun getItemCount() = items.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val text: TextView = v.findViewById(R.id.messageText)
    }
}
