package com.a101apps.biblianote

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.a101apps.biblianote.notebook.Note
import com.a101apps.biblianote.notebook.NotesDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {

    private lateinit var progressBar: ProgressBar
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private lateinit var toolbar: Toolbar

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        progressBar = view.findViewById(R.id.progressBar)
        setupToolbar(view)
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.clear() // Clear the menu items
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return false // No menu item handling needed
            }
        }, viewLifecycleOwner)

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Handle the back button event and tell Navigation to navigate up
                findNavController().navigateUp()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

        setupExportButton(view)
        return view
    }

    private fun setupToolbar(view: View) {
        toolbar = view.findViewById(R.id.toolbar)
        (activity as? AppCompatActivity)?.let { activity ->
            activity.setSupportActionBar(toolbar)
            activity.supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                setDisplayShowHomeEnabled(true)
                title = getString(R.string.settings)
                toolbar.setTitleTextColor(ContextCompat.getColor(requireContext(), R.color.toolbar_text_color))

            }

            // Set title color to white
            toolbar.setTitleTextColor(ContextCompat.getColor(activity, R.color.toolbar_text_color))

            // Set navigation icon (back button) to white using DrawableCompat
            val upArrow = ContextCompat.getDrawable(activity, R.drawable.ic_back) // Ensure you have 'ic_back' drawable
            upArrow?.let {
                DrawableCompat.setTint(it, ContextCompat.getColor(activity, R.color.toolbar_icon_color))
                toolbar.navigationIcon = it
            }

            // Handle back navigation
            toolbar.setNavigationOnClickListener {
                findNavController().navigateUp()
            }
        }
    }

    private fun setupExportButton(view: View) {
        view.findViewById<Button>(R.id.exportButton).setOnClickListener {
            exportNotesToCSV()
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun exportNotesToCSV() {
        coroutineScope.launch {
            val db = NotesDatabase.getDatabase(requireContext())
            val notes = withContext(Dispatchers.IO) {
                db.noteDao().getAllNotesList()
            }
            val csvData = convertToCSV(notes)
            saveCSVToFile(csvData)
        }
    }

    private suspend fun saveCSVToFile(csvData: String) {
        withContext(Dispatchers.IO) {
            try {
                val downloadsPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

                if (!downloadsPath.exists() && !downloadsPath.mkdirs()) {
                    throw IOException("Cannot create or access the Downloads directory")
                }

                val dateFormat = SimpleDateFormat("ddMMyyyy", Locale.getDefault())
                val currentTime = dateFormat.format(Date())
                val file = File(downloadsPath, "Notes_export_$currentTime.csv")

                file.writeText(csvData)

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Exported to Downloads: ${file.name}", Toast.LENGTH_LONG).show()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to export data. Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun convertToCSV(notes: List<Note>): String {
        val header = "ID,Title,Content,Timestamp\n"
        return notes.joinToString(separator = "\n", prefix = header) { note ->
            val title = "\"${note.title?.replace("\"", "\"\"")}\""
            val content = "\"${note.content?.replace("\"", "\"\"")}\""
            "${note.id},$title,$content,${formatTimestamp(note.timestamp)}"
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CREATE_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.also { uri ->
                showLoading(true)
                exportData(uri)
            }
        }
    }

    private fun exportData(uri: Uri) {
        coroutineScope.launch {
            val db = NotesDatabase.getDatabase(requireContext())
            val notes = withContext(Dispatchers.IO) {
                db.noteDao().getAllNotesList()
            }
            val csvData = convertToCSV(notes)
            writeToFile(uri, csvData)
        }
    }

    private fun writeToFile(uri: Uri, data: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                var success = false
                requireContext().contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                    FileOutputStream(pfd.fileDescriptor).use { fos ->
                        fos.write(data.toByteArray())
                        success = true
                    }
                }

                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(requireContext(), "Successfully exported to the selected location.", Toast.LENGTH_LONG).show()
                    } else {
                        throw IOException("Failed to write to the selected file.")
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to export data. Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }

    companion object {
        private const val CREATE_FILE_REQUEST_CODE = 1
    }
}
