package com.dagli.dietapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.lifecycle.lifecycleScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * RecipeGenerationActivity, seçili malzemeler üzerinden
 * Gemini modelinden tarif üretilmesini ve üretilen tariflerin
 * veritabanına kaydedilmesini sağlar.
 */
class RecipeGenerationActivity : ComponentActivity() {

    // Google PaLM / Gemini API anahtarını alan yardımcı sınıf
    private val GeminiHelper = GeminiHelper("API_KEY")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Öğün kimliği (Meal ID) ve seçili malzemelerin JSON'u alınıyor
        val mealId = intent.getIntExtra("MEAL_ID", -1)
        val selectedJson = intent.getStringExtra("SELECTED_INGREDIENTS") ?: "[]"
        val listType = object : TypeToken<List<SelectedIngredient>>() {}.type
        val selectedIngredients: List<SelectedIngredient> =
            Gson().fromJson(selectedJson, listType) ?: emptyList()

        setContent {
            MaterialTheme {
                RecipeGenerationScreen(
                    mealId = mealId,
                    selectedIngredients = selectedIngredients,
                    /**
                     * Tarifin veritabanına kaydedilmesi için çağrılır.
                     *
                     * @param recipeText Kaydedilecek tarif metni.
                     */
                    onSaveRecipe = { recipeText ->
                        val app = application as DietApp
                        val recipeDao = app.db.recipeDao()
                        lifecycleScope.launch(Dispatchers.IO) {
                            val entity = RecipeEntity(
                                mealId = mealId.takeIf { it >= 0 },
                                recipeText = recipeText,
                                dateTime = LocalDateTime.now(),
                                category = null,
                                ingredientsJson = Gson().toJson(selectedIngredients)
                            )
                            recipeDao.insertRecipe(entity)
                            Log.d("RecipeGen", "Inserted recipe for mealId=$mealId")
                        }
                    },
                    /**
                     * Yeni tarif üretmek için (Gemini modelinden) çağrılır.
                     *
                     * @param ingredients Seçili malzeme listesi.
                     * @param userDescription Kullanıcının tarifle ilgili ek isteği, açıklaması.
                     * @param onResult Üretilen tarif metni ile sonuç döndürülür.
                     */
                    onGenerateRecipe = { ingredients, userDescription, onResult ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val promptText = buildPrompt(ingredients, userDescription)
                            val generated = GeminiHelper.callGemini(promptText)
                            withContext(Dispatchers.Main) {
                                onResult(generated)
                            }
                        }
                    },
                    /**
                     * Ana ekrana veya bir önceki aktiviteye dönmek için çağrılır.
                     */
                    onNavigateBack = {
                        finish()  // veya startActivity(Intent(this, HomeActivity::class.java))
                    }
                )
            }
        }
    }

    /**
     * Gemini modeli için prompt (istek metni) oluşturur.
     *
     * @param ingredients Seçili malzemelerin listesi.
     * @param userDescription Kullanıcının tarifle ilgili özel isteği veya açıklaması.
     * @return Gemini'ye gönderilecek tam prompt metni.
     */
    private fun buildPrompt(
        ingredients: List<SelectedIngredient>,
        userDescription: String
    ): String {
        val ingredientLines = ingredients.joinToString("\n") { formatIngredientLine(it) }
        return """
        $ingredientLines

        Lütfen yalnızca yukarıdaki malzemeler ve miktarlar ile bir tarif oluştur.
        Ayrıca şu isteklerimi göze al: $userDescription
        Return the recipe in a concise format. Generate the recipe in Turkish.
    """.trimIndent()
    }

}

