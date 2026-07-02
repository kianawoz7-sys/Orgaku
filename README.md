<h1 align="center">
  <br>
  <img src="app/src/main/res/drawable/ic_logo_orgaku.xml" width="100" alt="Orgaku Logo">
  <br>
  Orgaku
  <br>
</h1>

<h4 align="center">Aplikasi manajemen organisasi berbasis Android yang membantu tim kamu lebih terorganisir.</h4>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green?style=for-the-badge&logo=android" />
  <img src="https://img.shields.io/badge/Language-Kotlin-purple?style=for-the-badge&logo=kotlin" />
  <img src="https://img.shields.io/badge/Firebase-Firestore-orange?style=for-the-badge&logo=firebase" />
  <img src="https://img.shields.io/badge/Min%20SDK-30-blue?style=for-the-badge" />
</p>

---

## 📱 Tentang Aplikasi

**Orgaku** adalah aplikasi Android untuk manajemen organisasi yang memudahkan koordinasi antar anggota. Dengan Orgaku, kamu bisa mengelola tugas, jadwal rapat, absensi, dan dokumen organisasi dalam satu platform.

---

## ✨ Fitur Utama

| Fitur | Deskripsi |
|---|---|
| 🔐 **Autentikasi** | Login & Register dengan Email/Password atau Google Sign-In |
| 📋 **Manajemen Tugas** | Buat, kelola, dan pantau progres tugas tim dengan sub-task |
| 📅 **Jadwal Rapat** | Tambah & lihat jadwal meeting organisasi |
| ✅ **Absensi QR Code** | Sistem absensi berbasis scan QR Code |
| 📊 **Rekap Absensi** | Ekspor rekap kehadiran ke file Excel |
| 📁 **Dokumen Organisasi** | Upload dan kelola dokumen organisasi |
| 🔔 **Notifikasi Push** | Notifikasi real-time via OneSignal |
| 👥 **Invite Anggota** | Undang anggota baru ke organisasi |
| 🌙 **Dark Mode** | Dukungan tema gelap |

---

## 🛠️ Tech Stack

- **Language**: Kotlin
- **Architecture**: MVVM (ViewModel + LiveData)
- **Backend**: Firebase (Firestore, Auth, Cloud Messaging)
- **Notification**: OneSignal SDK 5.9.1
- **Networking**: OkHttp3, Retrofit2
- **Image Loading**: Glide
- **QR Code**: ZXing (Scan & Generate)
- **Excel Export**: Apache POI
- **UI**: Material Design 3, ViewBinding, DataBinding, Shimmer Effect

---

## 🚀 Cara Menjalankan Project

### Prasyarat
- Android Studio (Hedgehog atau lebih baru)
- JDK 11
- Android SDK API 30+
- Akun Firebase
- Akun OneSignal *(opsional, untuk notifikasi push)*

### Langkah Setup

**1. Clone repository**
```bash
git clone https://github.com/kianawoz7-sys/Orgaku.git
cd Orgaku
```

**2. Setup Firebase**
- Buat project baru di [Firebase Console](https://console.firebase.google.com/)
- Download file `google-services.json`
- Letakkan file tersebut di folder `app/`
- Aktifkan **Firebase Authentication**, **Firestore**, dan **Cloud Messaging**

**3. Setup `local.properties`**

Buat atau edit file `local.properties` di root project, tambahkan:
```properties
sdk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk
ONESIGNAL_REST_API_KEY=your_onesignal_rest_api_key_here
```

> ⚠️ File `local.properties` **tidak ter-commit ke Git** untuk menjaga keamanan API key kamu.

**4. Sync & Run**
- Buka project di Android Studio
- Klik **File → Sync Project with Gradle Files**
- Jalankan di emulator atau perangkat fisik

---

## 📂 Struktur Project

```
app/src/main/java/com/tokoku/orgaku/
├── data/               # Model data & repository
├── ui/                 # Fragment & ViewModel
│   ├── dashboard/
│   ├── meeting/
│   ├── profile/
│   └── tugas/
├── util/               # Helper & utility class
├── MainActivity.kt     # Entry point utama
├── LoginActivity.kt    # Autentikasi
├── OneSignalHelper.kt  # Push notification helper
└── OrgakuApp.kt        # Application class
```

---

## 🔒 Keamanan

- API keys disimpan di `local.properties` (tidak masuk Git)
- Key diakses melalui `BuildConfig` saat build time
- Autentikasi menggunakan Firebase Auth yang aman

---

## 🤝 Kontribusi

Pull request sangat disambut! Untuk perubahan besar, silakan buka issue terlebih dahulu untuk mendiskusikan apa yang ingin kamu ubah.

1. Fork project ini
2. Buat branch fitur baru (`git checkout -b feature/fitur-keren`)
3. Commit perubahan (`git commit -m 'feat: tambah fitur keren'`)
4. Push ke branch (`git push origin feature/fitur-keren`)
5. Buka Pull Request

---

## 📄 Lisensi

Distributed under the MIT License. See `LICENSE` for more information.

---

<p align="center">
  Dibuat dengan ❤️ menggunakan Kotlin & Firebase
</p>
