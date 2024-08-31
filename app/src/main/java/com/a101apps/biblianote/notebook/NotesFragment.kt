package com.a101apps.biblianote.notebook

import android.content.Context
import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.a101apps.biblianote.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale
import java.util.UUID

class NotesFragment : Fragment() {

    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabNewNote: ExtendedFloatingActionButton
    private val notesDatabase: NotesDatabase by lazy { NotesDatabase.getDatabase(requireContext()) }
    private val noteDao by lazy { notesDatabase.noteDao() }
    private lateinit var progressBar: ProgressBar

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_notes, container, false)
        toolbar = view.findViewById(R.id.toolbarNotes)
        recyclerView = view.findViewById(R.id.recyclerViewNotes)
        fabNewNote = view.findViewById(R.id.fabNewNote)
        progressBar = view.findViewById(R.id.progressBar)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as AppCompatActivity).setSupportActionBar(toolbar)

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.clear() // Clear the previous menu items
                menuInflater.inflate(R.menu.toolbar_menu, menu)

                // Set the color of the menu icon based on the theme
                val iconColor = ContextCompat.getColor(requireContext(), R.color.toolbar_icon_color)
                menu.findItem(R.id.action_settings)?.icon?.setTint(iconColor)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_settings -> {
                        val action = NotesFragmentDirections.actionNotesFragmentToSettingsFragment()
                        findNavController().navigate(action)
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner)

        toolbar.title = "Notes"
        toolbar.setTitleTextColor(resources.getColor(R.color.toolbar_icon_color))
        recyclerView.layoutManager = LinearLayoutManager(activity)

        fabNewNote.setOnClickListener {
            createAndNavigateToNote()
        }

        fetchAndDisplayNotes()
    }

    override fun onResume() {
        super.onResume()
        fetchAndDisplayNotes() // Fetch and display notes whenever the fragment resumes
    }

    private fun fetchAndDisplayNotes() {
        // Show progress bar
        progressBar.visibility = View.VISIBLE

        // Observing LiveData from Room for note list updates
        noteDao.getAllNotes().observe(viewLifecycleOwner) { notes ->
            // Hide progress bar
            progressBar.visibility = View.GONE

            recyclerView.adapter = NoteAdapter(
                ArrayList(notes),
                onNoteClicked = { noteId ->
                    // Navigate to the notebook fragment with the selected noteId
                    navigateToNotebookFragmentWithNoteId(noteId)
                },
                onNoteDeleted = { noteId ->
                    // Delete the note and refresh the list
                    deleteNoteById(noteId)
                }
            )
        }
    }

    private fun deleteNoteById(noteId: UUID) {
        // Show a confirmation dialog before deleting
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Note")
            .setMessage("Are you sure you want to delete this note?")
            .setPositiveButton("Delete") { dialog, which ->
                lifecycleScope.launch {
                    noteDao.deleteNoteById(noteId) // Assuming you have this method in your DAO
                    // No need to manually refresh the list here if using LiveData, as the observer will react to data changes
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun navigateToNotebookFragmentWithNoteId(noteId: UUID) {
        val action = NotesFragmentDirections.actionNotesFragmentToNotebookFragment(noteId.toString())
        findNavController().navigate(action)
    }

    private fun createAndNavigateToNote() {
        val newNote = Note()
        lifecycleScope.launch {
            noteDao.insert(newNote)
            val action = NotesFragmentDirections.actionNotesFragmentToNotebookFragment(newNote.id.toString())
            findNavController().navigate(action)
        }
    }

}

class NoteAdapter(
    private val notes: ArrayList<Note>,
    private val onNoteClicked: (UUID) -> Unit,
    private val onNoteDeleted: (UUID) -> Unit
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    class NoteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val noteNameTextView: TextView = view.findViewById(R.id.noteNameTextView)
        val noteContentTextView: TextView = view.findViewById(R.id.noteContentTextView)
        val noteDateTextView: TextView = view.findViewById(R.id.noteDateTextView)
        val kebabMenuImageView: ImageView = view.findViewById(R.id.kebabMenu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = notes[position]
        holder.noteNameTextView.text = note.title ?: "Untitled"

        // Check if the note content is null. If so, treat it as an empty string to avoid NullPointerException.
        val content = note.content ?: ""
        val plainTextContent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY).toString()
        } else {
            Html.fromHtml(content).toString()
        }
        // Display a snippet of the content. If the content length exceeds 100 characters, append an ellipsis.
        holder.noteContentTextView.text = plainTextContent.take(100) + if (plainTextContent.length > 100) "…" else ""

        // Format and display the date.
        holder.noteDateTextView.text = formatDate(note.timestamp)

        // Handle note item clicks.
        holder.itemView.setOnClickListener {
            onNoteClicked(note.id)
        }
        // Set the color of the menu icon based on the theme
        val iconColor = ContextCompat.getColor(holder.itemView.context, R.color.toolbar_icon_color)
        holder.kebabMenuImageView.setColorFilter(iconColor)

        // Kebab menu click listener
        holder.kebabMenuImageView.setOnClickListener { view ->
            showPopupMenu(view, note.id, onNoteDeleted)
        }

    }

    private fun showPopupMenu(view: View, noteId: UUID, onNoteDeleted: (UUID) -> Unit) {
        val popup = PopupMenu(view.context, view)
        popup.menuInflater.inflate(R.menu.note_actions_menu, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.delete_note -> {
                    onNoteDeleted(noteId)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    override fun getItemCount() = notes.size

    private fun formatDate(timestamp: Long): String {
        val formatter = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }
}

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    var title: String? = null,
    var content: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note)

    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun getAllNotes(): LiveData<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: UUID): Note?

    @Update
    suspend fun update(note: Note)

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNoteById(noteId: UUID)

    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    suspend fun getAllNotesList(): List<Note>

    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun getAllNotesLive(): LiveData<List<Note>>

}

@Database(entities = [Note::class], version = 1)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: NotesDatabase? = null

        fun getDatabase(context: Context): NotesDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NotesDatabase::class.java,
                    "notes_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}