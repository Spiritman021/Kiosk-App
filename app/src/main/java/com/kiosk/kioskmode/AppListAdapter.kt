package com.kiosk.kioskmode

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView

class AppListAdapter(
    private var apps: List<AppInfo>,
    private val onAppToggled: (AppInfo, Boolean) -> Unit
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    private var filteredApps: List<AppInfo> = apps

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        val appName: TextView = itemView.findViewById(R.id.appName)
        val packageName: TextView = itemView.findViewById(R.id.packageName)
        val appSwitch: SwitchCompat = itemView.findViewById(R.id.appSwitch)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = filteredApps[position]
        
        holder.appIcon.setImageDrawable(app.icon)
        holder.appName.text = app.appName
        holder.packageName.text = app.packageName
        
        // Remove all listeners first to prevent triggering during recycling
        holder.appSwitch.setOnCheckedChangeListener(null)
        holder.itemView.setOnClickListener(null)
        
        // Set the switch state
        holder.appSwitch.isChecked = app.isWhitelisted
        
        // Set up the switch listener
        holder.appSwitch.setOnCheckedChangeListener { _, isChecked ->
            // Use adapterPosition to get the current position
            val currentPosition = holder.adapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) {
                val currentApp = filteredApps[currentPosition]
                currentApp.isWhitelisted = isChecked
                onAppToggled(currentApp, isChecked)
            }
        }
        
        // Also toggle when clicking the entire row
        holder.itemView.setOnClickListener {
            val currentPosition = holder.adapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) {
                holder.appSwitch.isChecked = !holder.appSwitch.isChecked
            }
        }
    }

    override fun getItemCount(): Int = filteredApps.size

    fun filter(query: String) {
        filteredApps = if (query.isEmpty()) {
            apps
        } else {
            apps.filter {
                it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
            }
        }
        notifyDataSetChanged()
    }

    fun updateApps(newApps: List<AppInfo>) {
        apps = newApps
        filteredApps = newApps
        notifyDataSetChanged()
    }

    fun selectAll() {
        filteredApps.forEach { app ->
            app.isWhitelisted = true
            onAppToggled(app, true)
        }
        notifyDataSetChanged()
    }

    fun deselectAll() {
        filteredApps.forEach { app ->
            app.isWhitelisted = false
            onAppToggled(app, false)
        }
        notifyDataSetChanged()
    }
}
