package com.grupo5e.morapack.core.constants;

public class Constantes {
    // ===================================================================
    // MODO DE FUENTE DE DATOS
    // ===================================================================
    /**
     * Modo de fuente de datos del algoritmo ALNS.
     * Valores posibles: "ARCHIVO" o "BASEDATOS"
     * Se puede configurar mediante variable de entorno MODO_FUENTE_DATOS
     */
    public static final String MODO_FUENTE_DATOS = System.getenv("MODO_FUENTE_DATOS") != null
            ? System.getenv("MODO_FUENTE_DATOS")
            : "ARCHIVO";

    // ===================================================================
    // RUTAS DE ARCHIVOS
    // ===================================================================
    public static final String RUTA_ARCHIVO_INFO_AEROPUERTOS = "data/aeropuertosinfo.txt";
    public static final String RUTA_ARCHIVO_VUELOS = "data/vuelos.txt";
    public static final String RUTA_ARCHIVO_PRODUCTOS = "data/productos.txt";
    public static final String RUTA_ARCHIVO_CANCELACIONES = "data/cancelaciones.txt";

    // ===================================================================
    // CONSTANTES DE TIEMPO (ALGORITMO)
    // ===================================================================
    /**
     * Tiempo mínimo de layover en aeropuertos intermedios: 1 hora
     * Requisito del sistema: productos en tránsito (destino intermedio) deben
     * esperar mínimo 1 hora
     */
    public static final int TIEMPO_LAYOVER_MINIMO_MINUTOS = 60;

    /**
     * Tiempo de conexión entre vuelos: 1 hora
     */
    public static final int TIEMPO_CONEXION_MINUTOS = 60;

    // ===================================================================
    // CONSTANTES DEL ALGORITMO
    // ===================================================================
    public static final int LIMITE_INFERIOR_ESPACIO_SOLUCION = 100;
    public static final int LIMITE_SUPERIOR_ESPACIO_SOLUCION = 200;

    // Parámetros de destrucción ALNS optimizados para MoraPack
    public static final double RATIO_DESTRUCCION = 0.15; // 15% - Ratio moderado para ALNS
    public static final int DESTRUCCION_MIN_PAQUETES = 10; // Mínimo 10 paquetes
    public static final int DESTRUCCION_MAX_PAQUETES = 500; // Máximo 500 paquetes (ajustable según problema)
    public static final int DESTRUCCION_MAX_PAQUETES_EXPANSION = 100; // Para expansiones más controladas

    // Constantes de tiempo de entrega
    public static final double TIEMPO_MAX_ENTREGA_MISMO_CONTINENTE = 2.0;
    public static final double TIEMPO_MAX_ENTREGA_DIFERENTE_CONTINENTE = 3.0;

    public static final double TIEMPO_TRANSPORTE_MISMO_CONTINENTE = 0.5;
    public static final double TIEMPO_TRANSPORTE_DIFERENTE_CONTINENTE = 1.0;

    public static final int CAPACIDAD_MIN_MISMO_CONTINENTE = 200;
    public static final int CAPACIDAD_MAX_MISMO_CONTINENTE = 300;
    public static final int CAPACIDAD_MIN_DIFERENTE_CONTINENTE = 250;
    public static final int CAPACIDAD_MAX_DIFERENTE_CONTINENTE = 400;

    public static final int CAPACIDAD_MIN_ALMACEN = 600;
    public static final int CAPACIDAD_MAX_ALMACEN = 1000;

    public static final int HORAS_MAX_RECOGIDA_CLIENTE = 2;

    // NUEVO: Control de tipo de solución inicial
    public static final boolean USAR_SOLUCION_INICIAL_CODICIOSA = false; // true=codiciosa, false=aleatoria
    public static final double PROBABILIDAD_ASIGNACION_ALEATORIA = 0.3; // Para solución aleatoria: 30% de asignación

    // Control de logs
    public static final boolean LOGGING_VERBOSO = false; // true=logs detallados, false=logs mínimos
    public static final int INTERVALO_LOG_ITERACION = 100; // Mostrar solo cada X iteraciones

