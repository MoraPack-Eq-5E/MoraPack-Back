package com.grupo5e.morapack.algorithm.alns2;

import com.grupo5e.morapack.algorithm.alns.RutaConTiempos;
import com.grupo5e.morapack.algorithm.alns.TramoConTiempo;
import com.grupo5e.morapack.core.constants.Constantes;
import com.grupo5e.morapack.core.enums.EstadoInstanciaVuelo;
import com.grupo5e.morapack.core.enums.EstadoProducto;
import com.grupo5e.morapack.core.model.*;
import com.grupo5e.morapack.service.AeropuertoService;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.System.exit;

/**
 * Clase que implementa operadores de reparación para el algoritmo ALNS
 * (Adaptive Large Neighborhood Search) específicamente diseñados para el problema
 * de logística MoraPack.
 *
 * Los operadores priorizan las entregas a tiempo y la eficiencia de rutas.
 */
public class ALNSRepair2 {

    private ArrayList<Aeropuerto> aeropuertos;
    private ArrayList<Vuelo> vuelos;
    private HashMap<Aeropuerto, Integer> ocupacionAlmacenes;
    private Random aleatorio;
    private Map<String, InstanciaVuelo> instanciaCache;
    private Map<String, ProductAssignment> assignmentCache;

    private final CapacidadInstanciasManager capacidadManager;
    private final LocalDateTime T0;

    private final AeropuertoService aeropuertoService;

    /**
     * Constructor simplificado sin dependencias de Spring.
     * Usado por ALNSSolver con FuenteDatosInput modular.
     */
    public ALNSRepair2(ArrayList<Aeropuerto> aeropuertos, ArrayList<Vuelo> vuelos,
                       HashMap<Aeropuerto, Integer> ocupacionAlmacenes, Map<String, InstanciaVuelo> instanciaCache,
                       CapacidadInstanciasManager capacidadManager, LocalDateTime T0,
                       Map<String, ProductAssignment> assignmentCache) {
        this.aeropuertos = aeropuertos;
        this.vuelos = vuelos;
        this.ocupacionAlmacenes = ocupacionAlmacenes;
        this.aeropuertoService = null; // No se usa en la implementación actual
        this.aleatorio = new Random(System.currentTimeMillis());
        this.instanciaCache = instanciaCache;
        this.assignmentCache = assignmentCache;

        this.capacidadManager = capacidadManager;
        this.T0 = T0;

        // VERIFICACIÓN DE DATOS
        System.out.println("ALNSRepair inicializado con:");
        System.out.println("  - Aeropuertos: " + this.aeropuertos.size());
        System.out.println("  - Vuelos: " + this.vuelos.size());
        System.out.println("  - Ocupación almacenes: " + this.ocupacionAlmacenes.size());
        System.out.println("  - Instancias en caché: " + this.instanciaCache.size());

        if (this.vuelos.isEmpty()) {
            System.err.println("❌ ALERTA: ALNSRepair recibió lista de vuelos vacía");
        }
    }

    /**
     * Reparación Greedy Mejorada: Inserta paquetes usando enfoque optimizado para MoraPack.
     * Prioriza paquetes por deadline y eficiencia de ruta específica para el negocio.
     */
    public ResultadoReparacion reparacionCodiciosa(
            HashMap<Pedido, ArrayList<Vuelo>> solucionParcial,
            ArrayList<Map.Entry<Pedido, ArrayList<Vuelo>>> paquetesDestruidos) {

//        System.out.println("\n=== INICIANDO REPARACIÓN GREEDY ===");
//        System.out.println("Paquetes a reparar: " + paquetesDestruidos.size());
//        System.out.println("Solución parcial inicial: " + solucionParcial.size() + " paquetes");

        HashMap<Pedido, ArrayList<Vuelo>> solucionReparada = new HashMap<>(solucionParcial);
        ArrayList<Pedido> paquetesNoAsignados = new ArrayList<>();

        // Ordenamiento inteligente específico para MoraPack
        ArrayList<Pedido> paquetesParaReparar = new ArrayList<>();
        for (Map.Entry<Pedido, ArrayList<Vuelo>> entrada : paquetesDestruidos) {
            paquetesParaReparar.add(entrada.getKey());
        }

        paquetesParaReparar.sort((p1, p2) -> {
            // 1. Priorizar por urgencia (tiempo restante vs promesa MoraPack)
            double urgencia1 = calcularUrgenciaPaquete(p1);
            double urgencia2 = calcularUrgenciaPaquete(p2);
            int comparacionUrgencia = Double.compare(urgencia2, urgencia1); // Mayor urgencia primero
            if (comparacionUrgencia != 0) return comparacionUrgencia;

            // 2. Priorizar paquetes con más productos (mayor valor de negocio)
            int productos1 = p1.getProductos() != null ? p1.getProductos().size() : 1;
            int productos2 = p2.getProductos() != null ? p2.getProductos().size() : 1;
            int comparacionProductos = Integer.compare(productos2, productos1);
            if (comparacionProductos != 0) return comparacionProductos;

            // 3. Tie-break por deadline absoluto
            LocalDateTime d1 = p1.getFechaLimiteEntrega();
            LocalDateTime d2 = p2.getFechaLimiteEntrega();
            if (d1 == null && d2 == null) return 0;
            if (d1 == null) return 1; // nulls last
            if (d2 == null) return -1; // nulls last
            return d1.compareTo(d2);
        });

        int conteoReinsertados = 0;

        // Intentar reinsertar cada paquete
        for (Pedido pedido : paquetesParaReparar) {
            Aeropuerto aeropuertoDestino = obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo());

            // Obtener conteo de productos para este pedido
            int conteoProductos = pedido.getProductos() != null ? pedido.getProductos().size() : 1;
            //System.out.println("Productos: " + conteoProductos);

            // Verificar capacidad del almacén
            if (aeropuertoDestino == null) {
                System.out.println("❌ ERROR: Aeropuerto destino no encontrado: " + pedido.getAeropuertoDestinoCodigo());
                exit(1);
            }

            if (!tieneCapacidadAlmacen(aeropuertoDestino, conteoProductos)) {
                paquetesNoAsignados.add(pedido);
                continue;
            }

            // Buscar la mejor ruta
            ArrayList<Vuelo> mejorRuta = encontrarMejorRuta(pedido);

            if (mejorRuta == null) {
                //System.out.println("❌ No se encontró ninguna ruta válida");
                paquetesNoAsignados.add(pedido);
                continue;
            }

            // Validar ruta
            if (!esRutaValida(pedido, mejorRuta, conteoProductos)) {
                //System.out.println("❌ Ruta no válida (no cumple validaciones)");
                paquetesNoAsignados.add(pedido);
                continue;
            }
            // Construir tiempos reales de la ruta
            RutaConTiempos tiempos = calcularTiemposRuta(pedido,mejorRuta);
            if (!seRespetaDeadlineConTiempos(pedido, tiempos)) {
                paquetesNoAsignados.add(pedido);
                continue;
            }

            if (!esRutaValida(pedido, mejorRuta, conteoProductos)) {
                paquetesNoAsignados.add(pedido);
                continue;
            }
            // Reservar capacidad en instancias y crear assignments
            reservarCapacidadEnInstancias(tiempos, pedido, T0);
            solucionReparada.put(pedido, mejorRuta);
            actualizarCapacidadAeropuertos(aeropuertoDestino.getCodigoIATA(), conteoProductos);
            conteoReinsertados++;
        }
        System.out.println("Reparación Greedy: " + conteoReinsertados + "/" + paquetesParaReparar.size() +
                          " paquetes reinsertados");

