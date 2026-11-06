package ua.zapys

import android.test.AndroidTestCase
import ua.zapys.data.SQLiteRepository
import ua.zapys.domain.Client
import ua.zapys.domain.Service
import java.time.ZoneId

/** Real SQLite acceptance cases; require an Android device or emulator. */
class ClientCrudTest : AndroidTestCase() {
    private val databaseName = "client-crud-test.db"
    private lateinit var repo: SQLiteRepository

    override fun setUp() {
        super.setUp()
        context.deleteDatabase(databaseName)
        repo = open()
    }

    override fun tearDown() {
        repo.close()
        context.deleteDatabase(databaseName)
        super.tearDown()
    }

    private fun open() = SQLiteRepository(context, databaseName, { 0L }, ZoneId.of("UTC"))
    private fun reopen() { repo.close(); repo = open() }

    fun testDuplicatePhonesKeepSeparateIdentitiesAndEditingDoesNotOverwriteOtherClient() {
        val first = Client(firstName = "Олена", phone = "+380500000001")
        val second = Client(firstName = "Марія", phone = first.phone, note = "Окремий запис")
        assertFalse(first.id == second.id)
        repo.saveClient(first)
        repo.saveClient(second)
        assertEquals(mapOf(first.id to first, second.id to second), repo.clients().associateBy { it.id })

        val edited = first.copy(firstName = "Оленка", surname = "Тестова", patronymic = "Іванівна",
            note = "Оновлена нотатка", color = 0xFF336699.toInt())
        repo.saveClient(edited)
        reopen()
        assertEquals(mapOf(edited.id to edited, second.id to second), repo.clients().associateBy { it.id })
    }

    fun testEditingServiceKeepsUnknownAndFreePricesAndOptionalFieldsAfterReopen() {
        val unknown = Service(name = "Консультація", note = "Без визначеної ціни")
        val free = Service(name = "Знайомство", durationMinutes = 15, priceMinor = 0,
            note = "Безкоштовно", color = 0xFF336699.toInt())
        repo.saveService(unknown)
        repo.saveService(free)
        val editedUnknown = unknown.copy(name = "Розмова", note = "Оновлено")
        val editedFree = free.copy(name = "Перша зустріч", durationMinutes = null, color = null)
        repo.saveService(editedUnknown)
        repo.saveService(editedFree)
        reopen()
        assertEquals(mapOf(editedUnknown.id to editedUnknown, editedFree.id to editedFree),
            repo.services().associateBy { it.id })
        assertNull(repo.services().single { it.id == unknown.id }.priceMinor)
        assertEquals(0L, repo.services().single { it.id == free.id }.priceMinor)
    }
}
