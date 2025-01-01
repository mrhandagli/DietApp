package com.dagli.dietapp

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

/**
 * JSON yapısına denk gelen veri sınıfları.
 */

/**
 * Gıda değişim tablosunu (foodExchangeTable) içeren veri sınıfı.
 *
 * @param foodExchangeTable Gıda değişim tablosu listesini temsil eder.
 */
data class FoodsWrapper(
    val foodExchangeTable: List<Food>
)

/**
 * Room veritabanında "foods" tablosunda saklanan gıda bilgilerini temsil eder.
 *
 * @property id Gıdanın benzersiz kimlik numarası.
 * @property name Gıdanın adı.
 * @property group Gıdanın ait olduğu grup bilgisi.
 * @property defaultMeasurement Varsayılan ölçüm detaylarını içerir.
 * @property measurements Gıdanın farklı ölçüm durumlarını (çiğ, pişmiş vb.) içerir.
 */
@Entity(tableName = "foods")
data class Food(
    @PrimaryKey val id: Int,
    val name: String,
    @ColumnInfo(name = "group") val group: String,
    val defaultMeasurement: DefaultMeasurement,
    val measurements: Measurements
)

/**
 * Gıdanın varsayılan ölçüm bilgilerini tutar.
 *
 * @property state Gıdanın durumu (örnek: "raw", "cooked").
 * @property weightType Gıdanın ağırlık türü (örnek: "g", "ml").
 * @property unit Gıdanın birimi (örnek: "adet", "gram").
 */
data class DefaultMeasurement(
    val state: String,
    val weightType: String,
    val unit: String
)

/**
 * Gıdanın farklı ölçüm durumlarını (çiğ, pişmiş vb.) tutan veri sınıfı.
 *
 * @property raw Gıdanın çiğ hali için ölçüm detayları.
 * @property cooked Gıdanın pişmiş hali için ölçüm detayları.
 * @property neutral Gıdanın işlenmemiş/nötr hali için ölçüm detayları.
 */
data class Measurements(
    val raw: MeasurementDetail? = null,
    val cooked: MeasurementDetail? = null,
    val neutral: MeasurementDetail? = null
)

/**
 * Belirli bir durum (çiğ/pişmiş/nötr) için brüt ve net ölçümleri tutar.
 *
 * @property gross Brüt (kabuklu veya işlenmemiş) ölçümleri içeren liste.
 * @property net Net (temizlenmiş veya ayıklanmış) ölçümleri içeren liste.
 */
data class MeasurementDetail(
    val gross: List<SingleMeasurement> = emptyList(),
    val net: List<SingleMeasurement> = emptyList()
)

/**
 * Tekil bir ölçüm bilgisini temsil eder.
 *
 * @property unit Ölçüm birimi (örnek: "gram", "adet").
 * @property amount Ölçüm miktarı (örnek: 100.0).
 */
data class SingleMeasurement(
    val unit: String,
    val amount: Double
)