        return new ResultadoReparacion(solucionReparada, paquetesNoAsignados);
    }

    /**
     * Reparación por Regret: Calcula el "arrepentimiento" de no insertar cada paquete
     * y prioriza aquellos con mayor diferencia entre mejor y segunda mejor opción.
     */
    public ResultadoReparacion reparacionArrepentimiento(
            HashMap<Pedido, ArrayList<Vuelo>> solucionParcial,
            ArrayList<Map.Entry<Pedido, ArrayList<Vuelo>>> paquetesDestruidos,
            int nivelArrepentimiento) {

        HashMap<Pedido, ArrayList<Vuelo>> solucionReparada = new HashMap<>(solucionParcial);
        ArrayList<Pedido> paquetesNoAsignados = new ArrayList<>();

        ArrayList<Pedido> paquetesRestantes = new ArrayList<>();
        for (Map.Entry<Pedido, ArrayList<Vuelo>> entrada : paquetesDestruidos) {
            paquetesRestantes.add(entrada.getKey());
        }

        int conteoReinsertados = 0;

        // Mientras haya paquetes por insertar
        while (!paquetesRestantes.isEmpty()) {
            Pedido mejorPedido = null;
            ArrayList<Vuelo> mejorRuta = null;
            double maxArrepentimiento = Double.NEGATIVE_INFINITY;

            // Calcular arrepentimiento para cada paquete restante
            for (Pedido pedido : paquetesRestantes) {
                Aeropuerto aeropuertoDestino = obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo());
                int conteoProductos = pedido.getProductos() != null ? pedido.getProductos().size() : 1;

                if (aeropuertoDestino == null) {
                    System.out.println("❌ Aeropuerto destino no encontrado para pedido: " + pedido.getId() + " " + pedido.getAeropuertoDestinoCodigo());
                    exit(1);
                }
                if(!tieneCapacidadAlmacen(aeropuertoDestino,conteoProductos)){
                    continue;
                }
                // Encontrar las mejores rutas para este pedido
                ArrayList<OpcionRuta> opcionesRuta = encontrarTodasLasOpcionesRuta(pedido);

                if (opcionesRuta.isEmpty()) {
                    continue;
                }

                // Ordenar por margen de tiempo (mejor primero)
                opcionesRuta.sort((r1, r2) -> Double.compare(r2.margenTiempo, r1.margenTiempo));

                // Calcular regret-k real
                double arrepentimiento = 0;
                int k = Math.max(2, nivelArrepentimiento);
                int limite = Math.min(k, opcionesRuta.size());
                if (limite >= 2) {
                    double mejorMargen = opcionesRuta.get(0).margenTiempo;
                    for (int i = 1; i < limite; i++) {
                        arrepentimiento += (mejorMargen - opcionesRuta.get(i).margenTiempo);
                    }
                } else if (opcionesRuta.size() == 1) {
                    // Solo una opción: usar urgencia basada en orderDate→deadline
                    if (pedido.getFechaPedido() != null && pedido.getFechaLimiteEntrega() != null) {
                        long horasHastaDeadline = ChronoUnit.HOURS.between(pedido.getFechaPedido(), pedido.getFechaLimiteEntrega());
                        arrepentimiento = Math.max(0, 72 - Math.min(72, horasHastaDeadline));
                    } else {
                        arrepentimiento = 0;
                    }
                }

                // Añadir factor de urgencia al arrepentimiento
                LocalDateTime ahora = LocalDateTime.now();
                long horasHastaDeadline = ChronoUnit.HOURS.between(ahora, pedido.getFechaLimiteEntrega());
                double factorUrgencia = Math.max(1, 72.0 / Math.max(1, horasHastaDeadline));
                arrepentimiento *= factorUrgencia;

                if (arrepentimiento > maxArrepentimiento) {
                    maxArrepentimiento = arrepentimiento;
                    mejorPedido = pedido;
                    mejorRuta = opcionesRuta.get(0).ruta;
                }
            }

            // Insertar el paquete con mayor arrepentimiento
            if (mejorPedido != null && mejorRuta != null && esRutaValida(mejorPedido, mejorRuta, Math.max(1, mejorPedido.getProductos() != null ? mejorPedido.getProductos().size() : 1))) {
                int conteoProductos = mejorPedido.getProductos() != null ? mejorPedido.getProductos().size() : 1;
                // Construir tiempos reales de la ruta
                RutaConTiempos tiempos = calcularTiemposRuta(mejorPedido,mejorRuta);
                if (!seRespetaDeadlineConTiempos(mejorPedido, tiempos)) {
                    paquetesNoAsignados.add(mejorPedido);
                    continue;
                }

                if (!esRutaValida(mejorPedido, mejorRuta, conteoProductos)) {
                    paquetesNoAsignados.add(mejorPedido);
                    continue;
                }
                // Reservar capacidad en instancias y crear assignments
                reservarCapacidadEnInstancias(tiempos, mejorPedido, T0);
                solucionReparada.put(mejorPedido, mejorRuta);
                actualizarCapacidadAeropuertos(mejorPedido.getAeropuertoDestinoCodigo(), conteoProductos);
                paquetesRestantes.remove(mejorPedido);
                conteoReinsertados++;
            } else {
                // No se pudo insertar ningún paquete, agregar todos los restantes como no asignados
                paquetesNoAsignados.addAll(paquetesRestantes);
                break;
            }
        }

        System.out.println("Reparación por Arrepentimiento: " + conteoReinsertados + "/" + paquetesDestruidos.size() +
                          " paquetes reinsertados");

        return new ResultadoReparacion(solucionReparada, paquetesNoAsignados);
    }
    /**
     * Reparación por tiempo: Prioriza paquetes con deadlines más cercanos.
     */
    public ResultadoReparacion reparacionPorTiempo(
            HashMap<Pedido, ArrayList<Vuelo>> solucionParcial,
            ArrayList<Map.Entry<Pedido, ArrayList<Vuelo>>> paquetesDestruidos) {

        HashMap<Pedido, ArrayList<Vuelo>> solucionReparada = new HashMap<>(solucionParcial);
        ArrayList<Pedido> paquetesNoAsignados = new ArrayList<>();

        // Extraer paquetes y ordenar por urgencia (deadline más cercano primero)
        ArrayList<Pedido> paquetesParaReparar = new ArrayList<>();
        for (Map.Entry<Pedido, ArrayList<Vuelo>> entrada : paquetesDestruidos) {
            paquetesParaReparar.add(entrada.getKey());
        }

        paquetesParaReparar.sort((p1, p2) -> {
            // Ordenar por presupuesto real desde orderDate (nulls last)
            if (p1.getFechaPedido() == null || p1.getFechaLimiteEntrega() == null) return 1;
            if (p2.getFechaPedido() == null || p2.getFechaLimiteEntrega() == null) return -1;
            long horasP1 = ChronoUnit.HOURS.between(p1.getFechaPedido(), p1.getFechaLimiteEntrega());
            long horasP2 = ChronoUnit.HOURS.between(p2.getFechaPedido(), p2.getFechaLimiteEntrega());
            return Long.compare(horasP1, horasP2);
        });

        int conteoReinsertados = 0;

        // Insertar paquetes en orden de urgencia
        for (Pedido pedido : paquetesParaReparar) {
            Aeropuerto aeropuertoDestino = obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo());
            if (aeropuertoDestino == null) {
                System.out.println("❌ Aeropuerto destino no encontrado para pedido: " + pedido.getId() + " " + pedido.getAeropuertoDestinoCodigo());
                exit(1);
            }
            int conteoProductos = pedido.getProductos() != null ? pedido.getProductos().size() : 1;

            if (!tieneCapacidadAlmacen(aeropuertoDestino, conteoProductos)) {
                paquetesNoAsignados.add(pedido);
                continue;
            }

            // Buscar ruta con mayor margen de tiempo
            ArrayList<Vuelo> mejorRuta = encontrarRutaConMaximoMargen(pedido);

//            if (mejorRuta != null && !esRutaValida(pedido, mejorRuta, conteoProductos)) {
//                System.out.println("⚠️ Ruta no válida para pedido: " + pedido.getId());
//            }
//            if (mejorRuta == null) {
//                System.out.println("⚠️ No se encontró ruta para pedido: " + pedido.getAeropuertoOrigenCodigo() +
//                        " → " + pedido.getAeropuertoDestinoCodigo());
//            }
            if (mejorRuta != null && esRutaValida(pedido, mejorRuta, conteoProductos)) {
                // Construir tiempos reales de la ruta
                RutaConTiempos tiempos = calcularTiemposRuta(pedido,mejorRuta);
                if (!seRespetaDeadlineConTiempos(pedido, tiempos)) {
                    paquetesNoAsignados.add(pedido);
                    continue;
                }

                if (!esRutaValida(pedido, mejorRuta, conteoProductos)) {
                    paquetesNoAsignados.add(pedido);
                    continue;
                }
                // Reservar capacidad en instancias y crear assignments
                reservarCapacidadEnInstancias(tiempos, pedido, T0);
                solucionReparada.put(pedido, mejorRuta);
                actualizarCapacidadAeropuertos(aeropuertoDestino.getCodigoIATA(), conteoProductos);
                conteoReinsertados++;
            } else {
                paquetesNoAsignados.add(pedido);
            }
        }

        System.out.println("Reparación por tiempo: " + conteoReinsertados + "/" + paquetesParaReparar.size() +
                          " paquetes reinsertados");

        return new ResultadoReparacion(solucionReparada, paquetesNoAsignados);
    }

    /**
     * Reparación por capacidad: Prioriza rutas con mayor capacidad disponible.
     */
    public ResultadoReparacion reparacionPorCapacidad(
            HashMap<Pedido, ArrayList<Vuelo>> solucionParcial,
            ArrayList<Map.Entry<Pedido, ArrayList<Vuelo>>> paquetesDestruidos) {

        HashMap<Pedido, ArrayList<Vuelo>> solucionReparada = new HashMap<>(solucionParcial);
        ArrayList<Pedido> paquetesNoAsignados = new ArrayList<>();

        ArrayList<Pedido> paquetesParaReparar = new ArrayList<>();
        for (Map.Entry<Pedido, ArrayList<Vuelo>> entrada : paquetesDestruidos) {
            paquetesParaReparar.add(entrada.getKey());
        }

        // Ordenar por deadline como criterio secundario
        // PATCH: Null-safe sorting
        paquetesParaReparar.sort((p1, p2) -> {
            if (p1.getFechaLimiteEntrega() == null && p2.getFechaLimiteEntrega() == null) return 0;
            if (p1.getFechaLimiteEntrega() == null) return 1; // nulls last
            if (p2.getFechaLimiteEntrega() == null) return -1; // nulls last
            return p1.getFechaLimiteEntrega().compareTo(p2.getFechaLimiteEntrega());
        });

        int conteoReinsertados = 0;

        for (Pedido pedido : paquetesParaReparar) {
            Aeropuerto aeropuertoDestino = obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo());
            int conteoProductos = pedido.getProductos() != null ? pedido.getProductos().size() : 1;

            if (aeropuertoDestino == null) {
                System.out.println("❌ Aeropuerto destino no encontrado para pedido: " + pedido.getId() + " " + pedido.getAeropuertoDestinoCodigo());
                exit(1);
            }
            if(!tieneCapacidadAlmacen(aeropuertoDestino, conteoProductos)) {
                paquetesNoAsignados.add(pedido);
                continue;
            }

            // Buscar ruta con mayor capacidad disponible
            ArrayList<Vuelo> mejorRuta = encontrarRutaConMaximaCapacidad(pedido);
            if (mejorRuta != null && esRutaValida(pedido, mejorRuta, conteoProductos)) {
                // Construir tiempos reales de la ruta
                RutaConTiempos tiempos = calcularTiemposRuta(pedido,mejorRuta);
                if (!seRespetaDeadlineConTiempos(pedido, tiempos)) {
                    paquetesNoAsignados.add(pedido);
                    continue;
                }

                if (!esRutaValida(pedido, mejorRuta, conteoProductos)) {
                    paquetesNoAsignados.add(pedido);
                    continue;
                }
                // Reservar capacidad en instancias y crear assignments
                reservarCapacidadEnInstancias(tiempos, pedido, T0);
                solucionReparada.put(pedido, mejorRuta);
                actualizarCapacidadAeropuertos(aeropuertoDestino.getCodigoIATA(), conteoProductos);
                conteoReinsertados++;
            } else {
                paquetesNoAsignados.add(pedido);
            }
        }

        System.out.println("Reparación por capacidad: " + conteoReinsertados + "/" + paquetesParaReparar.size() +
                          " paquetes reinsertados");

        return new ResultadoReparacion(solucionReparada, paquetesNoAsignados);
    }
    // ================= MÉTODOS AUXILIARES =================
    /**
     * Calcula la urgencia de un pedido específico para MoraPack
     * Considera promesas de entrega según continentes y tiempo restante
     */
    private double calcularUrgenciaPaquete(Pedido pedido) {
        if (pedido.getFechaPedido() == null || pedido.getFechaLimiteEntrega() == null) {
            return 0.0; // Sin información de tiempo, baja prioridad
        }

        // Calcular tiempo disponible desde orden hasta deadline
        long totalHorasDisponibles = ChronoUnit.HOURS.between(pedido.getFechaPedido(), pedido.getFechaLimiteEntrega());

        // Determinar promesa MoraPack según continentes
        boolean rutaMismoContinente = obtenerAeropuerto(pedido.getAeropuertoOrigenCodigo()).getCiudad().getContinente() ==
                                    obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo()).getCiudad().getContinente();
        long promesaHorasMoraPack = rutaMismoContinente ? 48 : 72; // 2 días intra / 3 días inter

        // Calcular factor de urgencia
        double factorUrgencia;
        if (totalHorasDisponibles <= promesaHorasMoraPack * 0.5) {
            // Muy urgente: menos de la mitad del tiempo de promesa disponible
            factorUrgencia = 10.0;
        } else if (totalHorasDisponibles <= promesaHorasMoraPack * 0.75) {
            // Urgente: menos del 75% del tiempo de promesa disponible
            factorUrgencia = 5.0;
        } else if (totalHorasDisponibles <= promesaHorasMoraPack) {
            // Moderadamente urgente: dentro del tiempo de promesa
            factorUrgencia = 3.0;
        } else if (totalHorasDisponibles <= promesaHorasMoraPack * 1.5) {
            // Tiempo holgado: 50% más tiempo que la promesa
            factorUrgencia = 1.0;
        } else {
            // Mucho tiempo disponible
            factorUrgencia = 0.5;
        }

        // Ajustar por prioridad del pedido (si está disponible)
        if (pedido.getPrioridad() > 0) {
            factorUrgencia *= (1.0 + pedido.getPrioridad() / 10.0); // Boost por prioridad
        }

        // Penalizar si excede promesa MoraPack
        if (totalHorasDisponibles > promesaHorasMoraPack * 1.2) {
            factorUrgencia *= 0.8; // Reducir prioridad para paquetes con demasiado tiempo
        }

        return factorUrgencia;
    }

    private ArrayList<OpcionRuta> encontrarTodasLasOpcionesRuta(Pedido pedido) {
        ArrayList<OpcionRuta> opciones = new ArrayList<>();
        String codigoOrigen = pedido.getAeropuertoOrigenCodigo();
        String codigoDestino = pedido.getAeropuertoDestinoCodigo();

        //System.out.println("\n    🔍 BUSCANDO RUTAS PARA: " + codigoOrigen + " → " + codigoDestino);

        // VERIFICAR DATOS PRIMERO
        //verificarVuelosDisponibles(codigoOrigen, codigoDestino);

        if (codigoOrigen.equals(codigoDestino)) {
            //System.out.println("    ✅ MISMO AEROPUERTO - RUTA VACÍA");
            opciones.add(new OpcionRuta(new ArrayList<>(), Double.MAX_VALUE));
            return opciones;
        }

        // Buscar ruta directa
        //System.out.println("\n    --- BUSCANDO RUTA DIRECTA ---");
        ArrayList<Vuelo> rutaDirecta = encontrarRutaDirecta(codigoOrigen, codigoDestino);
        if (rutaDirecta != null && esRutaValida(pedido, rutaDirecta)) {
            double margen = calcularMargenTiempoRuta(pedido, rutaDirecta);
            opciones.add(new OpcionRuta(rutaDirecta, margen));
            //System.out.println("    ✅ RUTA DIRECTA VÁLIDA - Margen: " + margen);
        }

        // Buscar rutas con una escala
        //System.out.println("\n    --- BUSCANDO RUTA CON 1 ESCALA ---");
        ArrayList<Vuelo> rutaUnaEscala = encontrarRutaUnaEscala(codigoOrigen, codigoDestino);
        if (rutaUnaEscala != null && esRutaValida(pedido, rutaUnaEscala)) {
            double margen = calcularMargenTiempoRuta(pedido, rutaUnaEscala);
            opciones.add(new OpcionRuta(rutaUnaEscala, margen));
            //System.out.println("    ✅ RUTA CON 1 ESCALA VÁLIDA - Margen: " + margen);
        }

        // Buscar rutas con dos escalas
        //System.out.println("\n    --- BUSCANDO RUTA CON 2 ESCALAS ---");
        ArrayList<Vuelo> rutaDosEscalas = encontrarRutaDosEscalas(codigoOrigen, codigoDestino);
        if (rutaDosEscalas != null && esRutaValida(pedido, rutaDosEscalas)) {
            double margen = calcularMargenTiempoRuta(pedido, rutaDosEscalas);
            opciones.add(new OpcionRuta(rutaDosEscalas, margen));
            //System.out.println("    ✅ RUTA CON 2 ESCALAS VÁLIDA - Margen: " + margen);
        }

        //System.out.println("    📊 TOTAL OPCIONES ENCONTRADAS: " + opciones.size());
        return opciones;
    }

    private ArrayList<Vuelo> encontrarMejorRuta(Pedido pedido) {
        ArrayList<OpcionRuta> opciones = encontrarTodasLasOpcionesRuta(pedido);
        if (opciones.isEmpty()) return null;

        // Seleccionar la ruta con mayor margen de tiempo
        opciones.sort((r1, r2) -> Double.compare(r2.margenTiempo, r1.margenTiempo));
        return opciones.get(0).ruta;
    }

    private ArrayList<Vuelo> encontrarRutaConMaximoMargen(Pedido pedido) {
        return encontrarMejorRuta(pedido); // Ya implementado arriba
    }

    private ArrayList<Vuelo> encontrarRutaConMaximaCapacidad(Pedido pedido) {
        ArrayList<OpcionRuta> opciones = encontrarTodasLasOpcionesRuta(pedido);
        if (opciones.isEmpty()) return null;

        // Calcular capacidad disponible para cada ruta
        ArrayList<OpcionCapacidadRuta> opcionesCapacidad = new ArrayList<>();
        for (OpcionRuta opcion : opciones) {
            double capacidadTotal = 0;
            double capacidadUsada = 0;

            for (Vuelo vuelo : opcion.ruta) {
                capacidadTotal += vuelo.getCapacidadMaxima();
                capacidadUsada += vuelo.getCapacidadUsada();
            }

            double ratioCapacidadDisponible = (capacidadTotal - capacidadUsada) / Math.max(1, capacidadTotal);
            opcionesCapacidad.add(new OpcionCapacidadRuta(opcion.ruta, ratioCapacidadDisponible, opcion.margenTiempo));
        }

        // Ordenar por capacidad disponible, pero considerando también el margen de tiempo
        opcionesCapacidad.sort((r1, r2) -> {
            // Priorizar rutas con capacidad disponible, pero no sacrificar entregas a tiempo
            if (r1.margenTiempo <= 0 && r2.margenTiempo > 0) return 1;
            if (r2.margenTiempo <= 0 && r1.margenTiempo > 0) return -1;

            return Double.compare(r2.capacidadDisponible, r1.capacidadDisponible);
        });

        return opcionesCapacidad.get(0).ruta;
    }
    /**
     * PATCH: Calcula margen sin doble conteo y usando fechaPedido
     */
    private double calcularMargenTiempoRuta(Pedido pedido, ArrayList<Vuelo> ruta) {
        if (ruta == null || ruta.isEmpty()) {
            return 0.0;
        }

        // Sumar solo tiempo de vuelos + conexiones (sin extras por continente)
        double tiempoTotal = 0.0;
        for (Vuelo vuelo : ruta) {
            tiempoTotal += vuelo.getTiempoTransporte();
        }

        // Agregar tiempo de conexión (2 horas por conexión)
        tiempoTotal += (ruta.size() - 1) * 2.0;

        // PATCH: Usar fechaPedido vs deadline, null-safe
        if (pedido.getFechaPedido() == null || pedido.getFechaLimiteEntrega() == null) {
            return 1.0;
        }

        // Calcular presupuesto de horas desde fechaPedido
        long presupuesto = ChronoUnit.HOURS.between(pedido.getFechaPedido(), pedido.getFechaLimiteEntrega());
        if (presupuesto < 0) presupuesto = 0; // Clamp negativo

        double margen = presupuesto - tiempoTotal;
        return Math.max(margen, 0.0) + 1.0;
    }

    /**
     * PATCH: Helper para validar capacidad de ruta con cantidad específica
     */
    private boolean cabeEnCapacidadRuta(ArrayList<Vuelo> ruta, int cantidad) {
        if (ruta == null) return false;
        for (Vuelo f : ruta) {
            if (f.getCapacidadUsada() + cantidad > f.getCapacidadMaxima()) return false;
        }
        return true;
    }

    /**
     * PATCH: Versión con cantidad específica de productos
     */
    private boolean esRutaValida(Pedido pedido, ArrayList<Vuelo> ruta, int cantidad) {
        if (ruta == null) return false;

        // 1) Construir RutaConTiempos
        RutaConTiempos tiempos = calcularTiemposRuta(pedido,ruta);

        // 2) Verificar deadline real con tiempos reales
        if (!seRespetaDeadlineConTiempos(pedido, tiempos)) {
            return false;
        }

        // 3) Verificar capacidad por instancia
        for (TramoConTiempo tramo : tiempos.getTramos()) {
            Vuelo vuelo = tramo.getVuelo();
            LocalDateTime horaSalida = tramo.getHoraSalidaReal();

            String instanciaId = buildInstanciaVueloId(vuelo, horaSalida, T0);

            InstanciaVuelo instancia = instanciaCache.get(instanciaId);

            // Si no existe, se crea potencialmente → capacidad está vacía, siempre cabe
            if (instancia == null) continue;

            if (instancia.getCapacidadUsada() + cantidad > instancia.getCapacidadMaxima()) {
                return false;
            }
        }

        return true;
    }

    /**
     * PATCH: Versión original que delega calculando cantidad
     */
    private boolean esRutaValida(Pedido pedido, ArrayList<Vuelo> ruta) {
        int cantidad = (pedido.getProductos() != null && !pedido.getProductos().isEmpty()) ? pedido.getProductos().size() : 1;
        return esRutaValida(pedido, ruta, cantidad);
    }

    /**
     * CORRECCIÓN: Aplicar las mismas correcciones que Solution.java
     */
    private boolean seRespetaDeadline(Pedido pedido, ArrayList<Vuelo> ruta) {
        //System.out.println("    Validando deadline para pedido " + pedido.getId() + "...");

        double tiempoTotal = 0;

        // CORRECCIÓN: Solo usar transportTime de vuelos (sin doble conteo)
        for (Vuelo vuelo : ruta) {
            tiempoTotal += vuelo.getTiempoTransporte();
        }

        // Añadir penalización por conexiones
        if (ruta.size() > 1) {
            tiempoTotal += (ruta.size() - 1) * 2.0;
        }

        //System.out.println("    Tiempo total ruta: " + tiempoTotal + " horas");

        // CORRECCIÓN: Validar promesas MoraPack explícitamente
        Ciudad origen = obtenerAeropuerto(pedido.getAeropuertoOrigenCodigo()).getCiudad();
        Ciudad destino = obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo()).getCiudad();
        boolean rutaMismoContinente = origen.getContinente() == destino.getContinente();
        long promesaHorasMoraPack = rutaMismoContinente ? 48 : 72;

