// ==============================================
// FILE: src/config/database.js (Koneksi ke MySQL)
// ==============================================

// Import library mysql2 (promise version biar pake async/await)
const mysql = require('mysql2/promise');
// Load environment variables dari .env
require('dotenv').config();

// ==============================================
// BUAT POOL KONEKSI
// ==============================================

// Pool = kumpulan koneksi yang siap dipake (lebih efisien)
const pool = mysql.createPool({
    host: process.env.DB_HOST || 'localhost',    // Alamat database
    user: process.env.DB_USER || 'root',         // Username MySQL
    password: process.env.DB_PASSWORD || '',     // Password MySQL
    database: process.env.DB_NAME || 'siaga_db', // Nama database
    port: process.env.DB_PORT || 3306,           // Port MySQL (default 3306)
    waitForConnections: true,    // Tunggu kalo semua koneksi lagi dipake
    connectionLimit: 10,         // Maksimal 10 koneksi sekaligus
    queueLimit: 0                // 0 = unlimited antrian
});

// ==============================================
// FUNGSI TEST KONEKSI
// ==============================================

// Fungsi buat ngecek apakah koneksi ke database berhasil
async function connectDB() {
    try {
        // Ambil 1 koneksi dari pool
        const connection = await pool.getConnection();
        console.log('✅ Database connected!');
        // Lepas koneksi (balikin ke pool)
        connection.release();
        return true;
    } catch (error) {
        console.error('❌ Database connection failed:', error.message);
        return false;
    }
}

// Export biar bisa dipake di file lain
module.exports = { pool, connectDB };