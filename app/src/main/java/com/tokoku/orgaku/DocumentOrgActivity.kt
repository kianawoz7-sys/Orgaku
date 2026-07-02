package com.tokoku.orgaku

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tokoku.orgaku.databinding.ActivityDocumentOrgBinding
import com.tokoku.orgaku.ui.document.FragmentDocument

class DocumentOrgActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDocumentOrgBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDocumentOrgBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, FragmentDocument())
                .commit()
        }
    }
}
