# Tellauncher

Launcher Android pribadi dengan Kotlin dan Jetpack Compose. Minimum Android 8.0 (API 26).

## Cara pakai

- Home menampilkan jam kecil di tengah atas dan tanggal di bawahnya (`EEEE, dd-MM-yyyy`, misalnya `Senin, 02-11-2026`). Format waktu dan bahasa nama hari mengikuti perangkat.
- Swipe ke atas membuka drawer layar penuh.
- Tekan lama Home membuka Pengaturan.
- Aktifkan **Double tap to lock the app** di Pengaturan untuk mengunci HP dengan dua ketukan di Home (Android 9+). Jika layanan aksesibilitas belum aktif, dialog akan mengarahkan ke Pengaturan Aksesibilitas. Aktifkan **Tellauncher — Kunci layar**, lalu kembali. Layanan hanya menjalankan aksi kunci layar dan tidak meminta akses isi layar.
- Drawer dimulai dengan List tanpa ikon. Cari aplikasi lewat kolom di bawah; keyboard dibuka saat kolom diketuk.
- Pengaturan menyediakan List/Grid, ikon List, aplikasi tersembunyi, dan pilihan launcher default.
- Toggle aktif pada Aplikasi tersembunyi berarti aplikasi disembunyikan. Matikan toggle untuk menampilkannya kembali.
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

Jalankan Tellauncher, tekan lama Home, lalu pilih **Jadikan launcher default**. Untuk menggantinya kembali, pilih aplikasi Home lain lewat Pengaturan Android → Aplikasi → Aplikasi default → Aplikasi Home (nama menu bergantung perangkat).

## Cek manual singkat

1. Buka drawer dengan swipe, cari aplikasi, dan jalankan.
2. Ganti List/Grid dan opsi ikon List.
3. Sembunyikan aplikasi, pastikan hilang dari drawer dan pencarian, lalu tampilkan kembali.
4. Kembali dengan tombol Home dan pastikan jam tampil serta pencarian kosong.
5. Restart launcher/perangkat dan pastikan preferensi tetap tersimpan.

Pengelolaan dependency Compose mengikuti [dokumentasi BOM Android](https://developer.android.com/develop/ui/compose/bom).

## Release lokal

Release menonaktifkan mode debug dan mengaktifkan optimasi kode serta resource. Signing menggunakan `signing.properties` lokal dengan `storeFile`, `storePassword`, `keyAlias`, dan `keyPassword`. File ini dan direktori `.signing/` diabaikan Git. Simpan backup keduanya secara aman: key yang sama diperlukan untuk update release berikutnya.

```sh
./gradlew assembleRelease
```

Untuk memasang release berdampingan dengan debug, gunakan `./gradlew assembleRelease -PreleaseSideBySide=true`. Varian ini memakai ID `id.tellolabs.launcher.production` dan nama **Tellauncher Production**, dengan pengaturan terpisah. Gunakan opsi yang sama pada update berikutnya.

APK ada di `app/build/outputs/apk/release/app-release.apk`. Release dengan ID asli tidak bisa memperbarui debug yang ditandatangani key berbeda; uninstall akan menghapus pengaturan aplikasi.
