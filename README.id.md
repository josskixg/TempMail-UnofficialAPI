<p align="center">
  <img src="./banner.svg" alt="TempMail Unofficial API Wrapper" width="800">
</p>

# 📬 TempMail Unofficial API — Multi-Language Wrappers

<p align="center">
  <strong>v1.1.0</strong> — Rilis 2026-07-02 &nbsp;|&nbsp; <a href="./RELEASE_NOTES.md">Catatan Rilis</a> &nbsp;|&nbsp; <a href="./CHANGELOG.md">Changelog</a> &nbsp;|&nbsp; <a href="./docs/plan/PLAN_v1.1.0.md">Rencana v1.1.0</a>
</p>

[🇬🇧 English](./README.md) | [🇮🇩 Bahasa Indonesia](./README.id.md) | [🇨🇳 中文](./README.cn.md)

---

Kumpulan **unofficial wrapper** untuk berbagai layanan Temporary Email, ditulis di **7 bahasa pemrograman**. Satu repo, satu tujuan: bikin disposable email secara programatik tanpa ribet.

## 🎯 Bahasa yang Didukung

| Bahasa | Folder | Package Manager | Status |
|--------|--------|-----------------|--------|
| Go | [`/go`](./go) | `go get` | ✅ Selesai |
| Python | [`/python`](./python) | `pip` | ✅ Selesai |
| Java | [`/java`](./java) | `Maven` / `Gradle` | ✅ Selesai |
| PHP | [`/php`](./php) | `Composer` | ✅ Selesai |
| JavaScript | [`/javascript`](./javascript) | `npm` / `yarn` | ✅ Selesai |
| Rust | [`/rust`](./rust) | `cargo` | ✅ Selesai |
| C# | [`/csharp`](./csharp) | `NuGet` | ✅ Selesai |

## 🌐 Layanan TempMail yang Didukung

Library wrapper saat ini mendukung **16 layanan email sementara**:

