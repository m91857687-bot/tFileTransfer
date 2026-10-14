# Share Load - File Transfer & Games

A powerful Android file-sharing and local network communication application designed to give you the fastest, lightest, and easiest file transfer experience.

## Key Features:
1. **Integrated File Manager (مدير ملفات مدمج نفس زابيا):** A complete Zapya-style built-in file manager with category browsing:
   - **Apps & Games (التطبيقات والألعاب):** Browse, open, and select installed applications and games.
   - **Photos & Images (الصور):** High-speed thumbnail grid and photo viewer.
   - **Videos (الفيديوهات):** Video preview with durations, sizes, and instant playback.
   - **Music & Audios (الموسيقى والصوتيات):** Audio listing with track info.
   - **Documents (المستندات):** Filter for PDFs, Word, Excel, PPT, TXT, APKs, and archives.
   - **Storage Directory (ذاكرة الهاتف والمجلدات):** Full directory browser with breadcrumbs, create folder, rename, delete, and file details.
   - **Storage Usage Bar:** Real-time visual progress showing used and total internal storage.
   - **Search:** Real-time search across files and apps.
   - **Bottom Batch Action Bar:** Select multiple files and hit **Send** to launch the transfer barcode instantly.
2. **One-Tap Share & Barcode Generation (المشاركة الفورية وفتح باركود الإرسال):**
   - Share any file, app, or game from outside (via Android's Share menu / Export to Share Load).
   - Share Load instantly resolves and selects the shared items and automatically opens the Hotspot screen with the sending QR barcode on screen, ready for the receiver to scan and download!
3. **[Addition 1] Phone Cleaner & Storage Optimizer (منظف الهاتف وموفر المساحة الذكي):**
   - Scans system cache, temp files, duplicate logs/downloads, obsolete APK installers, empty directories, and large files.
   - Categorized cleanable files list with item selection and one-click "Clean Now" freeing up valuable storage space.
4. **[Addition 2] APK Extractor & App Backup (مستخرج تطبيقات وألعاب الـ APK):**
   - Extract and back up installed apps and games directly to `/Download/ShareLoad/Extracted_APKs/`.
   - One-tap "Send via Barcode" to transfer any extracted APK immediately without requiring internet or Google Play.
5. **[Addition 3] Live Transfer Speedometer & Performance Booster (عداد السرعة اللحظي ومؤشر السرعة العالية):**
   - Real-time transfer speed in MB/s with peak speed indicator (`Peak: XX MB/s`).
   - Dynamic ETA remaining time countdown (`ETA: 00:15`) and transfer percentage.
   - Super Speed badge (`⚡ Super Speed`) when transfer rate exceeds 5 MB/s.
   - Haptic vibration feedback on transfer completion.
6. **[Addition 4] Instant Text, Links & Clipboard Sharing (مشاركة النصوص والحافظة الفورية):**
   - One-tap "Paste & Send" clipboard text directly into chat between connected devices.
   - Tap-to-copy any received message with instant feedback.
   - Smart URL detection with one-tap "Open Link" in browser for links and shared websites.
7. **[Addition 5] Wi-Fi & Network Speed Analyzer (فاحص سرعة الشبكة والواي فاي وقوة الإشارة):**
   - Live signal strength in dBm with quality badges (Excellent, Good, Fair, Weak).
   - Frequency band detection (2.4 GHz vs 5.0 GHz Ultra-Fast) and link speed in Mbps.
   - Built-in latency ping tester and throughput benchmark with optimization tips to maximize transfer rate.
8. **[Addition 7] Secure Vault & Private Safe (الخزنة السرية لحماية وقفل الملفات برقم PIN):**
   - Protect sensitive photos, videos, and private documents with a 4-digit security PIN.
   - Scrambled hidden storage in private app directory where gallery and external file explorers cannot view them.
   - Built-in file preview, category tabs (Photos, Videos, Documents), and one-tap restore back to public storage.
9. **Lightning Fast File Transfer (P2P):** Share files via Wi-Fi Direct (P2P) directly between devices without needing an internet connection.
10. **Local Network / Hotspot Connection:** Connect devices via a shared router or a mobile hotspot. The receiving device scans the QR code using the in-app scanner to connect automatically!
11. **Phone Clone (One-Click Transfer):** Transfer your entire phone (Apps, Images, Videos, Audios) with a single click using the "Clone Phone" option in the top menu.
12. **Web Share:** Share files with non-Android devices (like iOS, Windows, Mac) using a built-in web server.
13. **Share Load Games:** Play professional 2-player games directly over the connection while you transfer files (Othello / Reversi, Connect 4, Tic-Tac-Toe).
14. **Optimized Dark Mode:** Deep, pure black dark mode optimized for OLED screens to save battery.
15. **In-App Instructions:** Read localized Arabic and English usage instructions right from the app settings menu.

## How to use:
- **Share files from another app:** Tap Share in any app/game -> Select **Share Load**. The files are selected and the sending QR code opens automatically.
- **Built-in File Manager:** Tap the **File Manager** card on the home screen or choose any category (Apps, Photos, Videos, Music, Storage). Select any files and tap **Send** to open the barcode!
- **Phone Cleaner (1):** Tap the **Phone Cleaner** card to scan cache, temp files, duplicate downloads, and leftover APKs, then tap **Clean Now**.
- **APK Extractor (2):** Tap the **APK Extractor** card to extract installed apps/games and send them via barcode or back them up to storage.
- **Speedometer (3):** Automatically active during transfers with peak speed, ETA, and Super Speed badge.
- **Instant Clipboard (4):** Open chat in connected transfer, tap "Paste & Send" to send copied links or text.
- **Network Speed Analyzer (5):** Tap the **Network Speed** card to check Wi-Fi signal, frequency (5GHz vs 2.4GHz), ping latency, and transfer speed.
- **Secure Vault (7):** Tap the **Secure Vault** card, set your 4-digit PIN, and safely hide photos and documents with one tap!

Greetings from the developer Mohammed Hisham Al-Salahi / مع تحيات المطور محمد هشام الصلاحي.

Built using Kotlin, Jetpack Compose, ViewBinding, and Netty.
