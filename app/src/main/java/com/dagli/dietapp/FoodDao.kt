package com.dagli.dietapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * FoodDao, Room kütüphanesi üzerinden `Food` nesneleri için
 * veri erişim (CRUD) işlemlerini yönetir.
 */
@Dao
interface FoodDao {

    /**
     * Bir liste içindeki `Food` nesnelerini veritabanına ekler veya günceller.
     *
     * @param foods Eklenmek veya güncellenmek istenen `Food` nesnelerinin listesi.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoods(foods: List<Food>)


    /**
     * Veritabanında bulunan tüm `Food` nesnelerini döndürür.
     *
     * @return Tüm `Food` nesnelerinin listesi.
     */
    @Query("SELECT * FROM foods")
    suspend fun getAllFoods(): List<Food>
}