    // NEW: Diversificación extrema / Restart inteligente
    public static final int UMBRAL_ESTANCAMIENTO_PARA_RESTART = 50; // Iteraciones sin mejora significativa para restart
    public static final double UMBRAL_MEJORA_SIGNIFICATIVA = 0.1; // 0.1% mínimo para considerar mejora significativa
    public static final double RATIO_DESTRUCCION_EXTREMA = 0.8; // 80% destrucción para restart
    public static final int MAX_RESTARTS = 3; // Máximo número de restarts por ejecución

    // OPTIMIZACIÓN: Terminación inteligente
    public static final int UMBRAL_ESTANCAMIENTO_PARADA = 300; // Iteraciones sin mejora para parar
    public static final double UMBRAL_SOLUCION_OPTIMA = 0.98; // 98% asignación = solución óptima
    public static final int ITERACIONES_MINIMAS_ANTES_PARADA = 50; // Mínimo de iteraciones antes de considerar parada
    public static final int ITERACIONES_SIN_MEJORA_SOLUCION_OPTIMA = 500; // Iteraciones sin mejora para parar con
                                                                          // solución óptima

    // OPTIMIZACIÓN: Temperatura adaptativa
    public static final double FACTOR_RECALENTAMIENTO = 1.2; // Aumentar temperatura 20% en estancamiento
    public static final double FACTOR_ENFRIAMIENTO_RAPIDO = 0.95; // Enfriar más rápido si hay mejoras
    public static final int INTERVALO_RECALENTAMIENTO = 50; // Cada cuántas iteraciones sin mejora recalentar

    // ===================================================================
    // BALANCEO DE ESCALAS - Evitar concentración en aeropuertos
    // ===================================================================
    public static final double UMBRAL_CONCENTRACION_ESCALA = 0.25;

    public static final int PESO_PENALIZACION_CONCENTRACION = 200;

    public static final int BONUS_ESCALA_MISMO_CONTINENTE = 50;

    public static final int PENALIZACION_ESCALA_FUERA_CONTINENTE = 100;

    public static final int PENALIZACION_ESCALA_TERCER_CONTINENTE = 50;

    public static final double UMBRAL_SATURACION_AEROPUERTO = 0.7;

    // ===================================================================
    // ALMACENES PRINCIPALES CON CAPACIDAD ILIMITADA (CRÍTICO)
    // ===================================================================
    /**
     * Almacenes principales de MoraPack con capacidad ILIMITADA.
     * Lima (Perú), Bruselas (Bélgica) y Baku (Azerbaiyán) son las sedes centrales.
     * Estos almacenes tienen stock ilimitado Y capacidad de almacenamiento
     * ilimitada.
     * 
     * IMPORTANTE: Esta es una optimización crítica del algoritmo.
     */
    public static final String ALMACEN_LIMA = "Lima";
    public static final String ALMACEN_BRUSELAS = "Brussels";
    public static final String ALMACEN_BRUSELAS_ES = "Bruselas";
    public static final String ALMACEN_BAKU = "Baku";

    /**
     * Lista de nombres de ciudades que corresponden a almacenes principales.
     * Se usa para verificar rápidamente si un aeropuerto es almacén principal.
     */
    public static final String[] ALMACENES_PRINCIPALES = {
            "Lima", "lima", "LIMA",
            "Brussels", "brussels", "BRUSSELS",
            "Bruselas", "bruselas", "BRUSELAS",
            "Brussel", "brussel", "BRUSSEL",
            "Baku", "baku", "BAKU"
    };

    /**
     * Verifica si un nombre de ciudad corresponde a un almacén principal.
     * 
     * @param nombreCiudad Nombre de la ciudad a verificar
     * @return true si es almacén principal, false en caso contrario
     */
    public static boolean esAlmacenPrincipal(String nombreCiudad) {
        if (nombreCiudad == null)
            return false;
        String nombreLower = nombreCiudad.toLowerCase();
        return nombreLower.contains("lima") ||
                nombreLower.contains("brussel") ||
                nombreLower.contains("bruselas") ||
                nombreLower.contains("baku");
    }
}