//        System.out.println("    Promesa MoraPack: " + promesaHorasMoraPack + " horas");
//        System.out.println("    Mismo continente: " + rutaMismoContinente);

        if (tiempoTotal > promesaHorasMoraPack) {
            //System.out.println("    ❌ Excede promesa MoraPack: " + tiempoTotal + " > " + promesaHorasMoraPack);
            return false;
        }

        // CORRECCIÓN: Usar orderDate en lugar de "now"
        long horasHastaDeadline = ChronoUnit.HOURS.between(pedido.getFechaPedido(), pedido.getFechaLimiteEntrega());
        //System.out.println("    Horas hasta deadline: " + horasHastaDeadline);

        boolean resultado = tiempoTotal <= horasHastaDeadline;
        //System.out.println("    ✅ Deadline respetado: " + resultado + " (" + tiempoTotal + " <= " + horasHastaDeadline + ")");

        return resultado;
    }

    // Métodos de búsqueda de rutas (simplificados, podrían referenciar a Solution.java)
    private ArrayList<Vuelo> encontrarRutaDirecta(String codigoOrigen, String codigoDestino) {

        for (Vuelo vuelo : vuelos) {
            boolean mismoOrigen = vuelo.getAeropuertoOrigen().getCodigoIATA().equals(codigoOrigen);
            boolean mismoDestino = vuelo.getAeropuertoDestino().getCodigoIATA().equals(codigoDestino);

            if (mismoOrigen && mismoDestino) {
                ArrayList<Vuelo> ruta = new ArrayList<>();
                ruta.add(vuelo);
                return ruta;
            }
        }

        //System.out.println("    ❌ No se encontró vuelo directo");
        return null;
    }

    private ArrayList<Vuelo> encontrarRutaUnaEscala(String codigoOrigen, String codigoDestino) {
        // Buscar todos los posibles aeropuertos intermedios
        for (Aeropuerto intermedio : aeropuertos) {
            if (intermedio.getCodigoIATA().equals(codigoOrigen) ||
                    intermedio.getCodigoIATA().equals(codigoDestino)) {
                continue;
            }

            Vuelo primerVuelo = null;
            Vuelo segundoVuelo = null;

            // Buscar primer tramo: Origen → Intermedio
            for (Vuelo vuelo : vuelos) {
                if (vuelo.getAeropuertoOrigen().getCodigoIATA().equals(codigoOrigen) &&
                        vuelo.getAeropuertoDestino().getCodigoIATA().equals(intermedio.getCodigoIATA())) {
                    primerVuelo = vuelo;
                    break;
                }
            }

            if (primerVuelo == null) continue;

            // Buscar segundo tramo: Intermedio → Destino
            for (Vuelo vuelo : vuelos) {
                if (vuelo.getAeropuertoOrigen().getCodigoIATA().equals(intermedio.getCodigoIATA()) &&
                        vuelo.getAeropuertoDestino().getCodigoIATA().equals(codigoDestino)) {
                    segundoVuelo = vuelo;
                    break;
                }
            }

            if (segundoVuelo != null) {
                ArrayList<Vuelo> ruta = new ArrayList<>();
                ruta.add(primerVuelo);
                ruta.add(segundoVuelo);
                return ruta;
            }
        }

        System.out.println("    ❌ No se encontró ruta con 1 escala");
        return null;
    }

    private ArrayList<Vuelo> encontrarRutaDosEscalas(String codigoOrigen, String codigoDestino) {
        ArrayList<Aeropuerto> candidatos = new ArrayList<>();
        for (Aeropuerto aeropuerto : aeropuertos) {
            if (!aeropuerto.getCodigoIATA().equals(codigoOrigen) &&
                    !aeropuerto.getCodigoIATA().equals(codigoDestino)) {
                candidatos.add(aeropuerto);
            }
        }

        if (candidatos.size() < 2) return null;

        Collections.shuffle(candidatos, aleatorio);
        int maxIntentos = Math.min(10, candidatos.size() - 1);

        for (int i = 0; i < maxIntentos; i++) {
            Aeropuerto primero = candidatos.get(i);
            for (int j = i + 1; j < Math.min(i + 5, candidatos.size()); j++) {
                Aeropuerto segundo = candidatos.get(j);

                ArrayList<Vuelo> ruta = intentarRutaDosEscalas(codigoOrigen, primero.getCodigoIATA(),
                        segundo.getCodigoIATA(), codigoDestino);
                if (ruta != null) return ruta;

                // También probar en orden inverso
                ruta = intentarRutaDosEscalas(codigoOrigen, segundo.getCodigoIATA(),
                        primero.getCodigoIATA(), codigoDestino);
                if (ruta != null) return ruta;
            }
        }

        return null;
    }

    private ArrayList<Vuelo> intentarRutaDosEscalas(String origen, String primero, String segundo, String destino) {
        Vuelo vuelo1 = null, vuelo2 = null, vuelo3 = null;

        // Buscar vuelo 1: origen -> primero
        for (Vuelo vuelo : vuelos) {
            if (vuelo.getAeropuertoOrigen().getCodigoIATA().equals(origen) &&
                vuelo.getAeropuertoDestino().getCodigoIATA().equals(primero) &&
                vuelo.getCapacidadUsada() < vuelo.getCapacidadMaxima()) {
                vuelo1 = vuelo;
                break;
            }
        }

        if (vuelo1 == null) return null;

        // Buscar vuelo 2: primero -> segundo
        for (Vuelo vuelo : vuelos) {
            if (vuelo.getAeropuertoOrigen().getCodigoIATA().equals(primero) &&
                vuelo.getAeropuertoDestino().getCodigoIATA().equals(segundo) &&
                vuelo.getCapacidadUsada() < vuelo.getCapacidadMaxima()) {
                vuelo2 = vuelo;
                break;
            }
        }

        if (vuelo2 == null) return null;

        // Buscar vuelo 3: segundo -> destino
        for (Vuelo vuelo : vuelos) {
            if (vuelo.getAeropuertoOrigen().getCodigoIATA().equals(segundo) &&
                vuelo.getAeropuertoDestino().getCodigoIATA().equals(destino) &&
                vuelo.getCapacidadUsada() < vuelo.getCapacidadMaxima()) {
                vuelo3 = vuelo;
                break;
            }
        }

        if (vuelo3 != null) {
            ArrayList<Vuelo> ruta = new ArrayList<>();
            ruta.add(vuelo1);
            ruta.add(vuelo2);
            ruta.add(vuelo3);
            return ruta;
        }

        return null;
    }

    /**
     * PATCH: Ciudad→Aeropuerto robusto por nombre (evita equals frágil)
     */
    private Aeropuerto obtenerAeropuertoPorCiudad(Ciudad ciudad) {
        if (ciudad == null || ciudad.getNombre() == null) return null;

        String nombreCiudad = ciudad.getNombre().trim().toLowerCase();

        for (Aeropuerto aeropuerto : aeropuertos) {
            if (aeropuerto.getCiudad() != null && aeropuerto.getCiudad().getNombre() != null &&
                aeropuerto.getCiudad().getNombre().trim().toLowerCase().equals(nombreCiudad)) {
                return aeropuerto;
            }
        }
        return null;
    }

    private Ciudad obtenerCiudadPorAeropuerto(Aeropuerto aeropuerto) {
        return aeropuerto.getCiudad();
    }

    private boolean tieneCapacidadAlmacen(Aeropuerto aeropuertoDestino, int conteoProductos) {
        if (aeropuertoDestino == null) return false;

        // Evitar null en el mapa
//        if (!ocupacionAlmacenes.containsKey(aeropuertoDestino)) {
//            ocupacionAlmacenes.put(aeropuertoDestino, 0);
//        }

        int capacidad = aeropuertoDestino.getCapacidadMaxima();
        //int ocupacion = ocupacionAlmacenes.getOrDefault(aeropuertoDestino, 0);
        int ocupacion = aeropuertoDestino.getCapacidadActual();
        // Si la capacidad del aeropuerto es 0, se considera sin espacio
        if (capacidad == 0) {
            if (Constantes.LOGGING_VERBOSO)
                System.out.println("⚠️ Aeropuerto " + aeropuertoDestino.getCodigoIATA() + " tiene capacidad 0.");
            return false;
        }

        boolean tieneEspacio = (ocupacion + conteoProductos) <= capacidad;

        if (!tieneEspacio && Constantes.LOGGING_VERBOSO) {
            System.out.println("⚠️ Sin capacidad en almacén destino: " + aeropuertoDestino.getCodigoIATA() +
                    " (ocup=" + ocupacion + ", cap=" + capacidad + ", solicitados=" + conteoProductos + ")");
        }

        return tieneEspacio;
    }

    private void actualizarCapacidadesVuelos(ArrayList<Vuelo> ruta, int conteoProductos) {
        for (Vuelo vuelo : ruta) {
            vuelo.setCapacidadUsada(vuelo.getCapacidadUsada() + conteoProductos);
        }
    }

    private void incrementarOcupacionAlmacen(Aeropuerto aeropuerto, int conteoProductos) {
        int actual = aeropuerto.getCapacidadActual();
        aeropuerto.setCapacidadActual(actual + conteoProductos);
    }

    // ================= CLASES AUXILIARES =================

    private static class OpcionRuta {
        ArrayList<Vuelo> ruta;
        double margenTiempo;

        OpcionRuta(ArrayList<Vuelo> ruta, double margenTiempo) {
            this.ruta = ruta;
            this.margenTiempo = margenTiempo;
        }
    }

    private static class OpcionCapacidadRuta {
        ArrayList<Vuelo> ruta;
        double capacidadDisponible;
        double margenTiempo;

        OpcionCapacidadRuta(ArrayList<Vuelo> ruta, double capacidadDisponible, double margenTiempo) {
            this.ruta = ruta;
            this.capacidadDisponible = capacidadDisponible;
            this.margenTiempo = margenTiempo;
        }
    }

    /**
     * Clase para encapsular el resultado de una operación de reparación
     */
    public static class ResultadoReparacion {
        private HashMap<Pedido, ArrayList<Vuelo>> solucionReparada;
        private ArrayList<Pedido> paquetesNoAsignados;

        public ResultadoReparacion(HashMap<Pedido, ArrayList<Vuelo>> solucionReparada,
                           ArrayList<Pedido> paquetesNoAsignados) {
            this.solucionReparada = solucionReparada;
            this.paquetesNoAsignados = paquetesNoAsignados;
        }

        public HashMap<Pedido, ArrayList<Vuelo>> getSolucionReparada() {
            return solucionReparada;
        }

        public ArrayList<Pedido> getPaquetesNoAsignados() {
            return paquetesNoAsignados;
        }

        public int getNumPaquetesReparados() {
            return solucionReparada.size();
        }

        public boolean esExitoso() {
            return !solucionReparada.isEmpty() || paquetesNoAsignados.isEmpty();
        }

        public int getNumPaquetesNoAsignados() {
            return paquetesNoAsignados.size();
        }
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
                //System.out.println("✅ Aeropuerto encontrado: " + codigoIATA);
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

    private void verificarVuelosDisponibles(String codigoOrigen, String codigoDestino) {
        System.out.println("\n🔍 VERIFICANDO VUELOS DISPONIBLES:");
        System.out.println("Buscando vuelos desde " + codigoOrigen + " hacia " + codigoDestino);

        Aeropuerto origen = obtenerAeropuerto(codigoOrigen);
        Aeropuerto destino = obtenerAeropuerto(codigoDestino);

        if (origen == null) {
            System.out.println("❌ ERROR: Aeropuerto origen " + codigoOrigen + " no encontrado");
            return;
        }
        if (destino == null) {
            System.out.println("❌ ERROR: Aeropuerto destino " + codigoDestino + " no encontrado");
            return;
        }

        // Verificar vuelos directos
        System.out.println("Vuelos directos:");
        boolean hayDirectos = false;
        for (Vuelo vuelo : vuelos) {
            if (vuelo.getAeropuertoOrigen().getCodigoIATA().equals(origen.getCodigoIATA()) &&
                    vuelo.getAeropuertoDestino().getCodigoIATA().equals(destino.getCodigoIATA())) {
                System.out.println("  ✅ " + vuelo.getAeropuertoOrigen().getCodigoIATA() +
                        " → " + vuelo.getAeropuertoDestino().getCodigoIATA() +
                        " - Capacidad: " + vuelo.getCapacidadUsada() + "/" + vuelo.getCapacidadMaxima());
                hayDirectos = true;
            }
        }
        if (!hayDirectos) System.out.println("  ❌ No hay vuelos directos");

        // Verificar vuelos desde origen
        System.out.println("Todos los vuelos desde " + codigoOrigen + ":");
        int vuelosDesdeOrigen = 0;
        for (Vuelo vuelo : vuelos) {
            if (vuelo.getAeropuertoOrigen().getCodigoIATA().equals(origen.getCodigoIATA())) {
                System.out.println("  ✈️  " + vuelo.getAeropuertoOrigen().getCodigoIATA() +
                        " → " + vuelo.getAeropuertoDestino().getCodigoIATA() +
                        " - Cap: " + vuelo.getCapacidadUsada() + "/" + vuelo.getCapacidadMaxima());
                vuelosDesdeOrigen++;
            }
        }
        System.out.println("  Total vuelos desde " + codigoOrigen + ": " + vuelosDesdeOrigen);

        // Verificar vuelos hacia destino
        System.out.println("Todos los vuelos hacia " + codigoDestino + ":");
        int vuelosHaciaDestino = 0;
        for (Vuelo vuelo : vuelos) {
            if (vuelo.getAeropuertoDestino().getCodigoIATA().equals(destino.getCodigoIATA())) {
                System.out.println("  ✈️  " + vuelo.getAeropuertoOrigen().getCodigoIATA() +
                        " → " + vuelo.getAeropuertoDestino().getCodigoIATA() +
                        " - Cap: " + vuelo.getCapacidadUsada() + "/" + vuelo.getCapacidadMaxima());
                vuelosHaciaDestino++;
            }
        }
        System.out.println("  Total vuelos hacia " + codigoDestino + ": " + vuelosHaciaDestino);
    }
    void actualizarCapacidadAeropuertos(String codigoAeropuertoDestino, int cantidad) {
        for(Aeropuerto aeropuerto : aeropuertos) {
            if(aeropuerto.getCodigoIATA().equals(codigoAeropuertoDestino)) {
                int capacidadActual = aeropuerto.getCapacidadActual();
                aeropuerto.setCapacidadActual(capacidadActual + cantidad);
                break;
            }
        }
    }
    /**
     * Reserva capacidad en las instancias de vuelo correspondientes a la ruta.
     * Esto reemplaza por completo actualizarCapacidadesVuelos().
     */
    private void reservarCapacidadEnInstancias(RutaConTiempos tiempos, Pedido pedido, LocalDateTime T0) {
        if (tiempos == null || pedido == null) return;

        int cantidad = pedido.getCantidadProductosRapido();
        List<Producto> productos = pedido.getProductos();
        for (TramoConTiempo tramo : tiempos.getTramos()) {
            Vuelo vuelo = tramo.getVuelo();
            LocalDateTime horaSalida = tramo.getHoraSalidaReal();
            if (vuelo == null || horaSalida == null) continue;

            String instanciaId = buildInstanciaVueloId(vuelo, horaSalida, T0);

            InstanciaVuelo instancia = instanciaCache.get(instanciaId);

            if (instancia == null) {
                // Si no existe, crear y colocar en cache
                instancia = new InstanciaVuelo();
                instancia.setIdInstancia(instanciaId);
                instancia.setVueloBase(tramo.getVuelo());
                instancia.setFechaHoraSalida(tramo.getHoraSalidaReal());
                instancia.setFechaHoraLlegada(tramo.getHoraLlegadaReal());
                instancia.setCapacidadMaxima(tramo.getVuelo().getCapacidadMaxima());
                instancia.setCapacidadUsada(0); // inicializa en 0
                instancia.setEstadoInstancia(EstadoInstanciaVuelo.PLANIFICADO);
                instanciaCache.put(instanciaId, instancia);
            }

            instancia.reservarCapacidad(cantidad);
            for (Producto producto : productos) {
                // Crear ProductAssignment en memoria
                String llave = producto.getId() + "-" + instanciaId;
                ProductAssignment assignment = new ProductAssignment();
                assignment.setProductoId(producto.getId());
                assignment.setPedidoId(pedido.getId());
                assignment.setFlightInstanceId(instanciaId);
                assignment.setHoraSalidaReal(tramo.getHoraSalidaReal());
                assignment.setHoraLlegadaReal(tramo.getHoraLlegadaReal());
                assignment.setEstadoProducto(EstadoProducto.PLANIFICADO);
                assignmentCache.put(llave, assignment);
            }
        }
    }
    private String buildInstanciaVueloId(Vuelo vuelo, LocalDateTime horaSalida, LocalDateTime T0) {
        int dayOffset = (int) ChronoUnit.DAYS.between(T0.toLocalDate().atStartOfDay(),
                horaSalida.toLocalDate().atStartOfDay());
        String hhmm = String.format("%02d%02d", horaSalida.getHour(), horaSalida.getMinute());
        return "FL-" + vuelo.getId() + "-DAY-" + dayOffset + "-" + hhmm;
    }
    /**
     * NUEVO: Calcula los tiempos absolutos para una ruta de vuelos.
     * Convierte la secuencia de vuelos en tramos con fechas exactas de
     * salida/llegada.
     *
     * @param pedido Pedido al que pertenece la ruta
     * @param vuelos Lista de vuelos que forman la ruta
     * @return RutaConTiempos con todos los tiempos calculados
     */
    private RutaConTiempos calcularTiemposRuta(Pedido pedido, ArrayList<Vuelo> vuelos) {
        RutaConTiempos rutaConTiempos = RutaConTiempos.builder()
                .pedido(pedido)
                .tramos(new ArrayList<>())
                .build();

        if (vuelos == null || vuelos.isEmpty()) {
            return rutaConTiempos;
        }

        // Punto de partida: fecha del pedido o T0
        LocalDateTime tiempoActual = (pedido != null && pedido.getFechaPedido() != null)
                ? pedido.getFechaPedido()
                : T0;

        double tiempoTotalHoras = 0.0;

        for (int i = 0; i < vuelos.size(); i++) {
            Vuelo vuelo = vuelos.get(i);

            // Calcular hora de salida real (ajustar al próximo horario disponible)
            LocalDateTime horaSalidaReal = calcularProximaSalida(tiempoActual, vuelo);

            // Calcular hora de llegada usando tiempoTransporte del vuelo
            double horasTransporte = vuelo.getTiempoTransporte();
            if (horasTransporte <= 0) {
                horasTransporte = 2.0; // Default: 2 horas si no hay dato
            }
            long minutosTransporte = (long) (horasTransporte * 60);
            LocalDateTime horaLlegadaReal = horaSalidaReal.plusMinutes(minutosTransporte);

            // Crear tramo con tiempos
            TramoConTiempo tramo = TramoConTiempo.builder()
                    .vuelo(vuelo)
                    .horaSalidaReal(horaSalidaReal)
                    .horaLlegadaReal(horaLlegadaReal)
                    .indiceEnRuta(i)
                    .build();

            rutaConTiempos.agregarTramo(tramo);
            tiempoTotalHoras += horasTransporte;

            // Para el siguiente vuelo: llegada + tiempo de conexión (1 hora)
            tiempoActual = horaLlegadaReal.plusMinutes(Constantes.TIEMPO_CONEXION_MINUTOS);
        }

        rutaConTiempos.setTiempoTotalHoras(tiempoTotalHoras);

        return rutaConTiempos;
    }
    /**
     * NUEVO: Calcula la próxima salida disponible de un vuelo.
     * Considera la hora programada del vuelo y ajusta al día siguiente si ya pasó.
     *
     * @param tiempoActual Momento actual desde el que se busca la próxima salida
     * @param vuelo        Vuelo con su hora de salida programada
     * @return LocalDateTime de la próxima salida disponible
     */
    private LocalDateTime calcularProximaSalida(LocalDateTime tiempoActual, Vuelo vuelo) {
        if (tiempoActual == null) {
            tiempoActual = T0 != null ? T0 : LocalDateTime.now();
        }

        // Si el vuelo no tiene hora de salida programada, usar tiempoActual
        if (vuelo == null || vuelo.getHoraSalida() == null) {
            return tiempoActual;
        }

        // Construir hora de salida para hoy
        LocalDateTime salidaHoy = tiempoActual.toLocalDate().atTime(vuelo.getHoraSalida());

        // Si ya pasó la hora de salida hoy, usar la de mañana
        if (salidaHoy.isBefore(tiempoActual) || salidaHoy.equals(tiempoActual)) {
            salidaHoy = salidaHoy.plusDays(1);
        }

        return salidaHoy;
    }
    private boolean seRespetaDeadlineConTiempos(Pedido pedido, RutaConTiempos tiempos) {
        if (pedido == null || tiempos == null) {
            return false;
        }

        if (pedido.getFechaLimiteEntrega() == null) {
            return false;
        }

        // Hora en que finaliza la ruta
        LocalDateTime horaFinRuta = tiempos.getHoraFinRuta();
        if (horaFinRuta == null) {
            return false;
        }

        // Regla principal: el paquete debe llegar ANTES del deadline
        return !horaFinRuta.isAfter(pedido.getFechaLimiteEntrega());
    }
}