package com.dagli.dietapp

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * AppDatabase, Room kütüphanesi ile oluşturulan
 * uygulama veritabanını tanımlar.
 *
 * @Database anotasyonu ile belirtilen varlıkları ([Meal], [Food], [FoodJournalEntry], [RecipeEntity])
 * ve veritabanının sürüm numarasını içerir.
 * @TypeConverters, veritabanında özel tip dönüştürücülerin (örn. [Converters]) kullanılmasını sağlar.
 */
@Database(
    entities = [Meal::class, Food::class, FoodJournalEntry::class, RecipeEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    /**
     * @return [MealDao] - Öğün verileri (Meal) ile ilgili veritabanı işlemleri için DAO nesnesi.
     */
    abstract fun mealDao(): MealDao

    /**
     * @return [FoodDao] - Yiyecek verileri (Food) ile ilgili veritabanı işlemleri için DAO nesnesi.
     */
    abstract fun foodDao(): FoodDao

    /**
     * @return [FoodJournalDao] - Yiyecek günlük verileri (FoodJournalEntry) ile ilgili veritabanı işlemleri için DAO nesnesi.
     */
    abstract fun foodJournalDao(): FoodJournalDao

    /**
     * @return [RecipeDao] - Tarif verileri ([RecipeEntity]) ile ilgili veritabanı işlemleri için DAO nesnesi.
     */
    abstract fun recipeDao(): RecipeDao
}
