package library.service.business.books

import com.nhaarman.mockitokotlin2.*
import library.service.business.books.domain.BookRecord
import library.service.business.books.domain.events.*
import library.service.business.books.domain.states.Available
import library.service.business.books.domain.states.Borrowed
import library.service.business.books.domain.types.BookId
import library.service.business.books.domain.types.Borrower
import library.service.business.books.exceptions.BookAlreadyBorrowedException
import library.service.business.books.exceptions.BookAlreadyReturnedException
import library.service.business.books.exceptions.BookNotFoundException
import library.service.business.events.EventDispatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import utils.Books
import utils.assertThrows
import utils.classification.UnitTest
import utils.clockWithFixedTime
import java.time.OffsetDateTime

@UnitTest
internal class BookCollectionTest {

    val fixedTimestamp = "2017-09-23T12:34:56.789Z"
    val fixedClock = clockWithFixedTime(fixedTimestamp)

    val dataStore: BookDataStore = mock {
        on { createOrUpdate(any()) } doAnswer { it.arguments[0] as BookRecord }
    }
    val idGenerator: BookIdGenerator = BookIdGenerator(dataStore)
    val eventDispatcher: EventDispatcher<BookEvent> = mock()

    val cut = BookCollection(fixedClock, dataStore, idGenerator, eventDispatcher)

    @Nested inner class `adding a book` {

        @Test fun `generates a new book ID`() {
            with(cut.addBook(Books.THE_MARTIAN)) {
                assertThat(id).isNotNull()
            }
        }

        @Test fun `sets the initial state to available`() {
            with(cut.addBook(Books.THE_MARTIAN)) {
                assertThat(state).isEqualTo(Available)
            }
        }

        @Test fun `stores the book's data`() {
            with(cut.addBook(Books.THE_MARTIAN)) {
                assertThat(book).isEqualTo(Books.THE_MARTIAN)
            }
        }

        @Test fun `dispatches a BookAdded event`() {
            val bookRecord = cut.addBook(Books.THE_MARTIAN)
            verify(eventDispatcher).dispatch(check<BookAdded> {
                assertThat(it.bookId).isEqualTo("${bookRecord.id}")
                assertThat(it.timestamp).isEqualTo(fixedTimestamp)
            })
        }

        @Test fun `does not dispatch any events in case of an exception`() {
            given { dataStore.createOrUpdate(any()) } willThrow { RuntimeException() }
            assertThrows(RuntimeException::class) {
                cut.addBook(Books.THE_MARTIAN)
            }
            verifyZeroInteractions(eventDispatcher)
        }

    }

    @Nested inner class `updating a book` {

        val id = BookId.generate()
        val bookRecord = BookRecord(id, Books.THE_DARK_TOWER_VII)
        val updatedBookRecord = bookRecord.changeNumberOfPages(42)

        @Test fun `updates the record in the database`() {
            given { dataStore.findById(id) } willReturn { bookRecord }

            val updatedBook = cut.updateBook(id) { updatedBookRecord }

            assertThat(updatedBook).isEqualTo(updatedBook)
            verify(dataStore).createOrUpdate(updatedBook)
        }

        @Test fun `dispatches a BookUpdated event`() {
            given { dataStore.findById(id) } willReturn { bookRecord }

            cut.updateBook(id) { updatedBookRecord }

            verify(eventDispatcher).dispatch(check<BookUpdated> {
                assertThat(it.bookId).isEqualTo("$id")
                assertThat(it.timestamp).isEqualTo(fixedTimestamp)
            })
        }

        @Test fun `throws exception if it was not found in data store`() {
            given { dataStore.findById(id) } willReturn { null }
            assertThrows(BookNotFoundException::class) {
                cut.updateBook(id) { updatedBookRecord }
            }
        }

    }

    @Nested inner class `getting a book` {

        val id = BookId.generate()
        val bookRecord = BookRecord(id, Books.THE_DARK_TOWER_I)

        @Test fun `returns it if it was found in data store`() {
            given { dataStore.findById(id) } willReturn { bookRecord }
            val gotBook = cut.getBook(id)
            assertThat(gotBook).isEqualTo(bookRecord)
        }

        @Test fun `throws exception if it was not found in data store`() {
            given { dataStore.findById(id) } willReturn { null }
            assertThrows(BookNotFoundException::class) {
                cut.getBook(id)
            }
        }

    }

    @Nested inner class `getting all books` {

        @Test fun `delegates directly to data store`() {
            val bookRecord1 = BookRecord(BookId.generate(), Books.THE_DARK_TOWER_II)
            val bookRecord2 = BookRecord(BookId.generate(), Books.THE_DARK_TOWER_III)
            given { dataStore.findAll() } willReturn { listOf(bookRecord1, bookRecord2) }

            val allBooks = cut.getAllBooks()

            assertThat(allBooks).containsExactly(bookRecord1, bookRecord2)
        }

    }

