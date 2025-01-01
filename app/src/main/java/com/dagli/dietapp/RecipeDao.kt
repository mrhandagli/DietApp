package com.dagli.dietapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * RecipeDao, `RecipeEntity` nesneleri için veri erişim (CRUD) işlemlerini
 * Room kütüphanesiyle yönetir.
 */
@Dao
interface RecipeDao {

    /**
     * Tek bir `RecipeEntity` nesnesini veritabanına ekler veya günceller.
     *
     * @param recipe Eklenmek veya güncellenmek istenen `RecipeEntity` nesnesi.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: RecipeEntity)

    /**
     * Belirli bir `mealId` değerine sahip tüm tarifleri (en yeni -> en eski) sıralayarak döndürür.
     *
     * @param mealId Tariflerin ilişkili olduğu öğünün kimliği.
     * @return Eşleşen `RecipeEntity` nesnelerinin listesi.
     */
    @Query("SELECT * FROM recipes WHERE mealId = :mealId ORDER BY dateTime DESC")
    suspend fun getRecipesForMeal(mealId: Int): List<RecipeEntity>

    /**
     * Tüm `RecipeEntity` nesnelerini (en yeni -> en eski) sıralayarak döndürür.
     *
     * @return Veritabanındaki tüm `RecipeEntity` nesnelerinin listesi.
     */
    @Query("SELECT * FROM recipes ORDER BY dateTime DESC")
    suspend fun getAllRecipes(): List<RecipeEntity>
}
