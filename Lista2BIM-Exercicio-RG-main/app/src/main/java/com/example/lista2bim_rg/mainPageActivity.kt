package com.example.apprglistbruno

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.lista2bim_rg.R
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.regex.Pattern

class mainPageActivity : AppCompatActivity() {
    private lateinit var fotoDocumento: ImageView
    private lateinit var resultadoView: TextView

    private val REQUEST_IMAGE_PICK = 1
    private val dateFormatter: DateTimeFormatter by lazy {
        DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Mantemos o mesmo layout file (main_page.xml)
        setContentView(R.layout.main_page)

        fotoDocumento = findViewById(R.id.imageViewRG)
        resultadoView = findViewById(R.id.labelResult)

        val botaoImagem = findViewById<Button>(R.id.btnSendImage)
        botaoImagem.setOnClickListener {
            // Preferimos ACTION_OPEN_DOCUMENT para permitir acesso persistente e limitar a imagens
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            startActivityForResult(intent, REQUEST_IMAGE_PICK)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_IMAGE_PICK && resultCode == Activity.RESULT_OK && data != null) {
            val imageUri: Uri? = data.data
            if (imageUri != null) {
                contentResolver.takePersistableUriPermission(
                    imageUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                val inputStream = contentResolver.openInputStream(imageUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                fotoDocumento.setImageBitmap(bitmap)

                // UX: nome do arquivo (se existir) e aviso de processamento
                getDisplayName(imageUri)?.let { name ->
                    Toast.makeText(this, "Imagem carregada: $name", Toast.LENGTH_SHORT).show()
                }
                resultadoView.text = "Processando documento..."
                resultadoView.setTextColor(Color.parseColor("#1A73E8")) // azul

                val image = InputImage.fromBitmap(bitmap, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val texto = visionText.text

                        val cpf = extractCPF(texto)
                        val dataNascimento = extractDataNascimento(texto)
                        val dataValidade = extractDataValidade(texto)

                        if (cpf != null && dataNascimento != null && dataValidade != null) {
                            if (validateRG(cpf, dataNascimento, dataValidade)) {
                                resultadoView.text = "Documento válido ✅"
                                resultadoView.setTextColor(Color.parseColor("#0F9D58")) // verde
                            } else {
                                resultadoView.text = "Documento inválido ❌"
                                resultadoView.setTextColor(Color.parseColor("#D93025")) // vermelho
                            }
                        } else {
                            resultadoView.text = "Dados insuficientes ❌"
                            resultadoView.setTextColor(Color.parseColor("#D93025"))
                        }
                    }
                    .addOnFailureListener { e ->
                        resultadoView.text = "Erro no reconhecimento: ${e.message}"
                        resultadoView.setTextColor(Color.parseColor("#D93025"))
                    }
            } else {
                Toast.makeText(this, "Nenhuma imagem selecionada.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun validateRG(cpf: String, dataNascimentoRaw: String, dataValidadeRaw: String): Boolean {
        return try {
            val nascimento = LocalDate.parse(normalizeDate(dataNascimentoRaw), dateFormatter)
            val validade = LocalDate.parse(normalizeDate(dataValidadeRaw), dateFormatter)
            val hoje = LocalDate.now()

            val nascimentoOk = nascimento.isBefore(hoje) // precisa ser no passado
            val validadeOk = !validade.isBefore(hoje)    // não pode estar vencido
            val cpfOk = isValidCPF(cpf)

            cpfOk && nascimentoOk && validadeOk
        } catch (e: DateTimeParseException) {
            false
        }
    }

    private fun isValidCPF(cpf: String): Boolean {
        val clean = cpf.replace(Regex("[^0-9]"), "")
        if (clean.length != 11) return false
        if (clean.all { it == clean[0] }) return false

        val digits = clean.map { it.toString().toInt() }

        val first = calculateCheckDigit(digits.take(9), 10)
        if (first != digits[9]) return false

        val second = calculateCheckDigit(digits.take(10), 11)
        if (second != digits[10]) return false

        return true
    }

    private fun calculateCheckDigit(digits: List<Int>, startWeight: Int): Int {
        val sum = digits.mapIndexed { index, d -> d * (startWeight - index) }.sum()
        val remainder = sum % 11
        return if (remainder < 2) 0 else 11 - remainder
    }

    private fun extractCPF(text: String): String? {
        // Uma regex única (com e sem máscara)
        val regex = Pattern.compile("""\b(\d{3}\.\d{3}\.\d{3}-\d{2}|\d{11})\b""")
        val m = regex.matcher(text)
        return if (m.find()) m.group(1) else null
    }

    private fun extractDataNascimento(text: String): String? {
        // Palavras-chave e busca por data nas linhas próximas
        val birthKeywords = listOf("nascimento", "nasc", "data de nascimento", "dt nasc")
        return findDateNearKeywords(text, birthKeywords)
    }

    private fun extractDataValidade(text: String): String? {
        val validityKeywords = listOf("validade", "valid", "vencimento", "venc", "expira")
        return findDateNearKeywords(text, validityKeywords)
    }

    private fun findDateNearKeywords(text: String, keywords: List<String>): String? {
        val lines = text.split("\n")
        val dateRegex = Pattern.compile("""\b(\d{1,2})[\/\-.](\d{1,2})[\/\-.](\d{4})\b""")

        for (i in lines.indices) {
            val lineLower = lines[i].lowercase(Locale.getDefault())
            if (keywords.any { kw -> lineLower.contains(kw) }) {
                for (j in i..minOf(i + 2, lines.lastIndex)) {
                    val m = dateRegex.matcher(lines[j])
                    if (m.find()) {
                        return m.group()
                    }
                }
            }
        }

        // fallback: tenta no texto todo
        val mAll = dateRegex.matcher(text)
        return if (mAll.find()) mAll.group() else null
    }

    private fun normalizeDate(raw: String): String {
        // Normaliza 12-05-2024 ou 12.05.2024 para 12/05/2024
        return raw.trim().replace('-', '/').replace('.', '/')
    }

    private fun getDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
