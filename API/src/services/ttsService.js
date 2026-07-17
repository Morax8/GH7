// FILE: src/services/ttsService.js (Text-to-Speech via Edge TTS)

// Pakai suara neural Microsoft Edge (gratis, tanpa API key).
// Catatan: ini endpoint tidak resmi punya Edge — cocok buat demo,
// jangan diandalkan untuk produksi. Client Android punya fallback
// ke TTS bawaan Android kalau endpoint ini gagal.
const { MsEdgeTTS, OUTPUT_FORMAT } = require('msedge-tts');

// Suara Bahasa Indonesia yang tersedia:
// - id-ID-GadisNeural (perempuan)
// - id-ID-ArdiNeural  (laki-laki)
const TTS_VOICE = process.env.TTS_VOICE || 'id-ID-GadisNeural';

// ==============================================
// POOL KONEKSI
// ==============================================

// Satu koneksi Edge cuma bisa nangani SATU sintesis dalam satu waktu — kalau
// dipakai barengan, request kedua ngantri (~2 detik). Makanya kita simpan
// beberapa koneksi yang sudah hangat: handshake (~1 detik) dibayar di muka,
// dan sintesis paralel gak saling tunggu.
const POOL_SIZE = 4;
const idlePool = [];
let warmingCount = 0;

async function createConnection() {
    const tts = new MsEdgeTTS();
    await tts.setMetadata(TTS_VOICE, OUTPUT_FORMAT.AUDIO_24KHZ_48KBITRATE_MONO_MP3);
    return tts;
}

/** Isi ulang pool di background (gak bikin request nunggu). */
function refillPool() {
    const need = POOL_SIZE - idlePool.length - warmingCount;
    for (let i = 0; i < need; i++) {
        warmingCount++;
        createConnection()
            .then((tts) => idlePool.push(tts))
            .catch((error) => console.warn('⚠️ TTS warm-up gagal:', error.message))
            .finally(() => warmingCount--);
    }
}

async function acquireConnection() {
    const pooled = idlePool.pop();
    refillPool(); // langsung siapkan pengganti buat request berikutnya
    return pooled || createConnection();
}

function releaseConnection(tts) {
    if (idlePool.length < POOL_SIZE) idlePool.push(tts);
}

/** Siapkan pool saat server start biar suara pertama gak kena delay handshake. */
async function prewarm() {
    try {
        const connections = await Promise.all(
            Array.from({ length: POOL_SIZE }, () => createConnection())
        );
        idlePool.push(...connections);
        console.log(`🔊 TTS siap (voice: ${TTS_VOICE}, ${idlePool.length} koneksi hangat)`);
    } catch (error) {
        console.warn('⚠️ TTS prewarm gagal:', error.message);
    }
}

// ==============================================
// PEMECAHAN KALIMAT
// ==============================================

// Edge baru mengirim audio setelah selesai menyintesis seluruh teks, jadi
// makin panjang teks makin lama suara pertama keluar (40 char ≈ 0.9 detik,
// 300 char ≈ 2.2 detik). Solusinya: potong jadi beberapa segmen. Segmen
// pertama sengaja pendek biar cepat bunyi; segmen sisanya disintesis
// barengan dan sudah siap sebelum segmen pertama habis diputar.
// Segmen pertama dibatasi sepanjang satu-dua kalimat pendek: cukup untuk
// selesai di batas kalimat yang wajar, tapi tetap cepat disintesis.
const FIRST_SEGMENT_MAX = 110;
const NEXT_SEGMENT_MAX = 220;

/** Ambil potongan <= limit dari depan teks, dipotong di batas yang wajar. */
function takeSegment(text, limit) {
    if (text.length <= limit) return [text, ''];

    const window = text.slice(0, limit);
    const minCut = limit * 0.4; // jangan motong kependekan

    // Prioritas titik potong: akhir kalimat → koma → spasi
    let cut = Math.max(window.lastIndexOf('. '), window.lastIndexOf('! '), window.lastIndexOf('? '));
    if (cut < minCut) cut = window.lastIndexOf(', ');
    if (cut < minCut) cut = window.lastIndexOf(' ');
    if (cut <= 0) cut = limit;
    else cut += 1; // ikutkan tanda bacanya

    return [text.slice(0, cut).trim(), text.slice(cut).trim()];
}

function splitForSpeech(text) {
    const segments = [];
    let rest = text.trim();
    let limit = FIRST_SEGMENT_MAX;

    while (rest) {
        const [segment, remainder] = takeSegment(rest, limit);
        segments.push(segment);
        rest = remainder;
        limit = NEXT_SEGMENT_MAX; // hanya segmen pertama yang perlu ekstra pendek
    }
    return segments;
}

// ==============================================
// CACHE AUDIO (biar sintesis bisa dimulai lebih awal)
// ==============================================

// Key: teks, Value: { segments: [...] }
// Controller memanggil startSynthesis() saat 'ai-response' dikirim, jadi
// Edge sudah mulai bikin audio sebelum HP sempat minta.
const audioCache = new Map();
const CACHE_TTL_MS = 60_000;

/** Sintesis satu segmen; chunk-nya langsung diteruskan ke listener. */
async function runSegment(text, segment) {
    const tts = await acquireConnection();
    let reusable = false;
    try {
        const { audioStream } = await tts.toStream(text);
        for await (const chunk of audioStream) {
            segment.chunks.push(chunk);
            segment.listeners.forEach((l) => l.onChunk(chunk));
        }
        reusable = true; // sukses → koneksi masih sehat
    } catch (error) {
        console.warn('⚠️ TTS gagal:', error.message);
    } finally {
        if (reusable) releaseConnection(tts);
        else refillPool(); // koneksi bermasalah dibuang, siapkan gantinya

        segment.done = true;
        segment.listeners.forEach((l) => l.onEnd());
        segment.listeners = [];
    }
}

/**
 * Mulai (atau ambil) sintesis untuk sebuah teks.
 * Aman dipanggil berkali-kali untuk teks yang sama.
 */
function startSynthesis(text) {
    if (audioCache.has(text)) return audioCache.get(text);

    const texts = splitForSpeech(text);
    const entry = { segments: texts.map(() => ({ chunks: [], done: false, listeners: [] })) };
    audioCache.set(text, entry);
    setTimeout(() => audioCache.delete(text), CACHE_TTL_MS);

    // Digarap berurutan, bukan barengan: sintesis paralel bikin segmen pertama
    // ikut ngantri (~2 detik). Sintesis jauh lebih cepat dari durasi bicara,
    // jadi segmen berikutnya selalu siap sebelum segmen sebelumnya habis.
    (async () => {
        for (let i = 0; i < texts.length; i++) {
            await runSegment(texts[i], entry.segments[i]);
        }
    })();

    return entry;
}

/** Tulis satu segmen ke response sampai habis. */
function pipeSegment(segment, res) {
    return new Promise((resolve) => {
        for (const chunk of segment.chunks) res.write(chunk);
        if (segment.done) return resolve();
        segment.listeners.push({
            onChunk: (chunk) => res.write(chunk),
            onEnd: resolve,
        });
    });
}

/**
 * Alirkan audio sebuah teks ke HTTP response, segmen demi segmen sesuai urutan.
 * Frame MP3 berdiri sendiri, jadi segmen bisa disambung langsung.
 */
async function pipeAudioTo(text, res) {
    const entry = startSynthesis(text);
    for (const segment of entry.segments) {
        if (res.writableEnded) return; // client sudah pergi
        await pipeSegment(segment, res);
    }
    res.end();
}

module.exports = { startSynthesis, pipeAudioTo, prewarm, TTS_VOICE };
