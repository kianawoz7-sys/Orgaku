package com.tokoku.orgaku.ui.document

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tokoku.orgaku.Document
import com.tokoku.orgaku.DocumentAdapter
import com.tokoku.orgaku.databinding.DialogAddDocumentBinding
import com.tokoku.orgaku.databinding.FragmentDocumentBinding
import com.tokoku.orgaku.util.SessionManager

class FragmentDocument : Fragment() {

    private var _binding: FragmentDocumentBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: DocumentAdapter
    private lateinit var sessionManager: SessionManager
    private var documentList = mutableListOf<Document>()
    // Hold the Firestore listener so we can remove it on destroy
    private var listenerRegistration: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDocumentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        sessionManager = SessionManager(requireContext())

        setupRecyclerView()
        setupSearchView()
        checkUserRole()
        fetchDocuments()

        binding.btnBack.setOnClickListener {
            requireActivity().finish()
        }

        binding.fabAdd.setOnClickListener {
            showAddDocumentDialog()
        }
    }

    private fun setupRecyclerView() {
        adapter = DocumentAdapter(emptyList())
        binding.rvDocuments.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDocuments.adapter = adapter
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterDocuments(newText ?: "")
                return true
            }
        })
    }

    private fun filterDocuments(query: String) {
        val filteredList = documentList.filter {
            it.title.contains(query, ignoreCase = true)
        }
        adapter.updateList(filteredList)
    }

    private fun checkUserRole() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                val role = document.getString("role")?.uppercase()
                if (role == "KETUA" || role == "CHAIRMAN") {
                    binding.fabAdd.visibility = View.VISIBLE
                }
            }
    }

    private fun fetchDocuments() {
        val uid = auth.currentUser?.uid ?: run {
            documentList = mutableListOf()
            adapter.updateList(emptyList())
            if (_binding != null) binding.layoutEmpty.visibility = View.VISIBLE
            return
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                if (_binding == null) return@addOnSuccessListener

                val organisasiId = userDoc.getString("organisasiId")
                if (organisasiId.isNullOrBlank() || organisasiId == "-") {
                    documentList = mutableListOf()
                    adapter.updateList(emptyList())
                    binding.layoutEmpty.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                // Attach real-time listener scoped to this org
                listenerRegistration?.remove()
                listenerRegistration = db.collection("documents")
                    .whereEqualTo("organisasiId", organisasiId)
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .addSnapshotListener { value, error ->
                        if (_binding == null) return@addSnapshotListener
                        if (error != null) {
                            Log.e("FragmentDocument", "Listen failed.", error)
                            return@addSnapshotListener
                        }

                        if (value != null) {
                            val documents = value.documents.mapNotNull { doc ->
                                try {
                                    val document = doc.toObject(Document::class.java)
                                    document?.id = doc.id
                                    document
                                } catch (e: Exception) {
                                    Log.e("DATA_ERROR", "Mapping failed for ${doc.id}", e)
                                    null
                                }
                            }
                            documentList = documents.toMutableList()
                            adapter.updateList(documentList)

                            binding.layoutEmpty.visibility =
                                if (documentList.isEmpty()) View.VISIBLE else View.GONE
                        }
                    }
            }
            .addOnFailureListener {
                if (_binding != null) {
                    documentList = mutableListOf()
                    adapter.updateList(emptyList())
                    binding.layoutEmpty.visibility = View.VISIBLE
                }
            }
    }

    private fun showAddDocumentDialog() {
        val dialogBinding = DialogAddDocumentBinding.inflate(layoutInflater)
        val categories = arrayOf("Proposal", "LPJ", "Surat", "Lainnya")
        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            categories
        )
        dialogBinding.spinnerCategory.adapter = spinnerAdapter

        AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("Simpan") { _, _ ->
                val title = dialogBinding.edtTitle.text.toString().trim()
                val url = dialogBinding.edtUrl.text.toString().trim()
                val category = dialogBinding.spinnerCategory.selectedItem.toString()

                if (title.isEmpty() || url.isEmpty()) {
                    Toast.makeText(requireContext(), "Harap isi semua bidang", Toast.LENGTH_SHORT)
                        .show()
                } else if (!url.startsWith("http")) {
                    Toast.makeText(
                        requireContext(),
                        "URL tidak valid. Harus diawali dengan http",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    saveDocument(title, category, url)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun saveDocument(title: String, category: String, url: String) {
        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(requireContext(), "Gagal: Anda belum login", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                val organisasiId = userDoc.getString("organisasiId")
                if (organisasiId.isNullOrBlank() || organisasiId == "-") {
                    Toast.makeText(requireContext(), "Gagal: Anda belum bergabung ke organisasi", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val docId = db.collection("documents").document().id
                val document = Document(
                    id = docId,
                    title = title,
                    category = category,
                    driveUrl = url,
                    createdAt = System.currentTimeMillis(),
                    organisasiId = organisasiId
                )

                db.collection("documents").document(docId).set(document)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Dokumen berhasil disimpan", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Gagal menyimpan: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Gagal memuat data organisasi", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Always remove the Firestore listener to avoid memory leaks
        listenerRegistration?.remove()
        listenerRegistration = null
        _binding = null
    }
}