// DPDP s.5(3): the consent notice must be accessible in English or an
// Eighth-Schedule language — the option lives ON the consent surfaces
// (onboarding consent step + the account page), independent of the app
// language. English + the 12 most-spoken Eighth-Schedule languages ship now;
// the remaining ten (Bodo, Dogri, Kashmiri, Konkani, Maithili, Manipuri,
// Nepali, Sanskrit, Santali, Sindhi) + professional legal review of all
// translations are owner items before the 13 May 2027 deadline (docs/TODO.md).
//
// Cross-stack contract: duplicated by hand in iOS
// (Features/Trust/ConsentNotice.swift) — change both in the same commit.

export type ConsentKey =
  | "mood_history"
  | "ai_memory"
  | "journal_memory"
  | "sleep_history"
  | "voice_storage"
  | "model_training";

export type ConsentNotice = {
  nativeName: string;
  title: string;
  caption: string;
  recommendOn: string;
  recommendOnSub: string;
  recommendOff: string;
  recommendOffSub: string;
  categories: Record<ConsentKey, { label: string; hint: string }>;
};

export const CONSENT_NOTICE: Record<string, ConsentNotice> = {
  en: {
    nativeName: "English",
    title: "What CereBro remembers",
    caption:
      "Private by default — CereBro remembers nothing you don't switch on. Change any of this later in Settings.",
    recommendOn: "Remembering your patterns",
    recommendOnSub: "Thank you — plans and reflections will tune to you",
    recommendOff: "Remember my patterns",
    recommendOffSub: "Recommended — better plans and reflections over time",
    categories: {
      mood_history: { label: "Mood history", hint: "Check-ins shape insights" },
      ai_memory: { label: "AI memory", hint: "Context between chats" },
      journal_memory: { label: "Journal memory", hint: "Titles tune your plan" },
      sleep_history: { label: "Sleep history", hint: "Diary tunes your plan" },
      voice_storage: { label: "Voice storage", hint: "Off by default" },
      model_training: { label: "Model training", hint: "Separate opt-in only" },
    },
  },
  hi: {
    nativeName: "हिन्दी",
    title: "CereBro क्या याद रखता है",
    caption:
      "डिफ़ॉल्ट रूप से निजी — जो आप चालू नहीं करते, CereBro उसे याद नहीं रखता। यह सब बाद में सेटिंग्स में बदला जा सकता है।",
    recommendOn: "आपके पैटर्न याद रखे जा रहे हैं",
    recommendOnSub: "धन्यवाद — योजनाएँ और विचार आपके अनुरूप ढलेंगे",
    recommendOff: "मेरे पैटर्न याद रखें",
    recommendOffSub: "सुझाया गया — समय के साथ बेहतर योजनाएँ और विचार",
    categories: {
      mood_history: { label: "मूड इतिहास", hint: "चेक-इन से इनसाइट बनते हैं" },
      ai_memory: { label: "AI मेमोरी", hint: "बातचीत के बीच संदर्भ" },
      journal_memory: { label: "जर्नल मेमोरी", hint: "शीर्षक आपकी योजना को ढालते हैं" },
      sleep_history: { label: "नींद का इतिहास", hint: "डायरी आपकी योजना को ढालती है" },
      voice_storage: { label: "आवाज़ का संग्रह", hint: "डिफ़ॉल्ट रूप से बंद" },
      model_training: { label: "मॉडल प्रशिक्षण", hint: "केवल अलग सहमति से" },
    },
  },
  bn: {
    nativeName: "বাংলা",
    title: "CereBro কী মনে রাখে",
    caption:
      "ডিফল্টভাবে ব্যক্তিগত — আপনি যা চালু করেননি, CereBro তা মনে রাখে না। এসব পরে সেটিংসে বদলানো যায়।",
    recommendOn: "আপনার প্যাটার্ন মনে রাখা হচ্ছে",
    recommendOnSub: "ধন্যবাদ — পরিকল্পনা ও ভাবনা আপনার মতো করে গড়ে উঠবে",
    recommendOff: "আমার প্যাটার্ন মনে রাখো",
    recommendOffSub: "প্রস্তাবিত — সময়ের সঙ্গে আরও ভালো পরিকল্পনা ও ভাবনা",
    categories: {
      mood_history: { label: "মুড ইতিহাস", hint: "চেক-ইন থেকে ইনসাইট তৈরি হয়" },
      ai_memory: { label: "AI মেমোরি", hint: "কথোপকথনের মধ্যে প্রসঙ্গ" },
      journal_memory: { label: "জার্নাল মেমোরি", hint: "শিরোনাম আপনার পরিকল্পনা সাজায়" },
      sleep_history: { label: "ঘুমের ইতিহাস", hint: "ডায়েরি আপনার পরিকল্পনা সাজায়" },
      voice_storage: { label: "ভয়েস সংরক্ষণ", hint: "ডিফল্টভাবে বন্ধ" },
      model_training: { label: "মডেল প্রশিক্ষণ", hint: "কেবল আলাদা সম্মতিতে" },
    },
  },
  te: {
    nativeName: "తెలుగు",
    title: "CereBro ఏమి గుర్తుంచుకుంటుంది",
    caption:
      "డిఫాల్ట్‌గా ప్రైవేట్ — మీరు ఆన్ చేయనిది CereBro గుర్తుంచుకోదు. ఇవన్నీ తర్వాత సెట్టింగ్స్‌లో మార్చుకోవచ్చు.",
    recommendOn: "మీ ప్యాటర్న్‌లను గుర్తుంచుకుంటున్నాం",
    recommendOnSub: "ధన్యవాదాలు — ప్రణాళికలు మీకు తగ్గట్టుగా మారతాయి",
    recommendOff: "నా ప్యాటర్న్‌లను గుర్తుంచుకో",
    recommendOffSub: "సిఫార్సు — కాలంతో మెరుగైన ప్రణాళికలు, ఆలోచనలు",
    categories: {
      mood_history: { label: "మూడ్ చరిత్ర", hint: "చెక్-ఇన్‌ల నుంచి ఇన్‌సైట్‌లు" },
      ai_memory: { label: "AI మెమరీ", hint: "సంభాషణల మధ్య సందర్భం" },
      journal_memory: { label: "జర్నల్ మెమరీ", hint: "శీర్షికలు మీ ప్రణాళికను తీర్చిదిద్దుతాయి" },
      sleep_history: { label: "నిద్ర చరిత్ర", hint: "డైరీ మీ ప్రణాళికను తీర్చిదిద్దుతుంది" },
      voice_storage: { label: "వాయిస్ నిల్వ", hint: "డిఫాల్ట్‌గా ఆఫ్" },
      model_training: { label: "మోడల్ శిక్షణ", hint: "వేరే అనుమతితో మాత్రమే" },
    },
  },
  mr: {
    nativeName: "मराठी",
    title: "CereBro काय लक्षात ठेवते",
    caption:
      "मुळातच खासगी — तुम्ही सुरू न केलेले CereBro लक्षात ठेवत नाही. हे सर्व नंतर सेटिंग्जमध्ये बदलता येते.",
    recommendOn: "तुमचे पॅटर्न लक्षात ठेवले जात आहेत",
    recommendOnSub: "धन्यवाद — योजना आणि विचार तुमच्यानुसार घडतील",
    recommendOff: "माझे पॅटर्न लक्षात ठेवा",
    recommendOffSub: "शिफारस — काळानुसार अधिक चांगल्या योजना आणि विचार",
    categories: {
      mood_history: { label: "मूड इतिहास", hint: "चेक-इनमधून इनसाइट घडतात" },
      ai_memory: { label: "AI मेमरी", hint: "संभाषणांदरम्यान संदर्भ" },
      journal_memory: { label: "जर्नल मेमरी", hint: "शीर्षके तुमची योजना घडवतात" },
      sleep_history: { label: "झोपेचा इतिहास", hint: "डायरी तुमची योजना घडवते" },
      voice_storage: { label: "आवाज साठवण", hint: "मुळात बंद" },
      model_training: { label: "मॉडेल प्रशिक्षण", hint: "स्वतंत्र संमतीनेच" },
    },
  },
  ta: {
    nativeName: "தமிழ்",
    title: "CereBro எதை நினைவில் வைக்கிறது",
    caption:
      "இயல்பாகவே தனிப்பட்டது — நீங்கள் இயக்காததை CereBro நினைவில் வைக்காது. இவை அனைத்தையும் பின்னர் அமைப்புகளில் மாற்றலாம்.",
    recommendOn: "உங்கள் பேட்டர்ன்கள் நினைவில் வைக்கப்படுகின்றன",
    recommendOnSub: "நன்றி — திட்டங்கள் உங்களுக்கேற்ப அமையும்",
    recommendOff: "என் பேட்டர்ன்களை நினைவில் வை",
    recommendOffSub: "பரிந்துரை — காலப்போக்கில் சிறந்த திட்டங்களும் எண்ணங்களும்",
    categories: {
      mood_history: { label: "மனநிலை வரலாறு", hint: "செக்-இன்கள் இன்சைட்டுகளை உருவாக்கும்" },
      ai_memory: { label: "AI நினைவகம்", hint: "உரையாடல்களுக்கு இடையேயான சூழல்" },
      journal_memory: { label: "ஜர்னல் நினைவகம்", hint: "தலைப்புகள் உங்கள் திட்டத்தை அமைக்கும்" },
      sleep_history: { label: "தூக்க வரலாறு", hint: "டைரி உங்கள் திட்டத்தை அமைக்கும்" },
      voice_storage: { label: "குரல் சேமிப்பு", hint: "இயல்பாக அணைக்கப்பட்டுள்ளது" },
      model_training: { label: "மாடல் பயிற்சி", hint: "தனி ஒப்புதலுடன் மட்டுமே" },
    },
  },
  ur: {
    nativeName: "اردو",
    title: "CereBro کیا یاد رکھتا ہے",
    caption:
      "طے شدہ طور پر نجی — جو آپ آن نہیں کرتے، CereBro اسے یاد نہیں رکھتا۔ یہ سب بعد میں سیٹنگز میں بدلا جا سکتا ہے۔",
    recommendOn: "آپ کے پیٹرن یاد رکھے جا رہے ہیں",
    recommendOnSub: "شکریہ — منصوبے اور خیالات آپ کے مطابق ڈھلیں گے",
    recommendOff: "میرے پیٹرن یاد رکھیں",
    recommendOffSub: "تجویز کردہ — وقت کے ساتھ بہتر منصوبے اور خیالات",
    categories: {
      mood_history: { label: "موڈ کی تاریخ", hint: "چیک اِن سے بصیرتیں بنتی ہیں" },
      ai_memory: { label: "AI یادداشت", hint: "گفتگو کے درمیان سیاق" },
      journal_memory: { label: "جرنل یادداشت", hint: "عنوانات آپ کے منصوبے کو ڈھالتے ہیں" },
      sleep_history: { label: "نیند کی تاریخ", hint: "ڈائری آپ کے منصوبے کو ڈھالتی ہے" },
      voice_storage: { label: "آواز کا ذخیرہ", hint: "طے شدہ طور پر بند" },
      model_training: { label: "ماڈل تربیت", hint: "صرف الگ اجازت سے" },
    },
  },
  gu: {
    nativeName: "ગુજરાતી",
    title: "CereBro શું યાદ રાખે છે",
    caption:
      "મૂળભૂત રીતે ખાનગી — તમે ચાલુ ન કરો તે CereBro યાદ રાખતું નથી. આ બધું પછી સેટિંગ્સમાં બદલી શકાય છે.",
    recommendOn: "તમારા પેટર્ન યાદ રખાઈ રહ્યા છે",
    recommendOnSub: "આભાર — યોજનાઓ અને વિચારો તમારા પ્રમાણે ઘડાશે",
    recommendOff: "મારા પેટર્ન યાદ રાખો",
    recommendOffSub: "ભલામણ — સમય સાથે વધુ સારી યોજનાઓ અને વિચારો",
    categories: {
      mood_history: { label: "મૂડ ઇતિહાસ", hint: "ચેક-ઇનથી ઇનસાઇટ બને છે" },
      ai_memory: { label: "AI મેમરી", hint: "વાતચીત વચ્ચેનો સંદર્ભ" },
      journal_memory: { label: "જર્નલ મેમરી", hint: "શીર્ષકો તમારી યોજના ઘડે છે" },
      sleep_history: { label: "ઊંઘનો ઇતિહાસ", hint: "ડાયરી તમારી યોજના ઘડે છે" },
      voice_storage: { label: "અવાજ સંગ્રહ", hint: "મૂળભૂત રીતે બંધ" },
      model_training: { label: "મૉડલ તાલીમ", hint: "અલગ સંમતિથી જ" },
    },
  },
  kn: {
    nativeName: "ಕನ್ನಡ",
    title: "CereBro ಏನನ್ನು ನೆನಪಿಡುತ್ತದೆ",
    caption:
      "ಡೀಫಾಲ್ಟ್ ಆಗಿ ಖಾಸಗಿ — ನೀವು ಆನ್ ಮಾಡದ್ದನ್ನು CereBro ನೆನಪಿಡುವುದಿಲ್ಲ. ಇವನ್ನೆಲ್ಲ ನಂತರ ಸೆಟ್ಟಿಂಗ್ಸ್‌ನಲ್ಲಿ ಬದಲಾಯಿಸಬಹುದು.",
    recommendOn: "ನಿಮ್ಮ ಪ್ಯಾಟರ್ನ್‌ಗಳನ್ನು ನೆನಪಿಡಲಾಗುತ್ತಿದೆ",
    recommendOnSub: "ಧನ್ಯವಾದ — ಯೋಜನೆಗಳು ನಿಮಗೆ ತಕ್ಕಂತೆ ರೂಪುಗೊಳ್ಳುತ್ತವೆ",
    recommendOff: "ನನ್ನ ಪ್ಯಾಟರ್ನ್‌ಗಳನ್ನು ನೆನಪಿಡು",
    recommendOffSub: "ಶಿಫಾರಸು — ಕಾಲಕ್ರಮೇಣ ಉತ್ತಮ ಯೋಜನೆಗಳು ಮತ್ತು ಆಲೋಚನೆಗಳು",
    categories: {
      mood_history: { label: "ಮೂಡ್ ಇತಿಹಾಸ", hint: "ಚೆಕ್-ಇನ್‌ಗಳಿಂದ ಒಳನೋಟಗಳು" },
      ai_memory: { label: "AI ಮೆಮೊರಿ", hint: "ಸಂಭಾಷಣೆಗಳ ನಡುವಿನ ಸಂದರ್ಭ" },
      journal_memory: { label: "ಜರ್ನಲ್ ಮೆಮೊರಿ", hint: "ಶೀರ್ಷಿಕೆಗಳು ನಿಮ್ಮ ಯೋಜನೆಯನ್ನು ರೂಪಿಸುತ್ತವೆ" },
      sleep_history: { label: "ನಿದ್ರೆಯ ಇತಿಹಾಸ", hint: "ಡೈರಿ ನಿಮ್ಮ ಯೋಜನೆಯನ್ನು ರೂಪಿಸುತ್ತದೆ" },
      voice_storage: { label: "ಧ್ವನಿ ಸಂಗ್ರಹ", hint: "ಡೀಫಾಲ್ಟ್ ಆಗಿ ಆಫ್" },
      model_training: { label: "ಮಾಡೆಲ್ ತರಬೇತಿ", hint: "ಪ್ರತ್ಯೇಕ ಒಪ್ಪಿಗೆಯಿಂದ ಮಾತ್ರ" },
    },
  },
  ml: {
    nativeName: "മലയാളം",
    title: "CereBro എന്ത് ഓർക്കുന്നു",
    caption:
      "സ്വതവേ സ്വകാര്യം — നിങ്ങൾ ഓൺ ചെയ്യാത്തത് CereBro ഓർക്കില്ല. ഇവയെല്ലാം പിന്നീട് സെറ്റിങ്സിൽ മാറ്റാം.",
    recommendOn: "നിങ്ങളുടെ പാറ്റേണുകൾ ഓർക്കുന്നു",
    recommendOnSub: "നന്ദി — പ്ലാനുകൾ നിങ്ങൾക്കനുസരിച്ച് രൂപപ്പെടും",
    recommendOff: "എന്റെ പാറ്റേണുകൾ ഓർക്കുക",
    recommendOffSub: "ശുപാർശ — കാലക്രമേണ മെച്ചപ്പെട്ട പ്ലാനുകളും ചിന്തകളും",
    categories: {
      mood_history: { label: "മൂഡ് ചരിത്രം", hint: "ചെക്ക്-ഇന്നുകളിൽ നിന്ന് ഇൻസൈറ്റുകൾ" },
      ai_memory: { label: "AI മെമ്മറി", hint: "സംഭാഷണങ്ങൾക്കിടയിലെ സന്ദർഭം" },
      journal_memory: { label: "ജേണൽ മെമ്മറി", hint: "തലക്കെട്ടുകൾ നിങ്ങളുടെ പ്ലാൻ രൂപപ്പെടുത്തുന്നു" },
      sleep_history: { label: "ഉറക്ക ചരിത്രം", hint: "ഡയറി നിങ്ങളുടെ പ്ലാൻ രൂപപ്പെടുത്തുന്നു" },
      voice_storage: { label: "ശബ്ദ സംഭരണം", hint: "സ്വതവേ ഓഫ്" },
      model_training: { label: "മോഡൽ പരിശീലനം", hint: "പ്രത്യേക സമ്മതത്തോടെ മാത്രം" },
    },
  },
  or: {
    nativeName: "ଓଡ଼ିଆ",
    title: "CereBro କ'ଣ ମନେ ରଖେ",
    caption:
      "ମୂଳରୁ ବ୍ୟକ୍ତିଗତ — ଆପଣ ଅନ୍ ନକଲେ CereBro ତାହା ମନେ ରଖେ ନାହିଁ। ଏସବୁ ପରେ ସେଟିଂସରେ ବଦଳାଯାଇପାରିବ।",
    recommendOn: "ଆପଣଙ୍କ ପ୍ୟାଟର୍ନ ମନେ ରଖାଯାଉଛି",
    recommendOnSub: "ଧନ୍ୟବାଦ — ଯୋଜନା ଆପଣଙ୍କ ଅନୁସାରେ ଗଢ଼ିହେବ",
    recommendOff: "ମୋ ପ୍ୟାଟର୍ନ ମନେ ରଖ",
    recommendOffSub: "ପରାମର୍ଶିତ — ସମୟ ସହିତ ଉନ୍ନତ ଯୋଜନା ଓ ଚିନ୍ତା",
    categories: {
      mood_history: { label: "ମୁଡ୍ ଇତିହାସ", hint: "ଚେକ୍-ଇନ୍‌ରୁ ଇନସାଇଟ୍ ତିଆରି ହୁଏ" },
      ai_memory: { label: "AI ମେମୋରୀ", hint: "କଥାବାର୍ତ୍ତା ମଧ୍ୟରେ ପ୍ରସଙ୍ଗ" },
      journal_memory: { label: "ଜର୍ଣ୍ଣାଲ ମେମୋରୀ", hint: "ଶୀର୍ଷକ ଆପଣଙ୍କ ଯୋଜନା ଗଢ଼େ" },
      sleep_history: { label: "ନିଦ ଇତିହାସ", hint: "ଡାଏରୀ ଆପଣଙ୍କ ଯୋଜନା ଗଢ଼େ" },
      voice_storage: { label: "ସ୍ୱର ସଂରକ୍ଷଣ", hint: "ମୂଳରୁ ବନ୍ଦ" },
      model_training: { label: "ମଡେଲ ତାଲିମ", hint: "ଅଲଗା ସମ୍ମତିରେ ହିଁ" },
    },
  },
  pa: {
    nativeName: "ਪੰਜਾਬੀ",
    title: "CereBro ਕੀ ਯਾਦ ਰੱਖਦਾ ਹੈ",
    caption:
      "ਮੂਲ ਰੂਪ ਵਿੱਚ ਨਿੱਜੀ — ਜੋ ਤੁਸੀਂ ਚਾਲੂ ਨਹੀਂ ਕਰਦੇ, CereBro ਉਹ ਯਾਦ ਨਹੀਂ ਰੱਖਦਾ। ਇਹ ਸਭ ਬਾਅਦ ਵਿੱਚ ਸੈਟਿੰਗਾਂ ਵਿੱਚ ਬਦਲਿਆ ਜਾ ਸਕਦਾ ਹੈ।",
    recommendOn: "ਤੁਹਾਡੇ ਪੈਟਰਨ ਯਾਦ ਰੱਖੇ ਜਾ ਰਹੇ ਹਨ",
    recommendOnSub: "ਧੰਨਵਾਦ — ਯੋਜਨਾਵਾਂ ਤੁਹਾਡੇ ਮੁਤਾਬਕ ਢਲਣਗੀਆਂ",
    recommendOff: "ਮੇਰੇ ਪੈਟਰਨ ਯਾਦ ਰੱਖੋ",
    recommendOffSub: "ਸਿਫ਼ਾਰਸ਼ — ਸਮੇਂ ਨਾਲ ਬਿਹਤਰ ਯੋਜਨਾਵਾਂ ਅਤੇ ਵਿਚਾਰ",
    categories: {
      mood_history: { label: "ਮੂਡ ਇਤਿਹਾਸ", hint: "ਚੈੱਕ-ਇਨ ਤੋਂ ਇਨਸਾਈਟ ਬਣਦੀਆਂ ਹਨ" },
      ai_memory: { label: "AI ਮੈਮੋਰੀ", hint: "ਗੱਲਬਾਤ ਵਿਚਕਾਰ ਸੰਦਰਭ" },
      journal_memory: { label: "ਜਰਨਲ ਮੈਮੋਰੀ", hint: "ਸਿਰਲੇਖ ਤੁਹਾਡੀ ਯੋਜਨਾ ਘੜਦੇ ਹਨ" },
      sleep_history: { label: "ਨੀਂਦ ਦਾ ਇਤਿਹਾਸ", hint: "ਡਾਇਰੀ ਤੁਹਾਡੀ ਯੋਜਨਾ ਘੜਦੀ ਹੈ" },
      voice_storage: { label: "ਆਵਾਜ਼ ਸਟੋਰੇਜ", hint: "ਮੂਲ ਰੂਪ ਵਿੱਚ ਬੰਦ" },
      model_training: { label: "ਮਾਡਲ ਸਿਖਲਾਈ", hint: "ਸਿਰਫ਼ ਵੱਖਰੀ ਸਹਿਮਤੀ ਨਾਲ" },
    },
  },
  as: {
    nativeName: "অসমীয়া",
    title: "CereBro-এ কি মনত ৰাখে",
    caption:
      "স্বাভাৱিকতে ব্যক্তিগত — আপুনি অন নকৰাখিনি CereBro-এ মনত নাৰাখে। এই সকলো পিছত ছেটিংছত সলনি কৰিব পাৰি।",
    recommendOn: "আপোনাৰ পেটাৰ্নবোৰ মনত ৰখা হৈছে",
    recommendOnSub: "ধন্যবাদ — পৰিকল্পনাবোৰ আপোনাৰ অনুৰূপ গঢ় লব",
    recommendOff: "মোৰ পেটাৰ্নবোৰ মনত ৰাখা",
    recommendOffSub: "পৰামৰ্শিত — সময়ৰ লগে লগে উন্নত পৰিকল্পনা আৰু চিন্তা",
    categories: {
      mood_history: { label: "ম'ড ইতিহাস", hint: "চেক-ইনৰ পৰা ইনচাইট" },
      ai_memory: { label: "AI মেম'ৰি", hint: "কথোপকথনৰ মাজৰ প্ৰসংগ" },
      journal_memory: { label: "জাৰ্নেল মেম'ৰি", hint: "শিৰোনামে আপোনাৰ পৰিকল্পনা গঢ়ে" },
      sleep_history: { label: "টোপনিৰ ইতিহাস", hint: "ডায়েৰীয়ে আপোনাৰ পৰিকল্পনা গঢ়ে" },
      voice_storage: { label: "কণ্ঠ সংৰক্ষণ", hint: "স্বাভাৱিকতে বন্ধ" },
      model_training: { label: "মডেল প্ৰশিক্ষণ", hint: "পৃথক সন্মতিৰেহে" },
    },
  },
};

export const NOTICE_LANGS = Object.keys(CONSENT_NOTICE);

/** Best notice default from the app-language step ("Hindi, Punjabi" → "hi").
 * Hinglish deliberately maps to English — it reads in Latin script. */
export function defaultNoticeLang(appLanguages: string[] | string): string {
  const langs = Array.isArray(appLanguages) ? appLanguages : appLanguages.split(",");
  const map: Record<string, string> = { Hindi: "hi", Punjabi: "pa", Tamil: "ta" };
  for (const l of langs) {
    const code = map[l.trim()];
    if (code) return code;
  }
  return "en";
}
