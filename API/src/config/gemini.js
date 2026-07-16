// ==============================================
// FILE: src/config/gemini.js (Konfigurasi Gemini API)
// ==============================================

const { GoogleGenAI } = require('@google/genai');
// Load environment variables
require('dotenv').config();

// ==============================================
// INISIALISASI GEMINI
// ==============================================

const genAI = new GoogleGenAI({
    apiKey: process.env.GEMINI_API_KEY
});

// ==============================================
// DAFTAR MODEL YANG TERSEDIA
// ==============================================

const AVAILABLE_MODELS = [
    'gemini-3.1-flash-lite',
    'gemini-3.5-flash',
    'gemini-3.1-pro'           
];

// ==============================================
// KONFIGURASI DEFAULT
// ==============================================

const DEFAULT_CONFIG = {
    responseMimeType: 'application/json'   
};

module.exports = { genAI, AVAILABLE_MODELS, DEFAULT_CONFIG };