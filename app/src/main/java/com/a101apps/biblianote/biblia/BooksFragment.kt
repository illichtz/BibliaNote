package com.a101apps.biblianote.biblia

import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.a101apps.biblianote.R
import com.a101apps.biblianote.database.BibleData
import com.a101apps.biblianote.database.BibleRepository
import com.a101apps.biblianote.database.Book
import com.a101apps.biblianote.database.BooksAdapter
import com.a101apps.biblianote.database.BooksViewModel
import com.a101apps.biblianote.database.BooksViewModelFactory
import com.a101apps.biblianote.database.SearchAdapter
import com.a101apps.biblianote.database.SearchItem
import com.a101apps.biblianote.database.Verse
import com.a101apps.biblianote.database.VerseDisplay
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class BooksFragment : Fragment() {

    private lateinit var searchView: SearchView
    private var adapter: BooksAdapter? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var searchRecyclerView: RecyclerView
    private lateinit var searchAdapter: SearchAdapter
    private val viewModel: BooksViewModel by viewModels {
        BooksViewModelFactory(BibleRepository.getInstance(requireContext()))
    }
    private lateinit var toolbar: Toolbar
    private lateinit var searchMenuItem: MenuItem
    private val compositeDisposable = CompositeDisposable()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_books, container, false)
        recyclerView = view.findViewById(R.id.books_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)
        progressBar = view.findViewById(R.id.progress_bar)
        searchRecyclerView = view.findViewById(R.id.search_results_recycler_view)
        searchRecyclerView.layoutManager = LinearLayoutManager(context)

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.clear() // Clear the previous menu items

                menuInflater.inflate(R.menu.menu_toolbar, menu)
                // Set color for the icons
                val iconColor = ContextCompat.getColor(requireContext(), R.color.toolbar_icon_color)
                menu.findItem(R.id.action_language)?.icon?.setTint(iconColor)

                // Set color for the search icon
                val searchMenuItem = menu.findItem(R.id.action_search)
                searchMenuItem.icon?.setTint(iconColor)

                searchView = searchMenuItem.actionView as SearchView
                val searchManager = context?.getSystemService(Context.SEARCH_SERVICE) as SearchManager
                searchView.setSearchableInfo(searchManager.getSearchableInfo(activity?.componentName))

                // Set color for search view components
                val searchIcon = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon)
                searchIcon.setColorFilter(iconColor)

                val searchCloseIcon = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
                searchCloseIcon.setColorFilter(iconColor)

                val searchText = searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
                searchText.setTextColor(iconColor)
                searchText.setHintTextColor(iconColor)

                searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        query?.let {
                            performSearch(it)
                        }
                        searchView.clearFocus()
                        return true
                    }

                    override fun onQueryTextChange(newText: String?): Boolean {
                        newText?.let {
                            performSearch(it)
                        }
                        return true
                    }
                })

                searchView.setOnCloseListener {
                    resetSearchView()
                    false
                }

                //    super.onCreateMenu(menu, inflater)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_language -> {
                        showLanguageSelectionDialog()
                        true
                    }
                    else -> false
                }
            }

        }, viewLifecycleOwner)

        toolbar = view.findViewById(R.id.toolbar)
        (activity as? AppCompatActivity)?.setSupportActionBar(toolbar)
        toolbar.title = "Biblia"
        toolbar.setTitleTextColor(resources.getColor(R.color.toolbar_icon_color))

        searchAdapter = SearchAdapter(mutableListOf(), this::navigateToVerseFragment)
        searchRecyclerView.adapter = searchAdapter
        setupRecyclerView(view)

        if (adapter == null) {
            observeBooks()
        } else {
            recyclerView.adapter = adapter
            progressBar.visibility = View.GONE
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (activity as? AppCompatActivity)?.setSupportActionBar(toolbar)
        //setHasOptionsMenu(true)
        loadSelectedDatabases()
    }

    private fun loadLanguagesFromAssets(): List<String> {
        return context?.assets?.list("")
            ?.filter { it.startsWith("bib") }
            ?.toList() ?: emptyList()
    }

    private fun showLanguageSelectionDialog() {
        Log.d("BooksFragment", "Showing language selection dialog")
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_language_selection, null)
        val recyclerView: RecyclerView = dialogView.findViewById(R.id.recycler_view_languages)

        val languages = loadLanguagesFromAssets().sorted()
        Log.d("BooksFragment", "Loaded languages: $languages")

        val bibleRepository = BibleRepository.getInstance(requireContext())
        val currentDatabaseNames = bibleRepository.getCurrentDatabaseNames()
        val mutableSavedSelections = currentDatabaseNames.toMutableSet()

        val finalLanguages = currentDatabaseNames + languages.filter { it !in currentDatabaseNames }

        val adapter = LanguageAdapter(finalLanguages, mutableSavedSelections) { language, isSelected ->
            if (isSelected) {
                mutableSavedSelections.add(language)
            } else {
                mutableSavedSelections.remove(language)
            }
            Log.d("BooksFragment", "Updated selections: $mutableSavedSelections")
        }

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Version(s)")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val selectedLanguages = mutableSavedSelections
                if (selectedLanguages.isNotEmpty()) {
                    Log.d("BooksFragment", "Selected languages: $selectedLanguages")
                    bibleRepository.updateDatabases(selectedLanguages.toList())
                    saveSelectedLanguages(selectedLanguages)
                    observeBooks() // Re-observe books to refresh the data
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }
    private fun saveSelectedLanguages(selectedLanguages: Set<String>) {
        val sharedPreferences = requireContext().getSharedPreferences("language_prefs", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putStringSet("selected_languages", selectedLanguages)
            apply()
        }
    }
    private fun loadSelectedDatabases() {
        val sharedPreferences = requireContext().getSharedPreferences("language_prefs", Context.MODE_PRIVATE)
        val selectedDatabases = sharedPreferences.getStringSet("selected_languages", setOf())
        if (!selectedDatabases.isNullOrEmpty()) {
            BibleRepository.getInstance(requireContext()).updateDatabases(selectedDatabases.toList())
          //  observeBooks() // Load data on start
        }
    }

    private fun observeBooks() {
        progressBar.visibility = View.VISIBLE
        viewModel.getBooks().observe(viewLifecycleOwner) { books ->
            if (books.isNotEmpty()) {
                val items = processBooks(books)
                adapter = BooksAdapter(items) { book ->
                    val action = BooksFragmentDirections.actionBooksFragmentToChaptersFragment(book.bookNumber, book.bookName)
                    findNavController().navigate(action)
                }
                recyclerView.adapter = adapter
            }
            progressBar.visibility = View.GONE
        }
    }

    private fun processBooks(books: List<Book>): List<BibleData> {
        val items = mutableListOf<BibleData>()
        val selectedDatabases = BibleRepository.getInstance(requireContext()).getCurrentDatabaseNames()
        val isBibliaSelected = selectedDatabases.contains("biblia")

        if (isBibliaSelected) {
            // Add Swahili Old Testament header
            items.add(BibleData.Header("Agano La Kale"))

            // Add Old Testament books
            items.addAll(books.filter { it.bookNumber <= 39 }.map { BibleData.BookItem(it) })

            // Add Swahili New Testament header
            items.add(BibleData.Header("Agano Jipya"))

            // Add New Testament books
            items.addAll(books.filter { it.bookNumber > 39 }.map { BibleData.BookItem(it) })

            // Add English Old Testament header
            // items.add(BibleData.Header("Old Testament"))

            // Add Old Testament books again (assuming we want to display them under both headers)
            // items.addAll(books.filter { it.bookNumber <= 39 }.map { BibleData.BookItem(it) })

            // Add English New Testament header
            //  items.add(BibleData.Header("New Testament"))

            // Add New Testament books again
            // items.addAll(books.filter { it.bookNumber > 39 }.map { BibleData.BookItem(it) })
        } else {
            // Add English Old Testament header
            items.add(BibleData.Header("Old Testament"))

            // Add Old Testament books
            items.addAll(books.filter { it.bookNumber <= 39 }.map { BibleData.BookItem(it) })

            // Add English New Testament header
            items.add(BibleData.Header("New Testament"))

            // Add New Testament books
            items.addAll(books.filter { it.bookNumber > 39 }.map { BibleData.BookItem(it) })
        }

        return items
    }

    private fun navigateToVerseFragment(verse: Verse) {
        val bookNumber = verse.bookNumber // Obtain the book number from the 'verse' object
        val chapterNumber = verse.chapterNumber // Obtain the chapter number from the 'verse' object
        val bookName = BibleRepository.getInstance(requireContext()).getBookNameByNumber(bookNumber)
        val verseNumber = verse.verseNumber // Obtain the verse number from the 'verse' object

        val action = BooksFragmentDirections.actionBooksFragmentToVerseFragment(
            bookNumber,
            chapterNumber,
            bookName,
            verseNumber
        )
        findNavController().navigate(action)
    }

    private fun setupRecyclerView(view: View) {
        recyclerView = view.findViewById(R.id.books_recycler_view)
        recyclerView.layoutManager = GridLayoutManager(context, 2).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    // Safe call with Elvis operator to handle the case where adapter might be null
                    return if (adapter?.isHeader(position) == true) this@apply.spanCount else 1
                }
            }
        }
    }

    private fun performSearch(query: String) {
        // Dispose of any existing searches
        compositeDisposable.clear()

        // Ensure the search only happens after a pause in typing (e.g., 300ms)
        val disposable = Observable.just(query)
            .debounce(300, TimeUnit.MILLISECONDS)
            .distinctUntilChanged()
            .filter { it.isNotEmpty() }
            .switchMap { searchQuery ->
                Observable.fromCallable {
                    val limit = 50
                    var offset = 0
                    val verses = mutableListOf<Verse>()
                    var result: List<Verse>

                    do {
                        result = BibleRepository.getInstance(requireContext()).searchVerses(searchQuery, limit, offset)
                        verses.addAll(result)
                        offset += limit
                    } while (result.size == limit)

                    prepareVerseItems(verses)
                }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
            }
            .subscribe({ preparedItems ->
                val items = mutableListOf<SearchItem>()
                items.add(SearchItem.ResultCount(preparedItems.size))
                items.addAll(preparedItems)

                searchAdapter = SearchAdapter(items, this@BooksFragment::navigateToVerseFragment)
                searchAdapter.setCurrentQuery(query)
                searchRecyclerView.adapter = searchAdapter
                progressBar.visibility = View.GONE
            }, { throwable ->
                // Handle error gracefully
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Search failed due to insufficient memory.", Toast.LENGTH_LONG).show()
            })

        // Add the disposable to the compositeDisposable
        compositeDisposable.add(disposable)

        // Show progress bar while searching
        progressBar.visibility = View.VISIBLE
        toggleRecyclerViewVisibility(true)
    }

    // Don't forget to clear disposables when the fragment is destroyed
    override fun onDestroyView() {
        super.onDestroyView()
        compositeDisposable.clear()
    }

    private fun toggleRecyclerViewVisibility(isSearchActive: Boolean) {
        if (isSearchActive) {
            recyclerView.visibility = View.GONE
            searchRecyclerView.visibility = View.VISIBLE
            Log.d("BooksFragment", "Search is active, showing searchRecyclerView")
        } else {
            recyclerView.visibility = View.VISIBLE
            searchRecyclerView.visibility = View.GONE
            Log.d("BooksFragment", "Search is not active, showing main recyclerView")
        }
    }

    private fun resetSearchView() {
        toggleRecyclerViewVisibility(false)
        searchAdapter.setCurrentQuery("") // Clear the current query
        progressBar.visibility = View.GONE // Hide progress bar if visible
    }

    private fun prepareVerseItems(verses: List<Verse>): List<SearchItem.VerseItem> {
        val books = BibleRepository.getInstance(requireContext()).getBooks().associateBy { it.bookNumber }
        return verses.map { verse ->
            val bookName = books[verse.bookNumber]?.bookName ?: "Unknown"
            SearchItem.VerseItem(VerseDisplay(verse, bookName))
        }
    }

}

