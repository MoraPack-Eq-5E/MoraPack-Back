package com.grupo5e.morapack.utils;

/**
 * Utilidad para conversión de coordenadas geográficas
 */
public class CoordenadasUtils {

    /**
     * Convierte coordenadas en formato DMS (Grados, Minutos, Segundos) a formato decimal.
     * Formato esperado: "04°42'05" N" (sin espacios excepto antes de N/S/E/W)
     *
     * @param coordenada String con formato DMS, ejemplo: "00°06'48" N"
     * @return Valor decimal (positivo para N/E, negativo para S/W)
     */
    public static double dmsADecimal(String coordenada) {
        if (coordenada == null || coordenada.trim().isEmpty()) {
            return 0.0;
        }

        try {
            String limpia = coordenada.trim();

            // Determinar hemisferio (última letra)
            boolean esNegativo = limpia.endsWith("S") || limpia.endsWith("W");

            // Remover dirección (N/S/E/W) y espacios
            String numeros = limpia.replaceAll("[NSEW\\s]", "");

            // Remover las comillas dobles finales si existen
            numeros = numeros.replace("\"", "");

            // Dividir por ° y '
            // Formato: 04°42'05 → [04, 42, 05]
            String[] partes = numeros.split("[°'\"\\s:]+");

            double grados = 0;
            double minutos = 0;
            double segundos = 0;

            if (partes.length >= 1 && !partes[0].isEmpty()) {
                grados = Double.parseDouble(partes[0]);
            }
            if (partes.length >= 2 && !partes[1].isEmpty()) {
                minutos = Double.parseDouble(partes[1]);
            }
            if (partes.length >= 3 && !partes[2].isEmpty()) {
                segundos = Double.parseDouble(partes[2]);
            }

            // Convertir a decimal: D + M/60 + S/3600
            double decimal = grados + (minutos / 60.0) + (segundos / 3600.0);

            return esNegativo ? -decimal : decimal;

        } catch (Exception e) {
            System.err.println("❌ Error al parsear coordenada: '" + coordenada + "' - " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * Intenta parsear una coordenada que puede estar en formato DMS o ya en decimal.
     * Maneja formatos como: "Latitude: 40° 28' 02" N" o "40° 28' 02" N" o "40.4672"
     *
     * @param coordenada String con la coordenada
     * @return Valor decimal
     */
    public static double parsearCoordenada(String coordenada) {
//        if (coordenada == null || coordenada.trim().isEmpty()) {
//            return 0.0;
//        }
//
//        // Limpiar prefijos como "Latitude:" o "Longitude:"
//        String limpia = coordenada.trim()
//                .replaceFirst("(?i)Latitude:\\s*", "")
//                .replaceFirst("(?i)Longitude:\\s*", "")
//                .trim();
//
//        // Si contiene símbolos de grados, es formato DMS
//        if (limpia.contains("°") || limpia.contains("'") ||
//            limpia.contains("\"") || limpia.contains("N") ||
//            limpia.contains("S") || limpia.contains("E") ||
//            limpia.contains("W")) {
//            return dmsADecimal(limpia);
//        }
//
//        // Si no, intentar parsear como decimal directo
//        try {
//            return Double.parseDouble(limpia);
//        } catch (NumberFormatException e) {
//            System.err.println("No se pudo parsear coordenada: " + coordenada);
//            return 0.0;
//        }
        if (coordenada == null || coordenada.trim().isEmpty()) {
            return 0.0;
        }

        String limpia = coordenada.trim()
                .replaceFirst("(?i)Latitude:\\s*", "")
                .replaceFirst("(?i)Longitude:\\s*", "")
                .trim();

        // Detectar formatos DMS explícitos o con separadores por espacios/:
        boolean contieneDMSSymbols = limpia.contains("°") || limpia.contains("'") ||
                limpia.contains("\"") || limpia.matches(".*[NSEW].*");

        boolean pareceDMSConEspacios = limpia.matches("^\\s*\\d{1,3}[:\\s]+\\d{1,2}[:\\s]+\\d{1,2}\\s*[NSEW]?\\s*$");

        if (contieneDMSSymbols || pareceDMSConEspacios) {
            return dmsADecimal(limpia);
        }

        // Intentar parsear como decimal directo (acepta puntos o comas)
        try {
            String normalizada = limpia.replace(',', '.');
            return Double.parseDouble(normalizada);
        } catch (NumberFormatException e) {
            System.err.println("No se pudo parsear coordenada: " + coordenada);
            return 0.0;
        }
    }

    /**
     * Calcula la distancia entre dos puntos geográficos usando la fórmula de Haversine.
     *
     * @param lat1 Latitud del punto 1 en grados decimales
     * @param lon1 Longitud del punto 1 en grados decimales
     * @param lat2 Latitud del punto 2 en grados decimales
     * @param lon2 Longitud del punto 2 en grados decimales
     * @return Distancia en kilómetros
     */
    public static double calcularDistancia(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371; // Radio de la Tierra en km

        double latRad1 = Math.toRadians(lat1);
        double latRad2 = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(latRad1) * Math.cos(latRad2) *
                        Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    /**
     * Interpola linealmente entre dos coordenadas.
     *
     * @param inicio Coordenada inicial
     * @param fin Coordenada final
     * @param progreso Progreso (0.0 a 1.0)
     * @return Coordenada interpolada
     */
    public static double interpolar(double inicio, double fin, double progreso) {
        progreso = Math.max(0.0, Math.min(1.0, progreso)); // Clamp entre 0 y 1
        return inicio + (fin - inicio) * progreso;
    }
}

