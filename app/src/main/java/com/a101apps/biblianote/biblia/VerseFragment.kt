package com.a101apps.biblianote.biblia

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.a101apps.biblianote.database.BibleRepository
import com.a101apps.biblianote.R
import com.a101apps.biblianote.database.Verse
import com.a101apps.biblianote.database.VerseAdapter
import com.a101apps.biblianote.database.VerseViewModel
import com.a101apps.biblianote.database.VerseViewModelFactory

class VerseFragment : Fragment() {
    private lateinit var verses: List<Verse>

    private var verseNumberToHighlight: Int = -1
    private lateinit var recyclerView: RecyclerView
    private lateinit var toolbar: Toolbar
    private lateinit var progressBar: ProgressBar
    private var bookNumber: Int = 0
    private var chapterNumber: Int = 0
    private var bookName: String = ""
    private val viewModel: VerseViewModel by viewModels {
        VerseViewModelFactory(BibleRepository.getInstance(requireContext()))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            bookNumber = it.getInt("bookNumber")
            chapterNumber = it.getInt("chapterNumber")
            bookName = it.getString("bookName", "Default Book Name")
            verseNumberToHighlight = it.getInt("verseNumber", -1) // Retrieve the verse number
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_verse, container, false)

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.clear() // Clear the menu items
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return false // No menu item handling needed
            }
        }, viewLifecycleOwner)

        progressBar = view.findViewById(R.id.progress_bar)
        setupToolbar(view, "$bookName $chapterNumber")

        recyclerView = view.findViewById(R.id.verses_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)

        observeVerses()

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Handle the back button event and tell Navigation to navigate up
                findNavController().navigateUp()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

        return view
    }

    private fun setupToolbar(view: View, title: String) {
        toolbar = view.findViewById(R.id.toolbar_verses)
        (activity as? AppCompatActivity)?.let { activity ->
            activity.setSupportActionBar(toolbar)
            activity.supportActionBar?.apply {
                setTitle(title)
                setDisplayHomeAsUpEnabled(true)
                setDisplayShowHomeEnabled(true)
            }

            // Set title color to white
            toolbar.setTitleTextColor(ContextCompat.getColor(activity, R.color.toolbar_text_color))

            // Set navigation icon (back button) to white using DrawableCompat
            val upArrow = ContextCompat.getDrawable(activity, R.drawable.ic_back)
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


    private fun observeVerses() {
        progressBar.visibility = View.VISIBLE
        viewModel.getVerses(bookNumber, chapterNumber).observe(viewLifecycleOwner) { versesData ->
            verses = versesData // Store the verses in the member variable
            updateUI(versesData)
        }
    }

    private fun updateUI(verses: List<Verse>) {
        val adapter = VerseAdapter(verses)
        recyclerView.adapter = adapter
        progressBar.visibility = View.GONE

        if (verseNumberToHighlight != -1) {
            scrollToVerse(verseNumberToHighlight)
        }
    }

    private fun scrollToVerse(verseNumber: Int) {
        val position = verses.indexOfFirst { it.verseNumber == verseNumber }
        if (position != -1) {
            recyclerView.scrollToPosition(position)
            highlightVerse(position)
        }
    }

    private fun highlightVerse(position: Int) {
        recyclerView.post {
            val viewHolder = recyclerView.findViewHolderForAdapterPosition(position) as? VerseAdapter.VerseViewHolder
            viewHolder?.let { vh ->
                val highlightColor = Color.YELLOW // The highlight color
                val originalColor = vh.itemView.background // Save the original background

                // Create a ValueAnimator to animate the background color
                val colorAnimation = ValueAnimator.ofArgb(highlightColor, Color.TRANSPARENT).apply {
                    duration = 500 // Duration in milliseconds
                    addUpdateListener { animator ->
                        vh.itemView.setBackgroundColor(animator.animatedValue as Int)
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            // Reset to original background after animation ends
                            vh.itemView.background = originalColor
                        }
                    })
                    start()
                }
            }
        }
    }

}