    @Nested inner class `removing a book` {

        val id = BookId.generate()
        val bookRecord = BookRecord(id, Books.THE_DARK_TOWER_IV)

        @Test fun `deletes it from the data store if found`() {
            given { dataStore.findById(id) } willReturn { bookRecord }
            cut.removeBook(id)
            verify(dataStore).delete(bookRecord)
        }

        @Test fun `dispatches a BookRemoved event`() {
            given { dataStore.findById(id) } willReturn { bookRecord }
            cut.removeBook(id)
            verify(eventDispatcher).dispatch(check<BookRemoved> {
                assertThat(it.bookId).isEqualTo("$id")
                assertThat(it.timestamp).isEqualTo(fixedTimestamp)
            })
        }

        @Test fun `throws exception if it was not found in data store`() {
            given { dataStore.findById(id) } willReturn { null }
            assertThrows(BookNotFoundException::class) {
                cut.removeBook(id)
            }
        }

        @Test fun `does not dispatch any events in case of an exception`() {
            given { dataStore.findById(id) } willThrow { RuntimeException() }
            assertThrows(RuntimeException::class) {
                cut.removeBook(id)
            }
            verifyZeroInteractions(eventDispatcher)
        }

    }

    @Nested inner class `borrowing a book` {

        val id = BookId.generate()
        val availableBookRecord = BookRecord(id, Books.THE_DARK_TOWER_V)
        val borrowedBookRecord = availableBookRecord.borrow(Borrower("Someone"), OffsetDateTime.now())

        @Test fun `changes its state and updates it in the data store`() {
            given { dataStore.findById(id) } willReturn { availableBookRecord }

            val borrowedBook = cut.borrowBook(id, Borrower("Someone"))

            assertThat(borrowedBook.state).isInstanceOf(Borrowed::class.java)
            assertThat(borrowedBook).isEqualTo(borrowedBook)
        }

        @Test fun `dispatches a BookBorrowed event`() {
            given { dataStore.findById(id) } willReturn { availableBookRecord }

            cut.borrowBook(id, Borrower("Someone"))

            verify(eventDispatcher).dispatch(check<BookBorrowed> {
                assertThat(it.bookId).isEqualTo("$id")
                assertThat(it.timestamp).isEqualTo(fixedTimestamp)
            })
        }

        @Test fun `throws exception if it was not found in data store`() {
            given { dataStore.findById(id) } willReturn { null }
            assertThrows(BookNotFoundException::class) {
                cut.borrowBook(id, Borrower("Someone"))
            }
        }

        @Test fun `throws exception if it is already 'borrowed'`() {
            given { dataStore.findById(id) } willReturn { borrowedBookRecord }
            assertThrows(BookAlreadyBorrowedException::class) {
                cut.borrowBook(id, Borrower("Someone Else"))
            }
        }

        @Test fun `does not dispatch any events in case of an exception`() {
            given { dataStore.findById(id) } willThrow { RuntimeException() }
            assertThrows(RuntimeException::class) {
                cut.borrowBook(id, Borrower("Someone Else"))
            }
            verifyZeroInteractions(eventDispatcher)
        }

    }

    @Nested inner class `returning a book` {

        val id = BookId.generate()
        val availableBookRecord = BookRecord(id, Books.THE_DARK_TOWER_VI)
        val borrowedBookRecord = availableBookRecord.borrow(Borrower("Someone"), OffsetDateTime.now())

        @Test fun `changes its state and updates it in the data store`() {
            given { dataStore.findById(id) } willReturn { borrowedBookRecord }

            val result = cut.returnBook(id)

            assertThat(result.state).isEqualTo(Available)
            assertThat(result).isEqualTo(availableBookRecord)
        }

        @Test fun `dispatches a BookReturned event`() {
            given { dataStore.findById(id) } willReturn { borrowedBookRecord }

            cut.returnBook(id)

            verify(eventDispatcher).dispatch(check<BookReturned> {
                assertThat(it.bookId).isEqualTo("$id")
                assertThat(it.timestamp).isEqualTo(fixedTimestamp)
            })
        }

        @Test fun `throws exception if it was not found in data store`() {
            given { dataStore.findById(id) } willReturn { null }
            assertThrows(BookNotFoundException::class) {
                cut.returnBook(id)
            }
        }

        @Test fun `throws exception if it is already 'returned'`() {
            given { dataStore.findById(id) } willReturn { availableBookRecord }
            assertThrows(BookAlreadyReturnedException::class) {
                cut.returnBook(id)
            }
        }

        @Test fun `does not dispatch any events in case of an exception`() {
            given { dataStore.findById(id) } willThrow { RuntimeException() }
            assertThrows(RuntimeException::class) {
                cut.returnBook(id)
            }
            verifyZeroInteractions(eventDispatcher)
        }

    }

}