class LanguageAdapter(
    private val languages: List<String>,
    private val selectedLanguages: MutableSet<String>,
    private val onLanguageSelected: (String, Boolean) -> Unit
) : RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder>() {

    private val longFormMap = mapOf(
        "bible-asv" to "American Standard Version",
        "bible-bbe" to "Bible in Basic English",
        "bible-kjv" to "King James Version",
        "bible-web" to "World English Bible",
        "bible-ylt" to "Young's Literal Translation",
        "biblia" to "Biblia (Swahili)",
        "bible-esv" to "English Standard Version",
        "bible-niv" to "New International Version",
        "bible-amp" to "Amplified Bible",
        "bible-nasb" to "New American Standard Bible"
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_language, parent, false)
        return LanguageViewHolder(view)
    }

    override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
        val language = languages[position]
        holder.bind(language, selectedLanguages.contains(language), position + 1)
    }

    override fun getItemCount(): Int = languages.size

    inner class LanguageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkBox: CheckBox = itemView.findViewById(R.id.checkbox_language)
        private val textViewSubtitle: TextView = itemView.findViewById(R.id.textview_subtitle)
        private val textViewNumber: TextView = itemView.findViewById(R.id.textview_number)

        fun bind(language: String, isSelected: Boolean, position: Int) {
            checkBox.text = language
            checkBox.isChecked = isSelected
            textViewSubtitle.text = longFormMap[language] ?: "Unknown"
            textViewNumber.text = position.toString()

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                onLanguageSelected(language, isChecked)
            }
        }
    }
}