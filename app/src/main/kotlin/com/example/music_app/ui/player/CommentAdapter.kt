package com.example.music_app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.music_app.R
import com.example.music_app.model.Comment

class CommentAdapter(
    private val comments: MutableList<Comment>
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    inner class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userName: TextView = itemView.findViewById(R.id.commentUserName)
        val content: TextView = itemView.findViewById(R.id.commentContent)
        val timestamp: TextView = itemView.findViewById(R.id.commentTimestamp)
        val likeButton: ImageButton = itemView.findViewById(R.id.commentLikeButton)
        val likeCount: TextView = itemView.findViewById(R.id.commentLikeCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]
        holder.userName.text = comment.userId   // sau này bạn có thể map sang tên thật
        holder.content.text = comment.content
        holder.timestamp.text = formatTime(comment.timestamp)
        holder.likeCount.text = comment.likes.toString()

        holder.likeButton.setOnClickListener {
            comment.likes += 1
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int = comments.size

    private fun formatTime(timestamp: Long): String {
        // Demo: chỉ hiển thị số mili giây, bạn có thể format thành "2h ago"
        return "${timestamp} ms"
    }
}
