package com.dagli.dietapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import java.time.LocalDateTime

/**
 * FoodJournalDao, `FoodJournalEntry` nesneleri için veritabanı
 * erişim (CRUD) işlemlerini yönetmek amacıyla Room kütüphanesini kullanır.
 */
@Dao
interface FoodJournalDao {

    /**
     * Tek bir `FoodJournalEntry` nesnesini veritabanına ekler veya günceller.
     *
     * @param entry Eklenmek veya güncellenmek istenen `FoodJournalEntry` nesnesi.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: FoodJournalEntry)

    /**
     * Veritabanında bulunan tüm `FoodJournalEntry` nesnelerini, tarihe göre (en yeni -> en eski) sıralar.
     *
     * @return Tüm `FoodJournalEntry` nesnelerinin listesi.
     */
    @Query("SELECT * FROM food_journal ORDER BY dateTime DESC")
    suspend fun getAllEntries(): List<FoodJournalEntry>

    /**
     * Belirli bir `mealId` ve tarih (LocalDateTime) ile eşleşen
     * ilk `FoodJournalEntry` nesnesini döndürür.
     *
     * @param mealId İlişkili öğünün kimliği.
     * @param currentDate Karşılaştırma yapılacak tarih (LocalDateTime).
     * @return Eşleşen `FoodJournalEntry` nesnesi ya da `null`.
     */
    @Query("SELECT * FROM food_journal WHERE mealId = :mealId AND DATE(dateTime) = DATE(:currentDate) LIMIT 1")
    suspend fun getEntryForMealAndDate(mealId: Int, currentDate: LocalDateTime): FoodJournalEntry?

    /**
     * Mevcut bir `FoodJournalEntry` nesnesini veritabanında günceller.
     *
     * @param entry Güncellenecek `FoodJournalEntry` nesnesi.
     */
    @Update
    suspend fun updateEntry(entry: FoodJournalEntry)
}
