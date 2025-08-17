package com.example.apprglistbruno

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.lista2bim_rg.R
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.regex.Pattern
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class mainPageActivity : AppCompatActivity() {
    private lateinit var imageView: ImageView
    private lateinit var textViewResult: TextView
    private val REQUEST_IMAGE_PICK = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_page)

        imageView = findViewById(R.id.imageViewRG)
        textViewResult = findViewById(R.id.labelResult)

        val btnSelectImage = findViewById<Button>(R.id.btnSendImage)
        btnSelectImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*" // <- todos os tipos
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            startActivityForResult(intent, REQUEST_IMAGE_PICK)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_IMAGE_PICK && resultCode == Activity.RESULT_OK && data != null) {
            val imageUri: Uri? = data.data
            if (imageUri != null) {
                val inputStream = contentResolver.openInputStream(imageUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                imageView.setImageBitmap(bitmap)

                val image = InputImage.fromBitmap(bitmap, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        var cpf = extractCPF(visionText.text)
                        var datanascimento = extractDataNascimento(visionText.text)
                        var datavalidade = extractDataValidade(visionText.text)
                        if(cpf != null && datanascimento != null && datavalidade != null){
                            if(validateRG(cpf, datanascimento, datavalidade)){
                                textViewResult.text = "Válido! ✅"
                                textViewResult.setTextColor(Color.GREEN)
                            }else{
                                textViewResult.text = "Inválido! ❌"
                                textViewResult.setTextColor(Color.RED)
                            }
                        }else{
                            textViewResult.text = "Inválido! ❌"
                            textViewResult.setTextColor(Color.RED)
                        }
                    }
                    .addOnFailureListener { e ->
                        textViewResult.text = "Erro: ${e.message}"
                    }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun validateRG (cpf: String, dataNascimento: String, dataValidade: String): Boolean {
        try{
            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            val today = LocalDate.now()

            val inputDateNascimento = LocalDate.parse(dataNascimento, formatter)
            val validNascimento = inputDateNascimento.isBefore(today)

            val inputDateValidade = LocalDate.parse(dataValidade, formatter)
            val validValidade = inputDateValidade.isBefore(today)

            val validCPF = isValidCPF(cpf)

            if(!validNascimento && validValidade && validCPF) return false

            return true

        }catch (e: DateTimeParseException){
            return false
        }
    }

    private fun isValidCPF(cpf: String): Boolean {
        // Remove todas as pontuações e espaços
        val cleanCPF = cpf.replace(Regex("[^0-9]"), "")

        // Verifica se tem exatamente 11 dígitos
        if (cleanCPF.length != 11) {
            return false
        }

        // Verifica se todos os dígitos são iguais (CPFs inválidos conhecidos)
        if (cleanCPF.all { it == cleanCPF[0] }) {
            return false
        }

        // Converte para lista de inteiros para facilitar o cálculo
        val digits = cleanCPF.map { it.toString().toInt() }

        // Calcula o primeiro dígito verificador
        val firstCheck = calculateCheckDigit(digits.take(9), 10)
        if (firstCheck != digits[9]) {
            return false
        }

        // Calcula o segundo dígito verificador
        val secondCheck = calculateCheckDigit(digits.take(10), 11)
        if (secondCheck != digits[10]) {
            return false
        }

        return true
    }

    private fun calculateCheckDigit(digits: List<Int>, startWeight: Int): Int {
        val sum = digits.mapIndexed { index, digit ->
            digit * (startWeight - index)
        }.sum()

        val remainder = sum % 11
        return if (remainder < 2) 0 else 11 - remainder
    }

    private fun extractCPF(text: String): String? {
        // Padrões para CPF: xxx.xxx.xxx-xx ou xxxxxxxxxxx
        val patterns = listOf(
            "\\b\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}\\b", // com pontos e hífen
            "\\b\\d{11}\\b" // apenas números
        )

        for (pattern in patterns) {
            val regex = Pattern.compile(pattern)
            val matcher = regex.matcher(text)
            if (matcher.find()) {
                val cpf = matcher.group()
                // Valida se tem 11 dígitos quando removemos formatação
                val numbersOnly = cpf.replace("[^0-9]".toRegex(), "")
                if (numbersOnly.length == 11) {
                    return cpf
                }
            }
        }
        return null
    }

    private fun extractDataNascimento(text: String): String? {
        // Padrões para data: dd/mm/yyyy, dd-mm-yyyy, dd.mm.yyyy
        // Procura por "NASCIMENTO", "NASC", "DATA DE NASCIMENTO" nas proximidades
        val datePatterns = listOf(
            "\\b\\d{2}[/\\-.]\\d{2}[/\\-.]\\d{4}\\b",
            "\\b\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{4}\\b"
        )

        val birthKeywords = listOf("nascimento", "nasc", "data de nascimento", "dt nasc")

        val lines = text.split("\n")

        // Procura linha que contém palavra-chave de nascimento
        for (i in lines.indices) {
            val line = lines[i].toLowerCase()
            if (birthKeywords.any { keyword -> line.contains(keyword) }) {
                // Procura data na mesma linha ou nas próximas 2 linhas
                for (j in i..minOf(i + 2, lines.size - 1)) {
                    for (pattern in datePatterns) {
                        val regex = Pattern.compile(pattern)
                        val matcher = regex.matcher(lines[j])
                        if (matcher.find()) {
                            return matcher.group()
                        }
                    }
                }
            }
        }

        return null
    }

    private fun extractDataValidade(text: String): String? {
        // Padrões similares para data de validade
        val datePatterns = listOf(
            "\\b\\d{2}[/\\-.]\\d{2}[/\\-.]\\d{4}\\b",
            "\\b\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{4}\\b"
        )

        val validityKeywords = listOf("validade", "valid", "vencimento", "venc", "expira")

        val lines = text.split("\n")

        for (i in lines.indices) {
            val line = lines[i].toLowerCase()
            if (validityKeywords.any { keyword -> line.contains(keyword) }) {
                // Procura data na mesma linha ou nas próximas 2 linhas
                for (j in i..minOf(i + 2, lines.size - 1)) {
                    for (pattern in datePatterns) {
                        val regex = Pattern.compile(pattern)
                        val matcher = regex.matcher(lines[j])
                        if (matcher.find()) {
                            return matcher.group()
                        }
                    }
                }
            }
        }

        return null
    }

}