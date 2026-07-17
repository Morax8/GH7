-- ==============================================
-- FILE: seed.sql (Bikin database + isi data RS)
-- ==============================================
--
-- Cara pakai:
--   mysql -u root -p < seed.sql
--
-- Nama database harus sama dengan DB_NAME di .env (default: siaga_db).
-- Aman dijalankan berulang: data lama dihapus dulu, jadi tidak dobel.

CREATE DATABASE IF NOT EXISTS siaga_db;
USE siaga_db;

CREATE TABLE IF NOT EXISTS hospitals (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    address VARCHAR(255) NOT NULL,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    district VARCHAR(100) NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1
);

-- Kosongkan dulu biar tidak dobel kalau di-run ulang
DELETE FROM hospitals;

-- Data RS sekitar Gading Serpong / Tangerang.
-- Catatan: nomor di sini nomor RS asli, TAPI saat kondisi kritis aplikasi
-- menelepon DEMO_PHONE di emergencyController.js, bukan nomor ini — supaya
-- RS beneran tidak tertelepon saat testing.
INSERT INTO hospitals (name, phone, address, latitude, longitude, district, is_active) VALUES
('RS Bethsaida Gading Serpong', '021-29309999', 'Jl. Boulevard Raya Gading Serpong, Tangerang', -6.2394, 106.6281, 'Gading Serpong', 1),
('RS St. Carolus Summarecon Serpong', '021-54220811', 'Jl. Gading Golf Boulevard, Gading Serpong, Tangerang', -6.2367, 106.6350, 'Gading Serpong', 1),
('Siloam Hospitals Kelapa Dua', '021-80639900', 'Jl. Kelapa Dua Raya No.1001, Kelapa Dua, Tangerang', -6.2238, 106.6142, 'Kelapa Dua', 1),
('Eka Hospital BSD', '021-25655555', 'Central Business District Lot IX, BSD City, Tangerang Selatan', -6.3009, 106.6683, 'BSD', 1),
('Omni Hospital Alam Sutera', '021-29779999', 'Jl. Alam Sutera Boulevard No.Kav. 25, Tangerang Selatan', -6.2246, 106.6539, 'Alam Sutera', 1),
('RSUD Kabupaten Tangerang', '021-5523333', 'Jl. Jend. Ahmad Yani No.9, Tangerang', -6.1767, 106.6303, 'Tangerang', 1);

SELECT COUNT(*) AS total_rs FROM hospitals;
