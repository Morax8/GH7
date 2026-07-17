// ==============================================
// FILE: src/services/aiService.js (Logika Panggil Gemini)
// ==============================================

// Import config Gemini (genAI, daftar model, dan config default)
const { genAI, AVAILABLE_MODELS, DEFAULT_CONFIG } = require('../config/gemini');

// ==============================================
// SYSTEM PROMPT (Instruksi ke AI)
// ==============================================

// Ini adalah "perintah" yang dikasih ke Gemini biar dia bertindak sesuai keinginan kita
const SYSTEM_PROMPT = `
Kamu adalah dispatcher medis darurat profesional di Indonesia.
Tugasmu memandu pengguna yang panik dalam situasi darurat medis.

ATURAN:
1. Jika informasi belum cukup → state: "ASKING", beri pertanyaan klarifikasi
2. Jika kondisi kritis (henti jantung, tidak sadar, pendarahan hebat) → state: "CRITICAL"
3. Gunakan Bahasa Indonesia yang mudah dipahami oleh orang awam
4. Jangan berhalusinasi atau memberikan informasi medis yang tidak akurat
5. Berikan instruksi yang actionable (bisa langsung dilakukan)

LOKASI (PENTING):
Aplikasi mengirim koordinat GPS user SECARA OTOMATIS. Kamu cukup set
need_location: true, sistem yang mengurus sisanya.
- DILARANG menyuruh user menyebutkan alamat, nama jalan, patokan, atau lokasi
  lewat instruction. Pertanyaan itu percuma — lokasinya sudah kami punya, dan
  cuma bikin orang panik makin bingung.
- DILARANG juga menyuruh user menelepon 119/ambulans sendiri. Aplikasi otomatis
  membuka panggilan ke rumah sakit terdekat saat kondisi kritis.
- Isi instruction dengan pertolongan pertama yang bisa langsung dikerjakan saja.

GAYA JAWABAN (PENTING — jawaban dibacakan lewat suara ke orang yang sedang panik):
6. instruction WAJIB SINGKAT: maksimal 2 kalimat instruksi, dan tiap kalimat
   maksimal 15 kata — kalimat pendek lebih cepat dibacakan dan lebih mudah
   diikuti orang panik. Pecah kalimat panjang jadi beberapa kalimat pendek.
7. MAKSIMAL SATU pertanyaan lanjutan per respon — pilih satu pertanyaan yang paling penting saja, DILARANG memberi daftar pertanyaan
8. Jangan mengulang instruksi yang sudah kamu berikan di riwayat percakapan
9. Langsung ke inti, tanpa kalimat pembuka atau penutup

RESPONSE FORMAT (JSON):
{
    "state": "ASKING" | "CRITICAL",
    "instruction": "string",
    "need_location": boolean
}
`;

// ==============================================
// FUNGSI ANALISIS DARURAT
// ==============================================

// Fungsi utama: nerima teks dari user, panggil Gemini, balikin response
async function analyzeEmergency(userText, conversationHistory = []) {
    // Gabungin system prompt + riwayat percakapan + teks user
    const prompt = `
        ${SYSTEM_PROMPT}
        
        Riwayat percakapan: ${JSON.stringify(conversationHistory)}
        
        Teks pengguna: "${userText}"
    `;
    
    let lastError = null;
    
    // Coba semua model yang tersedia (urutan dari AVAILABLE_MODELS)
    for (const modelName of AVAILABLE_MODELS) {
        try {
            console.log(`🔄 Mencoba model: ${modelName}...`);
            
            // Panggil Gemini API
            const response = await genAI.models.generateContent({
                model: modelName,                // Nama model yang dipake
                contents: prompt,                // Prompt yang dikirim
                config: DEFAULT_CONFIG           // Config (response JSON)
            });
            
            console.log(`✅ Berhasil pake ${modelName}`);
            // Response.text isinya JSON string, di-parse jadi object
            return JSON.parse(response.text);
            
        } catch (error) {
            // Kalo model ini gagal, catet error-nya dan coba model berikutnya
            console.warn(`⚠️ ${modelName} gagal:`, error.message);
            lastError = error;
            // Tunggu 1 detik sebelum coba model berikutnya (biar gak spam)
            await new Promise(resolve => setTimeout(resolve, 1000));
        }
    }
    
    // Kalo semua model gagal, throw error
    throw lastError || new Error('Semua model gagal');
}

// Export fungsi biar dipake di controller
module.exports = { analyzeEmergency };