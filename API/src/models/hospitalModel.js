// ==============================================
// FILE: src/models/hospitalModel.js (Query Database RS)
// ==============================================

const { pool } = require('../config/database');

// FUNGSI CARI RS TERDEKAT
async function findNearestHospital(lat, lng, district = null) {
    let query = `
        SELECT 
            id, 
            name, 
            phone, 
            address,
            latitude, 
            longitude,
            district,
            // Rumus Haversine: menghitung jarak dalam km
            (6371 * ACOS(
                COS(RADIANS(?)) * COS(RADIANS(latitude)) * 
                COS(RADIANS(longitude) - RADIANS(?)) + 
                SIN(RADIANS(?)) * SIN(RADIANS(latitude))
            )) AS distance_km
        FROM hospitals
        WHERE is_active = 1
    `;
    
    // Parameter buat query (lat, lng, lat)
    const params = [lat, lng, lat];
    
    // Kalo ada district, prioritasin RS di district yang sama
    if (district) {
        query += ` AND district = ?`;
        params.push(district);
    }
    
    // Urutkan dari jarak terdekat, ambil 1
    query += ` ORDER BY distance_km LIMIT 1`;
    
    try {
        // Eksekusi query
        const [rows] = await pool.execute(query, params);
        return rows[0] || null;
    } catch (error) {
        console.error('❌ Query error:', error.message);
        return null;
    }
}

// FUNGSI AMBIL SEMUA RS

async function getAllHospitals() {
    try {
        const [rows] = await pool.execute(
            'SELECT id, name, phone, address, latitude, longitude, district FROM hospitals WHERE is_active = 1'
        );
        return rows;
    } catch (error) {
        console.error('❌ Query error:', error.message);
        return [];
    }
}

module.exports = { findNearestHospital, getAllHospitals };