/**
 * RecipeGenerationScreen, tarif oluşturma ve kayıt işlemleri için
 * yüksek seviyeli bir Compose ekran bileşenidir.
 *
 * @param mealId İlgili öğünün (Meal) kimliği.
 * @param selectedIngredients Kullanıcının seçtiği malzemelerin listesi.
 * @param onSaveRecipe Tarif kaydedileceğinde tetiklenecek callback.
 * @param onGenerateRecipe Yeni tarif üretimi için callback fonksiyonu.
 * @param onNavigateBack Geri dönmek için çağrılacak fonksiyon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeGenerationScreen(
    mealId: Int,
    selectedIngredients: List<SelectedIngredient>,
    onSaveRecipe: (String) -> Unit,
    onGenerateRecipe: (
        ingredients: List<SelectedIngredient>,
        userDescription: String,
        onResult: (String) -> Unit
    ) -> Unit,
    onNavigateBack: () -> Unit
) {
    val app = LocalContext.current.applicationContext as DietApp
    val recipeDao = app.db.recipeDao()

    // 0 => "Kayıtlı Tarifler", 1 => "Tarif Oluştur"
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Kayıtlı Tarifler", "Tarif Oluştur")

    // Kayıtlı tarifler
    var pastRecipes by remember { mutableStateOf<List<RecipeEntity>>(emptyList()) }
    // Üretilen veya seçilen tarif
    var generatedRecipe by remember { mutableStateOf<String?>(null) }
    // Yükleme durumu
    var isLoading by remember { mutableStateOf(false) }
    // Kullanıcı girdi alanı (örn. ek istekler)
    var userDescription by remember { mutableStateOf("") }
    // Tarif listesi yeniden yüklensin mi?
    var reloadPastRecipes by remember { mutableStateOf(false) }

    // Başlangıç yükleme
    LaunchedEffect(mealId) {
        isLoading = true
        val loaded = withContext(Dispatchers.IO) {
            if (mealId >= 0) recipeDao.getRecipesForMeal(mealId)
            else recipeDao.getAllRecipes()
        }
        pastRecipes = loaded
        isLoading = false
    }

    // Yeniden yükleme efekti (tarif kaydedildikten sonra güncellemek için)
    LaunchedEffect(reloadPastRecipes) {
        if (reloadPastRecipes) {
            isLoading = true
            val reloaded = withContext(Dispatchers.IO) {
                if (mealId >= 0) recipeDao.getRecipesForMeal(mealId)
                else recipeDao.getAllRecipes()
            }
            pastRecipes = reloaded
            isLoading = false
            reloadPastRecipes = false
        }
    }

    Scaffold(
        topBar = {
            // Üst menü çubuğu (TopAppBar) ve geri butonu
            TopAppBar(
                title = { Text("Tarif Üretici") },
                navigationIcon = {
                    IconButton(onClick = { onNavigateBack() }) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Üstte sekme (Tab) bar
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = (selectedTab == index),
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            // Seçili sekmeye göre içerik değiştir
            when (selectedTab) {
                0 -> {
                    // Mevcut (kayıtlı) tarifleri gösteren bölüm
                    SavedRecipesUI(
                        pastRecipes = pastRecipes,
                        isLoading = isLoading
                    )
                }
                1 -> {
                    // Yeni tarif oluşturma bölümü
                    GenerateRecipeUI(
                        selectedIngredients = selectedIngredients,
                        userDescription = userDescription,
                        generatedRecipe = generatedRecipe,
                        isLoading = isLoading,
                        onUserDescriptionChange = { userDescription = it },
                        onGenerateRecipeClick = { desc ->
                            isLoading = true
                            onGenerateRecipe(selectedIngredients, desc) { result ->
                                generatedRecipe = result
                                isLoading = false
                            }
                        },
                        onSaveClick = {
                            onSaveRecipe(generatedRecipe ?: "")
                            reloadPastRecipes = true
                        },
                        onClearRecipe = {
                            generatedRecipe = null
                        }
                    )
                }
            }
        }
    }
}

/**
 * "Kayıtlı Tarifler" sekmesi için UI.
 *
 * @param pastRecipes Veritabanından yüklenmiş tariflerin listesi.
 * @param isLoading Veriler yüklenirken `true`, aksi takdirde `false`.
 */
@Composable
fun SavedRecipesUI(
    pastRecipes: List<RecipeEntity>,
    isLoading: Boolean,
) {
    var selectedRecipe by remember { mutableStateOf<RecipeEntity?>(null) }

    // Yükleme durumu
    if (isLoading) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Yükleniyor...", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    // Belirli bir tarif seçildiyse, detay ekranını göster
    if (selectedRecipe != null) {
        SelectedRecipeDetail(
            recipe = selectedRecipe!!,
            onBackToList = {
                selectedRecipe = null
            }
        )
    } else {
        // Tarif seçilmediyse, liste göster
        if (pastRecipes.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("Henüz tarif yok.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                items(pastRecipes) { recipe ->
                    RecipeCard(recipe) {
                        selectedRecipe = recipe
                    }
                }
            }
        }
    }
}

