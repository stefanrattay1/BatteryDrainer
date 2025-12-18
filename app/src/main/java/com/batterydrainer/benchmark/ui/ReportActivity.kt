package com.batterydrainer.benchmark.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
            binding.reportCountText.visibility = View.VISIBLE
            binding.reportCountText.text = "${reports.size} report${if (reports.size != 1) "s" else ""}"

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

    inner class ReportAdapter(
        private val reports: List<File>,
        private val onClick: (File) -> Unit
    ) : RecyclerView.Adapter<ReportAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val reportTypeIcon: TextView = view.findViewById(R.id.reportTypeIcon)
            val reportName: TextView = view.findViewById(R.id.reportName)
            val reportDate: TextView = view.findViewById(R.id.reportDate)
            val reportTypeBadge: TextView = view.findViewById(R.id.reportTypeBadge)
            val reportSize: TextView = view.findViewById(R.id.reportSize)
            val reportFormat: TextView = view.findViewById(R.id.reportFormat)
            val reportStatus: TextView = view.findViewById(R.id.reportStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_report, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = reports[position]
            val extension = file.extension.lowercase()

            // Set report name (clean up the filename)
            val displayName = file.nameWithoutExtension
                .replace("_", " ")
                .replaceFirstChar { it.uppercase() }
            holder.reportName.text = displayName

            // Set date
            holder.reportDate.text = dateFormat.format(Date(file.lastModified()))

            // Set file type badge and icon based on extension
            when (extension) {
                "html" -> {
                    holder.reportTypeIcon.text = "📊"
                    holder.reportTypeBadge.text = "HTML"
                    holder.reportTypeBadge.setBackgroundResource(R.drawable.badge_html)
                    holder.reportFormat.text = "Interactive"
                }
                "json" -> {
                    holder.reportTypeIcon.text = "{ }"
                    holder.reportTypeBadge.text = "JSON"
                    holder.reportTypeBadge.setBackgroundResource(R.drawable.badge_json)
                    holder.reportFormat.text = "Data"
                }
                "csv" -> {
                    holder.reportTypeIcon.text = "📋"
                    holder.reportTypeBadge.text = "CSV"
                    holder.reportTypeBadge.setBackgroundResource(R.drawable.badge_csv)
                    holder.reportFormat.text = "Spreadsheet"
                }
                "pdf" -> {
                    holder.reportTypeIcon.text = "📄"
                    holder.reportTypeBadge.text = "PDF"
                    holder.reportTypeBadge.setBackgroundResource(R.drawable.badge_pdf)
                    holder.reportFormat.text = "Document"
                }
                else -> {
                    holder.reportTypeIcon.text = "📝"
                    holder.reportTypeBadge.text = extension.uppercase()
                    holder.reportTypeBadge.setBackgroundResource(R.drawable.badge_file_type)
                    holder.reportFormat.text = "Text"
                }
            }

            // Set file size
            holder.reportSize.text = formatFileSize(file.length())

            // Status is always complete for saved files
            holder.reportStatus.text = "Complete"

            // Click listener
            holder.itemView.setOnClickListener { onClick(file) }
        }

        override fun getItemCount() = reports.size

        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
                else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            }
        }
    }
}
