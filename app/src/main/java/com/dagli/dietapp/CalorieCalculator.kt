package com.dagli.dietapp

/**
 * CalorieCalculator, besin değişim tablosuna göre girilen
 * değerlerin kalori hesaplamasını yapmak için kullanılır.
 */
object CalorieCalculator {

    /**
     * Belirli bir besin kategorisi için makro değerlerini (karbonhidrat, protein, yağ)
     * içerir. Örneğin "Süt/Yoğurt" kategorisi Triple(9.0, 6.0, 6.0) olarak tanımlanmış olup
     * 9g karbonhidrat, 6g protein ve 6g yağ içerdiğini belirtir.
     */
    val exchangeMacros = mapOf(
        "Süt/Yoğurt" to Triple(9.0, 6.0, 6.0),
        "Et/Peynir/Yumurta" to Triple(0.0, 6.0, 5.0),
        "Ekmek/Tahıl/Kurubaklagil" to Triple(15.0, 2.0, 0.0),
        "Meyve" to Triple(15.0, 0.0, 0.0),
        "Sebze" to Triple(6.0, 2.0, 0.0),
        "Yağ" to Triple(0.0, 0.0, 5.0),
        "Yağlı Tohumlar/Sert Kabuklu Kuruyemişler" to Triple(0.0, 2.0, 5.0),
    )

    /**
     * Bir besin kategorisi ve belirli bir değişim (exchange) adedi için
     * kalori hesaplar. Karbonhidrat ve proteinin 1 gramı 4 kalori,
     * yağın 1 gramı 9 kalori olarak ele alınır.
     *
     * @param category Besin kategorisi (ör. "Süt/Yoğurt").
     * @param exchangeCount Kaç değişim (exchange) miktarı kullanıldığı.
     * @return Kategorinin toplam kalorisini [Double] olarak döndürür.
     */
    fun computeCaloriesForCategory(category: String, exchangeCount: Double): Double {
        val macros = exchangeMacros[category] ?: Triple(0.0, 0.0, 0.0)
        val (carbG, proteinG, oilG) = macros
        val totalCarbs = carbG * exchangeCount
        val totalProtein = proteinG * exchangeCount
        val totalOils = oilG * exchangeCount
        return (totalCarbs * 4 + totalProtein * 4 + totalOils * 9).round2decimals()
    }

    /**
     * Birden fazla besin kategorisi ve onların değişim miktarlarına
     * göre toplam kaloriyi hesaplar.
     *
     * @param nutrientValues Kategori -> değişim miktarı eşleştirmesi.
     * @return Toplam kaloriyi [Double] olarak döndürür.
     */

    fun computeTotalCalories(nutrientValues: Map<String, Double>): Double {
        return nutrientValues.entries.sumOf { (category, count) ->
            computeCaloriesForCategory(category, count)
        }
    }

    /**
     * Bir öğünün (örneğin kahvaltı) içindeki besin kategorileri ve
     * dağıtım miktarlarına göre toplam kalori hesaplar.
     *
     * @param mealDistribution Kategori -> dağıtılmış miktar eşleştirmesi.
     * @return Öğünün toplam kalorisi [Double] olarak döner.
     */
    fun computeTotalCaloriesForMeal(mealDistribution: Map<String, Double>): Double {
        return mealDistribution.entries.sumOf { (category, amount) ->
            computeCaloriesForCategory(category, amount)
        }
    }
}
