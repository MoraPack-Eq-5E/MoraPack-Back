package com.grupo5e.morapack.algorithm.alns;

import com.grupo5e.morapack.algorithm.input.FuenteDatosInput;
import com.grupo5e.morapack.algorithm.input.FabricaFuenteDatos;
import com.grupo5e.morapack.api.dto.EventoLineaDeTiempoVueloDTO;
import com.grupo5e.morapack.api.dto.LineaDeTiempoSimulacionDTO;
import com.grupo5e.morapack.core.model.*;
import com.grupo5e.morapack.core.model.Pedido;
import com.grupo5e.morapack.core.constants.Constantes;
import com.grupo5e.morapack.core.service.ServicioDisponibilidadVuelos;
import com.grupo5e.morapack.core.index.IndiceVuelos;
import com.grupo5e.morapack.core.index.CacheDisponibilidad;
import com.grupo5e.morapack.utils.LectorCancelaciones;

import java.util.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;
import java.util.PriorityQueue;
import java.util.Comparator;

/**
 * ALNSSolver adaptado desde el Solution que proporcionaste.
 * Mantiene la lógica lo más fiel posible, cambiando únicamente nombres y constantes
 * para integrarse en tu paquete/comunidad de modelos.
 */

public class ALNSSolver {
    private HashMap<HashMap<Pedido, ArrayList<Vuelo>>, Integer> solucion;

    // Cache robusta Ciudad→Aeropuerto por nombre
    private Map<String, Aeropuerto> cacheNombreCiudadAeropuerto;
    private ArrayList<Aeropuerto> aeropuertos;
    private ArrayList<Vuelo> vuelos;
    private List<Pedido> pedidos;

    // Unitización - DESACTIVADA para evitar problemas con IDs en BD
    private static final boolean HABILITAR_UNITIZACION_PRODUCTO = false;
    private ArrayList<Pedido> pedidosOriginales;
    
    // ProductTracker - Mapeo directo Producto → Ruta (versión simplificada)
    private ProductTracker productTracker;

    // Ancla temporal T0
    private LocalDateTime T0;
    // Ocupación de almacenes
    private HashMap<Aeropuerto, Integer> ocupacionAlmacenes;
    private HashMap<Aeropuerto, int[]> ocupacionTemporalAlmacenes;
    // Mejor solución y random
    private HashMap<HashMap<Pedido, ArrayList<Vuelo>>, Integer> mejorSolucion;
    private Random aleatorio;

    // ALNS operators
    private ALNSDestruction operadoresDestruccion;
    private ALNSRepair operadoresReparacion;
    private double[][] pesosOperadores;
    private double[][] puntajesOperadores;
    private int[][] usoOperadores;
    private double temperatura;
    private double tasaEnfriamiento;
    private int maxIteraciones;
    private int tamanoSegmento;

    // Diversificación / intensificación
    private int contadorEstancamiento;
    private int umbralDiversificacion;
    private boolean modoDiversificacion;
    private int ultimaIteracionMejora;
    private double factorDiversificacion;

    // OPTIMIZED: Pool de paquetes no asignados con PriorityQueue (Backend pattern)
    // Ordena automáticamente por urgencia (deadline más cercano primero)
    private PriorityQueue<Pedido> poolNoAsignados;

    // Diversificación extrema / restart
    private int iteracionesDesdeMejoraSignificativa;
    private int contadorRestarts;
    private double ultimoPesoSignificativo;

    // Servicio de disponibilidad de vuelos (cancelaciones)
    private ServicioDisponibilidadVuelos servicioDisponibilidad;

    // Optimizaciones de rendimiento
    private IndiceVuelos indiceVuelos;
    private CacheDisponibilidad cacheDisponibilidad;
    
    // CRÍTICO: RouteValidator para validación optimizada (Backend pattern)
    private RouteValidator routeValidator;

    // Horizon days
    private static final int HORIZON_DAYS = 4;
    private static final boolean DEBUG_MODE = false;

    // Campos adicionales para modo colapso
    private boolean modoColapso = false;
    private int iteracionesColapso = 0;
    private int maxIteracionesColapso = 1000; // Máximo de iteraciones en modo colapso
    private LocalDateTime horaInicioColapso;
    private Map<String, Object> metricasColapso = new HashMap<>();

    // En la clase ALNSSolver, agregar estos campos:
    private List<EventoSimulacion> eventosColapso;
    private boolean capturandoTimelineColapso = false;

    /**
     * Constructor principal simplificado que usa FuenteDatosInput modular.
     * Permite ejecutar el algoritmo sin dependencias de Spring.
     * 
     * @param maxIteraciones Número máximo de iteraciones ALNS
     */
    public ALNSSolver(int maxIteraciones) {
        this(maxIteraciones, null, null);
    }
    
    /**
     * Constructor con soporte para ventanas de tiempo (escenarios diarios/semanales).
     * Si horaInicio y horaFin son null, carga todos los pedidos.
     * 
     * @param maxIteraciones Número máximo de iteraciones ALNS
     * @param horaInicio Hora de inicio de la ventana de simulación (opcional)
     * @param horaFin Hora de fin de la ventana de simulación (opcional)
     */
    public ALNSSolver(int maxIteraciones, LocalDateTime horaInicio, LocalDateTime horaFin) {
        System.out.println("========================================");
        System.out.println("INICIALIZANDO ALNS SOLVER");
        if (horaInicio != null && horaFin != null) {
            System.out.println("MODO: Ventana de tiempo especificada");
            System.out.println("Ventana: " + horaInicio + " a " + horaFin);
        } else {
            System.out.println("MODO: Todos los pedidos (sin filtrado de tiempo)");
        }
        System.out.println("========================================");
        
        // 1. Usar factory para fuente de datos (ARCHIVO o BASEDATOS)
        FuenteDatosInput fuenteDatos = FabricaFuenteDatos.crearFuenteDatos();
        fuenteDatos.inicializar();
        
        System.out.println("Fuente de datos: " + fuenteDatos.obtenerNombreFuente());
        
        // 2. Cargar datos desde la fuente
        this.aeropuertos = new ArrayList<>(fuenteDatos.cargarAeropuertos());
        this.vuelos = new ArrayList<>(fuenteDatos.cargarVuelos(this.aeropuertos));
        
        // CRÍTICO: Cargar pedidos con filtrado de tiempo si se especifica
        if (horaInicio != null && horaFin != null) {
            this.pedidosOriginales = new ArrayList<>(
                fuenteDatos.cargarPedidosPorVentanaDeTiempo(this.aeropuertos, horaInicio, horaFin)
            );
        } else {
            this.pedidosOriginales = new ArrayList<>(fuenteDatos.cargarPedidos(this.aeropuertos));
        }
        
        System.out.println("Aeropuertos cargados: " + this.aeropuertos.size());
        System.out.println("Vuelos cargados: " + this.vuelos.size());
        System.out.println("Pedidos originales cargados: " + this.pedidosOriginales.size());
        
        // 3. Unitización de productos (si está habilitado)
        if (HABILITAR_UNITIZACION_PRODUCTO) {
            this.pedidos = expandirPaquetesAUnidadesProducto(this.pedidosOriginales);
            System.out.println("UNITIZACIÓN APLICADA: " + this.pedidosOriginales.size() +
                               " pedidos originales → " + this.pedidos.size() + " unidades de producto");
        } else {
            this.pedidos = new ArrayList<>(this.pedidosOriginales);
            System.out.println("UNITIZACIÓN DESHABILITADA: Usando pedidos originales");
        }
        
        // 4. Inicializar ProductTracker para seguimiento a nivel de producto
        this.productTracker = new ProductTracker();
        this.productTracker.initializeFromOrders(this.pedidos);
        System.out.println("ProductTracker inicializado con " + this.pedidos.size() + " pedidos");
        
        // 5. Estructuras base
        this.solucion = new HashMap<>();
        this.ocupacionAlmacenes = new HashMap<>();
        this.ocupacionTemporalAlmacenes = new HashMap<>();
        
        // 6. Inicializar caches y estructuras
        asignarAeropuertosOrigen();
        inicializarCacheCiudadAeropuerto();
        inicializarT0();
        
        // 7. RNG y operadores
        this.aleatorio = new Random(System.currentTimeMillis());
        this.operadoresDestruccion = new ALNSDestruction(this.aeropuertos);
        this.operadoresReparacion = new ALNSRepair(this.aeropuertos, this.vuelos, this.ocupacionAlmacenes);
        
        // 8. Parámetros ALNS
        this.maxIteraciones = maxIteraciones;
        this.temperatura = 100.0;
        this.tasaEnfriamiento = 0.95;
        this.tamanoSegmento = 100;
        
        inicializarParametrosALNS();
        inicializarCapacidadAeropuertos();
        inicializarOcupacionTemporalAlmacenes();
        
        // 9. Servicios auxiliares / optimizaciones
        inicializarServicioDisponibilidad();
        inicializarOptimizaciones();
        
        // 10. Inicializar RouteValidator (CRÍTICO - patrón Backend)
        System.out.println("Inicializando RouteValidator (optimización O(1))...");
        this.routeValidator = new RouteValidator(this.aeropuertos, this.vuelos);
        
        System.out.println("ALNS Solver inicializado correctamente");
        System.out.println("Iteraciones configuradas: " + this.maxIteraciones);
        System.out.println("========================================\n");
    }
    
    /**
     * Constructor con parámetros por defecto (500 iteraciones).
     */
    public ALNSSolver() {
        this(500);
    }

    private void inicializarParametrosALNS() {
        int numOperadoresDestruccion = 4;
        int numOperadoresReparacion = 4;

        this.pesosOperadores = new double[numOperadoresDestruccion][numOperadoresReparacion];
        this.puntajesOperadores = new double[numOperadoresDestruccion][numOperadoresReparacion];
        this.usoOperadores = new int[numOperadoresDestruccion][numOperadoresReparacion];

        for (int i = 0; i < numOperadoresDestruccion; i++) {
            for (int j = 0; j < numOperadoresReparacion; j++) {
                this.pesosOperadores[i][j] = 1.0;
                this.puntajesOperadores[i][j] = 0.0;
                this.usoOperadores[i][j] = 0;
            }
        }

        this.temperatura = 100.0;
        this.tasaEnfriamiento = 0.98;
        //OJO CON ESTE - Ya NO se sobrescribe, se usa el del constructor
        // this.maxIteraciones = 500;  // ← COMENTADO para usar el parámetro del constructor
        this.tamanoSegmento = 25;
        
        System.out.println("🔧 ALNS configurado con " + this.maxIteraciones + " iteraciones máximas");

        this.contadorEstancamiento = 0;
        this.umbralDiversificacion = 100;
        this.modoDiversificacion = false;
        this.ultimaIteracionMejora = 0;
        this.factorDiversificacion = 1.0;

        // OPTIMIZED: Inicializar pool con PriorityQueue que ordena por urgencia
        this.poolNoAsignados = new PriorityQueue<>(new Comparator<Pedido>() {
            @Override
            public int compare(Pedido p1, Pedido p2) {
                // Ordenar por deadline (más cercano primero = mayor urgencia)
                if (p1.getFechaLimiteEntrega() == null && p2.getFechaLimiteEntrega() == null) return 0;
                if (p1.getFechaLimiteEntrega() == null) return 1; // nulls last
                if (p2.getFechaLimiteEntrega() == null) return -1;

                int deadlineComp = p1.getFechaLimiteEntrega().compareTo(p2.getFechaLimiteEntrega());
                if (deadlineComp != 0) return deadlineComp;

                // Tie-break por prioridad (mayor primero)
                int priorityComp = Double.compare(p2.getPrioridad(), p1.getPrioridad());
                if (priorityComp != 0) return priorityComp;

                // Tie-break final por ID
                return Integer.compare(p1.getId(), p2.getId());
            }
        });

        this.iteracionesDesdeMejoraSignificativa = 0;
        this.contadorRestarts = 0;
        this.ultimoPesoSignificativo = 0.0;
    }

    /**
     * Inicializa el servicio de disponibilidad de vuelos y carga cancelaciones.
     * Maneja errores gracefully para no interrumpir el flujo si falta el archivo.
     */
    private void inicializarServicioDisponibilidad() {
        this.servicioDisponibilidad = new ServicioDisponibilidadVuelos();

        try {
            LectorCancelaciones lectorCancelaciones = new LectorCancelaciones(
                Constantes.RUTA_ARCHIVO_CANCELACIONES
            );
            servicioDisponibilidad.cargarCancelaciones(lectorCancelaciones);

            int totalCancelaciones = servicioDisponibilidad.getTotalCancelaciones();
            int vuelosAfectados = servicioDisponibilidad.getVuelosAfectados();

            System.out.println("\n=== CANCELACIONES DE VUELOS ===");
            System.out.println("Vuelos únicos afectados: " + vuelosAfectados);
            System.out.println("Total de cancelaciones (día×vuelo): " + totalCancelaciones);
            System.out.println("================================\n");

        } catch (Exception e) {
            System.err.println("Advertencia: No se pudieron cargar cancelaciones de vuelos");
            System.err.println("Archivo: " + Constantes.RUTA_ARCHIVO_CANCELACIONES);
            System.err.println("El algoritmo continuará sin considerar cancelaciones.");
            // No es crítico, continuar sin cancelaciones
        }
    }

    /**
     * Inicializa las estructuras de optimización de rendimiento.
     * Construye índices y caches para búsquedas eficientes.
     */
    private void inicializarOptimizaciones() {
        System.out.println("\n=== INICIALIZANDO OPTIMIZACIONES ===");
        long inicioIndices = System.currentTimeMillis();

        // Construir índice de vuelos
        this.indiceVuelos = new IndiceVuelos(this.vuelos);

        // Construir cache de disponibilidad
        this.cacheDisponibilidad = new CacheDisponibilidad(servicioDisponibilidad, indiceVuelos);

        long finIndices = System.currentTimeMillis();
        long tiempoIndices = finIndices - inicioIndices;

        System.out.println("Índices construidos en " + tiempoIndices + "ms");
        indiceVuelos.imprimirEstadisticas();
        System.out.println("=====================================\n");
    }

    /**
     * Calcula el día de operación para un pedido basado en su fecha de pedido.
     * El día es relativo a T0 (día inicial del horizonte de planificación).
     *
     * @param pedido Pedido para calcular su día de operación
     * @return Día de operación (1-based), donde 1 es el primer día del horizonte
     */
    private int calcularDiaOperacion(Pedido pedido) {
        if (pedido == null || pedido.getFechaPedido() == null || T0 == null) {
            return 1; // Default: día 1 si no hay información temporal
        }

        long minutosDesdeT0 = ChronoUnit.MINUTES.between(T0, pedido.getFechaPedido());

        // Convertir minutos a días (1-based)
        int dia = (int) (minutosDesdeT0 / (24 * 60)) + 1;

        // Clamp al rango válido [1, HORIZON_DAYS * 30]
        // Asumiendo horizonte de planificación de 30 días por defecto
        int maxDias = HORIZON_DAYS * 30;
        dia = Math.max(1, Math.min(dia, maxDias));

        return dia;
    }

    public void resolver() {
        System.out.println("Iniciando solución ALNS");
        System.out.println("Lectura de aeropuertos");
        System.out.println("Aeropuertos leídos: " + this.aeropuertos.size());
        System.out.println("Lectura de vuelos");
        System.out.println("Vuelos leídos: " + this.vuelos.size());
        System.out.println("Lectura de pedidos");
        System.out.println("Pedidos leídos: " + this.pedidos.size());
        
        // Contar productos totales (cada pedido puede tener múltiples productos)
        int totalProductos = this.pedidos.stream()
            .mapToInt(p -> (p.getProductos() != null && !p.getProductos().isEmpty()) 
                ? p.getProductos().size() 
                : 1)
            .sum();
        System.out.println("Productos totales: " + totalProductos);

        //SE GENERA UNA SOLUCION QUE SE MODIFICARA HASTA HALLAR LA CORRECTA O MEJOR
        System.out.println("\n=== GENERANDO SOLUCIÓN INICIAL ===");
        this.generarSolucionInicial();

        System.out.println("Validando solución...");
        boolean esValida = this.esSolucionValida();
        System.out.println("Solución válida: " + (esValida ? "SÍ" : "NO"));

        this.imprimirDescripcionSolucion(1);

        mejorSolucion = new HashMap<>(solucion);

        inicializarPoolNoAsignados();
        inicializarOcupacionTemporalAlmacenes();
        System.out.println("\n=== INICIANDO ALGORITMO ALNS ===");
        ejecutarAlgoritmoALNS();

        // CRÍTICO: Actualizar ProductTracker con la mejor solución encontrada
        // (Siguiendo el patrón del Backend de ejemplo)
        System.out.println("\n=== ACTUALIZANDO SEGUIMIENTO DE PRODUCTOS ===");
        actualizarSeguimientoProductos();

        System.out.println("\n=== RESULTADO FINAL ALNS ===");
        this.imprimirDescripcionSolucion(2);
        
        // Imprimir resumen de tracking de productos
        productTracker.printTrackingSummary();
    }

    //Actualiza el pool o inicializa el pool con los pedidos que todavia no tienen rutas por x o y motivos
    private void inicializarPoolNoAsignados() {
        poolNoAsignados.clear();
        if (solucion.isEmpty()) return;
        HashMap<Pedido, ArrayList<Vuelo>> solucionActual = solucion.keySet().iterator().next();
        for (Pedido pedido : this.pedidos) {
            if (!solucionActual.containsKey(pedido)) {
                poolNoAsignados.add(pedido);
            }
        }
        if (Constantes.LOGGING_VERBOSO) {
            System.out.println("Pool de no asignados inicializado: " + poolNoAsignados.size() + " pedidos disponibles para expansión ALNS");
        }
    }
    //Se busca agregar paquetes que fueron destruidos al pool de no asignados mediante
    private ArrayList<Map.Entry<Pedido, ArrayList<Vuelo>>> expandirConPaquetesNoAsignados(
            ArrayList<Map.Entry<Pedido, ArrayList<Vuelo>>> paquetesDestruidos, int maxAgregar) {

        if (poolNoAsignados.isEmpty() || maxAgregar <= 0) {
            return paquetesDestruidos;
        }
        //Duplica la lista de paquetes destruidos para añadir al pool de paquetes sin ruta
        ArrayList<Map.Entry<Pedido, ArrayList<Vuelo>>> listaExpandida = new ArrayList<>(paquetesDestruidos);

        //Calcula qué porcentaje del total de pedidos están actualmente no asignados.
        double ratioPool = (double) poolNoAsignados.size() / pedidos.size();
        double probabilidadExpansion;

        if (ratioPool > 0.5) {
            probabilidadExpansion = modoDiversificacion ? 0.9 : 0.7;
        } else if (ratioPool > 0.3) {
            probabilidadExpansion = modoDiversificacion ? 0.7 : 0.5;
        } else if (ratioPool > 0.1) {
            probabilidadExpansion = modoDiversificacion ? 0.5 : 0.3;
        } else {
            probabilidadExpansion = modoDiversificacion ? 0.3 : 0.1;
        }
        //Si cae por debajo de probabilidadExpansion, entonces decide expandir (añadir pedidos del pool)
        if (aleatorio.nextDouble() < probabilidadExpansion) {
            ArrayList<Pedido> noAsignadosOrdenados = new ArrayList<>(poolNoAsignados);
            //ORDENA LOS PEDIDOS SEGUN LA FECHA LIMITE DE ENTREGA, PRIMERO LOS DE ENTREGA MAXIMA
            noAsignadosOrdenados.sort((p1, p2) -> {
                LocalDateTime d1 = p1.getFechaLimiteEntrega();
                LocalDateTime d2 = p2.getFechaLimiteEntrega();
                if (d1 == null && d2 == null) return 0;
                if (d1 == null) return 1;
                if (d2 == null) return -1;
                return d1.compareTo(d2);
            });

            //Define cuantos pedidos como maximo se pueden agregar dependiendo del ratioPool
            int maxDinamico;
            if (ratioPool > 0.5) {
                maxDinamico = Math.min(200, poolNoAsignados.size());
            } else if (ratioPool > 0.3) {
                maxDinamico = Math.min(100, poolNoAsignados.size());
            } else {
                maxDinamico = Math.min(50, poolNoAsignados.size());
            }
            //Luego ajusta con el tamaño real del pool (por si tiene menos).
            int agregar = Math.min(maxDinamico, noAsignadosOrdenados.size());

            //Inserta los agregar primeros pedidos (los más urgentes) al final de la lista expandida.
            for (int i = 0; i < agregar; i++) {
                Pedido pedido = noAsignadosOrdenados.get(i);
                listaExpandida.add(new java.util.AbstractMap.SimpleEntry<>(pedido, new ArrayList<>()));
            }

            if (Constantes.LOGGING_VERBOSO) {
                System.out.println("Expansión ALNS: Agregando " + agregar + " pedidos no asignados para exploración" +
                                 " (Pool: " + poolNoAsignados.size() + "/" + pedidos.size() +
                                 " = " + String.format("%.1f%%", ratioPool * 100) +
                                 ", Prob: " + String.format("%.0f%%", probabilidadExpansion * 100) + ")");
            }
        }

        return listaExpandida;
    }

