    // ==============================================
    // FILE: server.js (Entry Point Utama)
    // ==============================================
    
    const express = require('express');
    const cors = require('cors');
    const http = require('http');
    const socketIo = require('socket.io');
    require('dotenv').config();
    
    // === IMPORT MODUL INTERNAL (buatan kita sendiri) ===
    
    // Koneksi ke database MySQL
    const { connectDB } = require('./src/config/database');
    const { AVAILABLE_MODELS } = require('./src/config/gemini');
    const apiRoutes = require('./src/routes/api');
    const { 
        handleEmergencyMessage, 
        handleUserLocation 
    } = require('./src/controllers/emergencyController');
    
    // INIT APP (Setup Server)
    
    const app = express();
    const server = http.createServer(app);
    const io = socketIo(server, {
        cors: {
            origin: "*",        
            methods: ["GET", "POST"]
        }
    });
    
    // MIDDLEWARE (Proses request sebelum sampai ke route)
    
    // Aktifkan CORS
    app.use(cors());
    app.use(express.json());
    app.use(express.urlencoded({ extended: true }));
    
    // ROUTES (Endpoint HTTP)
    
    app.use('/api', apiRoutes);
    
    app.get('/', (req, res) => {
        res.json({
            name: 'Emergency Dispatcher Backend',
            version: '1.0.0',
            status: 'running',
            endpoints: {
                health: 'GET /api/health',
                test: 'POST /api/test-gemini',
                hospitals: 'GET /api/hospitals',
                models: 'GET /api/models'
            },
            socket: {
                events: {
                    from_client: ['user-message', 'user-location'],
                    to_client: ['ai-response', 'ask-location', 'location-response', 'error']
                }
            }
        });
    });
    
    // SOCKET.IO (Komunikasi Real-time dengan Android)
    
    io.on('connection', (socket) => {
        console.log(`✅ User connected: ${socket.id}`);
        
        // --- EVENT 1: Nerima pesan darurat dari user ---
        socket.on('user-message', async (text) => {
            await handleEmergencyMessage(socket, text);
        });
        
        // --- EVENT 2: Nerima lokasi dari user ---
        socket.on('user-location', (data) => {
            // Panggil controller buat simpan lokasi
            handleUserLocation(socket, data);
        });
        
        // --- EVENT 3: User disconnect ---
        socket.on('disconnect', () => {
            console.log(`❌ User disconnected: ${socket.id}`);
        });
    });
    
    // START SERVER
    
    const PORT = process.env.PORT;
    
    // Jalankan server
    server.listen(PORT, async () => {
        console.log(`\n🚀 Server jalan di http://localhost:${PORT}`);
        console.log(`📋 Model yang digunakan: ${AVAILABLE_MODELS.join(', ')}`);
        
        await connectDB();

        // Buka koneksi TTS lebih awal biar suara pertama gak kena delay handshake
        await require('./src/services/ttsService').prewarm();

        console.log(`\n📡 Socket.IO: http://localhost:${PORT}`);
        console.log(`📝 API Docs: http://localhost:${PORT}/`);
    });