package com.parth.airgap

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.parth.airgap.databinding.ItemMessageMineBinding
import com.parth.airgap.databinding.ItemMessageSystemBinding
import com.parth.airgap.databinding.ItemMessageTheirsBinding

/**
 * Chat list with three tactical view types:
 *  - MINE:   right-aligned, red-tinted block, sender label "YOU" (or own nickname).
 *  - THEIRS: left-aligned, grey block, sender nickname in red mono.
 *  - SYSTEM: centered red mono line, no block (">> NICK JOINED THE ROOM").
 */
class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    /** Display name used for my own messages ("YOU" by default). */
    var myLabel: String = "YOU"

    /** Suffix appended to a sender label when the message came via relay. */
    var viaRelaySuffix: String = " - VIA RELAY"

    fun add(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun clear() {
        val size = messages.size
        messages.clear()
        if (size > 0) notifyItemRangeRemoved(0, size)
    }

    override fun getItemViewType(position: Int): Int = when (messages[position].kind) {
        ChatKind.MINE -> VIEW_TYPE_MINE
        ChatKind.THEIRS -> VIEW_TYPE_THEIRS
        ChatKind.SYSTEM -> VIEW_TYPE_SYSTEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_MINE -> MineViewHolder(
                ItemMessageMineBinding.inflate(inflater, parent, false)
            )
            VIEW_TYPE_THEIRS -> TheirsViewHolder(
                ItemMessageTheirsBinding.inflate(inflater, parent, false)
            )
            else -> SystemViewHolder(
                ItemMessageSystemBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is MineViewHolder -> {
                holder.binding.senderText.text = message.sender.ifEmpty { myLabel }
                holder.binding.messageText.text = message.text
            }
            is TheirsViewHolder -> {
                // Tactical sender label is caps, e.g. "AARAV" or "AARAV - VIA RELAY".
                holder.binding.senderText.text =
                    message.sender.uppercase() + if (message.viaRelay) viaRelaySuffix else ""
                holder.binding.messageText.text = message.text
            }
            is SystemViewHolder -> {
                holder.binding.messageText.text = message.text
            }
        }
    }

    override fun getItemCount(): Int = messages.size

    class MineViewHolder(val binding: ItemMessageMineBinding) :
        RecyclerView.ViewHolder(binding.root)

    class TheirsViewHolder(val binding: ItemMessageTheirsBinding) :
        RecyclerView.ViewHolder(binding.root)

    class SystemViewHolder(val binding: ItemMessageSystemBinding) :
        RecyclerView.ViewHolder(binding.root)

    companion object {
        private const val VIEW_TYPE_MINE = 0
        private const val VIEW_TYPE_THEIRS = 1
        private const val VIEW_TYPE_SYSTEM = 2
    }
}
