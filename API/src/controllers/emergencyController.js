// src/controllers/emergencyController.js

// ==============================================
// FILE: emergencyController.js (LOGIKA UTAMA APLIKASI)
// ==============================================

// Import service untuk buat manggil AI
const { analyzeEmergency } = require('../services/aiService');

// Import model untuk mengakses database rumah sakit 
const { findNearestHospital } = require('../models/hospitalModel');

// ==============================================
// SESSION STORAGE (Penyimpanan sementara di memori)
// ==============================================

const userSessions = new Map();

// ==============================================
// FUNGSI HANDLE PESAN DARURAT
// ==============================================

/** vvgh    
 * @param {Object} socket - Objek socket.io untuk komunikasi real-time
 * @param {string} text - Teks pesan dari user (hasil STT atau ketikan)
 */
async function handleEmergencyMessage(socket, text) {
    // get userId
    const userId = socket.id;

    // for debugging jdi gw buat console log di terminal
    console.log(`🔄 [${userId}] START handleEmergencyMessage`);
    console.log(`📨 [${userId}] Text received:`, text);
    
    // Inisialisasi session user if not exist
    if (!userSessions.has(userId)) {
        console.log(`🆕 [${userId}] Creating new session`);
        userSessions.set(userId, { 
            history: [],          
            location: null,       
            district: null        
        });
    }
    
    // Ambil data session user dari Map 
    const session = userSessions.get(userId);
    console.log(`📊 [${userId}] Session history length:`, session.history.length);
    
    try {
    // Call Gemini AI 
        console.log(`🤖 [${userId}] Calling analyzeEmergency...`);
        
        const aiResponse = await analyzeEmergency(text, session.history);
        
        console.log(`✅ [${userId}] analyzeEmergency success!`);
        console.log(`📦 [${userId}] AI Response:`, JSON.stringify(aiResponse, null, 2));
        
    // Save history 
        session.history.push({ user: text, ai: aiResponse });
        console.log(`📚 [${userId}] History updated, now:`, session.history.length);
        
    // cek AI need location apa ngga
        if (aiResponse.need_location) {
            console.log(`📍 [${userId}] need_location = true, requesting location...`);
            
            socket.emit('ask-location', {
                type: 'LOCATION_REQUEST',           
                instruction: aiResponse.instruction, 
                state: aiResponse.state              
            });
            
            console.log(`✅ [${userId}] ask-location event emitted!`);
            return; 
        }
        
    // state checking w/ victim location
        if (aiResponse.state === 'CRITICAL' && session.location) {
            console.log(`🏥 [${userId}] CRITICAL and location exists, finding hospital...`);
            console.log(`📍 [${userId}] Location:`, session.location);
            
            const hospital = await findNearestHospital(
                session.location.latitude,   
                session.location.longitude,  
                session.district             
            );
            
            if (hospital) {
                console.log(`🏥 [${userId}] Hospital found:`, hospital.name);
                console.log(`📞 [${userId}] Hospital phone:`, hospital.phone);
                
                socket.emit('ai-response', {
                    ...aiResponse,                       
                    hospital: {                          
                        name: hospital.name,             
                        phone: hospital.phone,           
                        address: hospital.address,       
                        distance: `${Math.round(hospital.distance_km)} km`
                    },
                    action: 'DIAL_EMERGENCY'                             });
                
                console.log(`✅ [${userId}] ai-response with hospital emitted!`);
                return; 
            } else {
                console.log(`⚠️ [${userId}] No hospital found!`);
            }
        }
        
        // - State ASKING (belum cukup info) ATAU
        // - State CRITICAL tapi belum ada lokasi ATAU
        // - Tidak ada RS di database
        console.log(`📤 [${userId}] Sending normal ai-response...`);
        
        socket.emit('ai-response', aiResponse);
        
        console.log(`✅ [${userId}] ai-response emitted!`);
        console.log(`📦 [${userId}] Response data:`, JSON.stringify(aiResponse, null, 2));
        
    } catch (error) {
        // ERROR HANDLING
        console.error(`❌ [${userId}] ERROR in handleEmergencyMessage:`);
        console.error(`❌ [${userId}] Error message:`, error.message);
        console.error(`❌ [${userId}] Full error:`, error);
        
        socket.emit('error', { 
            message: 'Terjadi kesalahan sistem. Silakan coba lagi.' 
        });
        
        console.log(`✅ [${userId}] error event emitted (fallback)`);
    }
    
    console.log(`🏁 [${userId}] END handleEmergencyMessage`);
}

// FUNGSI HANDLE LOKASI USER

/**
 * Fungsi untuk menangani data lokasi yang dikirim dari Android
 * @param {Object} socket - Objek socket.io
 * @param {Object} locationData - Data lokasi dari user { latitude, longitude, district }
 */
async function handleUserLocation(socket, locationData) {
    const userId = socket.id;
    
    console.log(`📍 [${userId}] START handleUserLocation`);
    console.log(`📍 [${userId}] Location data:`, locationData);
    
    if (!userSessions.has(userId)) {
        console.log(`🆕 [${userId}] Creating new session for location`);
        userSessions.set(userId, { 
            history: [],          
            location: null,       
            district: null        
        });
    }
    
    const session = userSessions.get(userId);
    
    session.location = locationData;                     
    session.district = locationData.district || null;        
    console.log(`✅ [${userId}] Location saved!`);
    console.log(`📍 [${userId}] Lat: ${locationData.latitude}, Lng: ${locationData.longitude}`);
    
    socket.emit('location-response', {
        status: 'OK',                                    
        message: 'Lokasi diterima. Mencari RS terdekat...'
    });
    
    console.log(`✅ [${userId}] location-response emitted!`);
    console.log(`🏁 [${userId}] END handleUserLocation`);
}

// EXPORT MODULE

module.exports = { 
    handleEmergencyMessage,   
    handleUserLocation,       
    userSessions              
};