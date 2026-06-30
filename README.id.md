<p align="center">
  <img src="./banner.svg" alt="TempMail Unofficial API Wrapper" width="800">
</p>

# 📬 TempMail Unofficial API — Multi-Language Wrappers

<p align="center">
  <strong>v1.0.0</strong> — Rilis 2026-06-30 &nbsp;|&nbsp; <a href="./RELEASE_NOTES.md">Catatan Rilis</a> &nbsp;|&nbsp; <a href="./CHANGELOG.md">Changelog</a>
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

| # | Layanan | Website | API Type | Auth | Tingkat Kesulitan |
|---|---------|---------|----------|------|-------------------|
| 1 | Mail.tm | mail.tm | REST+JSON | Bearer Token | ✅ Mudah |
| 2 | GuerrillaMail | guerrillamail.com | REST | Session Token | ⚡ Sedang |
| 3 | YOPmail | yopmail.com | HTML Scraping | Tidak ada | ⚡ Sedang |
| 4 | Dropmail | dropmail.me | GraphQL | Token (otomatis) | ✅ Mudah |
| 5 | 1secemail | 1secemail.com | REST | Tidak ada | ✅ Mudah |

## 📁 Struktur Project

```
TempMail-UnofficialAPI/
├── go/               # Go wrapper
├── python/           # Python wrapper
├── java/             # Java wrapper
├── php/              # PHP wrapper
├── javascript/       # Node.js / JavaScript wrapper
├── rust/             # Rust wrapper
├── csharp/           # C# / .NET wrapper
├── README.md         # English (default)
├── README.id.md      # Bahasa Indonesia
├── README.cn.md      # 中文
├── LICENSE           # Apache 2.0
└── NOTICE            # Atribusi & disclaimer
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

1. Fork repo ini
2. Buat branch: `feat/add-kotlin-wrapper`
3. Commit & push
4. Buka Pull Request

### Aturan Kontribusi

- Ikuti struktur interface yang sudah ada
- Tambahkan contoh penggunaan di README per bahasa
- Jangan hardcode API key (gunakan environment variable)
- Test dulu sebelum PR

## 🗺️ Roadmap

v1.0.0 dirilis dengan 5 layanan dan 7 bahasa. Rencana rilis selanjutnya:

- **Provider tambahan** — layanan tempmail baru akan ditambahkan di v1.1+
- **YOPmail scraper yang lebih tangguh** — terhadap perubahan DOM dan anti-bot
- **Dukungan WebSocket** untuk Dropmail.me (langganan inbox realtime)
- **Bahasa tambahan** — Kotlin, Swift, Ruby tergantung minat komunitas
- **CLI tool** — antarmuka baris perintah terpadu untuk semua provider

Kontribusi dibuka — lihat [CONTRIBUTING.md](./CONTRIBUTING.md).

## 📄 Lisensi

Apache License 2.0 — lihat [LICENSE](./LICENSE) dan [NOTICE](./NOTICE).

---

<p align="center">
  <strong>🌟 Star repo ini kalau membantu project kamu!</strong><br>
  Dibuat dengan 🫠 oleh komunitas, untuk komunitas.
</p>
