package com.a101apps.biblianote.database

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.method.ScrollingMovementMethod
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.RecyclerView
import com.a101apps.biblianote.R
import kotlinx.coroutines.Dispatchers
import java.util.regex.Pattern

sealed class BibleData {
    data class BookItem(val book: Book) : BibleData()
    data class Header(val title: String) : BibleData()
}
class BooksAdapter(private val items: List<BibleData>, private val onClick: (Book) -> Unit) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    fun isHeader(position: Int): Boolean {
        return items[position] is BibleData.Header
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_BOOK = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is BibleData.Header -> TYPE_HEADER
            is BibleData.BookItem -> TYPE_BOOK
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_header, parent, false))
            else -> BookViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is BibleData.Header -> (holder as HeaderViewHolder).bind(item)
            is BibleData.BookItem -> (holder as BookViewHolder).bind(item, onClick)
        }
    }

    override fun getItemCount() = items.size

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleTextView: TextView = view.findViewById(R.id.header_title)

        fun bind(header: BibleData.Header) {
            titleTextView.text = header.title
            // Adjust text color based on theme
            titleTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_color_primary))
        }

    }

    class BookViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val bookNameTextView: TextView = view.findViewById(R.id.book_name)
        private val cardView: CardView = view as CardView  // Cast the itemView to CardView

        fun bind(bookItem: BibleData.BookItem, onClick: (Book) -> Unit) {
            val book = bookItem.book
            bookNameTextView.text = book.bookName
            itemView.setOnClickListener { onClick(book) }

            // Use ContextCompat to fetch theme-appropriate colors
            val context = itemView.context
            cardView.setCardBackgroundColor(
                ContextCompat.getColor(
                    context,
                    if (isOldTestament(book)) R.color.old_testament_background else R.color.new_testament_background
                )
            )
            bookNameTextView.setTextColor(ContextCompat.getColor(context, R.color.text_color_primary))
        }

        private fun isOldTestament(book: Book): Boolean {
            // Assuming the first 39 books are in the Old Testament
            return book.bookNumber <= 39
        }
    }

}

class ChaptersAdapter(private val chapters: List<Chapter>, private val onClick: (Chapter) -> Unit) :
    RecyclerView.Adapter<ChaptersAdapter.ChapterViewHolder>() {

    class ChapterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val chapterName: TextView = view.findViewById(R.id.book_name) // Adjusted ID
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false)
        return ChapterViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChapterViewHolder, position: Int) {
        val chapter = chapters[position]
        holder.chapterName.text = chapter.chapterNumber.toString() // Display only the chapter number
        holder.itemView.setOnClickListener { onClick(chapter) }
    }

    override fun getItemCount() = chapters.size
}

class VerseAdapter(private val verses: List<Verse>) :
    RecyclerView.Adapter<VerseAdapter.VerseViewHolder>() {

    private var highlightedPosition: Int = -1

    class VerseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val verseNumber: TextView = view.findViewById(R.id.verse_number)
        val verseText: TextView = view.findViewById(R.id.verse_text)

        // Add a method to highlight the verse
        fun highlight() {
            verseText.setBackgroundColor(Color.YELLOW) // Change to your desired highlighting color
        }

        // Add a method to clear highlighting
        fun clearHighlight() {
            verseText.setBackgroundColor(Color.TRANSPARENT) // Clear highlighting
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_verse, parent, false)
        val viewHolder = VerseViewHolder(view)

        // Set ScrollingMovementMethod for verseText
        viewHolder.verseText.movementMethod = ScrollingMovementMethod()

        // Set an OnTouchListener that returns false
        viewHolder.verseText.setOnTouchListener { v, event -> false }

        return viewHolder
    }

    override fun onBindViewHolder(holder: VerseViewHolder, position: Int) {
        val verse = verses[position]
        holder.verseNumber.text = "${verse.verseNumber}. " // Full stop added after verse number
        holder.verseText.text = verse.verseText

        // Check if this position should be highlighted
        if (position == highlightedPosition) {
            holder.highlight() // Highlight this verse
        } else {
            holder.clearHighlight() // Clear highlighting for other verses
        }
    }

    override fun getItemCount() = verses.size

    // Add a method to highlight a specific verse
    fun highlightVerse(position: Int) {
        val previousHighlightedPosition = highlightedPosition
        highlightedPosition = position
        if (previousHighlightedPosition != -1) {
            notifyItemChanged(previousHighlightedPosition) // Clear previous highlight
        }
        if (position != -1) {
            notifyItemChanged(position) // Highlight the new verse
        }
    }
}