/**
 * Seçilmiş tarifi detaylı gösteren UI.
 *
 * @param recipe Gösterilecek `RecipeEntity`.
 * @param onBackToList Liste ekranına geri dönmek için callback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectedRecipeDetail(
    recipe: RecipeEntity,
    onBackToList: () -> Unit
) {
    // Tarih formatlama
    val formattedDateTime = remember(recipe.dateTime) {
        try {
            recipe.dateTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"))
        } catch (e: Exception) {
            "Geçersiz Tarih"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Seçilen Tarif") },
                navigationIcon = {
                    IconButton(onClick = { onBackToList() }) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack,
                            contentDescription = "Geri"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text("Tarif ID: ${recipe.recipeId}", style = MaterialTheme.typography.bodySmall)
            Text("Oluşturulma Zamanı: $formattedDateTime", style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(16.dp))

            Text("Tarif Detayı:", style = MaterialTheme.typography.titleMedium)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                MarkdownishText(
                    text = recipe.recipeText,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

/**
 * "Tarif Oluştur" sekmesi için UI.
 *
 * @param selectedIngredients Kullanıcının seçtiği malzemeler.
 * @param userDescription Kullanıcı tarafından girilen ek açıklama/istek.
 * @param generatedRecipe Üretilen tarif (jenerik metin).
 * @param isLoading Yükleme gösterimi için bool değer.
 * @param onUserDescriptionChange Kullanıcı metni değiştiğinde çağrılır.
 * @param onGenerateRecipeClick Tarif üretimi butonuna tıklanınca çağrılır.
 * @param onSaveClick Üretilen tarifi kaydetme işlemi için çağrılır.
 * @param onClearRecipe Üretilen tarifi temizlemek için çağrılır.
 */
