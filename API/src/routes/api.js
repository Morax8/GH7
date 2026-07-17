// FILE: src/routes/api.js (Route HTTP untuk Testing)

const express = require('express');
const { pool } = require('../config/database');
const { getAllHospitals } = require('../models/hospitalModel');
const { analyzeEmergency } = require('../services/aiService');

const router = express.Router();

// ENDPOINT 1: HEALTH CHECK

// GET /api/health - Buat cek server hidup
router.get('/health', (req, res) => {
    res.json({ 
        status: 'OK', 
        timestamp: new Date().toISOString(),
        service: 'Emergency Dispatcher'
    });
});

// ENDPOINT 2: TEST GEMINI (tanpa Socket.IO)

// POST /api/test-gemini - Buat test Gemini pake HTTP (biar gampang)
router.post('/test-gemini', async (req, res) => {
    try {
        const { text } = req.body;
        
        if (!text) {
            return res.status(400).json({ error: 'Text is required' });
        }
        
        const result = await analyzeEmergency(text);
        res.json(result);
        
    } catch (error) {
        console.error('❌ Test Gemini error:', error.message);
        res.status(500).json({ error: error.message });
    }
});

// ENDPOINT 3: LIST SEMUA RUMAH SAKIT

// GET /api/hospitals - Buat liat semua RS di database
router.get('/hospitals', async (req, res) => {
    try {
        const hospitals = await getAllHospitals();
        res.json({ 
            total: hospitals.length,
            data: hospitals 
        });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// ENDPOINT 4: TEXT-TO-SPEECH (Edge TTS, suara natural)

// GET /api/tts?text=... -> stream audio MP3 (chunk dikirim begitu jadi)
// Dipakai MediaPlayer Android secara streaming biar suara cepat mulai.
function handleTts(text, res) {
    if (!text) {
        return res.status(400).json({ error: 'Text is required' });
    }
    try {
        const { pipeAudioTo } = require('../services/ttsService');
        res.set('Content-Type', 'audio/mpeg');
        pipeAudioTo(text, res);
    } catch (error) {
        console.error('❌ TTS error:', error.message);
        res.status(500).json({ error: error.message });
    }
}

router.get('/tts', (req, res) => handleTts(req.query.text, res));

// POST /api/tts - body { "text": "..." } -> audio MP3 (untuk testing manual)
router.post('/tts', (req, res) => handleTts(req.body.text, res));

// ENDPOINT 5: LIST MODEL GEMINI

// GET /api/models - Buat liat model Gemini yang tersedia
router.get('/models', (req, res) => {
    const { AVAILABLE_MODELS } = require('../config/gemini');
    res.json({ 
        models: AVAILABLE_MODELS,
        count: AVAILABLE_MODELS.length
    });
});

module.exports = router;