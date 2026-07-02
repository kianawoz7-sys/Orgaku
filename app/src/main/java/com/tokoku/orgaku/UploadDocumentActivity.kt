package com.tokoku.orgaku

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tokoku.orgaku.databinding.ActivityUploadDocumentBinding

class UploadDocumentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUploadDocumentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUploadDocumentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnUpload.setOnClickListener {
            // Upload logic
            finish()
        }
    }
}
