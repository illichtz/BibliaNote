package com.a101apps.biblianote.notebook

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.a101apps.biblianote.R
import jp.wasabeef.richeditor.RichEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class NoteBookFragment : Fragment() {

    private lateinit var toolbar: Toolbar
    private lateinit var editor: RichEditor
    private var noteId: String? = null
    private lateinit var noteTitleEditText: EditText
    private val noteViewModel: NoteViewModel by viewModels {
        NoteViewModelFactory(NotesDatabase.getDatabase(requireContext()).noteDao(), requireActivity().application)
    }
    val vifupishoVyaVitabuVyaBiblia = mapOf(
        "Mwa" to "Mwanzo",
        "Kut" to "Kutoka",
        "Mam" to "Mambo ya Walawi",
        "Hes" to "Hesabu",
        "Kum" to "Kumbukumbu la Torati",
        "Yos" to "Yoshua",
        "Amu" to "Waamuzi",
        "Rut" to "Ruthu",
        "1Sam" to "Samweli wa Kwanza",
        "2Sam" to "Samweli wa Pili",
        "1Fal" to "Wafalme wa Kwanza",
        "2Fal" to "Wafalme wa Pili",
        "1Nya" to "Nyakati wa Kwanza",
        "2Nya" to "Nyakati wa Pili",
        "Ezr" to "Ezra",
        "Neh" to "Nehemia",
        "Est" to "Esta",
        "Ayu" to "Ayubu",
        "Zab" to "Zaburi",
        "Mit" to "Mithali",
        "Mhu" to "Mhubiri",
        "Wim" to "Wimbo Ulio Bora",
        "Isa" to "Isaya",
        "Yer" to "Yeremia",
        "Maa" to "Maombolezo",
        "Eze" to "Ezekieli",
        "Dan" to "Danieli",
        "Hos" to "Hosea",
        "Yoe" to "Yoeli",
        "Amo" to "Amosi",
        "Oba" to "Obadia",
        "Yon" to "Yona",
        "Mik" to "Mika",
        "Nah" to "Nahumu",
        "Hab" to "Habakuki",
        "Sef" to "Sefania",
        "Hag" to "Hagai",
        "Zek" to "Zekaria",
        "Mal" to "Malaki",
        "Mt" to "Mathayo",
        "Mk" to "Marko",
        "Lk" to "Luka",
        "Yn" to "Yohana",
        "Mdo" to "Matendo",
        "Rom" to "Waroma",
        "1Kor" to "Wakorintho wa Kwanza",
        "2Kor" to "Wakorintho wa Pili",
        "Gal" to "Wagalatia",
        "Efe" to "Waefeso",
        "Flp" to "Wafilipi",
        "Kol" to "Wakolosai",
        "1The" to "Wathesalonike wa Kwanza",
        "2The" to "Wathesalonike wa Pili",
        "1Tim" to "Timotheo wa Kwanza",
        "2Tim" to "Timotheo wa Pili",
        "Tit" to "Tito",
        "Flm" to "Filemoni",
        "Ebr" to "Waebrania",
        "Yak" to "Yakobo",
        "1Pet" to "Petro wa Kwanza",
        "2Pet" to "Petro wa Pili",
        "1Yoh" to "Yohana wa Kwanza",
        "2Yoh" to "Yohana wa Pili",
        "3Yoh" to "Yohana wa Tatu",
        "Yud" to "Yuda",
        "Ufu" to "Ufunuo"
    )
    private var isBoldActive = false
    private var isItalicActive = false
    private var isUnderlineActive = false
    private var isStrikethroughActive = false
    private var isBulletListActive = false
    private var isNumberedListActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Retrieve the note ID from the arguments
        arguments?.let {
            val safeArgs = NoteBookFragmentArgs.fromBundle(it)
            noteId = safeArgs.noteId
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_note_book, container, false)

        // Initialize the components
        initializeEditor(view)
        setupToolbar(view)
        setupMenuProvider(view)
        setupBackPressedHandler()
        initializeNoteTitleEditText(view)
        setupSemicolonButton(view)
        setupToolButtons(view)
        return view
    }

    private fun initializeEditor(view: View) {
        editor = view.findViewById(R.id.editor)
        editor.setEditorHeight(200)
        editor.setEditorFontSize(20)
        editor.setPadding(5, 5, 5, 5)
    }

    private fun setupMenuProvider(view: View) {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.clear() // Clear the menu items
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return false // No menu item handling needed
            }
        }, viewLifecycleOwner)
    }

    private fun setupBackPressedHandler() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Handle the back button event and tell Navigation to navigate up
                findNavController().navigateUp()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    private fun initializeNoteTitleEditText(view: View) {
        noteTitleEditText = view.findViewById(R.id.editTextNoteTitle)
        noteTitleEditText.setText("Note Title") // Ideally, this should be loaded from your note content

        noteTitleEditText.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus && noteTitleEditText.text.toString() == "Enter note title here...") {
                noteTitleEditText.setText("")
            } else if (!hasFocus) {
                if (noteTitleEditText.text.toString().isEmpty()) {
                    noteTitleEditText.setText("Title ...")
                }
                // Code to hide keyboard and potentially save the title if changed
                val imm =
                    context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }

        noteTitleEditText.setText("Enter note title here...")
        noteTitleEditText.clearFocus()
    }

    private fun setupSemicolonButton(view: View) {
        val btnSemicolon: Button = view.findViewById(R.id.btnSemicolon)
        btnSemicolon.setOnClickListener {
            // JavaScript to insert a colon at the current cursor position
            val script = "document.execCommand('insertText', false, ':');"
            editor.evaluateJavascript(script, null)
        }
    }

    private fun adjustEditorBackground() {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        when (nightModeFlags) {
            Configuration.UI_MODE_NIGHT_YES -> editor.setBackgroundColor(Color.BLACK) // Dark background for dark mode
            Configuration.UI_MODE_NIGHT_NO -> editor.setBackgroundColor(Color.WHITE) // Light background for light mode
            Configuration.UI_MODE_NIGHT_UNDEFINED -> editor.setBackgroundColor(Color.WHITE) // Default to light background
        }
    }

    fun updateEditTextTheme(editText: EditText) {
        val nightModeFlags = editText.context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK
        when (nightModeFlags) {
            Configuration.UI_MODE_NIGHT_YES -> editText.setTextColor(Color.WHITE)
            Configuration.UI_MODE_NIGHT_NO -> editText.setTextColor(Color.BLACK)
            Configuration.UI_MODE_NIGHT_UNDEFINED -> editText.setTextColor(Color.BLACK)
        }
    }

    private fun setupToolbar(view: View) {
        toolbar = view.findViewById(R.id.toolbar)
        (activity as? AppCompatActivity)?.let { activity ->
            activity.setSupportActionBar(toolbar)
            activity.supportActionBar?.apply {
                title = "Notepad"
                setDisplayHomeAsUpEnabled(true)
                setDisplayShowHomeEnabled(true)
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

    private fun setupToolButtons(view: View) {
        val btnBold: Button = view.findViewById(R.id.btnBold)
        val btnItalic: Button = view.findViewById(R.id.btnItalic)
        val btnUnderline: Button = view.findViewById(R.id.btnUnderline)
        val btnNumberedList: Button = view.findViewById(R.id.btnNumberedList)
        val btnBulletList: Button = view.findViewById(R.id.btnBulletList)
        val btnStrikethrough: Button = view.findViewById(R.id.btnStrikethrough)

        // Function to update button state
        fun updateButtonState(button: Button, isActive: Boolean) {
            val backgroundResource = if (isActive) R.drawable.button_active else R.drawable.button_inactive
            val textColorResource = if (isActive) R.color.button_active_text_color else R.color.button_inactive_text_color

            button.setBackgroundResource(backgroundResource)
            button.setTextColor(ContextCompat.getColor(button.context, textColorResource))
        }

        btnBold.setOnClickListener {
            isBoldActive = !isBoldActive
            editor.setBold()
            updateButtonState(btnBold, isBoldActive)
        }
        btnItalic.setOnClickListener {
            isItalicActive = !isItalicActive
            editor.setItalic()
            updateButtonState(btnItalic, isItalicActive)
        }
        btnUnderline.setOnClickListener {
            isUnderlineActive = !isUnderlineActive
            editor.setUnderline()
            updateButtonState(btnUnderline, isUnderlineActive)
        }
        btnNumberedList.setOnClickListener {
            isNumberedListActive = !isNumberedListActive
            editor.setNumbers()
            updateButtonState(btnNumberedList, isNumberedListActive)
        }
        btnBulletList.setOnClickListener {
            isBulletListActive = !isBulletListActive
            editor.setBullets()
            updateButtonState(btnBulletList, isBulletListActive)
        }
        btnStrikethrough.setOnClickListener {
            isStrikethroughActive = !isStrikethroughActive
            editor.setStrikeThrough()
            updateButtonState(btnStrikethrough, isStrikethroughActive)
        }
    }

    override fun onStop() {
        super.onStop()
        val title = noteTitleEditText.text.toString()
        val content = editor.html
        noteId?.let {
            noteViewModel.enqueueNoteSave(it, title, content)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        noteId?.let {
            loadNoteDetails(UUID.fromString(it))
        }
        noteTitleEditText = view.findViewById(R.id.editTextNoteTitle)
        updateEditTextTheme(noteTitleEditText)

    }

    private fun loadNoteDetails(noteId: UUID) {
        lifecycleScope.launch {
            try {
                val note = noteDao.getNoteById(noteId)
                if (note != null) {
                    Log.d("NoteBookFragment", "Note loaded successfully: Title: ${note.title}")
                    updateUI(note)
                } else {
                    Log.e("NoteBookFragment", "No note found with ID: $noteId")
                }
            } catch (e: Exception) {
                Log.e("NoteBookFragment", "Error loading note: ${e.message}", e)
            }
        }
    }

    private val noteDao: NoteDao by lazy {
        NotesDatabase.getDatabase(requireContext()).noteDao()
    }

    private fun updateUI(note: Note) {
        requireActivity().runOnUiThread {
            noteTitleEditText.setText(note.title)
            editor.html = note.content
        }
    }

}

class NoteViewModel(private val noteDao: NoteDao, private val application: Application) : ViewModel() {

    fun enqueueNoteSave(noteId: String, title: String, content: String) {
        val inputData = workDataOf(
            "noteId" to noteId,
            "title" to title,
            "content" to content
        )
        val saveNoteWorkRequest = OneTimeWorkRequestBuilder<SaveNoteWorker>()
            .setInputData(inputData)
            .build()
        WorkManager.getInstance(application).enqueue(saveNoteWorkRequest)
    }

}

class NoteViewModelFactory(private val noteDao: NoteDao, private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NoteViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NoteViewModel(noteDao, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class SaveNoteWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val noteId = inputData.getString("noteId")
            val title = inputData.getString("title")
            val content = inputData.getString("content")
            if (noteId != null && title != null && content != null) {
                val noteDao = NotesDatabase.getDatabase(applicationContext).noteDao()
                val uuid = UUID.fromString(noteId)
                val note = noteDao.getNoteById(uuid) ?: Note(id = uuid, title = title, content = content)
                note.title = title // Update the title
                note.content = content
                noteDao.update(note)
                Result.success()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }
}