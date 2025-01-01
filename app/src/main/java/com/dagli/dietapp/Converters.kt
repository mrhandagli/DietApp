package com.dagli.dietapp

import androidx.room.TypeConverter
import java.time.LocalTime
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Converters, Room veritabanında saklanması zor olan veya
 * özel tipteki verileri dönüştürmek için kullanılır.
 *
 * Örneğin: [LocalTime], [LocalDateTime], [MealType] gibi türler
 * JSON string'lere dönüştürülerek veritabanına kaydedilir.
 */
class Converters {

    /**
     * Bu Gson örneği, [LocalTime] için özel (de)serileştiriciler içerir.
     */
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(LocalTime::class.java, object : JsonDeserializer<LocalTime> {
            override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): LocalTime {
                return LocalTime.parse(json.asString)
            }
        })
        .registerTypeAdapter(LocalTime::class.java, object : JsonSerializer<LocalTime> {
            override fun serialize(src: LocalTime, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
                return JsonPrimitive(src.toString())
            }
        })
        .create()

    // -- LocalTime Dönüştürücüler --

    /**
     * @param time Dönüştürülecek [LocalTime] nesnesi.
     * @return [LocalTime] nesnesini veritabanında saklanabilmesi için [String] formatına çevirir.
     */
    @TypeConverter
    fun fromLocalTime(time: LocalTime): String {
        return time.toString() // İsterseniz belirli bir formatter da kullanabilirsiniz
    }

    /**
     * @param value Veritabanından alınan [String] değeri.
     * @return [LocalTime] nesnesine dönüştürülmüş halidir.
     */
    @TypeConverter
    fun toLocalTime(value: String): LocalTime {
        return LocalTime.parse(value)
    }

    // -- MealType Dönüştürücüler --

    /**
     * @param type [MealType] tipindeki enum değeri.
     * @return Enum değerinin adını [String] olarak döndürür (ör. "MAIN_MEAL").
     */
    @TypeConverter
    fun fromMealType(type: MealType): String {
        return type.name
    }

    /**
     * @param value Enum adını belirten [String].
     * @return [MealType] nesnesini oluşturur (ör. "SNACK" -> MealType.SNACK).
     */
    @TypeConverter
    fun toMealType(value: String): MealType {
        return MealType.valueOf(value)
    }


    // -- Ölçüm Dönüştürücüleri --

    /**
     * @param measurements Saklanacak [Measurements] nesnesi.
     * @return JSON formatındaki [String].
     */
    @TypeConverter
    fun fromMeasurements(measurements: Measurements): String {
        return gson.toJson(measurements)
    }

    /**
     * @param value JSON formatındaki [String].
     * @return Bir [Measurements] nesnesi döndürür.
     */
    @TypeConverter
    fun toMeasurements(value: String): Measurements {
        val type = object : TypeToken<Measurements>() {}.type
        return gson.fromJson(value, type) ?: Measurements()
    }

    // -- VarsayılanÖlçü Dönüştürücüleri --

    /**
     * @param defaultMeasurement [DefaultMeasurement] nesnesi.
     * @return JSON formatındaki [String] karşılığı.
     */
    @TypeConverter
    fun fromDefaultMeasurement(defaultMeasurement: DefaultMeasurement): String {
        return gson.toJson(defaultMeasurement)
    }

    /**
     * @param value JSON formatındaki [String].
     * @return [DefaultMeasurement] nesnesine dönüştürülmüş halidir.
     */
    @TypeConverter
    fun toDefaultMeasurement(value: String): DefaultMeasurement {
        val type = object : TypeToken<DefaultMeasurement>() {}.type
        return gson.fromJson(value, type)
    }

    // -- SelectedIngredient Listesi Dönüştürücüler --

    /**
     * fromSelectedIngredients, kullanıcı tarafından seçilen malzeme (besin) listesini
     * JSON string'e çevirir.
     *
     * @param ingredients [List] türünde [SelectedIngredient].
     * @return JSON formatındaki [String].
     */
    @TypeConverter
    fun fromSelectedIngredients(ingredients: List<SelectedIngredient>): String {
        return gson.toJson(ingredients)
    }

    /**
     * toSelectedIngredients, JSON string'i tekrar [SelectedIngredient] listesinin
     * nesnelerine dönüştürür.
     *
     * @param value JSON formatındaki [String].
     * @return [List] türünde [SelectedIngredient] döndürür.
     */
    @TypeConverter
    fun toSelectedIngredients(value: String): List<SelectedIngredient> {
        val listType = object : TypeToken<List<SelectedIngredient>>() {}.type
        return gson.fromJson(value, listType) ?: emptyList()
    }

    // -- LocalDateTime Dönüştürücüler --

    /**
     * formatter, tarih ve saati "yyyy-MM-dd'T'HH:mm:ss" formatında düzenler.
     */
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    /**
     * @param dateTime Dönüştürülecek [LocalDateTime] nesnesi (nullable).
     * @return [String] formatında saklanabilir veri (örn. "2024-05-21T14:30:00").
     */
    @TypeConverter
    fun fromLocalDateTime(dateTime: LocalDateTime?): String? {
        return dateTime?.format(formatter)
    }

    /**
     * @param value Veritabanından gelen [String] değeri ("2024-05-21T14:30:00" gibi).
     * @return [LocalDateTime] nesnesi veya null.
     *
     * Eğer değer sadece saat-minuteyle geliyorsa ("12:43"), bugünün tarihi
     * ile birleştirilerek [LocalDateTime] oluşturulmaya çalışılır.
     */
    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? {
        return try {
            // Geçerli LocalDateTime formatı parse edilir
            value?.let { LocalDateTime.parse(it, formatter) }
        } catch (e: Exception) {
            value?.let {
                if (it.contains(":")) {
                    val currentDate = LocalDateTime.now().toLocalDate()
                    val time = LocalDateTime.parse("$currentDate T $it", formatter)
                    LocalDateTime.of(currentDate, time.toLocalTime())
                } else {
                    null
                }
            }
        }
    }
}
