package com.dagli.dietapp

import androidx.room.*

/**
 * MealDao, Room kütüphanesi üzerinden `Meal` nesneleri için
 * veri erişim (CRUD) işlemlerini yönetir.
 */
@Dao
interface MealDao {

    /**
     * Tüm `Meal` kayıtlarını veritabanından çeker.
     *
     * @return Veritabanındaki tüm `Meal` nesnelerinin listesi.
     */
    @Query("SELECT * FROM meals")
    suspend fun getAllMeals(): List<Meal>

    /**
     * Bir liste halindeki birden fazla `Meal` nesnesini veritabanına ekler veya günceller.
     *
     * @param meals Eklenmek veya güncellenmek istenen `Meal` nesnelerinin listesi.
     * @return Eklenen `Meal` nesnelerinin veritabanı kimliklerinin (`ID`) listesi.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeals(meals: List<Meal>): List<Long>

    /**
     * Tek bir `Meal` nesnesini veritabanına ekler veya günceller.
     *
     * @param meal Eklenmek veya güncellenmek istenen `Meal` nesnesi.
     * @return Eklenen veya güncellenen `Meal` nesnesinin veritabanı kimliği.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeal(meal: Meal): Long

    /**
     * Parametre olarak verilen birden fazla `Meal` nesnesini veritabanından siler.
     *
     * @param meals Silinmek istenen `Meal` nesnelerinin listesi.
     */
    @Delete
    suspend fun deleteMeals(meals: List<Meal>)

    /**
     * Tek bir `Meal` nesnesini veritabanından siler.
     *
     * @param meal Silinmek istenen `Meal` nesnesi.
     */
    @Delete
    suspend fun deleteMeal(meal: Meal)

    /**
     * Veritabanında bulunan bir `Meal` nesnesini günceller.
     *
     * @param meal Güncellenmek istenen `Meal` nesnesi.
     */
    @Update
    suspend fun updateMeal(meal: Meal)

    /**
     * Tüm `Meal` kayıtlarını veritabanından siler.
     */
    @Query("DELETE FROM meals")
    suspend fun clearAll()

    /**
     * Veritabanında, belirtilen `mealId` değerine sahip `Meal` nesnesini getirir.
     *
     * @param mealId Aranan `Meal` nesnesinin kimliği.
     * @return Bulunması halinde ilgili `Meal` nesnesi, aksi takdirde `null`.
     */
    @Query("SELECT * FROM meals WHERE id = :mealId LIMIT 1")
    suspend fun getMealById(mealId: Int): Meal?

}
