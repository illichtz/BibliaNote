package com.a101apps.biblianote.biblia

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.a101apps.biblianote.database.BibleRepository
import com.a101apps.biblianote.database.ChaptersAdapter
import com.a101apps.biblianote.database.ChaptersViewModel
import com.a101apps.biblianote.database.ChaptersViewModelFactory
import com.a101apps.biblianote.R

class ChaptersFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private var adapter: ChaptersAdapter? = null
    private lateinit var toolbar: Toolbar
    private var bookNumber: Int = 0
    private var bookName: String = ""
    private lateinit var progressBar: ProgressBar
    private val viewModel: ChaptersViewModel by viewModels {
        ChaptersViewModelFactory(BibleRepository.getInstance(requireContext()))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            bookNumber = it.getInt("bookNumber")
            bookName = it.getString("bookName", "Default Book Name")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_chapters, container, false)
        progressBar = view.findViewById(R.id.progress_bar)
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

        recyclerView = view.findViewById(R.id.chapters_recycler_view)
        recyclerView.layoutManager = GridLayoutManager(context, 5) // 5 columns

        if (adapter == null) {
            observeChapters()
        } else {
            recyclerView.adapter = adapter
            progressBar.visibility = View.GONE
        }
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Handle the back button event and tell Navigation to navigate up
                findNavController().navigateUp()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

        return view
    }

    private fun setupToolbar(view: View) {
        toolbar = view.findViewById(R.id.toolbar_chapters)
        (activity as? AppCompatActivity)?.let { activity ->
            activity.setSupportActionBar(toolbar)
            activity.supportActionBar?.apply {
                title = bookName
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

    private fun observeChapters() {
        progressBar.visibility = View.VISIBLE
        viewModel.getChapters(bookNumber).observe(viewLifecycleOwner) { chapters ->
            if (chapters.isNotEmpty() && adapter == null) {
                adapter = ChaptersAdapter(chapters) { chapter ->
                    val action = ChaptersFragmentDirections.actionChaptersFragmentToVerseFragment(bookNumber, chapter.chapterNumber, bookName)
                    findNavController().navigate(action)
                }
                recyclerView.adapter = adapter
            }
            progressBar.visibility = View.GONE
        }
    }

}