    private void actualizarPoolNoAsignados(HashMap<Pedido, ArrayList<Vuelo>> solucionActual) {
        poolNoAsignados.clear();
        for (Pedido pedido : this.pedidos) {
            if (!solucionActual.containsKey(pedido)) {
                poolNoAsignados.add(pedido);
            }
        }
    }

    private void ejecutarAlgoritmoALNS() {
        // Extraer solución inicial de manera más robusta
        if (solucion.isEmpty()) {
            System.out.println("Error: No hay solución inicial");
            return;
        }

        Map.Entry<HashMap<Pedido, ArrayList<Vuelo>>, Integer> primeraEntrada =
                solucion.entrySet().iterator().next();
        HashMap<Pedido, ArrayList<Vuelo>> solucionActual = new HashMap<>(primeraEntrada.getKey());
        int pesoActual = primeraEntrada.getValue();

        System.out.println("Peso de solución inicial: " + pesoActual);

        ultimoPesoSignificativo = pesoActual;
        iteracionesDesdeMejoraSignificativa = 0;

        int mejorPeso = pesoActual;
        int mejoras = 0;
        int conteoSinMejoras = 0;

        for (int iteracion = 0; iteracion < maxIteraciones; iteracion++) {
            if (Constantes.LOGGING_VERBOSO || iteracion % Constantes.INTERVALO_LOG_ITERACION == 0) {
                System.out.println("ALNS Iteración " + iteracion + "/" + maxIteraciones);
            }
            //Seleccion de operadores para saber que operador de destruccion y repacion usar dependiendo de sus pesos
            /*  | Índice | Operador de destrucción | Operador de reparación |
                | ------ | ----------------------- | ---------------------- |
                | 0      | Aleatoria               | Codiciosa              |
                | 1      | Geográfica              | Arrepentimiento        |
                | 2      | Por tiempo              | Por tiempo             |
                | 3      | Congestionada           | Por capacidad          |
            */
            int[] operadoresSeleccionados = seleccionarOperadores();
            int operadorDestruccion = operadoresSeleccionados[0];
            int operadorReparacion = operadoresSeleccionados[1];

//            int operadorDestruccion = 3;
//            int operadorReparacion = 0;

            if (Constantes.LOGGING_VERBOSO) {
                System.out.println("  Operadores seleccionados: Destrucción=" + operadorDestruccion + ", Reparación=" + operadorReparacion);
            }

            HashMap<Pedido, ArrayList<Vuelo>> solucionTemporal = new HashMap<>(solucionActual);

            //Crear una foto de las capacidades en esa iteracion de los vuelos y de los aeropuertos
            //esto sirve para revertir una destruccion o una reparacion de una solucion
            Map<Vuelo, Integer> snapshotCapacidadesVuelos = crearSnapshotCapacidadesVuelos(); //VUELOS
            Map<Aeropuerto, Integer> snapshotCapacidadAeropuertos = crearSnapshotCapacidadAeropuerto(); //AEROPUERTOS

            if (Constantes.LOGGING_VERBOSO) {
                System.out.println("  Aplicando operador de destrucción...");
            }
            long tiempoInicio = System.currentTimeMillis();
            //empezamos con la destruccion de la solucion temporal (porque no queremos malograr la actual)
            //se manda tambien el operador de destruccion segun seleccionarOperadores()
            ALNSDestruction.ResultadoDestruccion resultadoDestruccion = aplicarOperadorDestruccion(
                solucionTemporal, operadorDestruccion);
            long tiempoFin = System.currentTimeMillis();

            if (Constantes.LOGGING_VERBOSO) {
                System.out.println("  Operador de destrucción completado en " + (tiempoFin - tiempoInicio) + "ms");
            }

            if (resultadoDestruccion == null || resultadoDestruccion.getPaquetesDestruidos().isEmpty()) {
                if (Constantes.LOGGING_VERBOSO) {
                    System.out.println("  No se pudo destruir nada, continuando...");
                }
                continue;
            }

            if (Constantes.LOGGING_VERBOSO) {
                System.out.println("  Paquetes destruidos: " + resultadoDestruccion.getPaquetesDestruidos().size());
            }

            solucionTemporal = new HashMap<>(resultadoDestruccion.getSolucionParcial());
            reconstruirCapacidadesDesdeSolucion(solucionTemporal); //VUELOS
            reconstruirAlmacenesDesdeSolucion(solucionTemporal); //AEROPUERTOS

            ArrayList<Map.Entry<Pedido, ArrayList<Vuelo>>> paquetesExpandidos =
                expandirConPaquetesNoAsignados(resultadoDestruccion.getPaquetesDestruidos(), 100);

            ALNSRepair.ResultadoReparacion resultadoReparacion = aplicarOperadorReparacion(
                solucionTemporal, operadorReparacion, paquetesExpandidos);

            if (resultadoReparacion == null || !resultadoReparacion.esExitoso()) {
                restaurarVuelos(snapshotCapacidadesVuelos);
                restaurarAeropuertos(snapshotCapacidadAeropuertos);
                continue;
            }

            solucionTemporal = new HashMap<>(resultadoReparacion.getSolucionReparada());
            reconstruirCapacidadesDesdeSolucion(solucionTemporal);
            reconstruirAlmacenesDesdeSolucion(solucionTemporal);

            int pesoTemporal = calcularPesoSolucion(solucionTemporal);

            usoOperadores[operadorDestruccion][operadorReparacion]++;

            boolean aceptada = false;
            double ratioMejora = 0.0;
            //VER EL PESO ACTUAL QUE DIO LA NUEVA SOLUCION
            if (pesoTemporal > pesoActual) {
                ratioMejora = (double)(pesoTemporal - pesoActual) / Math.max(pesoActual, 1);
                solucionActual = solucionTemporal;
                pesoActual = pesoTemporal;
                aceptada = true;

                if (pesoTemporal > mejorPeso) {
                    mejorPeso = pesoTemporal;
                    mejorSolucion.clear();
                    mejorSolucion.put(new HashMap<>(solucionActual), pesoActual);
                    puntajesOperadores[operadorDestruccion][operadorReparacion] += 300;
                    mejoras++;
                    conteoSinMejoras = 0;
                    ultimaIteracionMejora = iteracion;
                    contadorEstancamiento = 0;
                    modoDiversificacion = false;
                    actualizarPoolNoAsignados(solucionActual);

                    if (ratioMejora >= (Constantes.UMBRAL_MEJORA_SIGNIFICATIVA / 100.0)) {
                        iteracionesDesdeMejoraSignificativa = 0;
                        ultimoPesoSignificativo = mejorPeso;
                    } else {
                        iteracionesDesdeMejoraSignificativa++;
                    }

                    System.out.println("Iteración " + iteracion + ": ¡Nueva mejor solución! Peso: " + mejorPeso +
                                     " (mejora: " + String.format("%.2f%%", ratioMejora * 100) + ")" +
                                     " | No asignados: " + poolNoAsignados.size());
                } else if (ratioMejora > 0.05) {
                    puntajesOperadores[operadorDestruccion][operadorReparacion] += 100;
                    conteoSinMejoras = Math.max(0, conteoSinMejoras - 5);
                    actualizarPoolNoAsignados(solucionActual);
                } else if (ratioMejora > 0.01) {
                    puntajesOperadores[operadorDestruccion][operadorReparacion] += 50;
                    conteoSinMejoras = Math.max(0, conteoSinMejoras - 2);
                    actualizarPoolNoAsignados(solucionActual);
                } else {
                    puntajesOperadores[operadorDestruccion][operadorReparacion] += 25;
                    actualizarPoolNoAsignados(solucionActual);
                }
            } else {
                double delta = pesoTemporal - pesoActual;
                double temperaturaAjustada = temperatura * (1.0 + 0.1 * Math.random());
                double probabilidad = Math.exp(delta / temperaturaAjustada);

                if (aleatorio.nextDouble() < probabilidad) {
                    solucionActual = solucionTemporal;
                    pesoActual = pesoTemporal;
                    aceptada = true;
                    actualizarPoolNoAsignados(solucionActual);
                    puntajesOperadores[operadorDestruccion][operadorReparacion] += 15;
                    conteoSinMejoras++;
                } else {
                    puntajesOperadores[operadorDestruccion][operadorReparacion] += 5;
                    conteoSinMejoras++;
                }

                if (!aceptada) {
                    iteracionesDesdeMejoraSignificativa++;
                }
            }

            if (!aceptada) {
                restaurarVuelos(snapshotCapacidadesVuelos);
                restaurarAeropuertos(snapshotCapacidadAeropuertos);
                conteoSinMejoras++;
            }

            contadorEstancamiento = iteracion - ultimaIteracionMejora;
            if (contadorEstancamiento > umbralDiversificacion && !modoDiversificacion) {
                modoDiversificacion = true;
                factorDiversificacion = 1.5;
                temperatura *= 2.0;
                if (Constantes.LOGGING_VERBOSO) {
                    System.out.println("Iteración " + iteracion + ": Activando modo DIVERSIFICACIÓN");
                }
            } else if (modoDiversificacion && contadorEstancamiento <= umbralDiversificacion / 2) {
                modoDiversificacion = false;
                factorDiversificacion = 1.0;
                if (Constantes.LOGGING_VERBOSO) {
                    System.out.println("Iteración " + iteracion + ": Volviendo a modo INTENSIFICACIÓN");
                }
            }

            // Diversificación extrema / Restart
            if (iteracionesDesdeMejoraSignificativa >= Constantes.UMBRAL_ESTANCAMIENTO_PARA_RESTART &&
                contadorRestarts < Constantes.MAX_RESTARTS) {

                solucionActual = aplicarDiversificacionExtrema(solucionActual, iteracion);
                pesoActual = calcularPesoSolucion(solucionActual);

                if (pesoActual > mejorPeso) {
                    mejorPeso = pesoActual;
                    mejorSolucion.clear();
                    mejorSolucion.put(new HashMap<>(solucionActual), pesoActual);
                    mejoras++;
                    System.out.println("🎉 ¡Diversificación extrema encontró mejor solución! Peso: " + mejorPeso);
                }

                reconstruirCapacidadesDesdeSolucion(solucionActual);
                reconstruirAlmacenesDesdeSolucion(solucionActual);
            }

            if ((iteracion + 1) % tamanoSegmento == 0) {
                actualizarPesosOperadores();
                temperatura *= tasaEnfriamiento;

                if (iteracion % 100 == 0) {
                    System.out.println("Iteración " + iteracion +
                                     " | Mejor peso: " + mejorPeso +
                                     " | Temperatura: " + String.format("%.2f", temperatura) +
                                     " | Modo: " + (modoDiversificacion ? "DIVERSIFICACIÓN" : "INTENSIFICACIÓN"));
                }
            }

            if (contadorEstancamiento > 300) {
                if (Constantes.LOGGING_VERBOSO) {
                    System.out.println("Parada temprana en iteración " + iteracion +
                                     " (sin mejoras por " + contadorEstancamiento + " iteraciones)");
                }
                break;
            }
        }

        solucion.clear();
        solucion.putAll(mejorSolucion);

        System.out.println("ALNS completado:");
        System.out.println("  Mejoras encontradas: " + mejoras);
        System.out.println("  Peso final: " + (mejorSolucion.isEmpty() ? 0 : mejorSolucion.values().iterator().next()));
        if (Constantes.LOGGING_VERBOSO) {
            System.out.println("  Temperatura final: " + temperatura);
        }
    }

    private HashMap<Pedido, ArrayList<Vuelo>> aplicarDiversificacionExtrema(
            HashMap<Pedido, ArrayList<Vuelo>> solucionActual, int iteracion) {

        System.out.println("\n🚀 ACTIVANDO DIVERSIFICACIÓN EXTREMA 🚀");
        System.out.println("Iteración " + iteracion + ": " + iteracionesDesdeMejoraSignificativa +
                         " iteraciones sin mejora significativa");
        System.out.println("Restart #" + (contadorRestarts + 1) + "/" + Constantes.MAX_RESTARTS);

        HashMap<Pedido, ArrayList<Vuelo>> nuevaSolucion;

        switch (contadorRestarts % 3) {
            case 0:
                System.out.println("Estrategia: DESTRUCCIÓN EXTREMA (" + (int)(Constantes.RATIO_DESTRUCCION_EXTREMA*100) + "%)");
                nuevaSolucion = destruccionExtrema(solucionActual);
                break;
            case 1:
                System.out.println("Estrategia: RESTART GREEDY COMPLETO");
                nuevaSolucion = restartGreedy();
                break;
            case 2:
                System.out.println("Estrategia: RESTART HÍBRIDO");
                nuevaSolucion = restartHibrido(solucionActual);
                break;
            default:
                System.out.println("Estrategia: DESTRUCCIÓN EXTREMA (fallback)");
                nuevaSolucion = destruccionExtrema(solucionActual);
                break;
        }

        contadorRestarts++;
        iteracionesDesdeMejoraSignificativa = 0;

        temperatura = 100.0;

        actualizarPoolNoAsignados(nuevaSolucion);

        int nuevoPeso = calcularPesoSolucion(nuevaSolucion);
        System.out.println("Peso después de diversificación extrema: " + nuevoPeso);
        System.out.println("Paquetes asignados: " + nuevaSolucion.size() + "/" + pedidos.size());
        System.out.println("=== FIN DIVERSIFICACIÓN EXTREMA ===\n");

        return nuevaSolucion;
    }

