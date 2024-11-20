package com.example.a2subscriber

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

class StudentAdapter(
    private var students: MutableList<StudentInfo>,
    private val listener: OnMoreButtonClickListener
    ): RecyclerView.Adapter<StudentAdapter.ViewHolder>() {


    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val studentIdText: TextView = itemView.findViewById(R.id.studentId)
        val maxSpd: TextView = itemView.findViewById(R.id.maxspd)
        val minSpd: TextView = itemView.findViewById(R.id.minspd)
        val moreBtn: Button = itemView.findViewById(R.id.moreBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.student, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return students.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val student = students[position]

        holder.studentIdText.text = student.id.toString()
        holder.maxSpd.text = "Max Speed: "+ String.format("%.1f", student.maxSpd)
        holder.minSpd.text = "Min Speed: "+ String.format("%.1f", student.minSpd)

        holder.moreBtn.setOnClickListener {
            listener.onMoreButtonClick(student)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newStudents: MutableList<StudentInfo>) {
        fun updateList(newStudents: List<StudentInfo>) {
            // Use a more precise update method
            val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = students.size
                override fun getNewListSize() = newStudents.size
                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    return students[oldItemPosition].id == newStudents[newItemPosition].id
                }
                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    return students[oldItemPosition] == newStudents[newItemPosition]
                }
            })

            students.clear()
            students.addAll(newStudents)
            diffResult.dispatchUpdatesTo(this)
        }
    }

}