@Composable
fun GenerateRecipeUI(
    selectedIngredients: List<SelectedIngredient>,
    userDescription: String,
    generatedRecipe: String?,
    isLoading: Boolean,
    onUserDescriptionChange: (String) -> Unit,
    onGenerateRecipeClick: (String) -> Unit,
    onSaveClick: () -> Unit,
    onClearRecipe: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Seçili malzemeleri göster
        Text("Seçili Malzemeler:", style = MaterialTheme.typography.titleMedium)
        val selectedIngText = selectedIngredients.joinToString("\n") { formatIngredientLine(it) }
        Text(selectedIngText, style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(16.dp))

        // Kullanıcının tarifle ilgili ek istek gireceği alan
        OutlinedTextField(
            value = userDescription,
            onValueChange = { onUserDescriptionChange(it) },
            label = { Text("Tarif Özellikleri (opsiyonel)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Tarif oluşturma butonu
        Button(
            onClick = { onGenerateRecipeClick(userDescription) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Tarif Oluştur")
        }

        if (isLoading) {
            CircularProgressIndicator()
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Eğer yeni tarif oluşturulduysa göster
        if (!generatedRecipe.isNullOrEmpty()) {
            Text("Oluşturulan Tarif:", style = MaterialTheme.typography.titleMedium)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                MarkdownishText(
                    text = generatedRecipe,
                    modifier = Modifier.padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onSaveClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Kaydet")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = onClearRecipe,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Yeniden Oluştur")
                }
            }
        }
    }
}

/**
 * Basit bir kart bileşeni, veritabanından gelen tarifin kısa önizlemesini gösterir.
 *
 * @param recipe Gösterilecek `RecipeEntity`.
 * @param onSelect Bu tarife tıklandığında çağrılır.
 */
@Composable
fun RecipeCard(recipe: RecipeEntity, onSelect: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onSelect() }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Tarih formatlama
            val formattedDateTime = remember(recipe.dateTime) {
                try {
                    recipe.dateTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"))
                } catch (e: Exception) {
                    "Geçersiz Tarih"
                }
            }

            Text("Tarif No: ${recipe.recipeId}", style = MaterialTheme.typography.bodySmall)
            Text(formattedDateTime, style = MaterialTheme.typography.bodySmall)

            // Tarif metninin yalnızca ilk 100 karakteri
            val snippet = recipe.recipeText.take(100) +
                    if (recipe.recipeText.length > 100) "..." else ""
            MarkdownishText(
                text = snippet,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * Seçili malzeme satırını uygun biçimde gösteren yardımcı fonksiyon.
 *
 * @param ing Seçili malzeme bilgisi.
 * @return Görsel veya loglamak için uygun string.
 */
private fun formatIngredientLine(ing: SelectedIngredient): String {
    val realMeasured = ing.amount * ing.baseAmount
    return "- ${ing.name} (${formatNumber(realMeasured)} ${ing.unit})"
}

/**
 * Gemini API'yi çağırmak için kullanılan yardımcı sınıf.
 *
 * @property apiKey Google Gemini API anahtarını içerir.
 */
class GeminiHelper(private val apiKey: String) {
    private val client = OkHttpClient()

    /**
     * Gemini modeline `prompt` göndererek içerik üretir.
     *
     * @param prompt İstek metni.
     * @return Üretilen tarif metni veya hata mesajı.
     */
    suspend fun callGemini(prompt: String): String = withContext(Dispatchers.IO) {
        val requestBodyJson = """
        {
          "contents": [
            {
              "parts": [
                { "text": "$prompt" }
              ]
            }
          ]
        }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
            .header("Content-Type", "application/json")
            .post(requestBodyJson.toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        val statusCode = response.code

        Log.d("GeminiHelper", "Gemini response status code: $statusCode")
        Log.d("GeminiHelper", "Gemini raw response body: $body")

        if (!response.isSuccessful) {
            return@withContext "Request failed: $statusCode\n$body"
        }

        val jsonObj = JSONObject(body)
        val candidatesArray = jsonObj.optJSONArray("candidates")
            ?: return@withContext "No candidates field."

        if (candidatesArray.length() == 0) {
            return@withContext "Candidates array was empty."
        }

        val firstCandidate = candidatesArray.getJSONObject(0)
        val contentObj = firstCandidate.optJSONObject("content")
            ?: return@withContext "No content object in first candidate."

        val partsArray = contentObj.optJSONArray("parts")
            ?: return@withContext "No parts in content object."

        if (partsArray.length() == 0) {
            return@withContext "Parts array is empty."
        }

        val builder = StringBuilder()
        for (i in 0 until partsArray.length()) {
            val partObj = partsArray.getJSONObject(i)
            val textPart = partObj.optString("text", "")
            builder.append(textPart)
        }

        builder.toString().trim()
    }
}

/**
 * Son derece basit bir markdown (stil benzeri) metin gösterimi.
 *
 * @param text Gösterilecek metin.
 * @param modifier Ek Modifier parametreleri.
 */
@Composable
fun MarkdownishText(text: String, modifier: Modifier = Modifier) {
    val annotated = buildAnnotatedString {
        var currentIndex = 0
        while (currentIndex < text.length) {
            when {
                text.startsWith("**", currentIndex) -> {
                    val end = text.indexOf("**", startIndex = currentIndex + 2)
                    if (end == -1) {
                        append(text.substring(currentIndex))
                        currentIndex = text.length
                    } else {
                        val boldContent = text.substring(currentIndex + 2, end)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(boldContent) }
                        currentIndex = end + 2
                    }
                }
                text.startsWith("*", currentIndex) -> {
                    val end = text.indexOf("*", startIndex = currentIndex + 1)
                    if (end == -1) {
                        append(text.substring(currentIndex))
                        currentIndex = text.length
                    } else {
                        val italicContent = text.substring(currentIndex + 1, end)
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italicContent) }
                        currentIndex = end + 1
                    }
                }
                else -> {
                    val nextSpecial = sequenceOf(
                        text.indexOf("**", currentIndex),
                        text.indexOf("*", currentIndex)
                    ).filter { it >= 0 }.minOrNull() ?: text.length
                    append(text.substring(currentIndex, nextSpecial))
                    currentIndex = nextSpecial
                }
            }
        }
    }
    Text(annotated, modifier)
}

/**
 * Veritabanında tarif bilgilerini saklamak için kullanılan varlık (Entity).
 *
 * @property recipeId Tarifin birincil anahtar ID'si.
 * @property mealId İsteğe bağlı öğün kimliği (null olabilir).
 * @property recipeText Üretilen veya kaydedilen tarif metni.
 * @property dateTime Tarifin oluşturulma/kaydedilme zamanı.
 * @property category Seçili kategori (isteğe bağlı).
 * @property ingredientsJson Tarifle ilişkili malzemelerin JSON şeklindeki kaydı.
 */
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true)
    val recipeId: Int = 0,
    val mealId: Int?,
    val recipeText: String,
    val dateTime: LocalDateTime,
    val category: String?,
    val ingredientsJson: String
)