| # | Layanan | Website | Tipe API | Autentikasi | Tingkat Kesulitan | Status |
|---|---------|---------|----------|-------------|-------------------|--------|
| 1 | Mail.tm | [mail.tm](https://mail.tm) | REST+JSON | Bearer Token | ✅ Mudah | Aktif |
| 2 | GuerrillaMail | [guerrillamail.com](https://www.guerrillamail.com) | REST | Session Token | ⚡ Sedang | Aktif |
| 3 | YOPmail | [yopmail.com](https://yopmail.com) | HTML Scraping | Tidak ada | ⚡ Sedang | Aktif |
| 4 | Dropmail | [dropmail.me](https://dropmail.me) | GraphQL | Token (otomatis) | ✅ Mudah | Aktif |
| 5 | 1secemail | [1secemail.com](https://www.1secemail.com) | REST | Tidak ada | ✅ Mudah | Aktif |
| 6 | Ncaori Mail+ | [nca.my.id](https://www.nca.my.id) | REST+JSON | Tidak ada | ✅ Mudah | Aktif |
| 7 | Zoromail | [zoromail.com](https://zoromail.com) | REST+JSON | Tidak ada | ✅ Mudah | Aktif |
| 8 | Tempmail.lol | [tempmail.lol](https://tempmail.lol) | REST+JSON | Token | ✅ Mudah | Aktif |
| 9 | Tempmailc | [tempmailc.com](https://tempmailc.com) | REST+JSON | Tidak ada | ✅ Mudah | Aktif |
| 10 | Temp-mail.io | [temp-mail.io](https://temp-mail.io) | REST+JSON | Bearer Token | ⚡ Sedang | Aktif |
| 11 | Tempmail.plus | [tempmail.plus](https://tempmail.plus) | REST+JSON | Tidak ada | ✅ Mudah | Aktif |
| 12 | Emailfake | [emailfake.com](https://emailfake.com) | HTML Scraping | Cookie | ⚡ Sedang | Aktif |
| 13 | Generator.email | [generator.email](https://generator.email) | HTML Scraping | Cookie | ⚡ Sedang | Aktif |
| 14 | Mailnesia | [mailnesia.com](https://mailnesia.com) | HTML Scraping | Tidak ada | ⚡ Sedang | Aktif |
| 15 | 10minutemail | [10minutemail.net](https://10minutemail.net) | HTML Scraping | Cookie Session | ⚡ Sedang | Aktif (via 10minutemail.net) |
| 16 | Email-temp | [email-temp.com](https://email-temp.com) | HTML Scraping | Cookie | ⚡ Sedang | Aktif |

## 📁 Struktur Project

```
TempMail-UnofficialAPI/
├── go/                   # Go wrapper
├── python/               # Python wrapper
├── java/                 # Java wrapper
├── php/                  # PHP wrapper
├── javascript/           # Node.js / JavaScript wrapper
├── rust/                 # Rust wrapper
├── csharp/               # C# / .NET wrapper
├── docs/
│   └── plan/
│       └── PLAN_v1.1.0.md  # Rencana implementasi v1.1.0
├── README.md             # English (default)
├── README.id.md          # Bahasa Indonesia
├── README.cn.md          # 中文
├── RELEASE_NOTES.md      # Catatan rilis per versi
├── CHANGELOG.md          # Riwayat perubahan lengkap
├── LICENSE               # Apache 2.0
└── NOTICE                # Atribusi & disclaimer
```

## 🚀 Mulai Cepat

Setiap bahasa punya README-nya masing-masing. Klik folder di atas untuk detail instalasi dan contoh penggunaan.

### Contoh Umum (Pseudocode)

```
// 1. Generate email sementara
email = tempmail.generate()
// → "random123@mail.tm"

// 2. Cek inbox
messages = tempmail.get_inbox(email)

// 3. Baca pesan
if messages.length > 0:
    content = tempmail.read_message(messages[0].id)

// 4. Hapus (opsional, auto-expire juga bisa)
tempmail.delete(email)
```

## ⚡ Interface API (Semua Bahasa)

Semua wrapper mengimplementasikan interface yang konsisten:

| Method | Deskripsi | Return |
|--------|-----------|--------|
| `generate_email()` | Generate alamat email sementara | `string` (email address) |
| `get_inbox(email)` | Ambil daftar pesan | `[]Message` |
| `read_message(id)` | Baca isi pesan | `MessageDetail` |
| `delete_email(email)` | Hapus email (cleanup) | `bool` |
| `wait_for_email(email, timeout)` | Polling tunggu pesan baru | `Message` atau `null` |

## 📦 Data Model

### Message
- `id` — ID unik pesan
- `from` — Alamat pengirim
- `subject` — Subjek email
- `date` — Timestamp penerimaan

### MessageDetail (extends Message)
- `body_text` — Isi email plain text
- `body_html` — Isi email HTML (jika ada)
- `attachments` — Daftar attachment metadata

## 🛡️ Disclaimer

> **⚠️ PENTING**
>
> - Project ini **UNOFFICIAL** — tidak berafiliasi dengan layanan tempmail manapun.
> - API endpoint bisa berubah sewaktu-waktu tanpa pemberitahuan.
> - Gunakan untuk **testing, development, atau automasi personal** saja.
> - Jangan gunakan untuk spam, fraud, atau aktivitas ilegal.
> - Beberapa layanan punya rate limit — gunakan dengan bijak.

## 🤝 Kontribusi

Mau nambah bahasa? Mau fix bug? Silakan:

1. Fork repo ini di [github.com/josskixg/TempMail-UnofficialAPI](https://github.com/josskixg/TempMail-UnofficialAPI/fork)
2. Buat branch: `feat/add-kotlin-wrapper`
3. Commit & push
4. Buka Pull Request

### Aturan Kontribusi

- Ikuti struktur interface yang sudah ada
- Tambahkan contoh penggunaan di README per bahasa
- Jangan hardcode API key (gunakan environment variable)
- Test dulu sebelum PR

## 🗺️ Roadmap

v1.1.0 dirilis dengan 16 layanan dan 7 bahasa. Rencana rilis selanjutnya:

- **Ketahanan Scraping & YOPmail yang Ditingkatkan** — perbaikan bypass anti-bot dan dukungan headless
- **Dukungan WebSocket** untuk Dropmail.me dan Mail.tm (berlangganan inbox secara real-time)
- **Bahasa SDK Baru** — Kotlin, Swift, dan Ruby
- **CLI tool** — alat command-line terpadu untuk mengelola kotak surat sementara langsung dari terminal Anda

Kontribusi dibuka — lihat [CONTRIBUTING.md](./CONTRIBUTING.md) dan buka [PR](https://github.com/josskixg/TempMail-UnofficialAPI/pulls) atau [Issue](https://github.com/josskixg/TempMail-UnofficialAPI/issues).

## 📄 Lisensi

Apache License 2.0 — lihat [LICENSE](./LICENSE) dan [NOTICE](./NOTICE).

---

<p align="center">
  <strong>🌟 Star repo ini kalau membantu project kamu!</strong><br>
  Dibuat dengan 🫠 oleh komunitas, untuk komunitas.
</p>