    private HashMap<Pedido, ArrayList<Vuelo>> destruccionExtrema(HashMap<Pedido, ArrayList<Vuelo>> solucionActual) {
        HashMap<Pedido, ArrayList<Vuelo>> nuevaSolucion = new HashMap<>(solucionActual);

        ArrayList<Pedido> asignados = new ArrayList<>(nuevaSolucion.keySet());
        Collections.shuffle(asignados, aleatorio);

        int paquetesAEliminar = (int)(asignados.size() * Constantes.RATIO_DESTRUCCION_EXTREMA);

        for (int i = 0; i < paquetesAEliminar && i < asignados.size(); i++) {
            nuevaSolucion.remove(asignados.get(i));
        }

        System.out.println("Destruidos " + paquetesAEliminar + "/" + asignados.size() + " pedidos");

        reconstruirCapacidadesDesdeSolucion(nuevaSolucion);
        reconstruirAlmacenesDesdeSolucion(nuevaSolucion);

        return nuevaSolucion;
    }

//    private HashMap<Pedido, ArrayList<Vuelo>> restartGreedy() {
//        for (Vuelo f : vuelos) {
//            f.setCapacidadUsada(0);
//        }
//        for(Aeropuerto a : aeropuertos) {
//            a.setCapacidadActual(0);
//        }
//        //inicializarCapacidadAeropuertos();
//
//        HashMap<Pedido, ArrayList<Vuelo>> nuevaSolucion = new HashMap<>();
//
//        ArrayList<Pedido> ordenados = new ArrayList<>(pedidos);
//
//        switch (contadorRestarts % 4) {
//            case 0:
//                ordenados.sort((p1, p2) -> Double.compare(p1.getPrioridad(), p2.getPrioridad()));
//                System.out.println("Ordenamiento: Prioridad inversa");
//                break;
//            case 1:
//                ordenados.sort((p1, p2) -> {
//                    int a = p1.getProductos() != null ? p1.getProductos().size() : 1;
//                    int b = p2.getProductos() != null ? p2.getProductos().size() : 1;
//                    return Integer.compare(b, a);
//                });
//                System.out.println("Ordenamiento: Más productos primero");
//                break;
//            case 2:
//                ordenados.sort((p1, p2) -> {
//                    boolean p1Cont = obtenerAeropuerto(p1.getAeropuertoOrigenCodigo()).getCiudad().getContinente() ==
//                            obtenerAeropuerto(p1.getAeropuertoDestinoCodigo()).getCiudad().getContinente();
//                    boolean p2Cont = obtenerAeropuerto(p2.getAeropuertoOrigenCodigo()).getCiudad().getContinente() ==
//                            obtenerAeropuerto(p2.getAeropuertoDestinoCodigo()).getCiudad().getContinente();
//                    return Boolean.compare(p1Cont, p2Cont);
//                });
//                System.out.println("Ordenamiento: Intercontinentales primero");
//                break;
//            case 3:
//                Collections.shuffle(ordenados, aleatorio);
//                System.out.println("Ordenamiento: Aleatorio");
//                break;
//        }
//
//        int asignados = 0;
//        for (Pedido p : ordenados) {
//            ArrayList<Vuelo> mejorRuta = encontrarMejorRutaConVentanasTiempo(p, nuevaSolucion);
//            if (mejorRuta != null) {
//                int cnt = p.getProductos() != null ? p.getProductos().size() : 1;
//                if (puedeAsignarConOptimizacionEspacio(p, mejorRuta, nuevaSolucion)) {
//                    nuevaSolucion.put(p, mejorRuta);
//                    actualizarCapacidadesVuelos(mejorRuta, cnt);
//                    actualizarCapacidadAeropuertos(p.getAeropuertoDestinoCodigo(), cnt);
//                    asignados++;
//                }
//            }
//        }
//
//        System.out.println("Restart greedy: " + asignados + "/" + pedidos.size() + " pedidos asignados");
//        return nuevaSolucion;
//    }
    private HashMap<Pedido, ArrayList<Vuelo>> restartGreedy() {
        System.out.println("=== INICIANDO RESTART GREEDY ===");

        // Reiniciar capacidades PERO mantener la estructura
        for (Vuelo f : vuelos) {
            f.setCapacidadUsada(0);
        }
        for(Aeropuerto a : aeropuertos) {
            a.setCapacidadActual(0);
        }

        HashMap<Pedido, ArrayList<Vuelo>> nuevaSolucion = new HashMap<>();
        ArrayList<Pedido> ordenados = new ArrayList<>(pedidos);

        // Ordenamiento más agresivo
        ordenados.sort((p1, p2) -> {
            // 1. Prioridad más alta primero
            int prioridadCompare = Double.compare(p2.getPrioridad(), p1.getPrioridad());
            if (prioridadCompare != 0) return prioridadCompare;

            // 2. Menos productos primero (más fáciles de colocar)
            int productos1 = p1.getProductos() != null ? p1.getProductos().size() : 1;
            int productos2 = p2.getProductos() != null ? p2.getProductos().size() : 1;
            return Integer.compare(productos1, productos2);
        });

        System.out.println("Ordenamiento: Prioridad + Menos productos primero");

        int asignados = 0;
        int intentosFallidos = 0;
        int maxIntentosFallidos = 100; // Parada temprana

        for (Pedido p : ordenados) {
            if (intentosFallidos >= maxIntentosFallidos) {
                System.out.println("Parada temprana: muchos intentos fallidos consecutivos");
                break;
            }

            // DEBUG: Información del pedido
            if (Constantes.LOGGING_VERBOSO) {
                System.out.println("Procesando pedido " + p.getId() +
                        " - Origen: " + p.getAeropuertoOrigenCodigo() +
                        " - Destino: " + p.getAeropuertoDestinoCodigo());
            }

            ArrayList<Vuelo> mejorRuta = encontrarMejorRutaRobusta(p);

            if (mejorRuta != null && !mejorRuta.isEmpty()) {
                int cnt = p.getProductos() != null ? p.getProductos().size() : 1;

                // Verificar capacidad más permisiva
                if (puedeAsignarConCapacidadPermisiva(p, mejorRuta)) {
                    nuevaSolucion.put(p, mejorRuta);
                    actualizarCapacidadesVuelos(mejorRuta, cnt);
                    actualizarCapacidadAeropuertos(p.getAeropuertoDestinoCodigo(), cnt);
                    asignados++;
                    intentosFallidos = 0; // Resetear contador de fallos

                    if (Constantes.LOGGING_VERBOSO) {
                        System.out.println("✓ Asignado pedido " + p.getId() + " con " + mejorRuta.size() + " vuelos");
                    }
                } else {
                    intentosFallidos++;
                    if (Constantes.LOGGING_VERBOSO) {
                        System.out.println("✗ Capacidad insuficiente para pedido " + p.getId());
                    }
                }
            } else {
                intentosFallidos++;
                if (Constantes.LOGGING_VERBOSO) {
                    System.out.println("✗ No se encontró ruta para pedido " + p.getId());
                }
            }
        }

        System.out.println("Restart greedy: " + asignados + "/" + pedidos.size() + " pedidos asignados");
        System.out.println("=== FIN RESTART GREEDY ===");
        return nuevaSolucion;
    }
    private ArrayList<Vuelo> encontrarMejorRutaRobusta(Pedido pedido) {
        try {
            // Intentar método original primero
            ArrayList<Vuelo> ruta = encontrarMejorRuta(pedido);
            if (ruta != null && !ruta.isEmpty()) {
                return ruta;
            }

            // Fallback: búsqueda directa sin cache
            Aeropuerto origen = obtenerAeropuerto(pedido.getAeropuertoOrigenCodigo());
            Aeropuerto destino = obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo());

            if (origen == null || destino == null) {
                return null;
            }

            // Búsqueda directa en la lista de vuelos
            for (Vuelo vuelo : vuelos) {
                if (vuelo.getAeropuertoOrigen().equals(origen) &&
                        vuelo.getAeropuertoDestino().equals(destino) &&
                        vuelo.getCapacidadUsada() < vuelo.getCapacidadMaxima()) {
                    ArrayList<Vuelo> rutaDirecta = new ArrayList<>();
                    rutaDirecta.add(vuelo);
                    return rutaDirecta;
                }
            }

            // Buscar con una escala
            for (Vuelo primerVuelo : vuelos) {
                if (primerVuelo.getAeropuertoOrigen().equals(origen) &&
                        primerVuelo.getCapacidadUsada() < primerVuelo.getCapacidadMaxima()) {

                    Aeropuerto escala = primerVuelo.getAeropuertoDestino();

                    for (Vuelo segundoVuelo : vuelos) {
                        if (segundoVuelo.getAeropuertoOrigen().equals(escala) &&
                                segundoVuelo.getAeropuertoDestino().equals(destino) &&
                                segundoVuelo.getCapacidadUsada() < segundoVuelo.getCapacidadMaxima()) {

                            ArrayList<Vuelo> rutaConEscala = new ArrayList<>();
                            rutaConEscala.add(primerVuelo);
                            rutaConEscala.add(segundoVuelo);
                            return rutaConEscala;
                        }
                    }
                }
            }

            return null;

        } catch (Exception e) {
            System.err.println("Error en encontrarMejorRutaRobusta: " + e.getMessage());
            return null;
        }
    }
    private boolean puedeAsignarConCapacidadPermisiva(Pedido pedido, ArrayList<Vuelo> ruta) {
        if (ruta == null || ruta.isEmpty()) return false;

        int cantidadProductos = pedido.getProductos() != null ? pedido.getProductos().size() : 1;

        // Verificar capacidad de vuelos
        for (Vuelo vuelo : ruta) {
            if (vuelo.getCapacidadUsada() + cantidadProductos > vuelo.getCapacidadMaxima()) {
                return false;
            }
        }

        // Verificar capacidad del aeropuerto destino (más permisivo)
        Aeropuerto aeropuertoDestino = obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo());
        if (aeropuertoDestino == null) return false;

        // Permitir hasta el 95% de capacidad para restart
        int capacidadDisponible = aeropuertoDestino.getCapacidadMaxima() - aeropuertoDestino.getCapacidadActual();
        return cantidadProductos <= capacidadDisponible;
    }

    private HashMap<Pedido, ArrayList<Vuelo>> restartHibrido(HashMap<Pedido, ArrayList<Vuelo>> solucionActual) {
        HashMap<Pedido, ArrayList<Vuelo>> nuevaSolucion = new HashMap<>();

        ArrayList<Map.Entry<Pedido, ArrayList<Vuelo>>> entradas = new ArrayList<>(solucionActual.entrySet());
        entradas.sort((e1, e2) -> {
            try {
                int s1 = calcularCalidadRuta(e1.getKey(), e1.getValue());
                int s2 = calcularCalidadRuta(e2.getKey(), e2.getValue());
                int cmp = Integer.compare(s2, s1);
                if (cmp != 0) return cmp;
                int cmpVuelos = Integer.compare(e1.getValue().size(), e2.getValue().size());
                if (cmpVuelos != 0) return cmpVuelos;
                int cmpPrior = Double.compare(e2.getKey().getPrioridad(), e1.getKey().getPrioridad());
                if (cmpPrior != 0) return cmpPrior;
                return Integer.compare(e1.getKey().hashCode(), e2.getKey().hashCode());
            } catch (Exception ex) {
                System.out.println("Warning: Error en comparación de calidad, usando fallback");
                return Integer.compare(e1.getValue().size(), e2.getValue().size());
            }
        });

        int mantener = (int)(entradas.size() * 0.3);
        for (int i = 0; i < mantener && i < entradas.size(); i++) {
            nuevaSolucion.put(entradas.get(i).getKey(), entradas.get(i).getValue());
        }

        System.out.println("Híbrido: Manteniendo " + mantener + " mejores pedidos, regenerando " + (solucionActual.size() - mantener));

        reconstruirCapacidadesDesdeSolucion(nuevaSolucion); //VUELOS
        reconstruirAlmacenesDesdeSolucion(nuevaSolucion); //AEROPUERTOS

        return nuevaSolucion;
    }

    private int calcularCalidadRuta(Pedido p, ArrayList<Vuelo> ruta) {
        if (ruta == null || ruta.isEmpty()) return 0;

        int score = 0;
        if (ruta.size() == 1) score += 1000;
        else if (ruta.size() == 2) score += 500;
        else score += 100;

        double total = 0;
        for (Vuelo v : ruta) total += v.getTiempoTransporte();
        if (ruta.size() > 1) total += (ruta.size() - 1) * 2.0;

        score += Math.max(0, 2000 - (int)(total * 10));

        int products = p.getProductos() != null ? p.getProductos().size() : 1;
        score += products * 10;
        score += (int)(p.getPrioridad() * 50);

        boolean mismoContinente = obtenerAeropuerto(p.getAeropuertoOrigenCodigo()).getCiudad().getContinente() ==
                obtenerAeropuerto(p.getAeropuertoDestinoCodigo()).getCiudad().getContinente();
        if (mismoContinente) score += 200;
        else score += 100;

        return Math.max(1, score);
    }

    private int[] seleccionarOperadores() {
        try {
            if (Constantes.LOGGING_VERBOSO) {
                System.out.println("    Seleccionando operadores...");
            }

            //Calcular el peso total de todos los operadores
            double pesoTotal = 0.0;
            for (int i = 0; i < pesosOperadores.length; i++) {
                for (int j = 0; j < pesosOperadores[i].length; j++) {
                    pesoTotal += pesosOperadores[i][j];
                }
            }

            if (Constantes.LOGGING_VERBOSO) {
                System.out.println("    Peso total: " + pesoTotal);
            }
            /*  | D\R | R1  | R2  | R3  | R4  |
                | D1  | 1.0 | 2.0 | 1.5 | 1.0 |
                | D2  | 0.5 | 3.0 | 2.5 | 1.0 |
                | D3  | 1.0 | 1.0 | 1.0 | 1.0 |
                | D4  | 0.8 | 0.9 | 1.1 | 1.2 |
            */
            //Genera un numero aleatorio proporcional
            double valorAleatorio = aleatorio.nextDouble() * pesoTotal;
            double pesoAcumulado = 0.0;
            //selecciona usando ruleta ponderada
            //Va sumando los pesos fila por fila, columna por columna, hasta que el peso acumulado supera el número aleatorio.
            for (int i = 0; i < pesosOperadores.length; i++) {
                for (int j = 0; j < pesosOperadores[i].length; j++) {
                    pesoAcumulado += pesosOperadores[i][j];
                    if (valorAleatorio <= pesoAcumulado) {
                        if (Constantes.LOGGING_VERBOSO) {
                            System.out.println("    Operadores seleccionados: " + i + ", " + j);
                        }
                        return new int[]{i, j};
                    }
                }
            }

            if (Constantes.LOGGING_VERBOSO) {
                System.out.println("    Usando fallback: 0, 0");
            }
            return new int[]{0, 0};
        } catch (Exception e) {
            System.out.println("    Error en selección de operadores: " + e.getMessage());
            e.printStackTrace();
            return new int[]{0, 0};
        }
    }

    private ALNSDestruction.ResultadoDestruccion aplicarOperadorDestruccion(
            HashMap<Pedido, ArrayList<Vuelo>> solucion, int indiceOperador) {
        try {
            //porcentaje de pedidos a eliminar
            double ratioAjustado = Constantes.RATIO_DESTRUCCION * factorDiversificacion;
            //minimo absoluto de paquetes a eliminar
            int minAjustado = (int)(Constantes.DESTRUCCION_MIN_PAQUETES * factorDiversificacion);
            //maximo absoluto de paquetes a eliminar
            int maxAjustado = (int)(Constantes.DESTRUCCION_MAX_PAQUETES * factorDiversificacion);
            /*El multiplicador factorDiversificacion aumenta o reduce la agresividad:
            Si el algoritmo está estancado, factorDiversificacion > 1 → destruye más.
            Si va mejorando, factorDiversificacion < 1 → destruye menos.*/
            switch (indiceOperador) {
                case 0:
                    if (Constantes.LOGGING_VERBOSO) {
                        System.out.println("    Ejecutando destruccionAleatoria... (ratio: " + String.format("%.2f", ratioAjustado) + ")");
                    }
                    //Destruye sin un orden especifico solo al azar
                    return operadoresDestruccion.destruccionAleatoria(solucion, ratioAjustado, minAjustado, maxAjustado);
                case 1:
                    if (Constantes.LOGGING_VERBOSO) {
                        System.out.println("    Ejecutando destruccionGeografica... (ratio: " + String.format("%.2f", ratioAjustado) + ")");
                    }
                    //Elimina pedidos cercanos entre sí geográficamente (por ciudad o región).
                    return operadoresDestruccion.destruccionGeografica(solucion, ratioAjustado, minAjustado, maxAjustado);
                case 2:
                    if (Constantes.LOGGING_VERBOSO) {
                        System.out.println("    Ejecutando destruccionBasadaEnTiempo... (ratio: " + String.format("%.2f", ratioAjustado) + ")");
                    }
                    //Elimina pedidos cuyo deadline o fecha de entrega está cerca (urgentes o conflictivos).
                    return operadoresDestruccion.destruccionBasadaEnTiempo(solucion, ratioAjustado, minAjustado, maxAjustado);
                case 3:
                    if (Constantes.LOGGING_VERBOSO) {
                        System.out.println("    Ejecutando destruccionRutaCongestionada... (ratio: " + String.format("%.2f", ratioAjustado) + ")");
                    }
                    //Elimina pedidos que pasan por rutas o vuelos saturados.
                    return operadoresDestruccion.destruccionRutaCongestionada(solucion, ratioAjustado, minAjustado, maxAjustado);
                default:
                    if (Constantes.LOGGING_VERBOSO) {
                        System.out.println("    Ejecutando destruccionAleatoria (default)... (ratio: " + String.format("%.2f", ratioAjustado) + ")");
                    }
                    //aleatorio como respaldo
                    return operadoresDestruccion.destruccionAleatoria(solucion, ratioAjustado, minAjustado, maxAjustado);
            }
        } catch (Exception e) {
            System.out.println("    Error en operador de destrucción: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private ALNSRepair.ResultadoReparacion aplicarOperadorReparacion(
            HashMap<Pedido, ArrayList<Vuelo>> solucion, int indiceOperador,
            ArrayList<Map.Entry<Pedido, ArrayList<Vuelo>>> paquetesDestruidos) {

        switch (indiceOperador) {
            case 0:
                return operadoresReparacion.reparacionCodiciosa(solucion, paquetesDestruidos);
            case 1:
                return operadoresReparacion.reparacionArrepentimiento(solucion, paquetesDestruidos, 2);
            case 2:
                return operadoresReparacion.reparacionPorTiempo(solucion, paquetesDestruidos);
            case 3:
                return operadoresReparacion.reparacionPorCapacidad(solucion, paquetesDestruidos);
            default:
                return operadoresReparacion.reparacionCodiciosa(solucion, paquetesDestruidos);
        }
    }

    private void actualizarPesosOperadores() {
        double lambda = 0.1;

        for (int i = 0; i < puntajesOperadores.length; i++) {
            for (int j = 0; j < puntajesOperadores[i].length; j++) {
                if (usoOperadores[i][j] > 0) {
                    double puntajePromedio = puntajesOperadores[i][j] / usoOperadores[i][j];
                    pesosOperadores[i][j] = (1 - lambda) * pesosOperadores[i][j] +
                                           lambda * puntajePromedio;

                    puntajesOperadores[i][j] = 0.0;
                    usoOperadores[i][j] = 0;
                }
            }
        }
    }

    private Map<Vuelo, Integer> crearSnapshotCapacidadesVuelos() {
        Map<Vuelo, Integer> snapshot = new HashMap<>();
        for (Vuelo f : vuelos) {
            snapshot.put(f, f.getCapacidadUsada());
        }
        return snapshot;
    }

    private void restaurarVuelos(Map<Vuelo, Integer> snapshot) {
        for (Vuelo f : vuelos) {
            f.setCapacidadUsada(snapshot.getOrDefault(f, 0));
        }
    }

    private void reconstruirCapacidadesDesdeSolucion(HashMap<Pedido, ArrayList<Vuelo>> solucion) {
        for (Vuelo f : vuelos) {
            f.setCapacidadUsada(0);
        }

        for (Map.Entry<Pedido, ArrayList<Vuelo>> entrada : solucion.entrySet()) {
            Pedido pedido = entrada.getKey();
            ArrayList<Vuelo> ruta = entrada.getValue();
            int conteoProductos = pedido.getProductos() != null ? pedido.getProductos().size() : 1;

            for (Vuelo f : ruta) {
                f.setCapacidadUsada(f.getCapacidadUsada() + conteoProductos);
            }
        }
    }

    private Map<Aeropuerto, Integer> crearSnapshotCapacidadAeropuerto() {
        Map<Aeropuerto, Integer> snapshot = new HashMap<>();
        for (Aeropuerto aeropuerto : aeropuertos) {
            // CORREGIDO: Obtener del almacen directamente
            int capacidad = (aeropuerto.getAlmacen() != null) 
                ? aeropuerto.getAlmacen().getCapacidadUsada() 
                : 0;
            snapshot.put(aeropuerto, capacidad);
        }
        return snapshot;
    }

    private void restaurarAeropuertos(Map<Aeropuerto, Integer> snapshot) {
        for(Aeropuerto aeropuerto : aeropuertos) {
            // CORREGIDO: Restaurar en el almacen directamente
            if (aeropuerto.getAlmacen() != null) {
                aeropuerto.getAlmacen().setCapacidadUsada(snapshot.getOrDefault(aeropuerto, 0));
            }
        }
    }

    private void reconstruirAlmacenesDesdeSolucion(HashMap<Pedido, ArrayList<Vuelo>> solucion) {
        inicializarCapacidadAeropuertos();

        for (Map.Entry<Pedido, ArrayList<Vuelo>> entrada : solucion.entrySet()) {
            Pedido pedido = entrada.getKey();
            ArrayList<Vuelo> ruta = entrada.getValue();
            int conteoProductos = pedido.getProductos() != null ? pedido.getProductos().size() : 1;

            if (ruta == null || ruta.isEmpty()) {
                Aeropuerto aeropuertoDestino = obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo());
                if (aeropuertoDestino != null) {
                    actualizarCapacidadAeropuertos(aeropuertoDestino.getCodigoIATA(), conteoProductos);
                }
            } else {
                Vuelo ultimoVuelo = ruta.get(ruta.size() - 1);
                actualizarCapacidadAeropuertos(ultimoVuelo.getAeropuertoDestino().getCodigoIATA(), conteoProductos);
            }
        }
    }
    void actualizarCapacidadAeropuertos(String codigoAeropuertoDestino, int cantidad) {
        for(Aeropuerto aeropuerto : aeropuertos) {
            if(aeropuerto.getCodigoIATA().equals(codigoAeropuertoDestino)) {
                // CORREGIDO: Actualizar el almacen directamente
                if (aeropuerto.getAlmacen() != null) {
                    int capacidadActual = aeropuerto.getAlmacen().getCapacidadUsada();
                    aeropuerto.getAlmacen().setCapacidadUsada(capacidadActual + cantidad);
                }
                break;
            }
        }
    }
    private boolean cabeEnCapacidad(ArrayList<Vuelo> ruta, int cantidad) {
        if (ruta == null || ruta.isEmpty()) return true;

        for (Vuelo vuelo : ruta) {
            if (vuelo.getCapacidadUsada() + cantidad > vuelo.getCapacidadMaxima()) {
                return false;
            }
        }
        return true;
    }

    private void inicializarCacheCiudadAeropuerto() {
        cacheNombreCiudadAeropuerto = new HashMap<>();
        for (Aeropuerto aeropuerto : aeropuertos) {
            if (aeropuerto.getCiudad() != null && aeropuerto.getCiudad().getNombre() != null) {
                String claveCiudad = aeropuerto.getCiudad().getNombre().toLowerCase().trim();
                cacheNombreCiudadAeropuerto.put(claveCiudad, aeropuerto);
            }
        }
        System.out.println("Cache inicializada: " + cacheNombreCiudadAeropuerto.size() + " ciudades");
    }

    private void inicializarT0() {
        T0 = LocalDateTime.now();

        if (pedidos != null && !pedidos.isEmpty()) {
            LocalDateTime minFechaPedido = pedidos.stream()
                .filter(p -> p.getFechaPedido() != null)
                .map(Pedido::getFechaPedido)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
            T0 = minFechaPedido;
        }

        System.out.println("T0 inicializado: " + T0);
    }

    /**
     * Actualiza el ProductTracker con la mejor solución encontrada por el algoritmo ALNS.
     * 
     * Este método se llama UNA VEZ al final del algoritmo, no durante las iteraciones.
     * 
     * Para cada pedido en la mejor solución, asigna todos sus productos a la ruta correspondiente.
     */
    private void actualizarSeguimientoProductos() {
        if (productTracker == null) {
            System.out.println("⚠️ ProductTracker no inicializado");
            return;
        }

        if (mejorSolucion == null || mejorSolucion.isEmpty()) {
            System.out.println("⚠️ No hay solución para rastrear productos");
            return;
        }

        // Extraer el mapa de solución interno (mejorSolucion es un Map<HashMap<Pedido,ArrayList<Vuelo>>, Integer>)
        HashMap<Pedido, ArrayList<Vuelo>> solucionActual = mejorSolucion.keySet().iterator().next();
        
        if (solucionActual == null || solucionActual.isEmpty()) {
            System.out.println("⚠️ No hay asignaciones en la mejor solución");
            return;
        }

        int productosRastreados = 0;
        
        // Recorrer la MEJOR solución encontrada
        for (Map.Entry<Pedido, ArrayList<Vuelo>> entry : solucionActual.entrySet()) {
            Pedido pedido = entry.getKey();
            ArrayList<Vuelo> ruta = entry.getValue();
            
            // Asignar TODOS los productos de este pedido a su ruta
            List<Producto> productos = pedido.getProductos();
            if (productos != null && !productos.isEmpty()) {
                for (Producto producto : productos) {
                    if (producto != null && producto.getId() != null) {
                        productTracker.assignProductToRoute(producto, ruta);
                        productosRastreados++;
                    }
                }
            }
        }
        
        System.out.println("✓ Productos rastreados: " + productosRastreados);
    }

    private List<Pedido> expandirPaquetesAUnidadesProducto(List<Pedido> pedidosOriginales) {
        List<Pedido> unidadesProducto = new ArrayList<>();

        for (Pedido pedidoOriginal : pedidosOriginales) {
            int conteoProductos = pedidoOriginal.getCantidadProductos();
            for (int i = 0; i < conteoProductos; i++) {
                Pedido unidad = crearUnidadPaquete(pedidoOriginal, i);
                unidadesProducto.add(unidad);
            }
        }

        return unidadesProducto;
    }

    private Pedido crearUnidadPaquete(Pedido pedidoOriginal, int indiceUnidad) {
        Pedido unidad = new Pedido();

        // Generar ID único garantizado (convertir a Integer)
        int idUnico = pedidoOriginal.getId() * 1000 + indiceUnidad;
        unidad.setId(idUnico);


        unidad.setCliente(pedidoOriginal.getCliente());
        //unidad.setAeropuertoDestinoCodigo(obtenerAeropuerto(pedidoOriginal.getAeropuertoDestinoCodigo()).getCodigoIATA());
        //unidad.setAeropuertoOrigenCodigo(obtenerAeropuerto(pedidoOriginal.getAeropuertoOrigenCodigo()).getCodigoIATA());
        unidad.setAeropuertoOrigenCodigo(pedidoOriginal.getAeropuertoOrigenCodigo()); // ← Usar el código directamente
        unidad.setAeropuertoDestinoCodigo(pedidoOriginal.getAeropuertoDestinoCodigo());
        unidad.setFechaPedido(pedidoOriginal.getFechaPedido());
        unidad.setFechaLimiteEntrega(pedidoOriginal.getFechaLimiteEntrega());
        unidad.setEstado(pedidoOriginal.getEstado());
        unidad.setPrioridad(pedidoOriginal.getPrioridad());
        unidad.setCantidadProductos(1);
        unidad.setProductos(null);

        return unidad;
    }

    private Aeropuerto obtenerAeropuertoPorCiudad(Ciudad ciudad) {
        if (ciudad == null || ciudad.getNombre() == null) return null;
        String claveCiudad = ciudad.getNombre().toLowerCase().trim();
        return cacheNombreCiudadAeropuerto.get(claveCiudad);
    }

    /**
     * Encuentra una ruta directa entre dos ciudades, considerando disponibilidad por día.
     * OPTIMIZADO: Usa índice y cache de disponibilidad (O(1) en lugar de O(N))
     *
     * @param origen Ciudad de origen
     * @param destino Ciudad de destino
     * @param dia Día de operación para verificar disponibilidad de vuelo
     * @return Ruta directa si existe y está disponible, null en caso contrario
     */
    private ArrayList<Vuelo> encontrarRutaDirecta(Ciudad origen, Ciudad destino, int dia) {
        if (origen == null || destino == null) return null;

        Aeropuerto aeropuertoOrigen = obtenerAeropuertoPorCiudad(origen);
        Aeropuerto aeropuertoDestino = obtenerAeropuertoPorCiudad(destino);

        if (aeropuertoOrigen == null || aeropuertoDestino == null) return null;

        // OPTIMIZACIÓN: Usar cache de disponibilidad en lugar de búsqueda lineal
        List<Vuelo> vuelosDisponibles = cacheDisponibilidad.obtenerVuelosDisponibles(
            aeropuertoOrigen,
            aeropuertoDestino,
            dia
        );

        if (vuelosDisponibles.isEmpty()) {
            return null; // No hay vuelos directos disponibles en este día
        }

        // Tomar el primer vuelo disponible
        ArrayList<Vuelo> ruta = new ArrayList<>();
        ruta.add(vuelosDisponibles.get(0));
        return ruta;
    }

    private boolean esRutaValida(Pedido pedido, ArrayList<Vuelo> ruta) {
        if (pedido == null || ruta == null || ruta.isEmpty()) return false;

        int qty = pedido.getProductos() != null && !pedido.getProductos().isEmpty() ? pedido.getProductos().size() : 1;

        if (!cabeEnCapacidad(ruta, qty)) return false;

        Aeropuerto expectedOrigin = obtenerAeropuerto(pedido.getAeropuertoOrigenCodigo());
        if (expectedOrigin == null || !ruta.get(0).getAeropuertoOrigen().equals(expectedOrigin)) return false;

        for (int i = 0; i < ruta.size() - 1; i++) {
            if (!ruta.get(i).getAeropuertoDestino().equals(ruta.get(i + 1).getAeropuertoOrigen())) return false;
        }

        Aeropuerto expectedDestination = obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo());
        if (expectedDestination == null || !ruta.get(ruta.size() - 1).getAeropuertoDestino().equals(expectedDestination)) return false;

        return seRespetaDeadline(pedido, ruta);
    }

    private boolean puedeAsignarConOptimizacionEspacio(Pedido pedido, ArrayList<Vuelo> ruta,
                                                       HashMap<Pedido, ArrayList<Vuelo>> solucionActual) {
        Aeropuerto aeropuertoDestino = obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo());
        if (aeropuertoDestino == null) return false;

        int conteoProductos = pedido.getProductos() != null ? pedido.getProductos().size() : 1;
        int ocupacionActual = aeropuertoDestino.getCapacidadActual();
        int capacidadMaxima = aeropuertoDestino.getCapacidadMaxima();

        return (ocupacionActual + conteoProductos) <= capacidadMaxima;
    }

    private void actualizarCapacidadesVuelos(ArrayList<Vuelo> ruta, int conteoProductos) {
        for (Vuelo vuelo : ruta) {
            vuelo.setCapacidadUsada(vuelo.getCapacidadUsada() + conteoProductos);
        }
    }

    private void incrementarOcupacionAlmacen(Aeropuerto aeropuerto, int conteoProductos) {
        int ocupacionActual = ocupacionAlmacenes.getOrDefault(aeropuerto, 0);
        ocupacionAlmacenes.put(aeropuerto, ocupacionActual + conteoProductos);
    }

    private int obtenerTiempoInicioPaquete(Pedido pedido) {
        if (pedido == null || pedido.getFechaPedido() == null || T0 == null) {
            return 0;
        }
        long minutosDesdeT0 = ChronoUnit.MINUTES.between(T0, pedido.getFechaPedido());
        int offset = Math.floorMod(pedido.getId(), 60);
        int minutoInicio = (int) (minutosDesdeT0 + offset);
        final int TOTAL_MINUTOS = HORIZON_DAYS * 24 * 60;
        return Math.max(0, Math.min(minutoInicio, TOTAL_MINUTOS - 1));
    }

    private double calcularMargenTiempoRuta(Pedido pedido, ArrayList<Vuelo> ruta) {
        if (pedido == null || ruta == null) return 1.0;
        if (pedido.getFechaPedido() == null || pedido.getFechaLimiteEntrega() == null) return 1.0;

        double tiempoTotal = 0.0;
        for (Vuelo vuelo : ruta) tiempoTotal += vuelo.getTiempoTransporte();
        if (ruta.size() > 1) tiempoTotal += (ruta.size() - 1) * 2.0;

        long horasDisponibles = ChronoUnit.HOURS.between(pedido.getFechaPedido(), pedido.getFechaLimiteEntrega());
        double margen = horasDisponibles - tiempoTotal;
        return Math.max(margen, 0.0) + 1.0;
    }

    public void generarSolucionInicial() {
        if (Constantes.USAR_SOLUCION_INICIAL_CODICIOSA) {
            generarSolucionInicialGreedy();
        } else {
            generarSolucionInicialAleatoria();
        }
    }

    private void generarSolucionInicialGreedy() {
        System.out.println("=== GENERANDO SOLUCIÓN INICIAL GREEDY ===");

        // Reiniciar capacidades
        reiniciarCapacidades();

        // Crear estructura de solución temporal
        HashMap<Pedido, ArrayList<Vuelo>> solActual = new HashMap<>();

        // Ordenar pedidos con un componente aleatorio
        ArrayList<Pedido> paquetesOrdenados = new ArrayList<>(pedidos);

        // Decidir aleatoriamente entre diferentes estrategias de ordenamiento
        int estrategiaOrdenamiento = 0; // Puedes cambiar o parametrizar esta elección

        switch (estrategiaOrdenamiento) {
            case 0:
                // Ordenamiento por deadline (original)
                System.out.println("Estrategia de ordenamiento: Por deadline optimizado");
                paquetesOrdenados.sort((p1, p2) -> p1.getFechaLimiteEntrega().compareTo(p2.getFechaLimiteEntrega()));
                break;
            case 1:
                // Ordenamiento por prioridad
                System.out.println("Estrategia de ordenamiento: Por prioridad");
                paquetesOrdenados.sort((p1, p2) -> Double.compare(p2.getPrioridad(), p1.getPrioridad()));
                break;
            case 2:
                // Ordenamiento por distancia entre continentes
                System.out.println("Estrategia de ordenamiento: Por distancia entre continentes");
                paquetesOrdenados.sort((p1, p2) -> {
                    boolean p1DiffCont = obtenerAeropuerto(p1.getAeropuertoOrigenCodigo()).getCiudad().getContinente() !=
                            obtenerAeropuerto(p1.getAeropuertoDestinoCodigo()).getCiudad().getContinente();
                    boolean p2DiffCont = obtenerAeropuerto(p2.getAeropuertoOrigenCodigo()).getCiudad().getContinente() !=
                            obtenerAeropuerto(p2.getAeropuertoDestinoCodigo()).getCiudad().getContinente();
                    return Boolean.compare(p1DiffCont, p2DiffCont);
                });
                break;
            case 3:
                // Ordenamiento por margen de tiempo (más urgentes primero)
                System.out.println("Estrategia de ordenamiento: Por margen de tiempo");
                paquetesOrdenados.sort((p1, p2) -> {
                    LocalDateTime ahora = LocalDateTime.now();
                    long margen1 = ChronoUnit.HOURS.between(ahora, p1.getFechaLimiteEntrega());
                    long margen2 = ChronoUnit.HOURS.between(ahora, p2.getFechaLimiteEntrega());
                    return Long.compare(margen1, margen2);
                });
                break;
            case 4:
                // Ordenamiento aleatorio
                System.out.println("Estrategia de ordenamiento: Aleatorio");
                Collections.shuffle(paquetesOrdenados, aleatorio);
                break;
            default:
                // Fallback: aleatorio
                Collections.shuffle(paquetesOrdenados, aleatorio);
                break;
        }

        // Usar algoritmo optimizado con ventanas de tiempo y reasignación dinámica
        int paquetesAsignados = generarSolucionOptima(solActual, paquetesOrdenados);

        // Calcular el peso/costo de esta solución
        int pesoSolucion = calcularPesoSolucion(solActual);

        // Almacenar la solución con su peso
        solucion.put(solActual, pesoSolucion);

        System.out.println("Solución inicial generada: " + paquetesAsignados + "/" + pedidos.size() + " pedidos asignados");
        System.out.println("Peso de la solución: " + pesoSolucion);
    }
    private void reiniciarCapacidades() {
        for (Vuelo vuelo : vuelos) {
            vuelo.setCapacidadUsada(0);
        }
    }

    private void generarSolucionInicialAleatoria() {
        System.out.println("=== GENERANDO SOLUCIÓN INICIAL ALEATORIA ===");
        System.out.println("Probabilidad de asignación: " + (Constantes.PROBABILIDAD_ASIGNACION_ALEATORIA * 100) + "%");

        HashMap<Pedido, ArrayList<Vuelo>> solucionActual = new HashMap<>();
        int paquetesAsignados = 0;

        ArrayList<Pedido> paquetesBarajados = new ArrayList<>(pedidos);
        Collections.shuffle(paquetesBarajados, aleatorio);

        for (Pedido pedido : paquetesBarajados) {
            if (aleatorio.nextDouble() < Constantes.PROBABILIDAD_ASIGNACION_ALEATORIA) {
                ArrayList<Vuelo> rutaAleatoria = generarRutaAleatoria(pedido);

                if (rutaAleatoria != null && !rutaAleatoria.isEmpty()) {
                    int conteoProductos = pedido.getProductos() != null ? pedido.getProductos().size() : 1;

                    if (cabeEnCapacidad(rutaAleatoria, conteoProductos)) {
                        Aeropuerto aeropuertoDestino = obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo());
                        if (aeropuertoDestino != null &&
                            puedeAsignarConOptimizacionEspacio(pedido, rutaAleatoria, solucionActual)) {

                            solucionActual.put(pedido, rutaAleatoria);
                            actualizarCapacidadesVuelos(rutaAleatoria, conteoProductos);
                            actualizarCapacidadAeropuertos(aeropuertoDestino.getCodigoIATA(), conteoProductos);
                            //incrementarOcupacionAlmacen(aeropuertoDestino, conteoProductos);
                            paquetesAsignados++;
                        }
                    }
                }
            }
        }

        int pesoSolucion = calcularPesoSolucion(solucionActual);
        solucion.put(solucionActual, pesoSolucion);

        System.out.println("Solución inicial aleatoria generada: " + paquetesAsignados + "/" + pedidos.size() + " pedidos asignados");
        System.out.println("Peso de la solución: " + pesoSolucion);
    }

    private int generarSolucionOptima(HashMap<Pedido, ArrayList<Vuelo>> solucionActual,
                                  ArrayList<Pedido> paquetesOrdenados) {
    int paquetesAsignados = 0;
    int maxIteraciones = 3; // Máximo número de iteraciones para reasignación

    System.out.println("Iniciando algoritmo optimizado con " + maxIteraciones + " iteraciones...");

    for (int iteracion = 0; iteracion < maxIteraciones; iteracion++) {
        if (iteracion > 0) {
            System.out.println("Iteración " + iteracion + " - Reasignación dinámica...");
            // En iteraciones posteriores, intentar reasignar pedidos no asignados
            ArrayList<Pedido> paquetesNoAsignados = new ArrayList<>();
            for (Pedido pkg : paquetesOrdenados) {
                if (!solucionActual.containsKey(pkg)) {
                    paquetesNoAsignados.add(pkg);
                }
            }
            paquetesOrdenados = paquetesNoAsignados;
        }

        int asignadosEnIteracion = 0;

        for (Pedido pkg : paquetesOrdenados) {
            Aeropuerto aeropuertoDestino = obtenerAeropuerto(pkg.getAeropuertoDestinoCodigo());
            if (aeropuertoDestino == null) continue;

            int cantidadProductos = pkg.getProductos() != null ? pkg.getProductos().size() : 1;

            // Intentar asignar el paquete usando diferentes estrategias
            ArrayList<Vuelo> mejorRuta = encontrarMejorRutaConVentanasDeTiempo(pkg, solucionActual);

            if (mejorRuta != null && esRutaValida(pkg, mejorRuta)) {
                // Primero validar temporalmente sin actualizar capacidades
                if (puedeAsignarConOptimizacionEspacio(pkg, mejorRuta, solucionActual)) {
                    // Si la validación temporal pasa, entonces actualizar capacidades
                    solucionActual.put(pkg, mejorRuta);
                    paquetesAsignados++;
                    asignadosEnIteracion++;

                    // Actualizar capacidades DESPUÉS de la validación
                    actualizarCapacidadesVuelos(mejorRuta, cantidadProductos);
                    actualizarCapacidadAeropuertos(aeropuertoDestino.getCodigoIATA(), cantidadProductos);
                    //incrementarOcupacionAlmacen(aeropuertoDestino, cantidadProductos);

                    if (iteracion > 0) {
                        System.out.println("  Reasignado paquete " + pkg.getId() + " en iteración " + iteracion);
                    }
                }
            }
        }

        System.out.println("  Iteración " + iteracion + " completada: " + asignadosEnIteracion + " pedidos asignados");

        // Si no se asignaron pedidos en esta iteración, no hay punto en continuar
        if (asignadosEnIteracion == 0) {
            break;
        }
    }

        return paquetesAsignados;
    }

    private ArrayList<Vuelo> encontrarMejorRutaConVentanasDeTiempo(Pedido pedido, HashMap<Pedido, ArrayList<Vuelo>> solucionActual) {
         // Primero intentar con el método original (ruta estándar)
        ArrayList<Vuelo> rutaOriginal = encontrarMejorRuta(pedido);

        // Si no existe ruta original o no se puede asignar con optimización de espacio,
        // intentar con diferentes horarios de salida (salida retrasada)
        if (rutaOriginal == null || !puedeAsignarConOptimizacionDeEspacio(pedido, rutaOriginal, solucionActual)) {
            return encontrarRutaConSalidaRetrasada(pedido, solucionActual);
        }

        // Si la ruta original es válida y cabe, devolverla
        return rutaOriginal;
    }

    private boolean puedeAsignarConOptimizacionDeEspacio(Pedido pedido,
                                                         ArrayList<Vuelo> ruta,
                                                         HashMap<Pedido, ArrayList<Vuelo>> solucionActual) {
    // Validación simplificada de la capacidad del almacén final
    Aeropuerto aeropuertoDestino = obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo());
    if (aeropuertoDestino == null) return false;

    int cantidadProductos = pedido.getProductos() != null ? pedido.getProductos().size() : 1;
    int ocupacionActual = aeropuertoDestino.getCapacidadActual();
    int capacidadMaxima = aeropuertoDestino.getCapacidadMaxima();

    return (ocupacionActual + cantidadProductos) <= capacidadMaxima;
}


    /**
     * Genera una ruta aleatoria para un pedido, considerando disponibilidad.
     * Usado en la generación de solución inicial aleatoria.
     *
     * @param pedido Pedido para el cual generar ruta
     * @return Ruta aleatoria disponible, o null si no se encuentra ninguna
     */
    private ArrayList<Vuelo> generarRutaAleatoria(Pedido pedido) {
        Ciudad origen = obtenerAeropuerto(pedido.getAeropuertoOrigenCodigo()).getCiudad();
        Ciudad destino = obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo()).getCiudad();

        if (origen == null || destino == null) return null;

        Aeropuerto aeropuertoOrigen = obtenerAeropuertoPorCiudad(origen);
        Aeropuerto aeropuertoDestino = obtenerAeropuertoPorCiudad(destino);

        if (aeropuertoOrigen == null || aeropuertoDestino == null) return null;

        // Calcular día de operación
        int dia = calcularDiaOperacion(pedido);

        // Intentar ruta directa primero
        ArrayList<Vuelo> rutaDirecta = encontrarRutaDirecta(origen, destino, dia);
        if (rutaDirecta != null && !rutaDirecta.isEmpty()) {
            return rutaDirecta;
        }

        // Intentar con aeropuertos intermedios
        ArrayList<Aeropuerto> aeropuertosBarajados = new ArrayList<>(aeropuertos);
        Collections.shuffle(aeropuertosBarajados, aleatorio);

        for (int i = 0; i < Math.min(5, aeropuertosBarajados.size()); i++) {
            Aeropuerto intermedio = aeropuertosBarajados.get(i);
            if (intermedio.equals(aeropuertoOrigen) || intermedio.equals(aeropuertoDestino)) continue;

            ArrayList<Vuelo> tramo1 = encontrarRutaDirecta(origen, intermedio.getCiudad(), dia);
            ArrayList<Vuelo> tramo2 = encontrarRutaDirecta(intermedio.getCiudad(), destino, dia);

            if (tramo1 != null && tramo2 != null && !tramo1.isEmpty() && !tramo2.isEmpty()) {
                ArrayList<Vuelo> ruta = new ArrayList<>();
                ruta.addAll(tramo1);
                ruta.addAll(tramo2);
                return ruta;
            }
        }

        return null; // No hay rutas disponibles en este día
    }

    private ArrayList<Vuelo> encontrarMejorRutaConVentanasTiempo(Pedido pedido, HashMap<Pedido, ArrayList<Vuelo>> solucionActual) {
        ArrayList<Vuelo> rutaOriginal = encontrarMejorRuta(pedido);
        if (rutaOriginal == null || !puedeAsignarConOptimizacionEspacio(pedido, rutaOriginal, solucionActual)) {
            return encontrarRutaConSalidaRetrasada(pedido, solucionActual);
        }
        return rutaOriginal;
    }

    private ArrayList<Vuelo> encontrarRutaConSalidaRetrasada(Pedido pedido,
                                                             HashMap<Pedido, ArrayList<Vuelo>> solucionActual) {
        for (int delayHours = 2; delayHours <= 12; delayHours += 2) {
            Pedido pedidoRetrasado = crearPaqueteRetrasado(pedido, delayHours);
            if (pedidoRetrasado == null) continue;

            ArrayList<Vuelo> ruta = encontrarMejorRuta(pedidoRetrasado);
            if (ruta != null && puedeAsignarConOptimizacionEspacio(pedidoRetrasado, ruta, solucionActual)) {
                return ruta;
            }
        }
        return null;
    }

    private Pedido crearPaqueteRetrasado(Pedido original, int horasRetraso) {
        LocalDateTime fechaPedidoRetrasada = original.getFechaPedido().plusHours(horasRetraso);
        if (fechaPedidoRetrasada.isAfter(original.getFechaLimiteEntrega())) {
            return null;
        }

        Pedido retrasado = new Pedido();
        retrasado.setId(original.getId());
        retrasado.setCliente(original.getCliente());
        retrasado.setAeropuertoDestinoCodigo(obtenerAeropuerto(original.getAeropuertoDestinoCodigo()).getCodigoIATA());
        retrasado.setFechaPedido(fechaPedidoRetrasada);
        retrasado.setFechaLimiteEntrega(original.getFechaLimiteEntrega());
        retrasado.setAeropuertoOrigenCodigo(obtenerAeropuerto(original.getAeropuertoOrigenCodigo()).getCodigoIATA());
        retrasado.setProductos(original.getProductos());
        retrasado.setPrioridad(original.getPrioridad());
        return retrasado;
    }

    /**
     * Encuentra la mejor ruta para un pedido considerando disponibilidad de vuelos.
     * Intenta rutas directas primero, luego con 1 escala, y finalmente con 2 escalas.
     *
     * @param pedido Pedido para el cual buscar ruta
     * @return Mejor ruta encontrada, o null si no hay rutas disponibles
     */
    private ArrayList<Vuelo> encontrarMejorRuta(Pedido pedido) {
        Ciudad origen = obtenerAeropuerto(pedido.getAeropuertoOrigenCodigo()).getCiudad();
        Ciudad destino = obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo()).getCiudad();

        // ERROR: No se verifica si origen o destino son null
        if (origen == null || destino == null) {
            return null;
        }

        if (origen.equals(destino)) {
            return new ArrayList<>();
        }

        // Calcular día de operación para verificar disponibilidad
        int dia = calcularDiaOperacion(pedido);

        ArrayList<ArrayList<Vuelo>> rutasValidas = new ArrayList<>();
        ArrayList<Double> puntajesRuta = new ArrayList<>();

        // Intentar ruta directa con verificación de disponibilidad
        ArrayList<Vuelo> directa = encontrarRutaDirecta(origen, destino, dia);
        if (directa != null && esRutaValida(pedido, directa)) {
            rutasValidas.add(directa);
            puntajesRuta.add(calcularMargenTiempoRuta(pedido, directa));
        }

        // Intentar ruta con una escala
        ArrayList<Vuelo> unaEscala = encontrarRutaUnaEscala(origen, destino, dia);
        if (unaEscala != null && esRutaValida(pedido, unaEscala)) {
            rutasValidas.add(unaEscala);
            puntajesRuta.add(calcularMargenTiempoRuta(pedido, unaEscala));
        }

        // Intentar ruta con dos escalas
        ArrayList<Vuelo> dosEscalas = encontrarRutaDosEscalas(origen, destino, dia);
        if (dosEscalas != null && esRutaValida(pedido, dosEscalas)) {
            rutasValidas.add(dosEscalas);
            puntajesRuta.add(calcularMargenTiempoRuta(pedido, dosEscalas));
        }

        if (rutasValidas.isEmpty()) {
            // No hay rutas disponibles en este día (posiblemente por cancelaciones)
            if (Constantes.LOGGING_VERBOSO) {
                System.out.println("No se encontraron rutas disponibles para pedido " + pedido.getId() +
                                 " en día " + dia + " (cancelaciones activas)");
            }
            return null;
        }

        int total = rutasValidas.size();
        int indiceSeleccionado;
        if (total > 1) {
            double suma = 0;
            for (double p : puntajesRuta) suma += p;
            if (suma > 0) {
                double rand = aleatorio.nextDouble() * suma;
                double acum = 0;
                indiceSeleccionado = 0;
                for (int i = 0; i < puntajesRuta.size(); i++) {
                    acum += puntajesRuta.get(i);
                    if (rand <= acum) {
                        indiceSeleccionado = i;
                        break;
                    }
                }
            } else {
                indiceSeleccionado = aleatorio.nextInt(total);
            }
        } else {
            indiceSeleccionado = 0;
        }

        return rutasValidas.get(indiceSeleccionado);
    }

    /**
     * Encuentra una ruta con una escala entre dos ciudades, considerando disponibilidad.
     * OPTIMIZADO: Usa índice de vuelos salientes en lugar de búsqueda lineal
     *
     * @param origen Ciudad de origen
     * @param destino Ciudad de destino
     * @param dia Día de operación para verificar disponibilidad
     * @return Ruta con una escala si existe y está disponible, null en caso contrario
     */
    private ArrayList<Vuelo> encontrarRutaUnaEscala(Ciudad origen, Ciudad destino, int dia) {
        Aeropuerto aOrigen = obtenerAeropuertoPorCiudad(origen);
        Aeropuerto aDestino = obtenerAeropuertoPorCiudad(destino);
        if (aOrigen == null || aDestino == null) return null;

        // OPTIMIZACIÓN: Obtener aeropuertos intermedios viables desde el índice
        List<Vuelo> vuelosSalidaOrigen = indiceVuelos.obtenerVuelosSalientes(aOrigen);

        // Generar lista de aeropuertos intermedios únicos
        ArrayList<Aeropuerto> posibles = new ArrayList<>();
        for (Vuelo v : vuelosSalidaOrigen) {
            Aeropuerto destVuelo = v.getAeropuertoDestino();
            if (!destVuelo.equals(aDestino) && !posibles.contains(destVuelo)) {
                posibles.add(destVuelo);
            }
        }
        Collections.shuffle(posibles, aleatorio);

        // Buscar conexión viable usando cache de disponibilidad
        for (Aeropuerto escala : posibles) {
            List<Vuelo> primerTramo = cacheDisponibilidad.obtenerVuelosDisponibles(aOrigen, escala, dia);
            if (primerTramo.isEmpty()) continue;

            // Buscar vuelo con capacidad disponible
            Vuelo primero = null;
            for (Vuelo v : primerTramo) {
                if (v.getCapacidadUsada() < v.getCapacidadMaxima()) {
                    primero = v;
                    break;
                }
            }
            if (primero == null) continue;

            List<Vuelo> segundoTramo = cacheDisponibilidad.obtenerVuelosDisponibles(escala, aDestino, dia);
            if (segundoTramo.isEmpty()) continue;

            // Buscar vuelo con capacidad disponible
            Vuelo segundo = null;
            for (Vuelo v : segundoTramo) {
                if (v.getCapacidadUsada() < v.getCapacidadMaxima()) {
                    segundo = v;
                    break;
                }
            }

            if (segundo != null) {
                ArrayList<Vuelo> ruta = new ArrayList<>();
                ruta.add(primero);
                ruta.add(segundo);
                return ruta;
            }
        }
        return null; // No hay rutas con una escala disponibles en este día
    }

    /**
     * Encuentra una ruta con dos escalas entre dos ciudades, considerando disponibilidad.
     * OPTIMIZADO: Usa cache de disponibilidad en lugar de verificación directa
     *
     * @param origen Ciudad de origen
     * @param destino Ciudad de destino
     * @param dia Día de operación para verificar disponibilidad
     * @return Ruta con dos escalas si existe y está disponible, null en caso contrario
     */
    private ArrayList<Vuelo> encontrarRutaDosEscalas(Ciudad origen, Ciudad destino, int dia) {
        Aeropuerto aOrigen = obtenerAeropuertoPorCiudad(origen);
        Aeropuerto aDestino = obtenerAeropuertoPorCiudad(destino);
        if (aOrigen == null || aDestino == null) return null;

        // OPTIMIZACIÓN: Obtener aeropuertos alcanzables desde origen
        List<Vuelo> vuelosSalidaOrigen = indiceVuelos.obtenerVuelosSalientes(aOrigen);
        ArrayList<Aeropuerto> primeras = new ArrayList<>();
        for (Vuelo v : vuelosSalidaOrigen) {
            Aeropuerto destVuelo = v.getAeropuertoDestino();
            if (!destVuelo.equals(aDestino) && !primeras.contains(destVuelo)) {
                primeras.add(destVuelo);
            }
        }
        Collections.shuffle(primeras, aleatorio);
        int maxPrimeras = Math.min(10, primeras.size());

        for (int i = 0; i < maxPrimeras; i++) {
            Aeropuerto p1 = primeras.get(i);

            // OPTIMIZACIÓN: Obtener aeropuertos alcanzables desde p1
            List<Vuelo> vuelosSalidaP1 = indiceVuelos.obtenerVuelosSalientes(p1);
            ArrayList<Aeropuerto> segundas = new ArrayList<>();
            for (Vuelo v : vuelosSalidaP1) {
                Aeropuerto destVuelo = v.getAeropuertoDestino();
                if (!destVuelo.equals(aOrigen) && !destVuelo.equals(aDestino) &&
                    !destVuelo.equals(p1) && !segundas.contains(destVuelo)) {
                    segundas.add(destVuelo);
                }
            }
            Collections.shuffle(segundas, aleatorio);
            int maxSeg = Math.min(10, segundas.size());

            for (int j = 0; j < maxSeg; j++) {
                Aeropuerto p2 = segundas.get(j);

                // Buscar vuelos usando cache de disponibilidad
                List<Vuelo> primerTramo = cacheDisponibilidad.obtenerVuelosDisponibles(aOrigen, p1, dia);
                if (primerTramo.isEmpty()) continue;

                Vuelo f1 = null;
                for (Vuelo v : primerTramo) {
                    if (v.getCapacidadUsada() < v.getCapacidadMaxima()) {
                        f1 = v; break;
                    }
                }
                if (f1 == null) continue;

                List<Vuelo> segundoTramo = cacheDisponibilidad.obtenerVuelosDisponibles(p1, p2, dia);
                if (segundoTramo.isEmpty()) continue;

                Vuelo f2 = null;
                for (Vuelo v : segundoTramo) {
                    if (v.getCapacidadUsada() < v.getCapacidadMaxima()) {
                        f2 = v; break;
                    }
                }
                if (f2 == null) continue;

                List<Vuelo> tercerTramo = cacheDisponibilidad.obtenerVuelosDisponibles(p2, aDestino, dia);
                if (tercerTramo.isEmpty()) continue;

                Vuelo f3 = null;
                for (Vuelo v : tercerTramo) {
                    if (v.getCapacidadUsada() < v.getCapacidadMaxima()) {
                        f3 = v; break;
                    }
                }

                if (f3 != null) {
                    ArrayList<Vuelo> ruta = new ArrayList<>();
                    ruta.add(f1); ruta.add(f2); ruta.add(f3);
                    double tiempoTotal = f1.getTiempoTransporte() + f2.getTiempoTransporte() + f3.getTiempoTransporte();
                    tiempoTotal += 2.0;
                    if (tiempoTotal > Constantes.TIEMPO_MAX_ENTREGA_DIFERENTE_CONTINENTE * 24) continue;
                    return ruta;
                }
            }
        }
        return null; // No hay rutas con dos escalas disponibles en este día
    }

    private boolean seRespetaDeadline(Pedido pedido, ArrayList<Vuelo> ruta) {
        // Validación inicial
        if (ruta == null || ruta.isEmpty()) {
            //System.out.println("⚠️ Pedido " + pedido.getId() + " no tiene vuelos asignados en la ruta.");
            return false;
        }
        double tiempoTotal = 0;
        for (Vuelo v : ruta) tiempoTotal += v.getTiempoTransporte();
        if (ruta.size() > 1) tiempoTotal += (ruta.size() - 1) * 2.0;

        if (!validarPromesaEntregaMoraPack(pedido, tiempoTotal)) return false;

        double margenSeguridad = 0.0;
        if (aleatorio != null) {
            Ciudad origen = obtenerAeropuerto(pedido.getAeropuertoOrigenCodigo()).getCiudad();
            Ciudad destino = obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo()).getCiudad();
            boolean misma = (origen != null && destino != null) && origen.getContinente() == destino.getContinente();
            int factor = ruta.size() + (misma ? 0 : 2);

            int bound = Math.max(1, factor * 3); // evita valores <= 0

            // Log de diagnóstico
            if (bound == 1) {
                System.out.println("⚠️ Bound ajustado a 1 para evitar error. "
                        + "Pedido: " + pedido.getId()
                        + ", factor=" + factor
                        + ", misma=" + misma
                        + ", ruta.size()=" + ruta.size());
            }
            margenSeguridad = 0.01 * (1 + aleatorio.nextInt(factor * 3));
            tiempoTotal = tiempoTotal * (1.0 + margenSeguridad);
        }

        long horasHastaDeadline = ChronoUnit.HOURS.between(pedido.getFechaPedido(), pedido.getFechaLimiteEntrega());
        return tiempoTotal <= horasHastaDeadline;
    }

    private boolean validarPromesaEntregaMoraPack(Pedido pedido, double tiempoTotalHoras) {
        Ciudad origen = obtenerAeropuerto(pedido.getAeropuertoOrigenCodigo()).getCiudad();
        Ciudad destino = obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo()).getCiudad();

        if (origen == null || destino == null) {
            System.err.println("Error: origen o destino nulo para pedido " + pedido.getId());
            return false;
        }

        boolean mismoContinente = origen.getContinente() == destino.getContinente();
        long horasPromesa = mismoContinente ? 48 : 72;

        if (tiempoTotalHoras > horasPromesa) {
            if (DEBUG_MODE) {
                System.out.println("VIOLACIÓN PROMESA MORAPACK - Pedido " + pedido.getId() +
                    ": " + tiempoTotalHoras + "h > " + horasPromesa + "h");
            }
            return false;
        }

        long horasHastaDeadline = ChronoUnit.HOURS.between(pedido.getFechaPedido(), pedido.getFechaLimiteEntrega());
        if (tiempoTotalHoras > horasHastaDeadline) {
            if (DEBUG_MODE) {
                System.out.println("VIOLACIÓN DEADLINE CLIENTE - Pedido " + pedido.getId() +
                    ": " + tiempoTotalHoras + "h > " + horasHastaDeadline + "h disponibles");
            }
            return false;
        }

        if (!esSedeMoraPack(origen)) {
            if (DEBUG_MODE) {
                System.out.println("ADVERTENCIA - Pedido " + pedido.getId() + " no origina desde sede MoraPack: " + origen.getNombre());
            }
        }

        return true;
    }

    private boolean esSedeMoraPack(Ciudad ciudad) {
        if (ciudad == null || ciudad.getNombre() == null) return false;
        String nombre = ciudad.getNombre().toLowerCase();
        return nombre.contains("lima") || nombre.contains("bruselas") || nombre.contains("brussels") || nombre.contains("baku");
    }

    private int calcularPesoSolucion(HashMap<Pedido, ArrayList<Vuelo>> mapaSolucion) {
        int totalPaquetes = mapaSolucion.size();
        int totalProductos = 0;
        double tiempoTotalEntrega = 0;
        int entregasATiempo = 0;
        double utilizacionCapacidadTotal = 0;
        int totalVuelosUsados = 0;
        double margenEntregaTotal = 0;

        for (Map.Entry<Pedido, ArrayList<Vuelo>> entrada : mapaSolucion.entrySet()) {
            Pedido pedido = entrada.getKey();
            ArrayList<Vuelo> ruta = entrada.getValue();

            int productosEnPaquete = pedido.getProductos() != null ? pedido.getProductos().size() : 1;
            totalProductos += productosEnPaquete;

            double tiempoRuta = 0;
            for (Vuelo vuelo : ruta) {
                tiempoRuta += vuelo.getTiempoTransporte();
                utilizacionCapacidadTotal += (double) vuelo.getCapacidadUsada() / vuelo.getCapacidadMaxima();
                totalVuelosUsados++;
            }

            if (ruta.size() > 1) tiempoRuta += (ruta.size() - 1) * 2.0;

            tiempoTotalEntrega += tiempoRuta;

            if (seRespetaDeadline(pedido, ruta)) {
                entregasATiempo++;
                LocalDateTime entregaEstimada = pedido.getFechaPedido().plusHours((long)tiempoRuta);
                double horasMargen = ChronoUnit.HOURS.between(entregaEstimada, pedido.getFechaLimiteEntrega());
                margenEntregaTotal += horasMargen;
            }
        }

        double tiempoPromedioEntrega = totalPaquetes > 0 ? tiempoTotalEntrega / totalPaquetes : 0;
        double utilizacionCapacidadPromedio = totalVuelosUsados > 0 ? utilizacionCapacidadTotal / totalVuelosUsados : 0;
        double tasaATiempo = totalPaquetes > 0 ? (double) entregasATiempo / totalPaquetes : 0;
        double margenPromedioEntrega = entregasATiempo > 0 ? margenEntregaTotal / entregasATiempo : 0;

        double eficienciaContinental = calcularEficienciaContinental(mapaSolucion);
        double utilizacionAlmacenes = calcularUtilizacionAlmacenes();

        int peso = (int) (
            totalPaquetes * 100000 +
            totalProductos * 10000 +
            tasaATiempo * 5000 +
            Math.min(margenPromedioEntrega * 50, 1000) +
            eficienciaContinental * 500 +
            utilizacionCapacidadPromedio * 200 +
            utilizacionAlmacenes * 100 -
            tiempoPromedioEntrega * 20 -
            calcularComplejidadRuteo(mapaSolucion) * 50
        );

        if (tasaATiempo < 0.8) {
            peso = (int)(peso * 0.5);
        }

        if (tasaATiempo >= 0.95 && totalPaquetes > 10) {
            peso = (int)(peso * 1.1);
        }

        if (totalPaquetes > 1000) {
            peso = (int)(peso * 1.15);
        }

        return peso;
    }

    private double calcularEficienciaContinental(HashMap<Pedido, ArrayList<Vuelo>> mapaSolucion) {
        if (mapaSolucion.isEmpty()) return 0.0;

        int sameDirect = 0, sameOneStop = 0, diffDirect = 0, diffOneStop = 0, inefficient = 0;

        for (Map.Entry<Pedido, ArrayList<Vuelo>> e : mapaSolucion.entrySet()) {
            Pedido p = e.getKey();
            ArrayList<Vuelo> ruta = e.getValue();
            boolean mismo = obtenerAeropuerto(p.getAeropuertoOrigenCodigo()).getCiudad().getContinente() ==
                    obtenerAeropuerto(p.getAeropuertoDestinoCodigo()).getCiudad().getContinente();
            if (ruta.isEmpty()) continue;
            if (mismo) {
                if (ruta.size() == 1) sameDirect++;
                else if (ruta.size() == 2) sameOneStop++;
                else inefficient++;
            } else {
                if (ruta.size() == 1) diffDirect++;
                else if (ruta.size() <= 2) diffOneStop++;
                else inefficient++;
            }
        }

        double ef = sameDirect * 1.0 + sameOneStop * 0.8 + diffDirect * 1.2 + diffOneStop * 1.0 + inefficient * (-0.5);
        return ef;
    }

    private double calcularUtilizacionAlmacenes() {
        double total = 0.0;
        for(Aeropuerto aeropuerto : aeropuertos) {
            total += (double) aeropuerto.getCapacidadActual() / aeropuerto.getCapacidadMaxima();
        }
        return total;
    }

    private double calcularComplejidadRuteo(HashMap<Pedido, ArrayList<Vuelo>> mapaSolucion) {
        if (mapaSolucion.isEmpty()) return 0.0;
        double total = 0.0;
        for (Map.Entry<Pedido, ArrayList<Vuelo>> e : mapaSolucion.entrySet()) {
            Pedido p = e.getKey();
            ArrayList<Vuelo> ruta = e.getValue();
            if (ruta.isEmpty()) continue;
            boolean mismo = obtenerAeropuerto(p.getAeropuertoOrigenCodigo()).getCiudad().getContinente() == obtenerAeropuerto(p.getAeropuertoDestinoCodigo()).getCiudad().getContinente();
            int esperado = mismo ? 1 : 2;
            if (ruta.size() > esperado) total += (ruta.size() - esperado) * 2.0;
            if (ruta.size() > 1) {
                for (Vuelo f : ruta) {
                    double util = (double) f.getCapacidadUsada() / f.getCapacidadMaxima();
                    if (util < 0.3) total += 1.0;
                }
            }
        }
        return total;
    }

    public boolean esSolucionValida() {
        if (solucion.isEmpty()) return false;
        HashMap<Pedido, ArrayList<Vuelo>> solucionActual = solucion.keySet().iterator().next();

        for (Map.Entry<Pedido, ArrayList<Vuelo>> e : solucionActual.entrySet()) {
            Pedido p = e.getKey();
            ArrayList<Vuelo> ruta = e.getValue();
            if (!esRutaValida(p, ruta)) return false;
        }

        if (!esSolucionTemporalValida(solucionActual)) {
            System.out.println("La solución viola las restricciones de capacidad temporal de almacenes");
            return false;
        }

        return true;
    }

    public boolean esSolucionCapacidadValida() {
        if (solucion.isEmpty()) return false;
        HashMap<Pedido, ArrayList<Vuelo>> solucionActual = solucion.keySet().iterator().next();
        Map<Vuelo, Integer> uso = new HashMap<>();
        for (Map.Entry<Pedido, ArrayList<Vuelo>> e : solucionActual.entrySet()) {
            Pedido p = e.getKey();
            ArrayList<Vuelo> ruta = e.getValue();
            int productos = p.getProductos() != null && !p.getProductos().isEmpty() ? p.getProductos().size() : 1;
            for (Vuelo f : ruta) uso.merge(f, productos, Integer::sum);
        }
        for (Map.Entry<Vuelo, Integer> e : uso.entrySet()) {
            if (e.getValue() > e.getKey().getCapacidadMaxima()) return false;
        }
        return true;
    }

    public void imprimirDescripcionSolucion(int nivelDetalle) {
        if (solucion.isEmpty()) {
            System.out.println("No hay solución disponible para mostrar.");
            return;
        }

        HashMap<Pedido, ArrayList<Vuelo>> solucionActual = solucion.keySet().iterator().next();
        int pesoSolucion = solucion.get(solucionActual);

        int totalProductosAsignados = 0;
        int totalProductosEnSistema = 0;
        for (Pedido pedido : this.pedidos) {
            int conteoProductos = pedido.getProductos() != null ? pedido.getProductos().size() : 1;
            totalProductosEnSistema += conteoProductos;
            if (solucionActual.containsKey(pedido)) totalProductosAsignados += conteoProductos;
        }

        System.out.println("\n========== DESCRIPCIÓN DE LA SOLUCIÓN ==========");
        System.out.println("Peso de la solución: " + pesoSolucion);
        System.out.println("Paquetes asignados: " + solucionActual.size() + "/" + pedidos.size());
        System.out.println("Productos transportados: " + totalProductosAsignados + "/" + totalProductosEnSistema);

        int rutasDirectas = 0, rutasUnaEscala = 0, rutasDosEscalas = 0, rutasMismoContinente = 0, rutasDiferentesContinentes = 0, entregasATiempo = 0;
        for (Map.Entry<Pedido, ArrayList<Vuelo>> e : solucionActual.entrySet()) {
            Pedido p = e.getKey();
            ArrayList<Vuelo> ruta = e.getValue();
            if (ruta.size() == 1) rutasDirectas++;
            else if (ruta.size() == 2) rutasUnaEscala++;
            else if (ruta.size() == 3) rutasDosEscalas++;
            if (obtenerAeropuerto(p.getAeropuertoOrigenCodigo()).getCiudad().getContinente() == obtenerAeropuerto(p.getAeropuertoDestinoCodigo()).getCiudad().getContinente()) rutasMismoContinente++;
            else rutasDiferentesContinentes++;
            if (seRespetaDeadline(p, ruta)) entregasATiempo++;
        }

        System.out.println("\n----- Estadísticas de Rutas -----");
        System.out.println("Rutas directas: " + rutasDirectas + " (" + formatearPorcentaje(rutasDirectas, solucionActual.size()) + "%)");
        System.out.println("Rutas con 1 escala: " + rutasUnaEscala + " (" + formatearPorcentaje(rutasUnaEscala, solucionActual.size()) + "%)");
        System.out.println("Rutas con 2 escalas: " + rutasDosEscalas + " (" + formatearPorcentaje(rutasDosEscalas, solucionActual.size()) + "%)");
        System.out.println("Rutas en mismo continente: " + rutasMismoContinente + " (" + formatearPorcentaje(rutasMismoContinente, solucionActual.size()) + "%)");
        System.out.println("Rutas entre continentes: " + rutasDiferentesContinentes + " (" + formatearPorcentaje(rutasDiferentesContinentes, solucionActual.size()) + "%)");
        System.out.println("Entregas a tiempo: " + entregasATiempo + " (" + formatearPorcentaje(entregasATiempo, solucionActual.size()) + "% de asignados)");
        System.out.println("Entregas a tiempo del total: " + entregasATiempo + "/" + pedidos.size() + " (" + formatearPorcentaje(entregasATiempo, pedidos.size()) + "%)");

        int paquetesNoAsignados = pedidos.size() - solucionActual.size();
        if (paquetesNoAsignados > 0) {
            System.out.println("Paquetes no asignados: " + paquetesNoAsignados + "/" + pedidos.size() + " (" + formatearPorcentaje(paquetesNoAsignados, pedidos.size()) + "%)");
            System.out.println("Razón principal: Capacidad de almacenes insuficiente");
        }

        System.out.println("\n----- Ocupación de Almacenes -----");
        reconstruirCapacidadesDesdeSolucion(solucionActual);
        reconstruirAlmacenesDesdeSolucion(solucionActual);
        int totalCapacidad = 0, totalOcupacion = 0, almacenesAlMax = 0;
        for(Aeropuerto aeropuerto : aeropuertos) {
            int max = aeropuerto.getCapacidadMaxima();
            totalCapacidad += max;
            totalOcupacion += aeropuerto.getCapacidadActual();
            if (aeropuerto.getCapacidadActual() >= max) almacenesAlMax++;
            double porcentaje = (aeropuerto.getCapacidadActual() * 100.0) / max;
            //if (porcentaje > 80.0) {
                System.out.println("  " + aeropuerto.getCiudad().getNombre() + " - " + aeropuerto.getCodigoIATA()
                        + " : " + aeropuerto.getCapacidadActual()
                        + "/" + max + " (" + String.format("%.1f", porcentaje) + "%)");
            //}
        }

        double avgPorcentaje = totalCapacidad > 0 ? (totalOcupacion * 100.0) / totalCapacidad : 0.0;
        System.out.println("Ocupación promedio de aeropuertos: " + String.format("%.1f", avgPorcentaje) + "%");
        System.out.println("Aeropuertos llenos: " + almacenesAlMax + "/" + aeropuertos.size());

        if (ocupacionTemporalAlmacenes != null && !ocupacionTemporalAlmacenes.isEmpty()) {
            System.out.println("\n----- Picos de Ocupación Temporal -----");
            for (Aeropuerto aeropuerto : aeropuertos) {
                if (aeropuerto.getCapacidadMaxima() > 0) {
                    int[] pico = findPeakOccupancy(aeropuerto);
                    int minutoPico = pico[0];
                    int maxOcc = pico[1];
                    if (maxOcc > 0) {
                        int hora = minutoPico / 60;
                        int min = minutoPico % 60;
                        double pct = (maxOcc * 100.0) / aeropuerto.getCapacidadMaxima();
                        if (pct > 50.0) {
                            System.out.println("  " + aeropuerto.getCiudad().getNombre() +
                                              " - Pico: " + maxOcc + "/" + aeropuerto.getCapacidadMaxima() +
                                              " (" + String.format("%.1f", pct) + "%) a las " +
                                              String.format("%02d:%02d", hora, min));
                        }
                    }
                }
            }
        }

        if (nivelDetalle < 2) return;

        System.out.println("\n----- Rutas por Prioridad -----");
        List<Pedido> ordenados = new ArrayList<>(solucionActual.keySet());
        ordenados.sort((p1, p2) -> {
            int cmp = Double.compare(p2.getPrioridad(), p1.getPrioridad());
            if (cmp != 0) return cmp;
            return p1.getFechaLimiteEntrega().compareTo(p2.getFechaLimiteEntrega());
        });

        int mostrar = nivelDetalle == 2 ? Math.min(10, ordenados.size()) : ordenados.size();

        for (int i = 0; i < mostrar; i++) {
            Pedido p = ordenados.get(i);
            ArrayList<Vuelo> ruta = solucionActual.get(p);

            System.out.println("\nPedido #" + p.getId() +
                              " (Prioridad: " + String.format("%.2f", p.getPrioridad()) +
                              ", Deadline: " + p.getFechaLimiteEntrega() + ")");

            System.out.println("  Origen: " + obtenerAeropuerto(p.getAeropuertoOrigenCodigo()).getCiudad().getNombre() +
                              " (" + obtenerAeropuerto(p.getAeropuertoOrigenCodigo()).getCiudad().getContinente() + ")");
            System.out.println("  Destino: " + obtenerAeropuerto(p.getAeropuertoDestinoCodigo()).getCiudad().getNombre() +
                              " (" + obtenerAeropuerto(p.getAeropuertoDestinoCodigo()).getCiudad().getContinente() + ")");

            if (ruta.isEmpty()) {
                System.out.println("  Ruta: Ya está en el destino");
                continue;
            }

            System.out.println("  Ruta (" + ruta.size() + " vuelos):");
            double tiempoTotal = 0;
            for (int j = 0; j < ruta.size(); j++) {
                Vuelo v = ruta.get(j);
                tiempoTotal += v.getTiempoTransporte();
                System.out.println("    " + (j+1) + ". " +
                                  v.getAeropuertoOrigen().getCiudad().getNombre() + " → " +
                                  v.getAeropuertoDestino().getCiudad().getNombre() +
                                  " (" + String.format("%.1f", v.getTiempoTransporte()) + "h, " +
                                  v.getCapacidadUsada() + "/" + v.getCapacidadMaxima() + " pedidos)");
            }

            if (ruta.size() > 1) tiempoTotal += (ruta.size() - 1) * 2.0;

            System.out.println("  Tiempo total estimado: " + String.format("%.1f", tiempoTotal) + "h");

            boolean at = seRespetaDeadline(p, ruta);
            System.out.println("  Entrega a tiempo: " + (at ? "SÍ" : "NO"));
        }

        if (mostrar < ordenados.size()) {
            System.out.println("\n... y " + (ordenados.size() - mostrar) + " pedidos más (use nivel de detalle 3 para ver todos)");
        }

        System.out.println("\n=================================================");
    }

    private String formatearPorcentaje(int valor, int total) {
        if (total == 0) return "0.0";
        return String.format("%.1f", (valor * 100.0) / total);
    }

    private void inicializarCapacidadAeropuertos() {
        for (Aeropuerto a : aeropuertos) {
            a.setCapacidadActual(0);
        }

    }

    private void inicializarOcupacionTemporalAlmacenes() {
        final int TOTAL_MINUTOS = HORIZON_DAYS * 24 * 60;
        for (Aeropuerto aeropuerto : aeropuertos) {
            ocupacionTemporalAlmacenes.put(aeropuerto, new int[TOTAL_MINUTOS]);
        }
    }

    public boolean esSolucionTemporalValida(HashMap<Pedido, ArrayList<Vuelo>> mapaSolucion) {
        inicializarOcupacionTemporalAlmacenes();
        for (Map.Entry<Pedido, ArrayList<Vuelo>> entrada : mapaSolucion.entrySet()) {
            Pedido pedido = entrada.getKey();
            ArrayList<Vuelo> ruta = entrada.getValue();
            if (!simularFlujoPaquete(pedido, ruta)) {
                return false;
            }
        }
        return true;
    }

    private boolean simularFlujoPaquete(Pedido pedido, ArrayList<Vuelo> ruta) {
        if (ruta == null || ruta.isEmpty()) {
            Aeropuerto destino = obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo());
            int conteoProductos = pedido.getProductos() != null ? pedido.getProductos().size() : 1;
            int inicio = obtenerTiempoInicioPaquete(pedido);
            return agregarOcupacionTemporal(destino, inicio, Constantes.HORAS_MAX_RECOGIDA_CLIENTE * 60, conteoProductos);
        }

        int minutoActual = obtenerTiempoInicioPaquete(pedido);
        int conteoProductos = pedido.getProductos() != null ? pedido.getProductos().size() : 1;

        for (int i = 0; i < ruta.size(); i++) {
            Vuelo vuelo = ruta.get(i);
            Aeropuerto salida = vuelo.getAeropuertoOrigen();
            Aeropuerto llegada = vuelo.getAeropuertoDestino();

            int tiempoEspera = 120;
            if (!agregarOcupacionTemporal(salida, minutoActual, tiempoEspera, conteoProductos)) {
                System.out.println("Violación de capacidad en " + salida.getCiudad().getNombre() +
                                  " en minuto " + minutoActual + " (fase de espera) para pedido " + pedido.getId());
                return false;
            }

            int inicioVuelo = minutoActual + tiempoEspera;
            int duracionVuelo = (int)(vuelo.getTiempoTransporte() * 60);
            int minutoLlegada = inicioVuelo + duracionVuelo;

            int duracionEstancia;
            if (i < ruta.size() - 1) duracionEstancia = 120;
            else duracionEstancia = Constantes.HORAS_MAX_RECOGIDA_CLIENTE * 60;

            if (duracionEstancia > 0 && !agregarOcupacionTemporal(llegada, minutoLlegada, duracionEstancia, conteoProductos)) {
                System.out.println("Violación de capacidad en " + llegada.getCiudad().getNombre() +
                                  " en minuto " + minutoLlegada + " (fase de llegada) para pedido " + pedido.getId());
                return false;
            }

            minutoActual = minutoLlegada;
            if (i < ruta.size() - 1) minutoActual += 120;
        }

        return true;
    }

    private boolean agregarOcupacionTemporal(Aeropuerto aeropuerto, int minutoInicio, int duracionMinutos, int conteoProductos) {
        if (aeropuerto == null) return false;
        int[] array = ocupacionTemporalAlmacenes.get(aeropuerto);
        int capacidadMaxima = aeropuerto.getCapacidadMaxima();
        final int TOTAL_MINUTOS = HORIZON_DAYS * 24 * 60;
        int inicioClamp = Math.max(0, Math.min(minutoInicio, TOTAL_MINUTOS - 1));
        int finClamp = Math.max(0, Math.min(minutoInicio + duracionMinutos, TOTAL_MINUTOS));
        for (int m = inicioClamp; m < finClamp; m++) {
            array[m] += conteoProductos;
            if (array[m] > capacidadMaxima) return false;
        }
        return true;
    }

    private int[] findPeakOccupancy(Aeropuerto aeropuerto) {
        int[] array = ocupacionTemporalAlmacenes.get(aeropuerto);
        int max = 0; int minuto = 0;
        final int TOTAL_MINUTOS = HORIZON_DAYS * 24 * 60;
        for (int m = 0; m < TOTAL_MINUTOS; m++) {
            if (array[m] > max) { max = array[m]; minuto = m; }
        }
        return new int[]{minuto, max};
    }

    private Aeropuerto obtenerAeropuerto(String codigoIATA) {
        if (codigoIATA == null || codigoIATA.trim().isEmpty()) {
            System.err.println("❌ Código IATA nulo o vacío");
            return null;
        }

        for (Aeropuerto aeropuerto : this.aeropuertos) {
            if (aeropuerto != null &&
                    aeropuerto.getCodigoIATA() != null &&
                    aeropuerto.getCodigoIATA().equalsIgnoreCase(codigoIATA.trim())) {
                return aeropuerto;
            }
        }

        // Log para debugging
        System.err.println("❌ No se encontró aeropuerto con código IATA: '" + codigoIATA + "'");
        System.err.println("   Aeropuertos disponibles: " +
                this.aeropuertos.stream()
                        .filter(a -> a != null && a.getCodigoIATA() != null)
                        .map(Aeropuerto::getCodigoIATA)
                        .collect(Collectors.joining(", ")));

        return null;
    }
    //===============================NUEVOS METODOS PARA COLAPSO===========================================
    // Dentro del método ejecutarModoColapso(), agregar:
    public Map<String, Object> ejecutarModoColapso() {
        System.out.println("🚨 INICIANDO MODO COLAPSO OPTIMIZADO 🚨");

        // ✅ CONFIGURACIÓN OPTIMIZADA
        this.modoColapso = true;
        this.horaInicioColapso = LocalDateTime.now();
        this.eventosColapso = new ArrayList<>();
        this.capturandoTimelineColapso = true;

        // ✅ PARÁMETROS OPTIMIZADOS PARA GRANDES VOLÚMENES
        int maxEventosEnMemoria = 200;
        int intervaloMuestreo = calcularIntervaloMuestreoOptimo();

        System.out.println("📊 Configuración optimizada:");
        System.out.println("   - Máximo eventos: " + maxEventosEnMemoria);
        System.out.println("   - Intervalo muestreo: " + intervaloMuestreo + " iteraciones");
        System.out.println("   - Pedidos totales: " + pedidos.size());
        System.out.println("   - Horizonte días: " + HORIZON_DAYS);

        // Inicializar solución
        generarSolucionInicial();

        // Ejecutar simulación optimizada
        ejecutarHastaColapsoOptimizado(intervaloMuestreo, maxEventosEnMemoria);

        // ✅ TIMELINE COMPRIMIDO
        LineaDeTiempoSimulacionDTO timeline = construirTimelineComprimido();
        metricasColapso.put("timelineColapso", timeline);
        metricasColapso.put("eventosOriginales", eventosColapso.size());
        metricasColapso.put("eventosEnTimeline", timeline.getEventos().size());

        // Recolectar métricas finales
        recolectarMetricasColapso();

        imprimirEstadisticasColapso(solucion,iteracionesColapso);
        System.out.println("✅ MODO COLAPSO OPTIMIZADO COMPLETADO");
        System.out.println("   Eventos capturados: " + eventosColapso.size());
        System.out.println("   Eventos en timeline: " + timeline.getEventos().size());

        return metricasColapso;
    }
    // ✅ NUEVO MÉTODO PARA IMPRIMIR ESTADÍSTICAS DE COLAPSO
    private void imprimirEstadisticasColapso(HashMap<HashMap<Pedido, ArrayList<Vuelo>>, Integer> solucionActual, int iteracion) {
        int pedidosAsignados = solucionActual.keySet().iterator().next().size();
        System.out.println("\n=== ESTADÍSTICAS COLAPSO - Iteración " + iteracion + " ===");
        System.out.println("Pedidos asignados: " + pedidosAsignados + "/" + pedidos.size() +
                " (" + String.format("%.1f%%", (double)pedidosAsignados/pedidos.size()*100) + ")");
        System.out.println("Almacenes llenos: " + contarAlmacenesLlenos() + "/" + aeropuertos.size());
        System.out.println("Vuelos saturados: " + contarVuelosSaturados() + "/" + vuelos.size());
        System.out.println("Pedidos críticos no asignados: " + contarPedidosCriticosNoAsignados());
        System.out.println("Temperatura: " + String.format("%.2f", temperatura));
        System.out.println("====================================\n");
    }
    private int calcularIntervaloMuestreoOptimo() {
        int totalPedidos = pedidos.size();
        if (totalPedidos > 10000) return 100;
        if (totalPedidos > 5000) return 50;
        if (totalPedidos > 1000) return 25;
        return 10;
    }
    private void ejecutarHastaColapsoOptimizado(int intervaloMuestreo, int maxEventosEnMemoria) {
        if (solucion.isEmpty()) {
            System.out.println("Error: No hay solución inicial para modo colapso");
            return;
        }

        Map.Entry<HashMap<Pedido, ArrayList<Vuelo>>, Integer> primeraEntrada =
                solucion.entrySet().iterator().next();
        HashMap<Pedido, ArrayList<Vuelo>> solucionActual = new HashMap<>(primeraEntrada.getKey());
        int pesoActual = primeraEntrada.getValue();

        mejorSolucion = new HashMap<>(solucion);
        inicializarPoolNoAsignados();

        // ✅ CAPTURAR ESTADO INICIAL
        capturarEstadoColapsoOptimizado("INICIO", 0, solucionActual);

        boolean colapsoDetectado = false;

        for (iteracionesColapso = 1;
             iteracionesColapso < maxIteracionesColapso && !colapsoDetectado;
             iteracionesColapso++) {

            // ✅ CAPTURAR ESTADO OPTIMIZADO - Solo en intervalos específicos
            if (iteracionesColapso % intervaloMuestreo == 0) {
                capturarEstadoColapsoOptimizado("EJECUCION", iteracionesColapso, solucionActual);
            }
            // ✅ GESTIONAR MEMORIA CADA 50 ITERACIONES
            if (iteracionesColapso % 50 == 0) {
                gestionarMemoriaTimeline();
            }
            // ✅ EJECUTAR ITERACIÓN NORMAL PRIMERO
            ejecutarIteracionALNSColapso(solucionActual, pesoActual, iteracionesColapso);

            // ✅ VERIFICAR COLAPSO CADA 25 ITERACIONES (NO CADA 10)
            if (iteracionesColapso % 25 == 0) {
                colapsoDetectado = verificarCondicionesColapso(solucionActual, iteracionesColapso);

                if (colapsoDetectado) {
                    capturarEstadoColapsoOptimizado("COLAPSO_DETECTADO", iteracionesColapso, solucionActual);
                    break;
                }
            }

            // ✅ ACTUALIZAR MÉTRICAS PERIÓDICAMENTE
            if (iteracionesColapso % 50 == 0) {
                actualizarMetricasColapso(solucionActual, iteracionesColapso);

                // Log de progreso
                if (iteracionesColapso % 100 == 0) {
                    System.out.println("📊 Progreso colapso - Iteración: " + iteracionesColapso +
                            ", Asignados: " + solucionActual.size() + "/" + pedidos.size() +
                            " (" + String.format("%.1f%%",
                            (double)solucionActual.size()/pedidos.size()*100) + ")");
                }
            }
        }

        // ✅ SI LLEGAMOS AL MÁXIMO SIN COLAPSO
        if (!colapsoDetectado && iteracionesColapso >= maxIteracionesColapso) {
            capturarEstadoColapsoOptimizado("MAX_ITERACIONES", iteracionesColapso, solucionActual);
            metricasColapso.put("tipoColapso", "MAX_ITERACIONES_ALCANZADO");
            System.out.println("⚠️ Colapso por máximo de iteraciones alcanzado");
        }

        solucion.clear();
        solucion.putAll(mejorSolucion);
    }
    /**
     * Captura el estado actual de la simulación para el timeline
     */
    private void capturarEstadoColapsoOptimizado(String fase, int iteracion,
                                       HashMap<Pedido, ArrayList<Vuelo>> solucionActual) {
        if (!capturandoTimelineColapso) return;

        // ✅ CAPTURAR MÁS EVENTOS PARA MEJOR VISUALIZACIÓN
        boolean esEventoImportante =
                fase.equals("COLAPSO_DETECTADO") ||
                fase.equals("INICIO") ||
                fase.equals("MAX_ITERACIONES") ||
                (iteracion % 25 == 0) || // Cada 50 iteraciones
                detectarCambioSignificativo(iteracion, solucionActual);

        if (!esEventoImportante) return;

        try {
            EventoSimulacion evento = new EventoSimulacion();
            evento.setFase(fase);
            evento.setIteracion(iteracion);
            evento.setTimestamp(LocalDateTime.now());
            evento.setPedidosAsignados(solucionActual.size());
            evento.setPedidosTotales(pedidos.size());

            // Calcular métricas críticas
            evento.setAlmacenesLlenos(contarAlmacenesLlenos());
            evento.setVuelosSaturados(contarVuelosSaturados());
            evento.setPedidosCriticosNoAsignados(contarPedidosCriticosNoAsignados());

            // ✅ SOLO CAPTURAR RUTAS EN EVENTOS CRÍTICOS
            if (fase.equals("COLAPSO_DETECTADO") || iteracion % 100 == 0) {
                evento.setRutasMuestreadas(obtenerMuestraRutas(solucionActual, 20)); // Solo 20 rutas de muestra
            }

            eventosColapso.add(evento);

            if (Constantes.LOGGING_VERBOSO) {
                System.out.println("📅 Colapso Timeline - " + fase + " @ iter " + iteracion +
                        " - Asignados: " + solucionActual.size() + "/" + pedidos.size());
            }
        } catch (Exception e) {
            System.err.println("Error capturando estado de colapso: " + e.getMessage());
        }
    }
    // ✅ MUESTREO DE RUTAS REPRESENTATIVAS
    private HashMap<Pedido, ArrayList<Vuelo>> obtenerMuestraRutas(
            HashMap<Pedido, ArrayList<Vuelo>> solucionActual, int maxMuestra) {

        if (solucionActual.isEmpty()) {
            return new HashMap<>();
        }

        List<Map.Entry<Pedido, ArrayList<Vuelo>>> entradas = new ArrayList<>(solucionActual.entrySet());

        // Ordenar por: 1. Problemas de ruta, 2. Prioridad, 3. Urgencia
        entradas.sort((e1, e2) -> {
            // Priorizar pedidos con problemas
            boolean e1Problema = tieneProblemasRuta(e1.getKey(), e1.getValue());
            boolean e2Problema = tieneProblemasRuta(e2.getKey(), e2.getValue());
            if (e1Problema && !e2Problema) return -1;
            if (!e1Problema && e2Problema) return 1;

            // Luego por prioridad (mayor primero)
            int prioridadComp = Double.compare(e2.getKey().getPrioridad(), e1.getKey().getPrioridad());
            if (prioridadComp != 0) return prioridadComp;

            // Luego por urgencia (deadline más cercano primero)
            if (e1.getKey().getFechaLimiteEntrega() != null && e2.getKey().getFechaLimiteEntrega() != null) {
                int urgenciaComp = e1.getKey().getFechaLimiteEntrega().compareTo(e2.getKey().getFechaLimiteEntrega());
                if (urgenciaComp != 0) return urgenciaComp;
            }

            return Integer.compare(e1.getKey().getId(), e2.getKey().getId());
        });

        // Tomar muestra limitada
        int muestraSize = Math.min(maxMuestra, entradas.size());
        HashMap<Pedido, ArrayList<Vuelo>> muestra = new HashMap<>();

        for (int i = 0; i < muestraSize; i++) {
            Map.Entry<Pedido, ArrayList<Vuelo>> entrada = entradas.get(i);
            muestra.put(entrada.getKey(), entrada.getValue());
        }

        return muestra;
    }
    private boolean tieneProblemasRuta(Pedido pedido, ArrayList<Vuelo> ruta) {
        if (ruta == null || ruta.isEmpty()) return true;

        // Verificar si la ruta tiene problemas de capacidad
        for (Vuelo vuelo : ruta) {
            double utilizacion = (double) vuelo.getCapacidadUsada() / vuelo.getCapacidadMaxima();
            if (utilizacion > 0.9) return true;
        }

        // Verificar si incumple deadline
        return !seRespetaDeadline(pedido, ruta);
    }

    private boolean detectarCambioSignificativo(int iteracion,
                                                HashMap<Pedido, ArrayList<Vuelo>> solucionActual) {
        if (eventosColapso.isEmpty()) return true;

        EventoSimulacion ultimoEvento = eventosColapso.get(eventosColapso.size() - 1);

        // Cambio significativo si:
        // - Más de 10% cambio en asignaciones
        int cambioAsignaciones = Math.abs(solucionActual.size() - ultimoEvento.getPedidosAsignados());
        double porcentajeCambio = (double) cambioAsignaciones / ultimoEvento.getPedidosAsignados();

        return porcentajeCambio > 0.1 ||
                contarAlmacenesLlenos() > ultimoEvento.getAlmacenesLlenos() + 2;
    }
    /**
     * Construye el DTO de timeline para el frontend
     */
    private LineaDeTiempoSimulacionDTO construirTimelineComprimido() {
        System.out.println("🔄 Construyendo timeline CON RUTAS para colapso con " + eventosColapso.size() + " eventos...");

        if (eventosColapso.isEmpty()) {
            return construirTimelineVacio();
        }

        List<EventoLineaDeTiempoVueloDTO> eventosDTO = new ArrayList<>();
        Set<Integer> aeropuertosUnicos = new HashSet<>();

        // ✅ PROCESAR CADA EVENTO DE COLAPSO Y AGREGAR EVENTOS DE VUELO REALES
        for (EventoSimulacion eventoColapso : eventosColapso) {
            // 1. Primero agregar el evento de estado de simulación (como antes)
            EventoLineaDeTiempoVueloDTO eventoTimeline = crearEventoAgregado(eventoColapso,
                    eventoColapso.getFase() + "-" + eventoColapso.getIteracion());
            eventosDTO.add(eventoTimeline);

            // 2. ✅ NUEVO: AGREGAR EVENTOS DE VUELO REALES SI HAY RUTAS MUESTREADAS
            if (eventoColapso.getRutasMuestreadas() != null &&
                    !eventoColapso.getRutasMuestreadas().isEmpty()) {

                List<EventoLineaDeTiempoVueloDTO> eventosVuelo =
                        convertirRutasAEventosTimeline(eventoColapso.getRutasMuestreadas(),
                                eventoColapso.getTimestamp());

                eventosDTO.addAll(eventosVuelo);

                // Recolectar aeropuertos únicos de los eventos de vuelo
                for (EventoLineaDeTiempoVueloDTO eventoVuelo : eventosVuelo) {
                    if (eventoVuelo.getIdAeropuertoOrigen() != null) {
                        aeropuertosUnicos.add(eventoVuelo.getIdAeropuertoOrigen());
                    }
                    if (eventoVuelo.getIdAeropuertoDestino() != null) {
                        aeropuertosUnicos.add(eventoVuelo.getIdAeropuertoDestino());
                    }
                }

                System.out.println("   ✅ Evento " + eventoColapso.getFase() + " - " +
                        eventosVuelo.size() + " eventos de vuelo agregados");
            }
        }

        // ✅ ORDENAR POR TIEMPO Y LIMITAR
        eventosDTO.sort(Comparator.comparing(EventoLineaDeTiempoVueloDTO::getHoraEvento));

        // Limitar a 200 eventos máximo para el frontend (más generoso para incluir vuelos)
        if (eventosDTO.size() > 200) {
            eventosDTO = eventosDTO.subList(0, 200);
            System.out.println("📦 Timeline comprimido a " + eventosDTO.size() + " eventos");
        }

        // Calcular tiempos finales
        LocalDateTime horaInicio = eventosDTO.get(0).getHoraEvento();
        LocalDateTime horaFin = eventosDTO.get(eventosDTO.size() - 1).getHoraEvento();
        long duracionMinutos = ChronoUnit.MINUTES.between(horaInicio, horaFin);

        System.out.println("✅ Timeline final: " + eventosDTO.size() + " eventos, " +
                aeropuertosUnicos.size() + " aeropuertos, " +
                duracionMinutos + " minutos");

        return LineaDeTiempoSimulacionDTO.builder()
                .horaInicioSimulacion(horaInicio)
                .horaFinSimulacion(horaFin)
                .duracionTotalMinutos(duracionMinutos)
                .eventos(eventosDTO)
                .totalEventos(eventosDTO.size())
                .totalAeropuertos(aeropuertosUnicos.size())
                .build();
    }
    private LineaDeTiempoSimulacionDTO construirTimelineVacio() {
        return LineaDeTiempoSimulacionDTO.builder()
                .horaInicioSimulacion(horaInicioColapso)
                .horaFinSimulacion(LocalDateTime.now())
                .duracionTotalMinutos(0L)
                .eventos(Collections.emptyList())
                .totalEventos(0)
                .totalAeropuertos(0)
                .build();
    }
    /**
     * ✅ NUEVO MÉTODO: Convierte rutas muestreadas a eventos de timeline REALES para el mapa
     */
    private List<EventoLineaDeTiempoVueloDTO> convertirRutasAEventosTimeline(
            Map<Pedido, ArrayList<Vuelo>> rutasMuestreadas,
            LocalDateTime timestampBase) {

        List<EventoLineaDeTiempoVueloDTO> eventosVuelo = new ArrayList<>();

        if (rutasMuestreadas == null || rutasMuestreadas.isEmpty()) {
            return eventosVuelo;
        }

        int contadorEvento = 0;
        LocalDateTime tiempoActual = timestampBase;

        System.out.println("   🗺️ Convirtiendo " + rutasMuestreadas.size() + " rutas a eventos de mapa...");

        for (Map.Entry<Pedido, ArrayList<Vuelo>> entrada : rutasMuestreadas.entrySet()) {
            Pedido pedido = entrada.getKey();
            ArrayList<Vuelo> vuelos = entrada.getValue();

            if (vuelos == null || vuelos.isEmpty()) {
                continue;
            }

            // Procesar cada vuelo en la ruta
            for (int i = 0; i < vuelos.size(); i++) {
                Vuelo vuelo = vuelos.get(i);

                // ✅ EVENTO DE SALIDA (DEPARTURE)
                EventoLineaDeTiempoVueloDTO eventoSALIDA = EventoLineaDeTiempoVueloDTO.builder()
                        .idEvento("COLAPSO-DEP-" + contadorEvento++)
                        .tipoEvento("DEPARTURE")  // ✅ CRÍTICO: Tipo para el mapa
                        .horaEvento(tiempoActual)
                        .idVuelo(vuelo.getId())
                        .codigoVuelo(construirCodigoVuelo(vuelo))
                        .idProducto(obtenerIdProductoRepresentativo(pedido))
                        .idPedido(pedido.getId())
                        .ciudadOrigen(vuelo.getAeropuertoOrigen() != null ?
                                vuelo.getAeropuertoOrigen().getCiudad().getNombre() : "Desconocido")
                        .ciudadDestino(vuelo.getAeropuertoDestino() != null ?
                                vuelo.getAeropuertoDestino().getCiudad().getNombre() : "Desconocido")
                        .idAeropuertoOrigen(vuelo.getAeropuertoOrigen() != null ?
                                vuelo.getAeropuertoOrigen().getId() : null)
                        .idAeropuertoDestino(vuelo.getAeropuertoDestino() != null ?
                                vuelo.getAeropuertoDestino().getId() : null)
                        .tiempoTransporteDias(vuelo.getTiempoTransporte() / 24.0)
                        .descripcion(construirDescripcionVuelo(vuelo, "salida"))
                        .build();

                eventosVuelo.add(eventoSALIDA);

                // ✅ EVENTO DE LLEGADA (ARRIVAL)
                LocalDateTime horaLlegada = tiempoActual.plusMinutes(
                        (long)(vuelo.getTiempoTransporte() * 60));

                EventoLineaDeTiempoVueloDTO eventoLLEGADA = EventoLineaDeTiempoVueloDTO.builder()
                        .idEvento("COLAPSO-ARR-" + contadorEvento++)
                        .tipoEvento("ARRIVAL")  // ✅ CRÍTICO: Tipo para el mapa
                        .horaEvento(horaLlegada)
                        .idVuelo(vuelo.getId())
                        .codigoVuelo(construirCodigoVuelo(vuelo))
                        .idProducto(obtenerIdProductoRepresentativo(pedido))
                        .idPedido(pedido.getId())
                        .ciudadOrigen(vuelo.getAeropuertoOrigen() != null ?
                                vuelo.getAeropuertoOrigen().getCiudad().getNombre() : "Desconocido")
                        .ciudadDestino(vuelo.getAeropuertoDestino() != null ?
                                vuelo.getAeropuertoDestino().getCiudad().getNombre() : "Desconocido")
                        .idAeropuertoOrigen(vuelo.getAeropuertoOrigen() != null ?
                                vuelo.getAeropuertoOrigen().getId() : null)
                        .idAeropuertoDestino(vuelo.getAeropuertoDestino() != null ?
                                vuelo.getAeropuertoDestino().getId() : null)
                        .tiempoTransporteDias(vuelo.getTiempoTransporte() / 24.0)
                        .descripcion(construirDescripcionVuelo(vuelo, "llegada"))
                        .build();

                eventosVuelo.add(eventoLLEGADA);

                // Actualizar tiempo para próximo vuelo (incluir escala)
                tiempoActual = horaLlegada.plusMinutes(60); // 1 hora de escala
            }
        }

        System.out.println("   ✅ Generados " + eventosVuelo.size() + " eventos de vuelo a partir de rutas");
        return eventosVuelo;
    }
    //OJITO
    /**
     * Obtiene un ID de producto representativo del pedido
     */
    private Integer obtenerIdProductoRepresentativo(Pedido pedido) {
        if (pedido.getProductos() != null && !pedido.getProductos().isEmpty()) {
            return pedido.getProductos().get(0).getId();
        }
        return pedido.getId() * 1000; // ID derivado del pedido
    }

    /**
     * Construye código de vuelo legible
     */
    private String construirCodigoVuelo(Vuelo vuelo) {
        if (vuelo.getAeropuertoOrigen() == null || vuelo.getAeropuertoDestino() == null) {
            return "VUELO-" + vuelo.getId();
        }

        String origen = vuelo.getAeropuertoOrigen().getCodigoIATA();
        String destino = vuelo.getAeropuertoDestino().getCodigoIATA();

        return "VL" + origen + "-" + destino + "-" + vuelo.getId();
    }

    /**
     * Construye descripción legible para eventos de vuelo
     */
    private String construirDescripcionVuelo(Vuelo vuelo, String tipo) {
        String origen = vuelo.getAeropuertoOrigen() != null ?
                vuelo.getAeropuertoOrigen().getCiudad().getNombre() : "Origen desconocido";
        String destino = vuelo.getAeropuertoDestino() != null ?
                vuelo.getAeropuertoDestino().getCiudad().getNombre() : "Destino desconocido";

        if ("salida".equals(tipo)) {
            return String.format("Salida de %s hacia %s (%.1f horas)",
                    origen, destino, vuelo.getTiempoTransporte());
        } else {
            return String.format("Llegada a %s desde %s", destino, origen);
        }
    }
    //TERMINAR OJITO
    // ✅ CREAR EVENTO AGREGADO PARA TIMELINE
    private EventoLineaDeTiempoVueloDTO crearEventoAgregado(EventoSimulacion evento, String claveGrupo) {
        return EventoLineaDeTiempoVueloDTO.builder()
                .idEvento("COLAPSO-" + claveGrupo + "-" + evento.getIteracion())
                .tipoEvento("ESTADO_SIMULACION")
                .horaEvento(evento.getTimestamp())
                .ciudadOrigen("SISTEMA")
                .ciudadDestino("SISTEMA")
                .codigoVuelo("FASE-" + evento.getFase())
                .descripcion(construirDescripcionEvento(evento))
                .metricas(construirMetricasEvento(evento))
                .build();
    }
    private Map<String, Object> comprimirRutaIndividual(Pedido pedido, ArrayList<Vuelo> ruta) {
        Map<String, Object> rutaComprimida = new HashMap<>();
        rutaComprimida.put("pedidoId", pedido.getId());
        rutaComprimida.put("prioridad", pedido.getPrioridad());
        rutaComprimida.put("numVuelos", ruta.size());

        // Solo información esencial de la ruta
        if (!ruta.isEmpty()) {
            rutaComprimida.put("origen", ruta.get(0).getAeropuertoOrigen().getCodigoIATA());
            rutaComprimida.put("destino", ruta.get(ruta.size()-1).getAeropuertoDestino().getCodigoIATA());
        }

        return rutaComprimida;
    }
    // ✅ GESTIÓN DE MEMORIA OPTIMIZADA
    private void gestionarMemoriaTimeline() {
        if (eventosColapso.size() > 500) {
            System.out.println("🧹 Optimizando memoria timeline: " + eventosColapso.size() + " eventos");

            // Mantener eventos críticos
            List<EventoSimulacion> eventosCriticos = eventosColapso.stream()
                    .filter(e -> e.getFase().equals("COLAPSO_DETECTADO") ||
                            e.getFase().equals("INICIO") ||
                            e.getFase().equals("MAX_ITERACIONES") ||
                            e.getAlmacenesLlenos() > 0 ||
                            e.getVuelosSaturados() > 10)
                    .collect(Collectors.toList());

            // Para eventos regulares, tomar muestra representativa
            List<EventoSimulacion> eventosRegulares = eventosColapso.stream()
                    .filter(e -> !eventosCriticos.contains(e))
                    .collect(Collectors.toList());

            // Ordenar eventos regulares por importancia y tomar muestra
            eventosRegulares.sort((e1, e2) -> {
                int importancia1 = e1.getAlmacenesLlenos() + e1.getVuelosSaturados() + e1.getPedidosCriticosNoAsignados();
                int importancia2 = e2.getAlmacenesLlenos() + e2.getVuelosSaturados() + e2.getPedidosCriticosNoAsignados();
                return Integer.compare(importancia2, importancia1);
            });

            int mantenerRegulares = Math.min(100, eventosRegulares.size());

            eventosColapso.clear();
            eventosColapso.addAll(eventosCriticos);
            eventosColapso.addAll(eventosRegulares.subList(0, mantenerRegulares));

            System.out.println("✅ Memoria optimizada: " + eventosColapso.size() + " eventos");
        }
    }
    // ✅ MÉTODOS AUXILIARES PARA DESCRIPCIÓN Y MÉTRICAS
    private String construirDescripcionEvento(EventoSimulacion evento) {
        return String.format("Iteración %d: %d/%d pedidos asignados, %d almacenes llenos, %d vuelos saturados, %d pedidos críticos bloqueados",
                evento.getIteracion(),
                evento.getPedidosAsignados(),
                evento.getPedidosTotales(),
                evento.getAlmacenesLlenos(),
                evento.getVuelosSaturados(),
                evento.getPedidosCriticosNoAsignados());
    }

    private Map<String, Object> construirMetricasEvento(EventoSimulacion evento) {
        Map<String, Object> metricas = new HashMap<>();
        metricas.put("iteracion", evento.getIteracion());
        metricas.put("pedidosAsignados", evento.getPedidosAsignados());
        metricas.put("pedidosTotales", evento.getPedidosTotales());
        metricas.put("porcentajeAsignacion",
                (double) evento.getPedidosAsignados() / evento.getPedidosTotales() * 100);
        metricas.put("almacenesLlenos", evento.getAlmacenesLlenos());
        metricas.put("vuelosSaturados", evento.getVuelosSaturados());
        metricas.put("pedidosCriticosNoAsignados", evento.getPedidosCriticosNoAsignados());
        metricas.put("fase", evento.getFase());

        // Comprimir rutas si existen
        if (evento.getRutasMuestreadas() != null && !evento.getRutasMuestreadas().isEmpty()) {
            metricas.put("rutasMuestreadas", comprimirRutasParaTimeline(evento.getRutasMuestreadas()));
        }

        return metricas;
    }
    // ✅ COMPRESIÓN DE RUTAS PARA TIMELINE
    private Map<String, Object> comprimirRutasParaTimeline(Map<Pedido, ArrayList<Vuelo>> rutas) {
        if (rutas == null || rutas.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> rutasComprimidas = new HashMap<>();

        // Estadísticas agregadas en lugar de rutas individuales
        rutasComprimidas.put("totalRutas", rutas.size());
        rutasComprimidas.put("rutasDirectas",
                rutas.values().stream().filter(r -> r.size() == 1).count());
        rutasComprimidas.put("rutasConEscalas",
                rutas.values().stream().filter(r -> r.size() > 1).count());
        rutasComprimidas.put("rutasProblema",
                rutas.values().stream().filter(r -> tieneProblemasRuta(rutas.keySet().iterator().next(), r)).count());

        // Solo guardar información de rutas problemáticas (máximo 5)
        List<Map<String, Object>> rutasProblema = rutas.entrySet().stream()
                .filter(entry -> tieneProblemasRuta(entry.getKey(), entry.getValue()))
                .limit(5)
                .map(entry -> comprimirRutaIndividual(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        rutasComprimidas.put("rutasProblemaDetalle", rutasProblema);

        return rutasComprimidas;
    }

    //=============================== CORRECCIÓN DE DETECCIÓN DE COLAPSO ================================
    private boolean verificarCondicionesColapso(HashMap<Pedido, ArrayList<Vuelo>> solucionActual,
                                                int iteracion) {
        List<String> condiciones = new ArrayList<>();

        // ✅ EVITAR DETECCIÓN PREMATURA - Mínimo 50 iteraciones
        if (iteracion < 50) {
            return false;
        }

        boolean colapsoDetectado = false;
        String tipoColapso = "NO_COLAPSO";

        // 1. Colapso por almacenes llenos - SOLO si hay un número significativo
        int almacenesLlenos = contarAlmacenesLlenos();
        int totalAlmacenes = aeropuertos.size();
        double porcentajeAlmacenesLlenos = 0;

        // ✅ NUEVO: Detectar si hay ALGÚN almacén lleno (más sensible)
        if (almacenesLlenos > 0) {
            porcentajeAlmacenesLlenos = (double) almacenesLlenos / totalAlmacenes * 100;
            condiciones.add(String.format("ALMACENES_LLENOS:%d/%d (%.1f%%)",
                    almacenesLlenos, totalAlmacenes, porcentajeAlmacenesLlenos));

            // ✅ COLAPSO INMEDIATO si hay almacenes llenos críticos
            if (almacenesLlenos >= 1) {
                System.out.println("🚨 COLAPSO CRÍTICO: " + almacenesLlenos + " almacenes llenos");
                colapsoDetectado = true;
                tipoColapso = "ALMACENES_CRITICOS";
            }
        }

        // 2. Colapso por vuelos saturados - SOLO si es significativo
        int vuelosSaturados = contarVuelosSaturados();
        int totalVuelos = vuelos.size();
        double porcentajeVuelosSaturados = 0;

        if (vuelosSaturados > 0) {
            porcentajeVuelosSaturados = (double) vuelosSaturados / totalVuelos * 100;
            condiciones.add(String.format("VUELOS_SATURADOS:%d/%d (%.1f%%)",
                    vuelosSaturados, totalVuelos, porcentajeVuelosSaturados));

            // ✅ COLAPSO si hay muchos vuelos saturados
            if (vuelosSaturados >= 50) {
                System.out.println("🚨 COLAPSO CRÍTICO: " + vuelosSaturados + " vuelos saturados");
                colapsoDetectado = true;
                tipoColapso = "VUELOS_CRITICOS";
            }
        }

        // 3. Colapso por pedidos no asignados críticos - SOLO si hay muchos
//        int pedidosCriticosNoAsignados = contarPedidosCriticosNoAsignados();
//        int totalPedidosCriticos = contarTotalPedidosCriticos();
//
//        if (totalPedidosCriticos > 0) {
//            double porcentajeCriticosBloqueados = (double) pedidosCriticosNoAsignados / totalPedidosCriticos * 100;
//            if (porcentajeCriticosBloqueados > 50.0) { // Más del 50% de críticos bloqueados
//                condiciones.add(String.format("PEDIDOS_CRITICOS_BLOQUEADOS:%d/%d (%.1f%%)",
//                        pedidosCriticosNoAsignados, totalPedidosCriticos, porcentajeCriticosBloqueados));
//            }
//        }

        // 4. Colapso por capacidad del sistema excedida - SOLO después de muchas iteraciones
//        double porcentajeAsignacion = (double) solucionActual.size() / pedidos.size() * 100;
//        if (porcentajeAsignacion < 20.0 && iteracion > 200) { // Menos del 20% después de 200 iteraciones
//            condiciones.add(String.format("CAPACIDAD_SISTEMA_EXCEDIDA:%.1f%%", porcentajeAsignacion));
//        }
//
//        // 5. Colapso por rutas inviables - SOLO si hay suficientes rutas asignadas
//        int rutasInvalidas = contarRutasInvalidas(solucionActual);
//        if (solucionActual.size() > 100) { // Solo evaluar si hay al menos 100 rutas
//            double porcentajeRutasInvalidas = (double) rutasInvalidas / solucionActual.size() * 100;
//            if (porcentajeRutasInvalidas > 40.0) { // Más del 40% de rutas inválidas
//                condiciones.add(String.format("RUTAS_INVIABLES:%d/%d (%.1f%%)",
//                        rutasInvalidas, solucionActual.size(), porcentajeRutasInvalidas));
//            }
//        }

        // 6. Colapso por congestión temporal - SOLO si es grave
//        if (detectarCongestionTemporalGrave()) {
//            condiciones.add("CONGESTION_TEMPORAL_GRAVE");
//        }
//
//        // ✅ REGISTRAR CONDICIONES PARA DEBUG (si hay alguna)
//        if (!condiciones.isEmpty() && Constantes.LOGGING_VERBOSO) {
//            System.out.println("🔍 Condiciones de colapso detectadas en iteración " + iteracion + ":");
//            for (String condicion : condiciones) {
//                System.out.println("   - " + condicion);
//            }
//        }

        // ✅ CRITERIOS MÁS ESTRICTOS PARA COLAPSO

        if (colapsoDetectado) {
            metricasColapso.put("condicionesColapso", condiciones);
            metricasColapso.put("iteracionColapso", iteracion);
            metricasColapso.put("tipoColapso", tipoColapso);

            System.out.println("🚨 COLAPSO DETECTADO - Tipo: " + tipoColapso + " en iteración " + iteracion);
            for (String condicion : condiciones) {
                System.out.println("   📍 " + condicion);
            }
        }

        return colapsoDetectado;
    }

    // ✅ CORRECCIÓN DEL MÉTODO contarAlmacenesLlenos
    private int contarAlmacenesLlenos() {
        int count = 0;
        for (Aeropuerto aeropuerto : aeropuertos) {
            // ✅ VERIFICACIÓN UNIFICADA - usar siempre la misma fuente de capacidad
            int capacidadActual = aeropuerto.getCapacidadActual();
            int capacidadMaxima = aeropuerto.getCapacidadMaxima();

            if (capacidadMaxima > 0) {
                double utilizacion = (double) capacidadActual / capacidadMaxima * 100;

                // ✅ MÁS SENSIBLE: 80% ya se considera "lleno" para detección de colapso
                if (utilizacion >= 95.0) {
                    if (Constantes.LOGGING_VERBOSO) {
                        System.out.println("📦 Almacén lleno: " + aeropuerto.getCodigoIATA() +
                                " - " + capacidadActual + "/" + capacidadMaxima +
                                " (" + String.format("%.1f%%", utilizacion) + ")");
                    }
                    count++;
                }
            }
        }
        return count;
    }

    // ✅ CORRECCIÓN DEL MÉTODO contarVuelosSaturados
    private int contarVuelosSaturados() {
        int count = 0;
        for (Vuelo vuelo : vuelos) {
            if (vuelo.getCapacidadMaxima() > 0) {
                double utilizacion = (double) vuelo.getCapacidadUsada() / vuelo.getCapacidadMaxima() * 100;
                if (utilizacion >= 90.0) { // 90% o más de capacidad
                    count++;
                }
            }
        }
        return count;
    }
    // ✅ NUEVO MÉTODO PARA CONTAR PEDIDOS CRÍTICOS TOTALES
    private int contarTotalPedidosCriticos() {
        int count = 0;
        LocalDateTime ahora = LocalDateTime.now();

        for (Pedido pedido : pedidos) {
            if (pedido.getFechaLimiteEntrega() != null && pedido.getPrioridad() > 0.7) {
                long horasHastaDeadline = ChronoUnit.HOURS.between(ahora, pedido.getFechaLimiteEntrega());
                if (horasHastaDeadline < 48) { // Menos de 48 horas
                    count++;
                }
            }
        }
        return count;
    }
    // ✅ CORRECCIÓN DEL MÉTODO contarPedidosCriticosNoAsignados
    private int contarPedidosCriticosNoAsignados() {
        int count = 0;
        LocalDateTime ahora = LocalDateTime.now();

        for (Pedido pedido : poolNoAsignados) {
            if (pedido.getFechaLimiteEntrega() != null && pedido.getPrioridad() > 0.7) {
                long horasHastaDeadline = ChronoUnit.HOURS.between(ahora, pedido.getFechaLimiteEntrega());
                if (horasHastaDeadline < 48) { // Menos de 48 horas (más realista)
                    count++;
                }
            }
        }
        return count;
    }

    private int contarRutasInvalidas(HashMap<Pedido, ArrayList<Vuelo>> solucionActual) {
        int count = 0;
        for (Map.Entry<Pedido, ArrayList<Vuelo>> entry : solucionActual.entrySet()) {
            if (!esRutaValida(entry.getKey(), entry.getValue())) {
                count++;
            }
        }
        return count;
    }

    // ✅ DETECCIÓN MÁS ESTRICTA DE CONGESTIÓN TEMPORAL
    private boolean detectarCongestionTemporalGrave() {
        if (ocupacionTemporalAlmacenes == null) return false;

        int periodosCongestionados = 0;
        final int TOTAL_MINUTOS = HORIZON_DAYS * 24 * 60;
        int totalPeriodos = (TOTAL_MINUTOS / 60) * aeropuertos.size();

        for (Aeropuerto aeropuerto : aeropuertos) {
            int[] ocupacion = ocupacionTemporalAlmacenes.get(aeropuerto);
            if (ocupacion == null) continue;

            for (int minuto = 0; minuto < TOTAL_MINUTOS; minuto += 60) { // Revisar cada hora
                if (minuto < ocupacion.length &&
                        ocupacion[minuto] > aeropuerto.getCapacidadMaxima() * 0.9) { // 90% de capacidad
                    periodosCongestionados++;
                }
            }
        }

        // Congestión grave si más del 20% de los periodos están congestionados
        double porcentajeCongestion = (double) periodosCongestionados / totalPeriodos * 100;
        return porcentajeCongestion > 20.0;
    }

    private void ejecutarIteracionALNSColapso(HashMap<Pedido, ArrayList<Vuelo>> solucionActual,
                                       int pesoActual, int iteracion) {
        int[] operadoresSeleccionados = seleccionarOperadores();
        int operadorDestruccion = operadoresSeleccionados[0];
        int operadorReparacion = operadoresSeleccionados[1];

        HashMap<Pedido, ArrayList<Vuelo>> solucionTemporal = new HashMap<>(solucionActual);

        // Aplicar destrucción y reparación
        Map<Vuelo, Integer> snapshotCapacidadesVuelos = crearSnapshotCapacidadesVuelos();
        Map<Aeropuerto, Integer> snapshotCapacidadAeropuertos = crearSnapshotCapacidadAeropuerto();

        ALNSDestruction.ResultadoDestruccion resultadoDestruccion =
                aplicarOperadorDestruccion(solucionTemporal, operadorDestruccion);

        if (resultadoDestruccion != null && !resultadoDestruccion.getPaquetesDestruidos().isEmpty()) {
            solucionTemporal = new HashMap<>(resultadoDestruccion.getSolucionParcial());
            reconstruirCapacidadesDesdeSolucion(solucionTemporal);
            reconstruirAlmacenesDesdeSolucion(solucionTemporal);

            ArrayList<Map.Entry<Pedido, ArrayList<Vuelo>>> paquetesExpandidos =
                    expandirConPaquetesNoAsignados(resultadoDestruccion.getPaquetesDestruidos(), 100);

            ALNSRepair.ResultadoReparacion resultadoReparacion =
                    aplicarOperadorReparacion(solucionTemporal, operadorReparacion, paquetesExpandidos);

            if (resultadoReparacion != null && resultadoReparacion.esExitoso()) {
                solucionTemporal = new HashMap<>(resultadoReparacion.getSolucionReparada());
                int pesoTemporal = calcularPesoSolucion(solucionTemporal);

                // Criterio de aceptación simplificado para modo colapso
                if (pesoTemporal > pesoActual ||
                        aleatorio.nextDouble() < Math.exp((pesoTemporal - pesoActual) / temperatura)) {
                    solucionActual.clear();
                    solucionActual.putAll(solucionTemporal);
                    pesoActual = pesoTemporal;

                    if (mejorSolucion.isEmpty() || pesoTemporal > mejorSolucion.values().iterator().next()) {
                        mejorSolucion.clear();
                        mejorSolucion.put(new HashMap<>(solucionActual), pesoActual);
                    }

                    // Actualizar pool de no asignados
                    actualizarPoolNoAsignados(solucionActual);
                } else {
                    restaurarVuelos(snapshotCapacidadesVuelos);
                    restaurarAeropuertos(snapshotCapacidadAeropuertos);
                }
            } else {
                restaurarVuelos(snapshotCapacidadesVuelos);
                restaurarAeropuertos(snapshotCapacidadAeropuertos);
            }
        }

        // Enfriamiento más lento para modo colapso
        if (iteracion % 50 == 0) {
            temperatura *= 0.99;
        }
    }

    private void actualizarMetricasColapso(HashMap<Pedido, ArrayList<Vuelo>> solucionActual,
                                           int iteracion) {
        metricasColapso.put("iteracion", iteracion);
        metricasColapso.put("pedidosAsignados", solucionActual.size());
        metricasColapso.put("pedidosNoAsignados", poolNoAsignados.size());
        metricasColapso.put("porcentajeAsignacion",
                (double) solucionActual.size() / pedidos.size() * 100);
        metricasColapso.put("almacenesLlenos", contarAlmacenesLlenos());
        metricasColapso.put("vuelosSaturados", contarVuelosSaturados());
        metricasColapso.put("temperatura", temperatura);
    }

    private void recolectarMetricasColapso() {
        LocalDateTime horaFin = LocalDateTime.now();
        long duracionSegundos = ChronoUnit.SECONDS.between(horaInicioColapso, horaFin);

        metricasColapso.put("horaInicio", horaInicioColapso);
        metricasColapso.put("horaFin", horaFin);
        metricasColapso.put("duracionSegundos", duracionSegundos);
        metricasColapso.put("iteracionesTotales", iteracionesColapso);
        metricasColapso.put("pedidosTotales", pedidos.size());

        // Estadísticas finales de la solución
        if (!mejorSolucion.isEmpty()) {
            HashMap<Pedido, ArrayList<Vuelo>> solucionFinal = mejorSolucion.keySet().iterator().next();
            metricasColapso.put("pedidosAsignadosFinal", solucionFinal.size());
            metricasColapso.put("pesoSolucionFinal", mejorSolucion.values().iterator().next());
        }

        // Análisis de bottlenecks
        analizarBottlenecks();
    }

    private void analizarBottlenecks() {
        // Identificar los cuellos de botella principales
        List<String> bottlenecks = new ArrayList<>();

        // Aeropuertos más congestionados
        aeropuertos.stream()
                .filter(a -> (double) a.getCapacidadActual() / a.getCapacidadMaxima() > 0.8)
                .sorted((a1, a2) -> Double.compare(
                        (double) a2.getCapacidadActual() / a2.getCapacidadMaxima(),
                        (double) a1.getCapacidadActual() / a1.getCapacidadMaxima()
                ))
                .limit(5)
                .forEach(a -> bottlenecks.add("AEROPUERTO_LLENO:" + a.getCodigoIATA() +
                        ":" + String.format("%.1f%%",
                        (double) a.getCapacidadActual() / a.getCapacidadMaxima() * 100)));

        // Vuelos más saturados
        vuelos.stream()
                .filter(v -> v.getCapacidadUsada() > 0)
                .sorted((v1, v2) -> Double.compare(
                        (double) v2.getCapacidadUsada() / v2.getCapacidadMaxima(),
                        (double) v1.getCapacidadUsada() / v1.getCapacidadMaxima()
                ))
                .limit(5)
                .forEach(v -> bottlenecks.add("VUELO_SATURADO:" +
                        v.getAeropuertoOrigen().getCodigoIATA() + "-" +
                        v.getAeropuertoDestino().getCodigoIATA() + ":" +
                        String.format("%.1f%%",
                                (double) v.getCapacidadUsada() / v.getCapacidadMaxima() * 100)));

        metricasColapso.put("bottlenecks", bottlenecks);
    }

    // Método para obtener las métricas del colapso
    public Map<String, Object> getMetricasColapso() {
        return new HashMap<>(metricasColapso);
    }
    //===============================FIN DE METODOS PARA COLAPSO===========================================

    // ==================== GETTERS PÚBLICOS PARA ACCEDER A LA SOLUCIÓN ====================

    /**
     * Obtiene la mejor solución encontrada por el algoritmo ALNS
     * @return HashMap con la solución: Pedido -> Lista de Vuelos asignados
     */
    public HashMap<Pedido, ArrayList<Vuelo>> getMejorSolucion() {
        if (mejorSolucion == null || mejorSolucion.isEmpty()) {
            return new HashMap<>();
        }
        // La estructura es HashMap<HashMap<Pedido, ArrayList<Vuelo>>, Integer>
        // donde Integer es el peso. Retornamos solo el HashMap interno
        return mejorSolucion.keySet().iterator().next();
    }

    /**
     * Obtiene el peso (fitness) de la mejor solución
     * @return Peso de la solución (mayor es mejor)
     */
    public Integer getPesoMejorSolucion() {
        if (mejorSolucion == null || mejorSolucion.isEmpty()) {
            return 0;
        }
        return mejorSolucion.values().iterator().next();
    }

    /**
     * Obtiene el tiempo inicial de referencia (T0) usado en la simulación
     * @return LocalDateTime con el tiempo T0
     */
    public LocalDateTime getT0() {
        return this.T0;
    }

    /**
     * Obtiene la lista de pedidos originales
     * @return Lista de pedidos
     */
    public List<Pedido> getPedidosOriginales() {
        return this.pedidosOriginales;
    }

    /**
     * Obtiene la lista de pedidos procesados (potencialmente unitizados)
     * @return Lista de pedidos
     */
    public List<Pedido> getPedidos() {
        return this.pedidos;
    }

    /**
     * Obtiene los pedidos que no fueron asignados en la solución
     * @return Lista de pedidos no asignados
     */
    public ArrayList<Pedido> getPedidosNoAsignados() {
        HashMap<Pedido, ArrayList<Vuelo>> solucion = getMejorSolucion();
        ArrayList<Pedido> noAsignados = new ArrayList<>();
        
        for (Pedido pedido : this.pedidos) {
            if (!solucion.containsKey(pedido)) {
                noAsignados.add(pedido);
            }
        }
        
        return noAsignados;
    }
    //METODO PARA AGREGAR AEROPUERTOS ORIGEN
    private void asignarAeropuertosOrigen(){
        for(Pedido pedido : pedidos){
            pedido.setAeropuertoOrigenCodigo(colocarAeropuertoPrincipalAleatorio(pedido.getAeropuertoDestinoCodigo()));
        }
//        // A) Completar en pedidosOriginales
//        for (Pedido p : pedidosOriginales) {
//            if (p.getAeropuertoOrigenCodigo() == null || p.getAeropuertoOrigenCodigo().isBlank()) {
//                p.setAeropuertoOrigenCodigo(colocarAeropuertoPrincipalAleatorio(p.getAeropuertoDestinoCodigo()));
//            }
//        }
//        // B) Completar en pedidos (las unidades creadas en DataLoader)
//        for (Pedido p : pedidos) {
//            if (p.getAeropuertoOrigenCodigo() == null || p.getAeropuertoOrigenCodigo().isBlank()) {
//                p.setAeropuertoOrigenCodigo(colocarAeropuertoPrincipalAleatorio(p.getAeropuertoDestinoCodigo()));
//            }
//        }
    }
    // Método auxiliar para encontrar aeropuerto por defecto
    private String colocarAeropuertoPrincipalAleatorio(String codigoDestino) {
        // Lista de códigos IATA de los aeropuertos principales de MoraPack
        String[] aeropuertosPrincipales = {"SPIM", "UBBB", "EBCI"};
        ArrayList<String> aeropuertos = new ArrayList<>();

        Random random = new Random();
        for (int i = 0; i < 3; i++) {
            if(Objects.equals(codigoDestino, aeropuertosPrincipales[i]))
                continue;
            aeropuertos.add(aeropuertosPrincipales[i]);
        }
        int indiceAleatorio = random.nextInt(aeropuertos.size());
        String codigoIATAOrigen = aeropuertos.get(indiceAleatorio);
        //System.out.println("🔀 Usando aeropuerto por defecto: " + codigoIATAOrigen);
        return codigoIATAOrigen;
    }
    
    /**
     * Obtiene la solución a nivel de producto.
     * Convierte la solución interna (Pedido -> Rutas) a formato producto-level.
     * Siguiendo patrón de MoraPack-Backend.
     * 
     * @return Map<Producto, ArrayList<Vuelo>> con la ruta de cada producto individual
     */
    public Map<Producto, ArrayList<Vuelo>> obtenerSolucionNivelProducto() {
        Map<Producto, ArrayList<Vuelo>> solucionProductos = new HashMap<>();
        
        // Convertir desde la mejor solución de pedidos a productos
        if (mejorSolucion != null && !mejorSolucion.isEmpty()) {
            for (HashMap<Pedido, ArrayList<Vuelo>> rutasPedidos : mejorSolucion.keySet()) {
                for (Map.Entry<Pedido, ArrayList<Vuelo>> entry : rutasPedidos.entrySet()) {
                    Pedido pedido = entry.getKey();
                    ArrayList<Vuelo> ruta = entry.getValue();
                    
                    // Si el pedido tiene productos, asignar la misma ruta a cada uno
                    if (pedido.getProductos() != null && !pedido.getProductos().isEmpty()) {
                        for (Producto producto : pedido.getProductos()) {
                            solucionProductos.put(producto, new ArrayList<>(ruta));
                        }
                    } else {
                        // Si no tiene productos, crear uno por defecto
                        Producto productoDefault = new Producto();
                        productoDefault.setId(pedido.getId() * 1000); // ID derivado
                        productoDefault.setPedido(pedido);
                        productoDefault.setNombre("Producto-Default-" + pedido.getId());
                        solucionProductos.put(productoDefault, new ArrayList<>(ruta));
                    }
                }
            }
        }
        
        return solucionProductos;
    }
}
