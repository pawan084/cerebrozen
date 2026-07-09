package com.cerebro.app.ui.screens

// DPDP s.5(3): the consent notice must be accessible in English or an
// Eighth-Schedule language — the option lives ON the consent surfaces
// (onboarding consent step + Privacy & memory), independent of the app
// language. English + the 12 most-spoken Eighth-Schedule languages; the
// remaining ten + professional review are owner items (docs/TODO.md).
//
// Cross-stack contract, duplicated by hand from apps/app/lib/consentNotice.ts
// and iOS Features/Trust/ConsentNotice.swift — change all three together.
// (Android renders title/caption/category label+hint; the recommend-card
// strings are iOS/web-only.) Pure Kotlin — JVM unit-testable.

internal data class NoticeCategory(val label: String, val hint: String)

internal data class ConsentNoticeText(
    val nativeName: String,
    val title: String,
    val caption: String,
    val categories: Map<String, NoticeCategory>,
)

/** Stable render order; keys mirror the server consent columns. */
internal val CONSENT_KEY_ORDER = listOf(
    "mood_history", "ai_memory", "journal_memory", "sleep_history", "voice_storage", "model_training",
)

/** Stable picker order: English first, then Eighth-Schedule languages. */
internal val NOTICE_CODES = listOf("en", "hi", "bn", "te", "mr", "ta", "ur", "gu", "kn", "ml", "or", "pa", "as")

internal fun noticeFor(code: String): ConsentNoticeText =
    CONSENT_NOTICES[code] ?: CONSENT_NOTICES.getValue("en")

/** Best notice default from the app-language choice. Hinglish deliberately
 * maps to English — it reads in Latin script. */
internal fun defaultNoticeCode(appLanguage: String): String = when {
    appLanguage.contains("Hindi") -> "hi"
    appLanguage.contains("Punjabi") -> "pa"
    appLanguage.contains("Tamil") -> "ta"
    else -> "en"
}

