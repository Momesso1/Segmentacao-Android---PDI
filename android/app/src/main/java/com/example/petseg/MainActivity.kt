package com.example.petseg

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var ivOriginal: ImageView
    private lateinit var ivResult: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var segmenter: Segmenter

    private var currentBitmap: Bitmap? = null

    // Seletor de imagem (Photo Picker — não exige permissão de armazenamento)
    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri != null) {
                val bmp = uriToBitmap(uri)
                currentBitmap = bmp
                ivOriginal.setImageBitmap(bmp)
                ivResult.setImageDrawable(null)
                tvStatus.text = getString(R.string.status_ready)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ivOriginal = findViewById(R.id.ivOriginal)
        ivResult = findViewById(R.id.ivResult)
        tvStatus = findViewById(R.id.tvStatus)
        val btnPick = findViewById<Button>(R.id.btnPick)
        val btnRun = findViewById<Button>(R.id.btnRun)

        segmenter = Segmenter(this)

        btnPick.setOnClickListener {
            pickImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        btnRun.setOnClickListener {
            val bmp = currentBitmap
            if (bmp == null) {
                Toast.makeText(this, R.string.toast_no_image, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            tvStatus.text = getString(R.string.status_running)
            // Inferência fora da thread principal
            Thread {
                val start = System.currentTimeMillis()
                val overlay = segmenter.segment(bmp)
                val ms = System.currentTimeMillis() - start
                runOnUiThread {
                    ivResult.setImageBitmap(overlay)
                    tvStatus.text = getString(R.string.status_done, ms)
                }
            }.start()
        }
    }

    override fun onDestroy() {
        segmenter.close()
        super.onDestroy()
    }

    private fun uriToBitmap(uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(contentResolver, uri)
        }.copy(Bitmap.Config.ARGB_8888, true)
    }
}
