package com.a101apps.biblianote.database

import android.content.Context
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import java.util.concurrent.CopyOnWriteArrayList

@Entity
data class Book(
    @PrimaryKey val bookNumber: Int,
    val bookName: String
)

@Entity(
    foreignKeys = [ForeignKey(
        entity = Book::class,
        parentColumns = ["bookNumber"],
        childColumns = ["bookNumber"],
        onDelete = ForeignKey.CASCADE
    )],
    primaryKeys = ["bookNumber", "chapterNumber"]
)
data class Chapter(
    val chapterNumber: Int,
    val bookNumber: Int
)


@Entity(
    foreignKeys = [ForeignKey(
        entity = Chapter::class,
        parentColumns = ["bookNumber", "chapterNumber"],
        childColumns = ["bookNumber", "chapterNumber"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Verse(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val verseNumber: Int,
    val chapterNumber: Int,
    val bookNumber: Int,
    val verseText: String
)

@Dao
interface BibleDao {

    @Query("SELECT * FROM book")
    fun getAllBooks(): List<Book>

    @Query("SELECT * FROM chapter WHERE bookNumber = :bookNumber")
    fun getChapters(bookNumber: Int): List<Chapter>

    @Query("SELECT * FROM verse WHERE bookNumber = :bookNumber AND chapterNumber = :chapterNumber")
    fun getVerses(bookNumber: Int, chapterNumber: Int): List<Verse>

    // Add this method to search verses
    @Query("SELECT * FROM verse WHERE verseText LIKE :query LIMIT :limit OFFSET :offset")
    fun searchVerses(query: String, limit: Int, offset: Int): List<Verse>

    // Update your DAO to fetch only necessary fields
    @Query("SELECT id, verseNumber, chapterNumber, bookNumber, verseText FROM verse")
    fun getAllVerses(): List<Verse>
}

@Database(entities = [Book::class, Chapter::class, Verse::class], version = 1)
abstract class BibleDatabase : RoomDatabase() {
    abstract fun bibleDao(): BibleDao

    companion object {
        @Volatile
        private var instances: MutableMap<String, BibleDatabase> = mutableMapOf()

        fun getDatabase(context: Context, dbName: String): BibleDatabase {
            return instances[dbName] ?: synchronized(this) {
                Log.d("BibleDatabase", "Creating database instance for: $dbName")
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BibleDatabase::class.java,
                    dbName // The database name
                )
                    .createFromAsset("$dbName")
                    .build()
                instances[dbName] = instance
                instance
            }
        }

        fun closeDatabase() {
            instances.values.forEach { it.close() }
            instances.clear()
        }
    }
}

class BibleRepository private constructor(private val context: Context) {
    private val booksCache = mutableSetOf<Book>()
    private val chaptersCache = mutableMapOf<Int, List<Chapter>>()
    private val versesCache = mutableMapOf<Pair<Int, Int>, List<Verse>>()
    private var currentDatabaseNames: List<String> = listOf("biblia" /**, "bible-kjv"*/ ) // List of databases

    companion object {
        @Volatile private var instance: BibleRepository? = null

        fun getInstance(context: Context): BibleRepository {
            return instance ?: synchronized(this) {
                instance ?: BibleRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    fun getBooks(): List<Book> {
        booksCache.clear() // Clear the cache before merging
        currentDatabaseNames.forEach { dbName ->
          //  Log.d("BibleRepository", "Fetching books from database: $dbName")
            val booksFromDb = getDatabase(dbName).bibleDao().getAllBooks()
        //    Log.d("BibleRepository", "Books fetched from $dbName: $booksFromDb")
            booksCache.addAll(booksFromDb)
        }
        return mergeBooks(booksCache.toList())
    }

    fun getChapters(bookNumber: Int): List<Chapter> {
        if (!chaptersCache.containsKey(bookNumber)) {
            val chapters = mutableListOf<Chapter>()
            currentDatabaseNames.forEach { dbName ->
              //  Log.d("BibleRepository", "Fetching chapters for book $bookNumber from database: $dbName")
                val chaptersFromDb = getDatabase(dbName).bibleDao().getChapters(bookNumber)
             //   Log.d("BibleRepository", "Chapters fetched from $dbName: $chaptersFromDb")
                chapters.addAll(chaptersFromDb)
            }
            chaptersCache[bookNumber] = mergeChapters(chapters)
        }
        return chaptersCache[bookNumber] ?: listOf()
    }

    fun getVerses(bookNumber: Int, chapterNumber: Int): List<Verse> {
        val key = bookNumber to chapterNumber
        if (!versesCache.containsKey(key)) {
            val verses = mutableListOf<Verse>()
            currentDatabaseNames.forEach { dbName ->
              //  Log.d("BibleRepository", "Fetching verses for book $bookNumber, chapter $chapterNumber from database: $dbName")
                val versesFromDb = getDatabase(dbName).bibleDao().getVerses(bookNumber, chapterNumber)
             //   Log.d("BibleRepository", "Verses fetched from $dbName: $versesFromDb")
                verses.addAll(versesFromDb)
            }
            versesCache[key] = mergeVerses(verses)
        }
        return versesCache[key] ?: listOf()
    }

    // In the BibleRepository class
    fun getAllVerses(): List<Verse> {
        val verses = mutableListOf<Verse>()
        currentDatabaseNames.forEach { dbName ->
          //  Log.d("BibleRepository", "Fetching all verses from database: $dbName")
            val versesFromDb = getDatabase(dbName).bibleDao().getAllVerses()
            verses.addAll(versesFromDb)
        }
        return mergeVerses(verses)
    }

    fun getBookNameByNumber(bookNumber: Int): String {
        if (booksCache.isEmpty()) {
            getBooks()
        }
        return booksCache.find { it.bookNumber == bookNumber }?.bookName ?: "Unknown Book"
    }

    fun updateDatabases(dbNames: List<String>) {
       // Log.d("BibleRepository", "Updating databases to: $dbNames")
        currentDatabaseNames = dbNames
        clearCaches()
    }

    fun getCurrentDatabaseNames(): List<String> {
        return currentDatabaseNames
    }

    fun clearCaches() {
       // Log.d("BibleRepository", "Clearing caches")
        booksCache.clear()
        chaptersCache.clear()
        versesCache.clear()
    }

    private fun getDatabase(dbName: String): BibleDatabase {
        return BibleDatabase.getDatabase(context, dbName)
    }

    private fun mergeVerses(verses: List<Verse>): List<Verse> {
        val mergedVerses = mutableMapOf<Int, Verse>()
        verses.forEach { verse ->
          //  Log.d("BibleRepository", "Merging verse: $verse")
            mergedVerses[verse.verseNumber] = mergedVerses[verse.verseNumber]?.let {
                val newVerseText = SpannableString("${it.verseText}\n\n${verse.verseText}")
                val start = it.verseText.length + 2 // Adding 2 for the newline characters
                newVerseText.setSpan(
                    ForegroundColorSpan(Color.RED),
                    start,
                    start + verse.verseText.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                Verse(
                    id = it.id,
                    verseNumber = verse.verseNumber,
                    chapterNumber = verse.chapterNumber,
                    bookNumber = verse.bookNumber,
                    verseText = newVerseText.toString()
                )
            } ?: Verse(
                id = verse.id,
                verseNumber = verse.verseNumber,
                chapterNumber = verse.chapterNumber,
                bookNumber = verse.bookNumber,
                verseText = SpannableString(verse.verseText).apply {
                    setSpan(
                        ForegroundColorSpan(Color.RED),
                        0,
                        verse.verseText.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }.toString()
            )
        }
        return mergedVerses.values.toList()
    }

    @Synchronized
    private fun mergeBooks(books: List<Book>): List<Book> {
        val mergedBooks = mutableMapOf<Int, MutableList<String>>()

        books.forEach { book ->
            if (mergedBooks.containsKey(book.bookNumber)) {
                if (!mergedBooks[book.bookNumber]!!.contains(book.bookName)) {
                    mergedBooks[book.bookNumber]!!.add(book.bookName)
                }
            } else {
                mergedBooks[book.bookNumber] = mutableListOf(book.bookName)
            }
        }

        return mergedBooks.map { (bookNumber, names) ->
            val combinedNames = SpannableString(names.joinToString("\n\n")).apply {
                var start = 0
                names.forEach { name ->
                    setSpan(
                        ForegroundColorSpan(Color.BLUE),
                        start,
                        start + name.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    start += name.length + 2 // Adding 2 for the newline characters
                }
            }.toString()
            Book(bookNumber, combinedNames)
        }
    }

    private fun mergeChapters(chapters: List<Chapter>): List<Chapter> {
      //  Log.d("BibleRepository", "Merging chapters: $chapters")
        return chapters.distinctBy { it.chapterNumber }
    }

    // Add this method in the BibleRepository class
    fun searchVerses(query: String, limit: Int, offset: Int): List<Verse> {
        val verses = mutableListOf<Verse>()
        currentDatabaseNames.forEach { dbName ->
          //  Log.d("BibleRepository", "Searching verses in database: $dbName")
            val versesFromDb = getDatabase(dbName).bibleDao().searchVerses("%$query%", limit, offset)
            verses.addAll(versesFromDb)
        }
        return verses
    }

}
