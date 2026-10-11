# Smsy - Autonomous On-Device AI SMS Manager

اپلیکیشن مستقل، سریع و مدرن مدیریت پیامک اندروید (Default SMS Manager) مجهز به موتور هوش مصنوعی محلی **ONNX Runtime** جهت فیلتراسیون، قرنطینه خودکار و پالایش بلادرنگ پیامک‌های تبلیغاتی فارسی با قابلیت یادگیری تطبیقی.

---

### ویژگی‌های کلیدی
1. **حفظ کامل حریم خصوصی (Zero Internet Permission):**
   - بدون مجوز اینترنت (`android.permission.INTERNET`). تمام فرآیندها ۱۰۰٪ روی پردازنده دستگاه انجام می‌شوند و داده‌ای خارج نمی‌گردد.
2. **استنتاج بلادرنگ روی پردازنده گوشی (<2ms):**
   - مدل فوق‌سبک Quantized ONNX (حجم ۵ کیلوبایت) بر پایه ۵۰۰ ویژگی زبانی فارسی TF-IDF، پیامک‌های ورودی را با سرعت بسیار بالا ارزیابی می‌کند.
3. **گارد امنیتی دوسطحی (Hard Whitelist + AI):**
   - تمامی پیامک‌های تراکنش‌های بانکی، کدهای اعتبارسنجی (OTP) و نهادهای رسمی به‌صورت آنی تایید شده و از غربالگری حذف نمی‌شوند.
4. **یادگیری تطبیقی (Adaptive Learning):**
   - در صورت بازگردانی (Restore) پیامی از بخش قرنطینه توسط کاربر، فرستنده بلافاصله در لیست سفید شخصی ثبت شده و دیگر هرگز مسدود نمی‌شود.
5. **اسکن و پالایش دسته‌ای پیام‌های گذشته (Retroactive Inbox Scan):**
   - امکان پالایش تاریخچه پیامک‌های موجود در اینباکس با یک کلیک و انتقال موارد تبلیغاتی به قرنطینه.
6. **رابط کاربری مدرن (Jetpack Compose & Material 3):**
   - پشتیبانی خودکار از حالت دارک/لایت گوشی (Dark Mode)، رندر صحیح متن‌های دوزبانه (RTL/LTR)، نمایش نشانگر پیام‌های خوانده‌نشده، جستجو و نشان‌گذاری گفتگوها.

---

### نحوه بیلد و ساخت فایل نصبی (APK)

```bash
cd spamguard-android
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug
```

فایل نهایی در مسیر زیر تولید می‌شود:
`app/build/outputs/apk/debug/app-debug.apk`

---

### معماری پروژه
- **UI:** Jetpack Compose + Material 3 + Lifecycle Compose
- **Database:** Android Telephony Provider (Inbox/Sent/Threads) + Room DB (Quarantine & Whitelist)
- **AI Engine:** Microsoft ONNX Runtime Android (1.18.0)
- **Telephony Services:** SmsDeliverReceiver, WapPushDeliverReceiver, RespondViaMessageService
