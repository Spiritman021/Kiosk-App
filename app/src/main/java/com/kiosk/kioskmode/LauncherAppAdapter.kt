package com.kiosk.kioskmode

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LauncherAppAdapter(
    private var apps: List<AppInfo>,
    private val onAppClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<LauncherAppAdapter.LauncherAppViewHolder>() {

    class LauncherAppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.appIcon)
        val appName: TextView = view.findViewById(R.id.appName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LauncherAppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_launcher_app, parent, false)
        return LauncherAppViewHolder(view)
    }

    override fun onBindViewHolder(holder: LauncherAppViewHolder, position: Int) {
        val app = apps[position]
        
        holder.appIcon.setImageDrawable(app.icon)
        holder.appName.text = app.appName
        
        holder.itemView.setOnClickListener {
            onAppClick(app)
        }
    }

    override fun getItemCount(): Int = apps.size

    fun updateApps(newApps: List<AppInfo>) {
        apps = newApps
        notifyDataSetChanged()
    }
}