class BooksViewModel(private val repository: BibleRepository) : ViewModel() {
    fun getBooks() = liveData(Dispatchers.IO) {
        emit(repository.getBooks())
    }
}

class BooksViewModelFactory(private val repository: BibleRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BooksViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BooksViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ChaptersViewModel(private val repository: BibleRepository) : ViewModel() {
    private var chaptersLiveData: LiveData<List<Chapter>>? = null

    fun getChapters(bookNumber: Int): LiveData<List<Chapter>> {
        if (chaptersLiveData == null) {
            chaptersLiveData = liveData(Dispatchers.IO) {
                emit(repository.getChapters(bookNumber))
            }
        }
        return chaptersLiveData as LiveData<List<Chapter>>
    }
}

class ChaptersViewModelFactory(private val repository: BibleRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChaptersViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChaptersViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class VerseViewModel(private val repository: BibleRepository) : ViewModel() {

    fun getVerses(bookNumber: Int, chapterNumber: Int) = liveData(viewModelScope.coroutineContext + Dispatchers.IO) {
        val verses = repository.getVerses(bookNumber, chapterNumber)
        emit(verses)
    }
}

class VerseViewModelFactory(private val repository: BibleRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VerseViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VerseViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


class SearchAdapter(
    private val items: MutableList<SearchItem>,
    private val onVerseClicked: (Verse) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), Filterable {

    private var currentQuery: String = ""

    fun setCurrentQuery(query: String) {
        currentQuery = query
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is SearchItem.VerseItem -> TYPE_VERSE
            is SearchItem.ResultCount -> TYPE_COUNT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_VERSE -> VerseViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_searchverse, parent, false), onVerseClicked)
            TYPE_COUNT -> CountViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_searchcount, parent, false))
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is SearchItem.VerseItem -> (holder as VerseViewHolder).bind(item.verseDisplay, currentQuery)
            is SearchItem.ResultCount -> (holder as CountViewHolder).bind(item.count)
        }
    }

    override fun getItemCount() = items.size

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): Filter.FilterResults {
                val charString = constraint.toString()
                currentQuery = charString

                val filteredList = if (charString.isEmpty()) {
                    listOf(SearchItem.ResultCount(0))
                } else {
                    val filteredVerseDisplays = items.filterIsInstance<SearchItem.VerseItem>().filter {
                        it.verseDisplay.verse.verseText.contains(charString, ignoreCase = true)
                    }
                    listOf(SearchItem.ResultCount(filteredVerseDisplays.size)) + filteredVerseDisplays
                }

                return Filter.FilterResults().apply { values = filteredList }
            }

            override fun publishResults(constraint: CharSequence?, results: Filter.FilterResults?) {
                items.clear()
                items.addAll(results?.values as List<SearchItem>)
                notifyDataSetChanged()
            }
        }
    }

    companion object {
        private const val TYPE_VERSE = 0
        private const val TYPE_COUNT = 1
    }

    class VerseViewHolder(view: View, private val onVerseClicked: (Verse) -> Unit) : RecyclerView.ViewHolder(view) {
        private val tvLocation: TextView = view.findViewById(R.id.tvLocation)
        private val tvVerseText: TextView = view.findViewById(R.id.tvVerseText)

        fun bind(verseDisplay: VerseDisplay, query: String) {
            tvLocation.text = "${verseDisplay.bookName} ${verseDisplay.verse.chapterNumber}:${verseDisplay.verse.verseNumber}"

            val context = itemView.context
            // Setting text colors for compatibility with dark mode
            tvLocation.setTextColor(context.getColor(R.color.text_primary))
            tvVerseText.setTextColor(context.getColor(R.color.text_primary))

            // Highlighting logic
            val spannable = SpannableString(verseDisplay.verse.verseText)
            if (query.isNotEmpty()) {
                val pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE)
                val matcher = pattern.matcher(verseDisplay.verse.verseText)
                val highlightColor = context.resources.getColor(R.color.highlight_color, null) // Use theme-adaptive color

                while (matcher.find()) {
                    spannable.setSpan(BackgroundColorSpan(highlightColor), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            tvVerseText.text = spannable

            // Ensure the itemView's click listener is properly set
            itemView.setOnClickListener { onVerseClicked(verseDisplay.verse) }
        }
    }

    class CountViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvCount: TextView = view.findViewById(R.id.tvCount)

        fun bind(count: Int) {
            tvCount.text = "$count matches found"
        }
    }
}

sealed class SearchItem {
    data class VerseItem(val verseDisplay: VerseDisplay) : SearchItem()
    data class ResultCount(val count: Int) : SearchItem()
}

data class VerseDisplay(
    val verse: Verse,
    val bookName: String
)
