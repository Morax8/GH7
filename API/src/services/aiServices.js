// ==============================================
// FILE: src/services/aiService.js (Logika Panggil Gemini)
// ==============================================

const { genAI, AVAILABLE_MODELS, DEFAULT_CONFIG } = require('../config/gemini');

// SYSTEM PROMPT (Instruksi ke AI)

const SYSTEM_PROMPT = `
Kamu adalah dispatcher medis darurat profesional di Indonesia.
Tugasmu memandu pengguna yang panik dalam situasi darurat medis.

ATURAN:
1. Jika informasi belum cukup → state: "ASKING", beri pertanyaan klarifikasi
2. Jika kondisi kritis (henti jantung, tidak sadar, pendarahan hebat) → state: "CRITICAL"
3. Gunakan Bahasa Indonesia yang mudah dipahami oleh orang awam
4. Jangan berhalusinasi atau memberikan informasi medis yang tidak akurat
5. Berikan instruksi yang actionable (bisa langsung dilakukan)

RESPONSE FORMAT (JSON):
{
    "state": "ASKING" | "CRITICAL",
    "instruction": "string",
    "need_location": boolean
}
`;

// FUNGSI ANALISIS DARURAT

// Fungsi utama: nerima teks dari user, panggil Gemini, balikin response
async function analyzeEmergency(userText, conversationHistory = []) {
    const prompt = `
        ${SYSTEM_PROMPT}
        
        Riwayat percakapan: ${JSON.stringify(conversationHistory)}
        
        Teks pengguna: "${userText}"
    `;
    
    let lastError = null;
    
    for (const modelName of AVAILABLE_MODELS) {
        try {
            console.log(`🔄 Mencoba model: ${modelName}...`);
            
            const response = await genAI.models.generateContent({
                model: modelName,                
                contents: prompt,                
                config: DEFAULT_CONFIG           
            });
            
            console.log(`✅ Berhasil pake ${modelName}`);
            return JSON.parse(response.text);
            
        } catch (error) {
            console.warn(`⚠️ ${modelName} gagal:`, error.message);
            lastError = error;
            await new Promise(resolve => setTimeout(resolve, 1000));
        }
    }
    
    throw lastError || new Error('Semua model gagal');
}

module.exports = { analyzeEmergency };