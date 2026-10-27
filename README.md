# Tellauncher

Launcher Android pribadi dengan Kotlin dan Jetpack Compose. Minimum Android 8.0 (API 26).

## Cara pakai

- Home menampilkan jam kecil di tengah atas dan tanggal di bawahnya (`EEEE, dd-MM-yyyy`, misalnya `Senin, 02-11-2026`). Format waktu dan bahasa nama hari mengikuti perangkat.
- **Show seconds** di Settings menambahkan detik pada jam Home (default nonaktif). Ketuk jam untuk membuka alarm; ketuk tanggal untuk membuka kalender. Saat double tap lock aktif, satu ketukan menunggu deteksi double tap agar alarm/kalender tidak terbuka saat mengunci HP.
- Swipe ke atas membuka drawer layar penuh.
- Aktifkan **Show drawer** di Settings untuk menampilkan tombol drawer di bawah Home (default nonaktif). Tombol ditempatkan di atas inset navigation bar; jam dan tanggal tetap di bawah inset atas. Swipe ke atas tetap tersedia.
- Tekan lama Home membuka Pengaturan.
- Aktifkan **Double tap to lock the app** di Pengaturan untuk mengunci HP dengan dua ketukan di Home (Android 9+). Jika layanan aksesibilitas belum aktif, dialog akan mengarahkan ke Pengaturan Aksesibilitas. Aktifkan **Tellauncher — Kunci layar**, lalu kembali. Layanan hanya menjalankan aksi kunci layar dan tidak meminta akses isi layar.
- Drawer dimulai dengan List tanpa ikon. Cari aplikasi lewat kolom di bawah; keyboard dibuka saat kolom diketuk.
- Pengaturan menyediakan List/Grid, ikon List, aplikasi tersembunyi, dan pilihan launcher default.
- **App authentication** (default nonaktif) meminta biometrik atau kunci layar perangkat setiap kali aplikasi terpilih dibuka dari drawer, shelf, atau pencarian. Mengaktifkan/mematikan fitur dan membuka **Protected apps** juga meminta autentikasi sistem. Daftar Protected apps menyediakan ikon, pencarian, dan switch; sesi edit berakhir saat keluar atau launcher masuk background. Jika biometrik tidak tersedia, Android memakai pola, PIN, atau password yang sudah dikonfigurasi di HP. Android 11+ memakai dialog gabungan; Android 8–10 memakai konfirmasi kunci layar sistem sebagai fallback. Tidak ada urutan pola lalu PIN buatan launcher. Jika belum ada kunci layar, pengguna harus mengaturnya terlebih dahulu; pembatalan autentikasi tidak membuka aplikasi. Proteksi hanya berlaku pada tiga jalur tersebut; notifikasi, Recent apps, shortcut jam/kalender di Home, App info, launcher lain, dan tautan aplikasi tidak dilindungi. Fitur ini tidak mengenkripsi atau mengisolasi data aplikasi.
- **Theme** menyediakan **System default**, **Light mode**, dan **Night mode**. Default mengikuti tema perangkat; pilihan disimpan di DataStore dan diterapkan langsung. Night mode memakai charcoal `#101010`, sedangkan Light mode memakai abu terang `#F5F5F5`. Jam, drawer, dialog, dan ikon system bar mengikuti tema yang dipilih.
- **Show launcher settings in drawer** mengatur visibilitas item Settings di List/Grid, termasuk shelf dan hasil pencarian (default aktif). Tekan lama Home tetap membuka Settings saat item disembunyikan.
- **Custom shelf** membatasi drawer ke satu shelf aktif. Aktifkan untuk membuat shelf pertama; gunakan **Add shelf** untuk menambah, radio button untuk memilih yang aktif, dan ketuk nama shelf untuk mengedit. Aplikasi yang dipilih naik ke **Selected**, lalu tekan **Save**. Shelf buatan menampilkan semua aplikasi yang dipilih, termasuk Hidden apps. Pencarian tetap menjangkau semua aplikasi.
- Saat Custom shelf aktif dan ada shelf, ketuk nama shelf kecil di atas drawer untuk berganti shelf atau memilih **All apps**. All apps tetap mengikuti Hidden apps. Pemilih ini disembunyikan saat Custom shelf nonaktif atau tidak ada shelf. Pilihan terakhir tersimpan, dan mengganti shelf mengosongkan pencarian.
- **Hidden apps** dan editor shelf menyediakan pencarian nama aplikasi di bawah daftar. Pencarian hanya memfilter tampilan; status switch dan pilihan aplikasi di luar hasil pencarian tetap dipertahankan saat disimpan.
- Toggle aktif pada Aplikasi tersembunyi menyembunyikan aplikasi dari drawer saat pencarian kosong. Aplikasi tersembunyi tetap dapat dicari dan dibuka. Matikan toggle untuk menampilkannya kembali di drawer.
- Back dari pengelolaan aplikasi tersembunyi kembali ke Pengaturan. Back dari drawer/Pengaturan kembali ke Home.
- Saat kembali ke Home, pencarian dikosongkan.

Preferensi disimpan melalui DataStore. Aplikasi tersembunyi berlaku untuk seluruh aktivitas launcher dari paket yang sama. Daftar aplikasi menggunakan PackageManager, diperbarui saat launcher aktif kembali dan saat ada perubahan paket. Profil kerja belum menjadi cakupan MVP.

Tidak memakai backend, database, akun, atau izin internet.

## Menjalankan

Buka folder proyek di Android Studio, gunakan JDK 17 dan Android SDK 36. Atur lokasi SDK melalui Android Studio atau `local.properties` (`sdk.dir=...`).

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Build debug memakai nama **Tellauncher Dev** dan ID `id.tellolabs.launcher.dev`, sehingga bisa dipasang berdampingan dengan production. Preferensi dan aksesibilitas masing-masing terpisah. Untuk build sekaligus install, gunakan `./gradlew installDebug`.

Jalankan Tellauncher, tekan lama Home, lalu pilih **Jadikan launcher default**. Untuk menggantinya kembali, pilih aplikasi Home lain lewat Pengaturan Android → Aplikasi → Aplikasi default → Aplikasi Home (nama menu bergantung perangkat).

## Cek manual singkat

1. Buka drawer dengan swipe, cari aplikasi, dan jalankan.
2. Ganti List/Grid dan opsi ikon List.
3. Sembunyikan aplikasi, pastikan hilang dari All apps/drawer biasa saat pencarian kosong, tetapi tetap bisa dicari dan dibuka di List/Grid. Tambahkan ke shelf buatan dan pastikan tetap muncul di shelf tersebut. Beralih ke All apps untuk menyembunyikannya lagi.
4. Kembali dengan tombol Home dan pastikan jam tampil serta pencarian kosong.
5. Restart launcher/perangkat dan pastikan preferensi tetap tersimpan.

Pengelolaan dependency Compose mengikuti [dokumentasi BOM Android](https://developer.android.com/develop/ui/compose/bom).
