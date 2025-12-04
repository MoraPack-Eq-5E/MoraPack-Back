package com.grupo5e.morapack.core.index;

import com.grupo5e.morapack.core.model.Vuelo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache de rutas precalculadas para evitar búsquedas repetidas.
 * Almacena las mejores rutas encontradas para pares origen-destino-día.
 * 
 * OPTIMIZACIÓN: Evita recalcular rutas complejas (directa, 1 escala, 2 escalas)
 * cuando múltiples pedidos comparten el mismo origen y destino en el mismo día.
 * 
 * Patrón: Memoization Pattern
 */
public class CacheRutas {

    // Cache: "ORIGEN-DESTINO-DIA" → Lista de rutas válidas encontradas
    private final Map<String, List<ArrayList<Vuelo>>> cacheRutasCalculadas;

    // Estadísticas
    private int hits = 0;
    private int misses = 0;
    private int rutasGuardadas = 0;

    /**
     * Constructor del cache de rutas.
     * OPTIMIZACIÓN: Usa ConcurrentHashMap para mejor rendimiento con muchas
     * entradas.
     */
    public CacheRutas() {
        this.cacheRutasCalculadas = new ConcurrentHashMap<>();
    }

    /**
     * Obtiene rutas precalculadas para un par origen-destino en un día específico.
     * 
     * @param origen  Código IATA del aeropuerto de origen
     * @param destino Código IATA del aeropuerto de destino
     * @param dia     Día de operación (1-based)
     * @return Lista de rutas válidas, o null si no está en cache
     */
    public List<ArrayList<Vuelo>> obtenerRutasCalculadas(String origen, String destino, int dia) {
        String clave = generarClave(origen, destino, dia);
        List<ArrayList<Vuelo>> rutas = cacheRutasCalculadas.get(clave);

        if (rutas != null) {
            hits++;
            // Retornar copia defensiva para proteger el cache
            return new ArrayList<>(rutas);
        }

        misses++;
        return null; // Cache miss
    }

    /**
     * Guarda rutas calculadas en el cache.
     * 
     * @param origen  Código IATA del aeropuerto de origen
     * @param destino Código IATA del aeropuerto de destino
     * @param dia     Día de operación (1-based)
     * @param rutas   Lista de rutas válidas encontradas
     */
    public void guardarRutas(String origen, String destino, int dia, List<ArrayList<Vuelo>> rutas) {
        if (rutas == null || rutas.isEmpty()) {
            return; // No guardar rutas vacías
        }

        String clave = generarClave(origen, destino, dia);
        // Guardar copia defensiva para proteger el cache
        cacheRutasCalculadas.put(clave, new ArrayList<>(rutas));
        rutasGuardadas++;
    }

    /**
     * Genera la clave única para el cache.
     * Formato: "ORIGEN-DESTINO-DIA"
     * 
     * @param origen  Código IATA del aeropuerto de origen
     * @param destino Código IATA del aeropuerto de destino
     * @param dia     Día de operación
     * @return Clave única para el cache
     */
    private String generarClave(String origen, String destino, int dia) {
        return origen + "-" + destino + "-" + dia;
    }

    /**
     * Limpia el cache completamente.
     * Útil para reiniciar el cache entre iteraciones del algoritmo.
     */
    public void limpiarCache() {
        cacheRutasCalculadas.clear();
        resetearEstadisticas();
    }

    /**
     * Limpia rutas para días anteriores al día actual.
     * Útil para gestión de memoria en ejecuciones largas.
     * 
     * @param diaActual Día actual de operación
     */
    public void limpiarDiasAnteriores(int diaActual) {
        cacheRutasCalculadas.keySet().removeIf(clave -> {
            String[] partes = clave.split("-");
            if (partes.length >= 3) {
                try {
                    int dia = Integer.parseInt(partes[2]);
                    return dia < diaActual;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            return false;
        });
    }

    // ========== Estadísticas ==========

    /**
     * Obtiene la tasa de aciertos del cache (hit rate).
     * 
     * @return Porcentaje de hits (0.0 - 1.0)
     */
    public double getHitRate() {
        int total = hits + misses;
        if (total == 0)
            return 0.0;
        return (double) hits / total;
    }

    /**
     * Obtiene el número total de entradas en cache.
     * 
     * @return Número de pares (origen-destino-día) cacheados
     */
    public int getTotalEntradas() {
        return cacheRutasCalculadas.size();
    }

    /**
     * Resetea las estadísticas del cache.
     */
    public void resetearEstadisticas() {
        hits = 0;
        misses = 0;
        rutasGuardadas = 0;
    }

    /**
     * Imprime estadísticas del cache para debugging.
     */
    public void imprimirEstadisticas() {
        System.out.println("=== Estadísticas de Cache de Rutas ===");
        System.out.println("Hits: " + hits);
        System.out.println("Misses: " + misses);
        System.out.println("Hit Rate: " + String.format("%.2f%%", getHitRate() * 100));
        System.out.println("Entradas cacheadas: " + getTotalEntradas());
        System.out.println("Rutas guardadas: " + rutasGuardadas);
        System.out.println("======================================");
    }
}
