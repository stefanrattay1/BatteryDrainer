package com.batterydrainer.benchmark.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.batterydrainer.benchmark.R
import com.batterydrainer.benchmark.databinding.ActivityReportBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ReportActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityReportBinding
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Test Reports"
        
        loadReports()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
    
    private fun loadReports() {
        val reportsDir = File(getExternalFilesDir(null), "reports")
        val reports = reportsDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        
        if (reports.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.reportsRecyclerView.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.reportsRecyclerView.visibility = View.VISIBLE
            
            binding.reportsRecyclerView.layoutManager = LinearLayoutManager(this)
            binding.reportsRecyclerView.adapter = ReportAdapter(reports) { file ->
                openReport(file)
            }
        }
    }
    
    private fun openReport(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(intent, "Open Report"))
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open report", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "html" -> "text/html"
            "json" -> "application/json"
            "csv" -> "text/csv"
            "pdf" -> "application/pdf"
            else -> "text/plain"
        }
    }
    
    // Simple adapter for reports list
    inner class ReportAdapter(
        private val reports: List<File>,
        private val onClick: (File) -> Unit
    ) : RecyclerView.Adapter<ReportAdapter.ViewHolder>() {
        
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: android.widget.TextView = view.findViewById(android.R.id.text1)
            val dateText: android.widget.TextView = view.findViewById(android.R.id.text2)
        }
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = reports[position]
            holder.nameText.text = file.nameWithoutExtension
            holder.dateText.text = dateFormat.format(Date(file.lastModified()))
            holder.itemView.setOnClickListener { onClick(file) }
        }
        
        override fun getItemCount() = reports.size
    }
}