internal val CONSENT_NOTICES: Map<String, ConsentNoticeText> = mapOf(
    "en" to ConsentNoticeText(
        nativeName = "English",
        title = "What CereBro remembers",
        caption = "Private by default — CereBro remembers nothing you don't switch on. Change any of this later in Settings.",
        categories = mapOf(
            "mood_history" to NoticeCategory("Mood history", "Check-ins shape insights"),
            "ai_memory" to NoticeCategory("AI memory", "Context between chats"),
            "journal_memory" to NoticeCategory("Journal memory", "Titles tune your plan"),
            "sleep_history" to NoticeCategory("Sleep history", "Diary tunes your plan"),
            "voice_storage" to NoticeCategory("Voice storage", "Off by default"),
            "model_training" to NoticeCategory("Model training", "Separate opt-in only"),
        ),
    ),
    "hi" to ConsentNoticeText(
        nativeName = "हिन्दी",
        title = "CereBro क्या याद रखता है",
        caption = "डिफ़ॉल्ट रूप से निजी — जो आप चालू नहीं करते, CereBro उसे याद नहीं रखता। यह सब बाद में सेटिंग्स में बदला जा सकता है।",
        categories = mapOf(
            "mood_history" to NoticeCategory("मूड इतिहास", "चेक-इन से इनसाइट बनते हैं"),
            "ai_memory" to NoticeCategory("AI मेमोरी", "बातचीत के बीच संदर्भ"),
            "journal_memory" to NoticeCategory("जर्नल मेमोरी", "शीर्षक आपकी योजना को ढालते हैं"),
            "sleep_history" to NoticeCategory("नींद का इतिहास", "डायरी आपकी योजना को ढालती है"),
            "voice_storage" to NoticeCategory("आवाज़ का संग्रह", "डिफ़ॉल्ट रूप से बंद"),
            "model_training" to NoticeCategory("मॉडल प्रशिक्षण", "केवल अलग सहमति से"),
        ),
    ),
    "bn" to ConsentNoticeText(
        nativeName = "বাংলা",
        title = "CereBro কী মনে রাখে",
        caption = "ডিফল্টভাবে ব্যক্তিগত — আপনি যা চালু করেননি, CereBro তা মনে রাখে না। এসব পরে সেটিংসে বদলানো যায়।",
        categories = mapOf(
            "mood_history" to NoticeCategory("মুড ইতিহাস", "চেক-ইন থেকে ইনসাইট তৈরি হয়"),
            "ai_memory" to NoticeCategory("AI মেমোরি", "কথোপকথনের মধ্যে প্রসঙ্গ"),
            "journal_memory" to NoticeCategory("জার্নাল মেমোরি", "শিরোনাম আপনার পরিকল্পনা সাজায়"),
            "sleep_history" to NoticeCategory("ঘুমের ইতিহাস", "ডায়েরি আপনার পরিকল্পনা সাজায়"),
            "voice_storage" to NoticeCategory("ভয়েস সংরক্ষণ", "ডিফল্টভাবে বন্ধ"),
            "model_training" to NoticeCategory("মডেল প্রশিক্ষণ", "কেবল আলাদা সম্মতিতে"),
        ),
    ),
    "te" to ConsentNoticeText(
        nativeName = "తెలుగు",
        title = "CereBro ఏమి గుర్తుంచుకుంటుంది",
        caption = "డిఫాల్ట్‌గా ప్రైవేట్ — మీరు ఆన్ చేయనిది CereBro గుర్తుంచుకోదు. ఇవన్నీ తర్వాత సెట్టింగ్స్‌లో మార్చుకోవచ్చు.",
        categories = mapOf(
            "mood_history" to NoticeCategory("మూడ్ చరిత్ర", "చెక్-ఇన్‌ల నుంచి ఇన్‌సైట్‌లు"),
            "ai_memory" to NoticeCategory("AI మెమరీ", "సంభాషణల మధ్య సందర్భం"),
            "journal_memory" to NoticeCategory("జర్నల్ మెమరీ", "శీర్షికలు మీ ప్రణాళికను తీర్చిదిద్దుతాయి"),
            "sleep_history" to NoticeCategory("నిద్ర చరిత్ర", "డైరీ మీ ప్రణాళికను తీర్చిదిద్దుతుంది"),
            "voice_storage" to NoticeCategory("వాయిస్ నిల్వ", "డిఫాల్ట్‌గా ఆఫ్"),
            "model_training" to NoticeCategory("మోడల్ శిక్షణ", "వేరే అనుమతితో మాత్రమే"),
        ),
    ),
    "mr" to ConsentNoticeText(
        nativeName = "मराठी",
        title = "CereBro काय लक्षात ठेवते",
        caption = "मुळातच खासगी — तुम्ही सुरू न केलेले CereBro लक्षात ठेवत नाही. हे सर्व नंतर सेटिंग्जमध्ये बदलता येते.",
        categories = mapOf(
            "mood_history" to NoticeCategory("मूड इतिहास", "चेक-इनमधून इनसाइट घडतात"),
            "ai_memory" to NoticeCategory("AI मेमरी", "संभाषणांदरम्यान संदर्भ"),
            "journal_memory" to NoticeCategory("जर्नल मेमरी", "शीर्षके तुमची योजना घडवतात"),
            "sleep_history" to NoticeCategory("झोपेचा इतिहास", "डायरी तुमची योजना घडवते"),
            "voice_storage" to NoticeCategory("आवाज साठवण", "मुळात बंद"),
            "model_training" to NoticeCategory("मॉडेल प्रशिक्षण", "स्वतंत्र संमतीनेच"),
        ),
    ),
    "ta" to ConsentNoticeText(
        nativeName = "தமிழ்",
        title = "CereBro எதை நினைவில் வைக்கிறது",
        caption = "இயல்பாகவே தனிப்பட்டது — நீங்கள் இயக்காததை CereBro நினைவில் வைக்காது. இவை அனைத்தையும் பின்னர் அமைப்புகளில் மாற்றலாம்.",
        categories = mapOf(
            "mood_history" to NoticeCategory("மனநிலை வரலாறு", "செக்-இன்கள் இன்சைட்டுகளை உருவாக்கும்"),
            "ai_memory" to NoticeCategory("AI நினைவகம்", "உரையாடல்களுக்கு இடையேயான சூழல்"),
            "journal_memory" to NoticeCategory("ஜர்னல் நினைவகம்", "தலைப்புகள் உங்கள் திட்டத்தை அமைக்கும்"),
            "sleep_history" to NoticeCategory("தூக்க வரலாறு", "டைரி உங்கள் திட்டத்தை அமைக்கும்"),
            "voice_storage" to NoticeCategory("குரல் சேமிப்பு", "இயல்பாக அணைக்கப்பட்டுள்ளது"),
            "model_training" to NoticeCategory("மாடல் பயிற்சி", "தனி ஒப்புதலுடன் மட்டுமே"),
        ),
    ),
    "ur" to ConsentNoticeText(
        nativeName = "اردو",
        title = "CereBro کیا یاد رکھتا ہے",
        caption = "طے شدہ طور پر نجی — جو آپ آن نہیں کرتے، CereBro اسے یاد نہیں رکھتا۔ یہ سب بعد میں سیٹنگز میں بدلا جا سکتا ہے۔",
        categories = mapOf(
            "mood_history" to NoticeCategory("موڈ کی تاریخ", "چیک اِن سے بصیرتیں بنتی ہیں"),
            "ai_memory" to NoticeCategory("AI یادداشت", "گفتگو کے درمیان سیاق"),
            "journal_memory" to NoticeCategory("جرنل یادداشت", "عنوانات آپ کے منصوبے کو ڈھالتے ہیں"),
            "sleep_history" to NoticeCategory("نیند کی تاریخ", "ڈائری آپ کے منصوبے کو ڈھالتی ہے"),
            "voice_storage" to NoticeCategory("آواز کا ذخیرہ", "طے شدہ طور پر بند"),
            "model_training" to NoticeCategory("ماڈل تربیت", "صرف الگ اجازت سے"),
        ),
    ),
    "gu" to ConsentNoticeText(
        nativeName = "ગુજરાતી",
        title = "CereBro શું યાદ રાખે છે",
        caption = "મૂળભૂત રીતે ખાનગી — તમે ચાલુ ન કરો તે CereBro યાદ રાખતું નથી. આ બધું પછી સેટિંગ્સમાં બદલી શકાય છે.",
        categories = mapOf(
            "mood_history" to NoticeCategory("મૂડ ઇતિહાસ", "ચેક-ઇનથી ઇનસાઇટ બને છે"),
            "ai_memory" to NoticeCategory("AI મેમરી", "વાતચીત વચ્ચેનો સંદર્ભ"),
            "journal_memory" to NoticeCategory("જર્નલ મેમરી", "શીર્ષકો તમારી યોજના ઘડે છે"),
            "sleep_history" to NoticeCategory("ઊંઘનો ઇતિહાસ", "ડાયરી તમારી યોજના ઘડે છે"),
            "voice_storage" to NoticeCategory("અવાજ સંગ્રહ", "મૂળભૂત રીતે બંધ"),
            "model_training" to NoticeCategory("મૉડલ તાલીમ", "અલગ સંમતિથી જ"),
        ),
    ),
    "kn" to ConsentNoticeText(
        nativeName = "ಕನ್ನಡ",
        title = "CereBro ಏನನ್ನು ನೆನಪಿಡುತ್ತದೆ",
        caption = "ಡೀಫಾಲ್ಟ್ ಆಗಿ ಖಾಸಗಿ — ನೀವು ಆನ್ ಮಾಡದ್ದನ್ನು CereBro ನೆನಪಿಡುವುದಿಲ್ಲ. ಇವನ್ನೆಲ್ಲ ನಂತರ ಸೆಟ್ಟಿಂಗ್ಸ್‌ನಲ್ಲಿ ಬದಲಾಯಿಸಬಹುದು.",
        categories = mapOf(
            "mood_history" to NoticeCategory("ಮೂಡ್ ಇತಿಹಾಸ", "ಚೆಕ್-ಇನ್‌ಗಳಿಂದ ಒಳನೋಟಗಳು"),
            "ai_memory" to NoticeCategory("AI ಮೆಮೊರಿ", "ಸಂಭಾಷಣೆಗಳ ನಡುವಿನ ಸಂದರ್ಭ"),
            "journal_memory" to NoticeCategory("ಜರ್ನಲ್ ಮೆಮೊರಿ", "ಶೀರ್ಷಿಕೆಗಳು ನಿಮ್ಮ ಯೋಜನೆಯನ್ನು ರೂಪಿಸುತ್ತವೆ"),
            "sleep_history" to NoticeCategory("ನಿದ್ರೆಯ ಇತಿಹಾಸ", "ಡೈರಿ ನಿಮ್ಮ ಯೋಜನೆಯನ್ನು ರೂಪಿಸುತ್ತದೆ"),
            "voice_storage" to NoticeCategory("ಧ್ವನಿ ಸಂಗ್ರಹ", "ಡೀಫಾಲ್ಟ್ ಆಗಿ ಆಫ್"),
            "model_training" to NoticeCategory("ಮಾಡೆಲ್ ತರಬೇತಿ", "ಪ್ರತ್ಯೇಕ ಒಪ್ಪಿಗೆಯಿಂದ ಮಾತ್ರ"),
        ),
    ),
    "ml" to ConsentNoticeText(
        nativeName = "മലയാളം",
        title = "CereBro എന്ത് ഓർക്കുന്നു",
        caption = "സ്വതവേ സ്വകാര്യം — നിങ്ങൾ ഓൺ ചെയ്യാത്തത് CereBro ഓർക്കില്ല. ഇവയെല്ലാം പിന്നീട് സെറ്റിങ്സിൽ മാറ്റാം.",
        categories = mapOf(
            "mood_history" to NoticeCategory("മൂഡ് ചരിത്രം", "ചെക്ക്-ഇന്നുകളിൽ നിന്ന് ഇൻസൈറ്റുകൾ"),
            "ai_memory" to NoticeCategory("AI മെമ്മറി", "സംഭാഷണങ്ങൾക്കിടയിലെ സന്ദർഭം"),
            "journal_memory" to NoticeCategory("ജേണൽ മെമ്മറി", "തലക്കെട്ടുകൾ നിങ്ങളുടെ പ്ലാൻ രൂപപ്പെടുത്തുന്നു"),
            "sleep_history" to NoticeCategory("ഉറക്ക ചരിത്രം", "ഡയറി നിങ്ങളുടെ പ്ലാൻ രൂപപ്പെടുത്തുന്നു"),
            "voice_storage" to NoticeCategory("ശബ്ദ സംഭരണം", "സ്വതവേ ഓഫ്"),
            "model_training" to NoticeCategory("മോഡൽ പരിശീലനം", "പ്രത്യേക സമ്മതത്തോടെ മാത്രം"),
        ),
    ),
    "or" to ConsentNoticeText(
        nativeName = "ଓଡ଼ିଆ",
        title = "CereBro କ'ଣ ମନେ ରଖେ",
        caption = "ମୂଳରୁ ବ୍ୟକ୍ତିଗତ — ଆପଣ ଅନ୍ ନକଲେ CereBro ତାହା ମନେ ରଖେ ନାହିଁ। ଏସବୁ ପରେ ସେଟିଂସରେ ବଦଳାଯାଇପାରିବ।",
        categories = mapOf(
            "mood_history" to NoticeCategory("ମୁଡ୍ ଇତିହାସ", "ଚେକ୍-ଇନ୍‌ରୁ ଇନସାଇଟ୍ ତିଆରି ହୁଏ"),
            "ai_memory" to NoticeCategory("AI ମେମୋରୀ", "କଥାବାର୍ତ୍ତା ମଧ୍ୟରେ ପ୍ରସଙ୍ଗ"),
            "journal_memory" to NoticeCategory("ଜର୍ଣ୍ଣାଲ ମେମୋରୀ", "ଶୀର୍ଷକ ଆପଣଙ୍କ ଯୋଜନା ଗଢ଼େ"),
            "sleep_history" to NoticeCategory("ନିଦ ଇତିହାସ", "ଡାଏରୀ ଆପଣଙ୍କ ଯୋଜନା ଗଢ଼େ"),
            "voice_storage" to NoticeCategory("ସ୍ୱର ସଂରକ୍ଷଣ", "ମୂଳରୁ ବନ୍ଦ"),
            "model_training" to NoticeCategory("ମଡେଲ ତାଲିମ", "ଅଲଗା ସମ୍ମତିରେ ହିଁ"),
        ),
    ),
    "pa" to ConsentNoticeText(
        nativeName = "ਪੰਜਾਬੀ",
        title = "CereBro ਕੀ ਯਾਦ ਰੱਖਦਾ ਹੈ",
        caption = "ਮੂਲ ਰੂਪ ਵਿੱਚ ਨਿੱਜੀ — ਜੋ ਤੁਸੀਂ ਚਾਲੂ ਨਹੀਂ ਕਰਦੇ, CereBro ਉਹ ਯਾਦ ਨਹੀਂ ਰੱਖਦਾ। ਇਹ ਸਭ ਬਾਅਦ ਵਿੱਚ ਸੈਟਿੰਗਾਂ ਵਿੱਚ ਬਦਲਿਆ ਜਾ ਸਕਦਾ ਹੈ।",
        categories = mapOf(
            "mood_history" to NoticeCategory("ਮੂਡ ਇਤਿਹਾਸ", "ਚੈੱਕ-ਇਨ ਤੋਂ ਇਨਸਾਈਟ ਬਣਦੀਆਂ ਹਨ"),
            "ai_memory" to NoticeCategory("AI ਮੈਮੋਰੀ", "ਗੱਲਬਾਤ ਵਿਚਕਾਰ ਸੰਦਰਭ"),
            "journal_memory" to NoticeCategory("ਜਰਨਲ ਮੈਮੋਰੀ", "ਸਿਰਲੇਖ ਤੁਹਾਡੀ ਯੋਜਨਾ ਘੜਦੇ ਹਨ"),
            "sleep_history" to NoticeCategory("ਨੀਂਦ ਦਾ ਇਤਿਹਾਸ", "ਡਾਇਰੀ ਤੁਹਾਡੀ ਯੋਜਨਾ ਘੜਦੀ ਹੈ"),
            "voice_storage" to NoticeCategory("ਆਵਾਜ਼ ਸਟੋਰੇਜ", "ਮੂਲ ਰੂਪ ਵਿੱਚ ਬੰਦ"),
            "model_training" to NoticeCategory("ਮਾਡਲ ਸਿਖਲਾਈ", "ਸਿਰਫ਼ ਵੱਖਰੀ ਸਹਿਮਤੀ ਨਾਲ"),
        ),
    ),
    "as" to ConsentNoticeText(
        nativeName = "অসমীয়া",
        title = "CereBro-এ কি মনত ৰাখে",
        caption = "স্বাভাৱিকতে ব্যক্তিগত — আপুনি অন নকৰাখিনি CereBro-এ মনত নাৰাখে। এই সকলো পিছত ছেটিংছত সলনি কৰিব পাৰি।",
        categories = mapOf(
            "mood_history" to NoticeCategory("ম'ড ইতিহাস", "চেক-ইনৰ পৰা ইনচাইট"),
            "ai_memory" to NoticeCategory("AI মেম'ৰি", "কথোপকথনৰ মাজৰ প্ৰসংগ"),
            "journal_memory" to NoticeCategory("জাৰ্নেল মেম'ৰি", "শিৰোনামে আপোনাৰ পৰিকল্পনা গঢ়ে"),
            "sleep_history" to NoticeCategory("টোপনিৰ ইতিহাস", "ডায়েৰীয়ে আপোনাৰ পৰিকল্পনা গঢ়ে"),
            "voice_storage" to NoticeCategory("কণ্ঠ সংৰক্ষণ", "স্বাভাৱিকতে বন্ধ"),
            "model_training" to NoticeCategory("মডেল প্ৰশিক্ষণ", "পৃথক সন্মতিৰেহে"),
        ),
    ),
)
