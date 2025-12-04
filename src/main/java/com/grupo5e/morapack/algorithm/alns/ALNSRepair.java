package com.grupo5e.morapack.algorithm.alns;

import com.grupo5e.morapack.core.constants.Constantes;
import com.grupo5e.morapack.core.model.Pedido;
import com.grupo5e.morapack.core.model.Vuelo;
import com.grupo5e.morapack.core.model.Ciudad;
import com.grupo5e.morapack.core.model.Aeropuerto;
import com.grupo5e.morapack.service.AeropuertoService;

import java.util.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

import static java.lang.System.exit;

/**
 * Clase que implementa operadores de reparación para el algoritmo ALNS
 * (Adaptive Large Neighborhood Search) específicamente diseñados para el
 * problema
 * de logística MoraPack.
 *
 * Los operadores priorizan las entregas a tiempo y la eficiencia de rutas.
 */
public class ALNSRepair {

    private ArrayList<Aeropuerto> aeropuertos;
    private ArrayList<Vuelo> vuelos;
    private HashMap<Aeropuerto, Integer> ocupacionAlmacenes;
    private Random aleatorio;

    private final AeropuertoService aeropuertoService;

    /**
     * Constructor simplificado sin dependencias de Spring.
     * Usado por ALNSSolver con FuenteDatosInput modular.
     */
    public ALNSRepair(ArrayList<Aeropuerto> aeropuertos, ArrayList<Vuelo> vuelos,
            HashMap<Aeropuerto, Integer> ocupacionAlmacenes) {
        this.aeropuertos = aeropuertos;
        this.vuelos = vuelos;
        this.ocupacionAlmacenes = ocupacionAlmacenes;
        this.aeropuertoService = null; // No se usa en la implementación actual
        this.aleatorio = new Random(System.currentTimeMillis());

        // VERIFICACIÓN DE DATOS
        System.out.println("ALNSRepair inicializado con:");
        System.out.println("  - Aeropuertos: " + this.aeropuertos.size());
        System.out.println("  - Vuelos: " + this.vuelos.size());
        System.out.println("  - Ocupación almacenes: " + this.ocupacionAlmacenes.size());

        if (this.vuelos.isEmpty()) {
            System.err.println("❌ ALERTA: ALNSRepair recibió lista de vuelos vacía");
        }
    }

    public ALNSRepair(ArrayList<Aeropuerto> aeropuertos, ArrayList<Vuelo> vuelos,
            HashMap<Aeropuerto, Integer> ocupacionAlmacenes, AeropuertoService aeropuertoService) {
        this.aeropuertos = aeropuertos;
        this.vuelos = vuelos;
        this.ocupacionAlmacenes = ocupacionAlmacenes;
        this.aeropuertoService = aeropuertoService;
        this.aleatorio = new Random(System.currentTimeMillis());

        // VERIFICACIÓN DE DATOS
        System.out.println("ALNSRepair inicializado con:");
        System.out.println("  - Aeropuertos: " + this.aeropuertos.size());
        System.out.println("  - Vuelos: " + this.vuelos.size());
        System.out.println("  - Ocupación almacenes: " + this.ocupacionAlmacenes.size());

        if (this.vuelos.isEmpty()) {
            System.err.println("❌ ALERTA: ALNSRepair recibió lista de vuelos vacía");
        }
    }

    /**
     * Constructor con semilla específica
     */
    public ALNSRepair(ArrayList<Aeropuerto> aeropuertos, ArrayList<Vuelo> vuelos,
            HashMap<Aeropuerto, Integer> ocupacionAlmacenes, long semilla, AeropuertoService aeropuertoService) {
        this.aeropuertos = aeropuertos;
        this.vuelos = vuelos;
        this.ocupacionAlmacenes = ocupacionAlmacenes;
        this.aleatorio = new Random(semilla);
        this.aeropuertoService = aeropuertoService;
    }

    /**
     * Reparación Greedy Mejorada: Inserta paquetes usando enfoque optimizado para
     * MoraPack.
     * Prioriza paquetes por deadline y eficiencia de ruta específica para el
     * negocio.
     */
    public ResultadoReparacion reparacionCodiciosa(
            HashMap<Pedido, ArrayList<Vuelo>> solucionParcial,
            ArrayList<Map.Entry<Pedido, ArrayList<Vuelo>>> paquetesDestruidos) {

        // System.out.println("\n=== INICIANDO REPARACIÓN GREEDY ===");
        // System.out.println("Paquetes a reparar: " + paquetesDestruidos.size());
        // System.out.println("Solución parcial inicial: " + solucionParcial.size() + "
        // paquetes");

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
            if (comparacionUrgencia != 0)
                return comparacionUrgencia;

            // 2. Priorizar paquetes con más productos (mayor valor de negocio)
            // OPTIMIZADO: Usar getCantidadProductosRapido() para evitar
            // LazyInitializationException
            int productos1 = p1.getCantidadProductosRapido();
            int productos2 = p2.getCantidadProductosRapido();
            int comparacionProductos = Integer.compare(productos2, productos1);
            if (comparacionProductos != 0)
                return comparacionProductos;

            // 3. Tie-break por deadline absoluto
            LocalDateTime d1 = p1.getFechaLimiteEntrega();
            LocalDateTime d2 = p2.getFechaLimiteEntrega();
            if (d1 == null && d2 == null)
                return 0;
            if (d1 == null)
                return 1; // nulls last
            if (d2 == null)
                return -1; // nulls last
            return d1.compareTo(d2);
        });

        int conteoReinsertados = 0;

        // Intentar reinsertar cada paquete
        for (Pedido pedido : paquetesParaReparar) {
            Aeropuerto aeropuertoDestino = obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo());

            // Obtener conteo de productos para este pedido
            // OPTIMIZADO: Usar getCantidadProductosRapido()
            int conteoProductos = pedido.getCantidadProductosRapido();
            // System.out.println("Productos: " + conteoProductos);

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
                // System.out.println("❌ No se encontró ninguna ruta válida");
                paquetesNoAsignados.add(pedido);
                continue;
            }

            // Validar ruta
            if (!esRutaValida(pedido, mejorRuta, conteoProductos)) {
                // System.out.println("❌ Ruta no válida (no cumple validaciones)");
                paquetesNoAsignados.add(pedido);
                continue;
            }

            solucionReparada.put(pedido, mejorRuta);
            actualizarCapacidadesVuelos(mejorRuta, conteoProductos);
            actualizarCapacidadAeropuertos(aeropuertoDestino.getCodigoIATA(), conteoProductos);
            // incrementarOcupacionAlmacen(aeropuertoDestino, conteoProductos);
            conteoReinsertados++;

            // if (mejorRuta != null && esRutaValida(pedido, mejorRuta, conteoProductos)) {
            // solucionReparada.put(pedido, mejorRuta);
            // actualizarCapacidadesVuelos(mejorRuta, conteoProductos);
            // incrementarOcupacionAlmacen(aeropuertoDestino, conteoProductos);
            // conteoReinsertados++;
            // } else {
            // paquetesNoAsignados.add(pedido);
            // }
        }
        System.out.println("Reparación Greedy: " + conteoReinsertados + "/" + paquetesParaReparar.size() +
                " paquetes reinsertados");

        return new ResultadoReparacion(solucionReparada, paquetesNoAsignados);
    }

    /**
     * Reparación por Regret: Calcula el "arrepentimiento" de no insertar cada
     * paquete
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
                int conteoProductos = pedido.getCantidadProductosRapido();

                if (aeropuertoDestino == null) {
                    System.out.println("❌ Aeropuerto destino no encontrado para pedido: " + pedido.getId() + " "
                            + pedido.getAeropuertoDestinoCodigo());
                    exit(1);
                }
                if (!tieneCapacidadAlmacen(aeropuertoDestino, conteoProductos)) {
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
                        long horasHastaDeadline = ChronoUnit.HOURS.between(pedido.getFechaPedido(),
                                pedido.getFechaLimiteEntrega());
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
            if (mejorPedido != null && mejorRuta != null && esRutaValida(mejorPedido, mejorRuta,
                    Math.max(1, mejorPedido.getCantidadProductosRapido()))) {
                solucionReparada.put(mejorPedido, mejorRuta);
                int conteoProductos = mejorPedido.getCantidadProductosRapido();
                actualizarCapacidadesVuelos(mejorRuta, conteoProductos);
                actualizarCapacidadAeropuertos(mejorPedido.getAeropuertoDestinoCodigo(), conteoProductos);
                paquetesRestantes.remove(mejorPedido);
                conteoReinsertados++;
            } else {
                // No se pudo insertar ningún paquete, agregar todos los restantes como no
                // asignados
                paquetesNoAsignados.addAll(paquetesRestantes);
                break;
            }
        }

        System.out.println("Reparación por Arrepentimiento: " + conteoReinsertados + "/" + paquetesDestruidos.size() +
                " paquetes reinsertados");

        return new ResultadoReparacion(solucionReparada, paquetesNoAsignados);
    }

    // public ResultadoReparacion reparacionArrepentimiento(
    // HashMap<Pedido, ArrayList<Vuelo>> solucionParcial,
    // ArrayList<Map.Entry<Pedido, ArrayList<Vuelo>>> paquetesDestruidos,
    // int nivelArrepentimiento) {
    //
    //// System.out.println("\n=== INICIANDO REPARACIÓN POR ARREPENTIMIENTO ===");
    //// System.out.println("Paquetes a reparar: " + paquetesDestruidos.size());
    //// System.out.println("Nivel de arrepentimiento: " + nivelArrepentimiento);
    //// System.out.println("Solución parcial inicial: " + solucionParcial.size() +
    // " paquetes");
    //
    // HashMap<Pedido, ArrayList<Vuelo>> solucionReparada = new
    // HashMap<>(solucionParcial);
    // ArrayList<Pedido> paquetesNoAsignados = new ArrayList<>();
    //
    // ArrayList<Pedido> paquetesRestantes = new ArrayList<>();
    // for (Map.Entry<Pedido, ArrayList<Vuelo>> entrada : paquetesDestruidos) {
    // paquetesRestantes.add(entrada.getKey());
    // }
    //
    // int conteoReinsertados = 0;
    // int iteracion = 0;
    //
    // // Mientras haya paquetes por insertar
    // while (!paquetesRestantes.isEmpty()) {
    // iteracion++;
    //// System.out.println("\n--- Iteración " + iteracion + " ---");
    //// System.out.println("Paquetes restantes: " + paquetesRestantes.size());
    //
    // Pedido mejorPedido = null;
    // ArrayList<Vuelo> mejorRuta = null;
    // double maxArrepentimiento = Double.NEGATIVE_INFINITY;
    //
    // int sinCapacidadAlmacen = 0;
    // int sinOpcionesRuta = 0;
    // int conOpcionesRuta = 0;
    //
    // // Calcular arrepentimiento para cada paquete restante
    // for (Pedido pedido : paquetesRestantes) {
    //// System.out.println("\n Analizando pedido " + pedido.getId() +
    //// " (" + pedido.getAeropuertoOrigenCodigo() + " → " +
    //// pedido.getAeropuertoDestinoCodigo() + ")");
    //
    // Aeropuerto aeropuertoDestino =
    // obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo());
    // int conteoProductos = pedido.getProductos() != null ?
    // pedido.getProductos().size() : 1;
    //
    // // Verificar capacidad del almacén
    // if (aeropuertoDestino == null) {
    // System.out.println(" ❌ Aeropuerto destino no encontrado: " +
    // pedido.getAeropuertoDestinoCodigo());
    // exit(1);
    // }
    //
    // if (!tieneCapacidadAlmacen(aeropuertoDestino, conteoProductos)) {
    // //System.out.println(" ❌ Sin capacidad en almacén destino: " +
    // aeropuertoDestino.getCodigoIATA());
    // paquetesNoAsignados.add(pedido);
    // continue;
    // }
    //
    // //System.out.println(" ✅ Capacidad almacén OK");
    //
    // // Encontrar las mejores rutas para este pedido
    // //System.out.println(" Buscando opciones de ruta...");
    // ArrayList<OpcionRuta> opcionesRuta = encontrarTodasLasOpcionesRuta(pedido);
    //
    // if (opcionesRuta.isEmpty()) {
    // System.out.println(" ❌ No se encontraron opciones de ruta");
    // continue;
    // }
    //
    // conOpcionesRuta++;
    // //System.out.println(" ✅ " + opcionesRuta.size() + " opciones de ruta
    // encontradas");
    //
    // // Ordenar por margen de tiempo (mejor primero)
    // opcionesRuta.sort((r1, r2) -> Double.compare(r2.margenTiempo,
    // r1.margenTiempo));
    //
    // // Calcular regret-k real
    // double arrepentimiento = 0;
    // int k = Math.max(2, nivelArrepentimiento);
    // int limite = Math.min(k, opcionesRuta.size());
    //
    // //System.out.println(" Calculando arrepentimiento (k=" + k + ", límite=" +
    // limite + ")");
    //
    // if (limite >= 2) {
    // double mejorMargen = opcionesRuta.get(0).margenTiempo;
    // //System.out.println(" Mejor margen: " + mejorMargen);
    //
    // for (int i = 1; i < limite; i++) {
    // double diferencia = mejorMargen - opcionesRuta.get(i).margenTiempo;
    // arrepentimiento += diferencia;
    // //System.out.println(" Diferencia con opción " + i + ": " + diferencia);
    // }
    // } else if (opcionesRuta.size() == 1) {
    // // Solo una opción: usar urgencia basada en orderDate→deadline
    // //System.out.println(" Solo 1 opción, usando urgencia...");
    // if (pedido.getFechaPedido() != null && pedido.getFechaLimiteEntrega() !=
    // null) {
    // long horasHastaDeadline = ChronoUnit.HOURS.between(pedido.getFechaPedido(),
    // pedido.getFechaLimiteEntrega());
    // arrepentimiento = Math.max(0, 72 - Math.min(72, horasHastaDeadline));
    // //System.out.println(" Horas hasta deadline: " + horasHastaDeadline + ",
    // arrepentimiento: " + arrepentimiento);
    // } else {
    // arrepentimiento = 0;
    // //System.out.println(" Sin fechas, arrepentimiento: 0");
    // }
    // }
    //
    // // Añadir factor de urgencia al arrepentimiento
    // LocalDateTime ahora = LocalDateTime.now();
    // long horasHastaDeadline = ChronoUnit.HOURS.between(ahora,
    // pedido.getFechaLimiteEntrega());
    // double factorUrgencia = Math.max(1, 72.0 / Math.max(1, horasHastaDeadline));
    // double arrepentimientoFinal = arrepentimiento * factorUrgencia;
    //
    //// System.out.println(" Arrepentimiento base: " + arrepentimiento);
    //// System.out.println(" Factor urgencia: " + factorUrgencia + " (horas hasta
    // deadline: " + horasHastaDeadline + ")");
    //// System.out.println(" Arrepentimiento final: " + arrepentimientoFinal);
    //// System.out.println(" Arrepentimiento máximo actual: " +
    // maxArrepentimiento);
    //
    // if (arrepentimientoFinal > maxArrepentimiento) {
    // maxArrepentimiento = arrepentimientoFinal;
    // mejorPedido = pedido;
    // mejorRuta = opcionesRuta.get(0).ruta;
    // //System.out.println(" 🎯 NUEVO MEJOR: Pedido " + pedido.getId() + " con
    // arrepentimiento: " + arrepentimientoFinal);
    // }
    // }
    //
    //// System.out.println("\n --- RESUMEN ITERACIÓN " + iteracion + " ---");
    //// System.out.println(" Sin capacidad almacén: " + sinCapacidadAlmacen);
    //// System.out.println(" Sin opciones ruta: " + sinOpcionesRuta);
    //// System.out.println(" Con opciones ruta: " + conOpcionesRuta);
    //// System.out.println(" Mejor arrepentimiento encontrado: " +
    // maxArrepentimiento);
    //
    // // Insertar el paquete con mayor arrepentimiento
    // if (mejorPedido != null && mejorRuta != null) {
    // //System.out.println("\n Intentando insertar mejor pedido: " +
    // mejorPedido.getId());
    //
    // int conteoProductos = Math.max(1, mejorPedido.getProductos() != null ?
    // mejorPedido.getProductos().size() : 1);
    // //System.out.println(" Validando ruta...");
    //
    // if (esRutaValida(mejorPedido, mejorRuta, conteoProductos)) {
    // solucionReparada.put(mejorPedido, mejorRuta);
    // actualizarCapacidadesVuelos(mejorRuta, conteoProductos);
    // incrementarOcupacionAlmacen(obtenerAeropuerto(mejorPedido.getAeropuertoDestinoCodigo()),
    // conteoProductos);
    // paquetesRestantes.remove(mejorPedido);
    // conteoReinsertados++;
    //
    //// System.out.println(" ✅ PEDIDO " + mejorPedido.getId() + " REINSERTADO
    // EXITOSAMENTE");
    //// System.out.println(" Ruta con " + mejorRuta.size() + " vuelos");
    // } else {
    // //System.out.println(" ❌ Ruta no válida para pedido " + mejorPedido.getId());
    // paquetesRestantes.remove(mejorPedido);
    // paquetesNoAsignados.add(mejorPedido);
    // }
    // } else {
    // // No se pudo insertar ningún paquete, agregar todos los restantes como no
    // asignados
    //// System.out.println("\n ❌ NO SE ENCONTRÓ NINGÚN PEDIDO VÁLIDO PARA
    // INSERTAR");
    //// System.out.println(" Agregando " + paquetesRestantes.size() + " paquetes a
    // no asignados");
    // paquetesNoAsignados.addAll(paquetesRestantes);
    // break;
    // }
    // }
    //
    // System.out.println("\n=== RESUMEN FINAL REPARACIÓN POR ARREPENTIMIENTO ===");
    // System.out.println("Total procesados: " + paquetesDestruidos.size());
    // //System.out.println("Reinsertados: " + conteoReinsertados);
    // System.out.println("No asignados: " + paquetesNoAsignados.size());
    // //System.out.println("Iteraciones realizadas: " + iteracion);
    //
    // return new ResultadoReparacion(solucionReparada, paquetesNoAsignados);
    // }
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
            if (p1.getFechaPedido() == null || p1.getFechaLimiteEntrega() == null)
                return 1;
            if (p2.getFechaPedido() == null || p2.getFechaLimiteEntrega() == null)
                return -1;
            long horasP1 = ChronoUnit.HOURS.between(p1.getFechaPedido(), p1.getFechaLimiteEntrega());
            long horasP2 = ChronoUnit.HOURS.between(p2.getFechaPedido(), p2.getFechaLimiteEntrega());
            return Long.compare(horasP1, horasP2);
        });

        int conteoReinsertados = 0;

        // Insertar paquetes en orden de urgencia
        for (Pedido pedido : paquetesParaReparar) {
            Aeropuerto aeropuertoDestino = obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo());
            if (aeropuertoDestino == null) {
                System.out.println("❌ Aeropuerto destino no encontrado para pedido: " + pedido.getId() + " "
                        + pedido.getAeropuertoDestinoCodigo());
                exit(1);
            }
            int conteoProductos = pedido.getCantidadProductosRapido();

            if (!tieneCapacidadAlmacen(aeropuertoDestino, conteoProductos)) {
                paquetesNoAsignados.add(pedido);
                continue;
            }

            // Buscar ruta con mayor margen de tiempo
            ArrayList<Vuelo> mejorRuta = encontrarRutaConMaximoMargen(pedido);

            // if (mejorRuta != null && !esRutaValida(pedido, mejorRuta, conteoProductos)) {
            // System.out.println("⚠️ Ruta no válida para pedido: " + pedido.getId());
            // }
            // if (mejorRuta == null) {
            // System.out.println("⚠️ No se encontró ruta para pedido: " +
            // pedido.getAeropuertoOrigenCodigo() +
            // " → " + pedido.getAeropuertoDestinoCodigo());
            // }
            if (mejorRuta != null && esRutaValida(pedido, mejorRuta, conteoProductos)) {
                solucionReparada.put(pedido, mejorRuta);
                actualizarCapacidadesVuelos(mejorRuta, conteoProductos);
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

    // public ResultadoReparacion reparacionPorTiempo(
    // HashMap<Pedido, ArrayList<Vuelo>> solucionParcial,
    // ArrayList<Map.Entry<Pedido, ArrayList<Vuelo>>> paquetesDestruidos) {
    //
    // System.out.println("\n=== INICIANDO REPARACIÓN POR TIEMPO ===");
    // System.out.println("Paquetes a reparar: " + paquetesDestruidos.size());
    // System.out.println("Solución parcial inicial: " + solucionParcial.size() + "
    // paquetes");
    //
    // HashMap<Pedido, ArrayList<Vuelo>> solucionReparada = new
    // HashMap<>(solucionParcial);
    // ArrayList<Pedido> paquetesNoAsignados = new ArrayList<>();
    //
    // // Extraer paquetes y ordenar por urgencia (deadline más cercano primero)
    // ArrayList<Pedido> paquetesParaReparar = new ArrayList<>();
    // for (Map.Entry<Pedido, ArrayList<Vuelo>> entrada : paquetesDestruidos) {
    // paquetesParaReparar.add(entrada.getKey());
    // }
    //
    // System.out.println("Ordenando paquetes por urgencia (deadline más cercano
    // primero)...");
    // paquetesParaReparar.sort((p1, p2) -> {
    // // Ordenar por presupuesto real desde orderDate (nulls last)
    // if (p1.getFechaPedido() == null || p1.getFechaLimiteEntrega() == null) return
    // 1;
    // if (p2.getFechaPedido() == null || p2.getFechaLimiteEntrega() == null) return
    // -1;
    // long horasP1 = ChronoUnit.HOURS.between(p1.getFechaPedido(),
    // p1.getFechaLimiteEntrega());
    // long horasP2 = ChronoUnit.HOURS.between(p2.getFechaPedido(),
    // p2.getFechaLimiteEntrega());
    // return Long.compare(horasP1, horasP2);
    // });
    //
    // int conteoReinsertados = 0;
    // int sinCapacidadAlmacen = 0;
    // int sinRutaMargen = 0;
    // int rutaNoValida = 0;
    // int erroresAeropuerto = 0;
    //
    // System.out.println("Procesando " + paquetesParaReparar.size() + " paquetes
    // por orden de urgencia...");
    //
    // // Insertar paquetes en orden de urgencia
    // for (Pedido pedido : paquetesParaReparar) {
    // //System.out.println("\n--- Procesando pedido " + pedido.getId() + "
    // (URGENTE) ---");
    //
    // // Calcular y mostrar urgencia
    // long horasDisponibles = 0;
    // if (pedido.getFechaPedido() != null && pedido.getFechaLimiteEntrega() !=
    // null) {
    // horasDisponibles = ChronoUnit.HOURS.between(pedido.getFechaPedido(),
    // pedido.getFechaLimiteEntrega());
    // System.out.println("⏰ Urgencia: " + horasDisponibles + " horas disponibles");
    // System.out.println(" Fecha pedido: " + pedido.getFechaPedido());
    // System.out.println(" Deadline: " + pedido.getFechaLimiteEntrega());
    // } else {
    // //System.out.println("⚠️ Sin información de fechas para calcular urgencia");
    // }
    //
    // System.out.println("Origen: " + pedido.getAeropuertoOrigenCodigo() +
    // " → Destino: " + pedido.getAeropuertoDestinoCodigo());
    //
    // Aeropuerto aeropuertoDestino =
    // obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo());
    // int conteoProductos = pedido.getProductos() != null ?
    // pedido.getProductos().size() : 1;
    // //System.out.println("Productos: " + conteoProductos);
    //
    // // Verificar aeropuerto destino
    // if (aeropuertoDestino == null) {
    // //System.out.println("❌ ERROR: Aeropuerto destino no encontrado: " +
    // pedido.getAeropuertoDestinoCodigo());
    // erroresAeropuerto++;
    // paquetesNoAsignados.add(pedido);
    // continue;
    // }
    //
    // // Verificar capacidad del almacén
    // System.out.println("Verificando capacidad en almacén " +
    // aeropuertoDestino.getCodigoIATA() +
    // " (ocupación: " + ocupacionAlmacenes.getOrDefault(aeropuertoDestino, 0) +
    // "/" + aeropuertoDestino.getCapacidadMaxima() + ")");
    //
    // if (!tieneCapacidadAlmacen(aeropuertoDestino, conteoProductos)) {
    // System.out.println("❌ SIN CAPACIDAD: Almacén " +
    // aeropuertoDestino.getCodigoIATA() +
    // " - Ocupación: " + ocupacionAlmacenes.getOrDefault(aeropuertoDestino, 0) +
    // "/" + aeropuertoDestino.getCapacidadMaxima());
    // sinCapacidadAlmacen++;
    // paquetesNoAsignados.add(pedido);
    // continue;
    // }
    //
    // System.out.println("✅ Capacidad de almacén OK");
    //
    // // Buscar ruta con mayor margen de tiempo
    // System.out.println("Buscando ruta con máximo margen de tiempo...");
    // ArrayList<Vuelo> mejorRuta = encontrarRutaConMaximoMargen(pedido);
    //
    // if (mejorRuta == null) {
    // System.out.println("❌ No se encontró ninguna ruta con margen de tiempo
    // adecuado");
    // sinRutaMargen++;
    // paquetesNoAsignados.add(pedido);
    // continue;
    // }
    //
    // System.out.println("✅ Ruta encontrada con " + mejorRuta.size() + " vuelos");
    //
    // // Mostrar información del margen de tiempo
    // double margenTiempo = calcularMargenTiempoRuta(pedido, mejorRuta);
    // System.out.println("Margen de tiempo de la ruta: " + margenTiempo + "
    // horas");
    //
    // // Mostrar detalles de la ruta
    // //System.out.println("Detalles de la ruta:");
    // double tiempoTotal = 0;
    // for (int i = 0; i < mejorRuta.size(); i++) {
    // Vuelo vuelo = mejorRuta.get(i);
    // tiempoTotal += vuelo.getTiempoTransporte();
    // System.out.println(" Vuelo " + (i+1) + ": " +
    // vuelo.getAeropuertoOrigen().getCodigoIATA() + " → " +
    // vuelo.getAeropuertoDestino().getCodigoIATA() +
    // " (" + vuelo.getTiempoTransporte() + " horas)");
    // }
    // if (mejorRuta.size() > 1) {
    // tiempoTotal += (mejorRuta.size() - 1) * 2.0;
    // System.out.println(" + Tiempo de conexiones: " + ((mejorRuta.size() - 1) *
    // 2.0) + " horas");
    // }
    // System.out.println(" = Tiempo total: " + tiempoTotal + " horas");
    //
    // // Validar ruta
    // System.out.println("Validando ruta...");
    // if (!esRutaValida(pedido, mejorRuta, conteoProductos)) {
    // System.out.println("❌ Ruta no válida (no cumple validaciones)");
    // rutaNoValida++;
    // paquetesNoAsignados.add(pedido);
    // continue;
    // }
    //
    // System.out.println("✅ Ruta válida - Insertando en solución...");
    // solucionReparada.put(pedido, mejorRuta);
    // actualizarCapacidadesVuelos(mejorRuta, conteoProductos);
    // incrementarOcupacionAlmacen(aeropuertoDestino, conteoProductos);
    // conteoReinsertados++;
    //
    // System.out.println("✅ Pedido " + pedido.getId() + " reinsertado
    // exitosamente");
    // }
    //
    // System.out.println("\n=== RESUMEN REPARACIÓN POR TIEMPO ===");
    // System.out.println("Total procesados: " + paquetesParaReparar.size());
    // System.out.println("Reinsertados: " + conteoReinsertados);
    // System.out.println("Sin capacidad almacén: " + sinCapacidadAlmacen);
    // System.out.println("Sin ruta con margen: " + sinRutaMargen);
    // System.out.println("Ruta no válida: " + rutaNoValida);
    // System.out.println("Errores aeropuerto: " + erroresAeropuerto);
    // System.out.println("No asignados: " + paquetesNoAsignados.size());
    //
    // return new ResultadoReparacion(solucionReparada, paquetesNoAsignados);
    // }
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
            if (p1.getFechaLimiteEntrega() == null && p2.getFechaLimiteEntrega() == null)
                return 0;
            if (p1.getFechaLimiteEntrega() == null)
                return 1; // nulls last
            if (p2.getFechaLimiteEntrega() == null)
                return -1; // nulls last
            return p1.getFechaLimiteEntrega().compareTo(p2.getFechaLimiteEntrega());
        });

        int conteoReinsertados = 0;

        for (Pedido pedido : paquetesParaReparar) {
            Aeropuerto aeropuertoDestino = obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo());
            int conteoProductos = pedido.getCantidadProductosRapido();

            if (aeropuertoDestino == null) {
                System.out.println("❌ Aeropuerto destino no encontrado para pedido: " + pedido.getId() + " "
                        + pedido.getAeropuertoDestinoCodigo());
                exit(1);
            }
            if (!tieneCapacidadAlmacen(aeropuertoDestino, conteoProductos)) {
                paquetesNoAsignados.add(pedido);
                continue;
            }

            // Buscar ruta con mayor capacidad disponible
            ArrayList<Vuelo> mejorRuta = encontrarRutaConMaximaCapacidad(pedido);
            if (mejorRuta != null && esRutaValida(pedido, mejorRuta, conteoProductos)) {
                solucionReparada.put(pedido, mejorRuta);
                actualizarCapacidadesVuelos(mejorRuta, conteoProductos);
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

    // public ResultadoReparacion reparacionPorCapacidad(
    // HashMap<Pedido, ArrayList<Vuelo>> solucionParcial,
    // ArrayList<Map.Entry<Pedido, ArrayList<Vuelo>>> paquetesDestruidos) {
    //
    // System.out.println("\n=== INICIANDO REPARACIÓN POR CAPACIDAD ===");
    // System.out.println("Paquetes a reparar: " + paquetesDestruidos.size());
    // System.out.println("Solución parcial inicial: " + solucionParcial.size() + "
    // paquetes");
    //
    // HashMap<Pedido, ArrayList<Vuelo>> solucionReparada = new
    // HashMap<>(solucionParcial);
    // ArrayList<Pedido> paquetesNoAsignados = new ArrayList<>();
    //
    // ArrayList<Pedido> paquetesParaReparar = new ArrayList<>();
    // for (Map.Entry<Pedido, ArrayList<Vuelo>> entrada : paquetesDestruidos) {
    // paquetesParaReparar.add(entrada.getKey());
    // }
    //
    // System.out.println("Ordenando paquetes por deadline...");
    // // Ordenar por deadline como criterio secundario
    // // PATCH: Null-safe sorting
    // paquetesParaReparar.sort((p1, p2) -> {
    // if (p1.getFechaLimiteEntrega() == null && p2.getFechaLimiteEntrega() == null)
    // return 0;
    // if (p1.getFechaLimiteEntrega() == null) return 1; // nulls last
    // if (p2.getFechaLimiteEntrega() == null) return -1; // nulls last
    // return p1.getFechaLimiteEntrega().compareTo(p2.getFechaLimiteEntrega());
    // });
    //
    // int conteoReinsertados = 0;
    // int sinCapacidadAlmacen = 0;
    // int sinRutaCapacidad = 0;
    // int rutaNoValida = 0;
    // int erroresAeropuerto = 0;
    //
    // System.out.println("Procesando " + paquetesParaReparar.size() + "
    // paquetes...");
    //
    // for (Pedido pedido : paquetesParaReparar) {
    // System.out.println("\n--- Procesando pedido " + pedido.getId() + " ---");
    // System.out.println("Origen: " + pedido.getAeropuertoOrigenCodigo() +
    // " → Destino: " + pedido.getAeropuertoDestinoCodigo());
    //
    // Aeropuerto aeropuertoDestino =
    // obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo());
    // int conteoProductos = pedido.getProductos() != null ?
    // pedido.getProductos().size() : 1;
    //
    // System.out.println("Productos: " + conteoProductos);
    // System.out.println("Deadline: " + pedido.getFechaLimiteEntrega());
    //
    // // Verificar aeropuerto destino
    // if (aeropuertoDestino == null) {
    // System.out.println("❌ ERROR: Aeropuerto destino no encontrado: " +
    // pedido.getAeropuertoDestinoCodigo());
    // erroresAeropuerto++;
    // paquetesNoAsignados.add(pedido);
    // continue;
    // }
    //
    // // Verificar capacidad del almacén
    // System.out.println("Verificando capacidad en almacén " +
    // aeropuertoDestino.getCodigoIATA() +
    // " (ocupación: " + ocupacionAlmacenes.getOrDefault(aeropuertoDestino, 0) +
    // "/" + aeropuertoDestino.getCapacidadMaxima() + ")");
    //
    // if (!tieneCapacidadAlmacen(aeropuertoDestino, conteoProductos)) {
    // System.out.println("❌ SIN CAPACIDAD: Almacén " +
    // aeropuertoDestino.getCodigoIATA() +
    // " - Ocupación: " + ocupacionAlmacenes.getOrDefault(aeropuertoDestino, 0) +
    // "/" + aeropuertoDestino.getCapacidadMaxima());
    // sinCapacidadAlmacen++;
    // paquetesNoAsignados.add(pedido);
    // continue;
    // }
    //
    // System.out.println("✅ Capacidad de almacén OK");
    //
    // // Buscar ruta con mayor capacidad disponible
    // System.out.println("Buscando ruta con máxima capacidad...");
    // ArrayList<Vuelo> mejorRuta = encontrarRutaConMaximaCapacidad(pedido);
    //
    // if (mejorRuta == null) {
    // System.out.println("❌ No se encontró ninguna ruta con capacidad disponible");
    // sinRutaCapacidad++;
    // paquetesNoAsignados.add(pedido);
    // continue;
    // }
    //
    // System.out.println("✅ Ruta encontrada con " + mejorRuta.size() + " vuelos");
    //
    // // Mostrar información de capacidad de la ruta
    // System.out.println("Capacidad de la ruta encontrada:");
    // for (int i = 0; i < mejorRuta.size(); i++) {
    // Vuelo vuelo = mejorRuta.get(i);
    // System.out.println(" Vuelo " + (i+1) + ": " +
    // vuelo.getAeropuertoOrigen().getCodigoIATA() + " → " +
    // vuelo.getAeropuertoDestino().getCodigoIATA() +
    // " (" + vuelo.getCapacidadUsada() + "/" + vuelo.getCapacidadMaxima() + ")");
    // }
    //
    // // Validar ruta
    // System.out.println("Validando ruta...");
    // if (!esRutaValida(pedido, mejorRuta, conteoProductos)) {
    // System.out.println("❌ Ruta no válida (no cumple validaciones)");
    // rutaNoValida++;
    // paquetesNoAsignados.add(pedido);
    // continue;
    // }
    //
    // System.out.println("✅ Ruta válida - Insertando en solución...");
    // solucionReparada.put(pedido, mejorRuta);
    // actualizarCapacidadesVuelos(mejorRuta, conteoProductos);
    // incrementarOcupacionAlmacen(aeropuertoDestino, conteoProductos);
    // conteoReinsertados++;
    //
    // System.out.println("✅ Pedido " + pedido.getId() + " reinsertado
    // exitosamente");
    // }
    //
    // System.out.println("\n=== RESUMEN REPARACIÓN POR CAPACIDAD ===");
    // System.out.println("Total procesados: " + paquetesParaReparar.size());
    // System.out.println("Reinsertados: " + conteoReinsertados);
    // System.out.println("Sin capacidad almacén: " + sinCapacidadAlmacen);
    // System.out.println("Sin ruta con capacidad: " + sinRutaCapacidad);
    // System.out.println("Ruta no válida: " + rutaNoValida);
    // System.out.println("Errores aeropuerto: " + erroresAeropuerto);
    // System.out.println("No asignados: " + paquetesNoAsignados.size());
    //
    // return new ResultadoReparacion(solucionReparada, paquetesNoAsignados);
    // }
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
        boolean rutaMismoContinente = obtenerAeropuerto(pedido.getAeropuertoOrigenCodigo()).getCiudad()
                .getContinente() == obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo()).getCiudad().getContinente();
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

        // System.out.println("\n 🔍 BUSCANDO RUTAS PARA: " + codigoOrigen + " → " +
        // codigoDestino);

        // VERIFICAR DATOS PRIMERO
        // verificarVuelosDisponibles(codigoOrigen, codigoDestino);

        if (codigoOrigen.equals(codigoDestino)) {
            // System.out.println(" ✅ MISMO AEROPUERTO - RUTA VACÍA");
            opciones.add(new OpcionRuta(new ArrayList<>(), Double.MAX_VALUE));
            return opciones;
        }

        // Buscar ruta directa
        // System.out.println("\n --- BUSCANDO RUTA DIRECTA ---");
        ArrayList<Vuelo> rutaDirecta = encontrarRutaDirecta(codigoOrigen, codigoDestino);
        if (rutaDirecta != null && esRutaValida(pedido, rutaDirecta)) {
            double margen = calcularMargenTiempoRuta(pedido, rutaDirecta);
            opciones.add(new OpcionRuta(rutaDirecta, margen));
            // System.out.println(" ✅ RUTA DIRECTA VÁLIDA - Margen: " + margen);
        }

        // Buscar rutas con una escala
        // System.out.println("\n --- BUSCANDO RUTA CON 1 ESCALA ---");
        ArrayList<Vuelo> rutaUnaEscala = encontrarRutaUnaEscala(codigoOrigen, codigoDestino);
        if (rutaUnaEscala != null && esRutaValida(pedido, rutaUnaEscala)) {
            double margen = calcularMargenTiempoRuta(pedido, rutaUnaEscala);
            opciones.add(new OpcionRuta(rutaUnaEscala, margen));
            // System.out.println(" ✅ RUTA CON 1 ESCALA VÁLIDA - Margen: " + margen);
        }

        // Buscar rutas con dos escalas
        // System.out.println("\n --- BUSCANDO RUTA CON 2 ESCALAS ---");
        ArrayList<Vuelo> rutaDosEscalas = encontrarRutaDosEscalas(codigoOrigen, codigoDestino);
        if (rutaDosEscalas != null && esRutaValida(pedido, rutaDosEscalas)) {
            double margen = calcularMargenTiempoRuta(pedido, rutaDosEscalas);
            opciones.add(new OpcionRuta(rutaDosEscalas, margen));
            // System.out.println(" ✅ RUTA CON 2 ESCALAS VÁLIDA - Margen: " + margen);
        }

        // System.out.println(" 📊 TOTAL OPCIONES ENCONTRADAS: " + opciones.size());
        return opciones;
    }

    private ArrayList<Vuelo> encontrarMejorRuta(Pedido pedido) {
        ArrayList<OpcionRuta> opciones = encontrarTodasLasOpcionesRuta(pedido);
        if (opciones.isEmpty())
            return null;

        // Seleccionar la ruta con mayor margen de tiempo
        opciones.sort((r1, r2) -> Double.compare(r2.margenTiempo, r1.margenTiempo));
        return opciones.get(0).ruta;
    }

    private ArrayList<Vuelo> encontrarRutaConMaximoMargen(Pedido pedido) {
        return encontrarMejorRuta(pedido); // Ya implementado arriba
    }

    private ArrayList<Vuelo> encontrarRutaConMaximaCapacidad(Pedido pedido) {
        ArrayList<OpcionRuta> opciones = encontrarTodasLasOpcionesRuta(pedido);
        if (opciones.isEmpty())
            return null;

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

        // Ordenar por capacidad disponible, pero considerando también el margen de
        // tiempo
        opcionesCapacidad.sort((r1, r2) -> {
            // Priorizar rutas con capacidad disponible, pero no sacrificar entregas a
            // tiempo
            if (r1.margenTiempo <= 0 && r2.margenTiempo > 0)
                return 1;
            if (r2.margenTiempo <= 0 && r1.margenTiempo > 0)
                return -1;

            return Double.compare(r2.capacidadDisponible, r1.capacidadDisponible);
        });

        return opcionesCapacidad.get(0).ruta;
    }

    // private ArrayList<Vuelo> encontrarRutaConMaximaCapacidad(Pedido pedido) {
    // System.out.println(" Buscando ruta con máxima capacidad para pedido " +
    // pedido.getId());
    //
    // ArrayList<OpcionRuta> opciones = encontrarTodasLasOpcionesRuta(pedido);
    // if (opciones.isEmpty()) {
    // //System.out.println(" ❌ No hay opciones de ruta disponibles");
    // return null;
    // }
    //
    // System.out.println(" " + opciones.size() + " opciones de ruta encontradas");
    //
    // // Calcular capacidad disponible para cada ruta
    // ArrayList<OpcionCapacidadRuta> opcionesCapacidad = new ArrayList<>();
    // for (OpcionRuta opcion : opciones) {
    // double capacidadTotal = 0;
    // double capacidadUsada = 0;
    // double capacidadMinimaDisponible = Double.MAX_VALUE;
    //
    // System.out.println(" Analizando ruta con " + opcion.ruta.size() + "
    // vuelos:");
    //
    // for (Vuelo vuelo : opcion.ruta) {
    // capacidadTotal += vuelo.getCapacidadMaxima();
    // capacidadUsada += vuelo.getCapacidadUsada();
    // double capacidadDisponibleVuelo = vuelo.getCapacidadMaxima() -
    // vuelo.getCapacidadUsada();
    // capacidadMinimaDisponible = Math.min(capacidadMinimaDisponible,
    // capacidadDisponibleVuelo);
    //
    // System.out.println(" " + vuelo.getAeropuertoOrigen().getCodigoIATA() + " → "
    // +
    // vuelo.getAeropuertoDestino().getCodigoIATA() +
    // " - Usada: " + vuelo.getCapacidadUsada() +
    // "/Max: " + vuelo.getCapacidadMaxima() +
    // " (Disponible: " + capacidadDisponibleVuelo + ")");
    // }
    //
    // double ratioCapacidadDisponible = (capacidadTotal - capacidadUsada) /
    // Math.max(1, capacidadTotal);
    // System.out.println(" Ratio capacidad disponible: " + String.format("%.2f",
    // ratioCapacidadDisponible * 100) + "%");
    // System.out.println(" Capacidad mínima disponible en un vuelo: " +
    // capacidadMinimaDisponible);
    // System.out.println(" Margen de tiempo: " + opcion.margenTiempo);
    //
    // opcionesCapacidad.add(new OpcionCapacidadRuta(opcion.ruta,
    // ratioCapacidadDisponible, opcion.margenTiempo));
    // }
    //
    // // Ordenar por capacidad disponible, pero considerando también el margen de
    // tiempo
    // opcionesCapacidad.sort((r1, r2) -> {
    // // Priorizar rutas con capacidad disponible, pero no sacrificar entregas a
    // tiempo
    // if (r1.margenTiempo <= 0 && r2.margenTiempo > 0) {
    // System.out.println(" Priorizando ruta 2 por margen de tiempo positivo");
    // return 1;
    // }
    // if (r2.margenTiempo <= 0 && r1.margenTiempo > 0) {
    // System.out.println(" Priorizando ruta 1 por margen de tiempo positivo");
    // return -1;
    // }
    //
    // int comparacion = Double.compare(r2.capacidadDisponible,
    // r1.capacidadDisponible);
    // System.out.println(" Comparando capacidad: " + r1.capacidadDisponible + " vs
    // " + r2.capacidadDisponible + " = " + comparacion);
    // return comparacion;
    // });
    //
    // ArrayList<Vuelo> mejorRuta = opcionesCapacidad.get(0).ruta;
    // System.out.println(" ✅ Mejor ruta seleccionada con capacidad disponible: " +
    // String.format("%.2f", opcionesCapacidad.get(0).capacidadDisponible * 100) +
    // "%");
    //
    // return mejorRuta;
    // }
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
        if (presupuesto < 0)
            presupuesto = 0; // Clamp negativo

        double margen = presupuesto - tiempoTotal;
        return Math.max(margen, 0.0) + 1.0;
    }

    /**
     * PATCH: Helper para validar capacidad de ruta con cantidad específica
     */
    private boolean cabeEnCapacidadRuta(ArrayList<Vuelo> ruta, int cantidad) {
        if (ruta == null)
            return false;
        for (Vuelo f : ruta) {
            if (f.getCapacidadUsada() + cantidad > f.getCapacidadMaxima())
                return false;
        }
        return true;
    }

    /**
     * PATCH: Versión con cantidad específica de productos
     */
    private boolean esRutaValida(Pedido pedido, ArrayList<Vuelo> ruta, int cantidad) {
        // System.out.println(" Validando ruta para pedido " + pedido.getId() + "...");

        if (ruta == null || ruta.isEmpty()) {
            boolean mismoAeropuerto = obtenerAeropuerto(pedido.getAeropuertoOrigenCodigo()).getCiudad()
                    .equals(obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo()).getCiudad());
            System.out.println("  Ruta vacía - Mismo aeropuerto: " + mismoAeropuerto);
            return mismoAeropuerto;
        }

        // Verificar capacidad de vuelos con cantidad específica
        if (!cabeEnCapacidadRuta(ruta, cantidad)) {
            // System.out.println(" ❌ No cabe en capacidad de vuelos");
            return false;
        }
        // System.out.println(" ✅ Capacidad de vuelos OK");

        // Verificar continuidad de ruta
        Ciudad ubicacionActual = obtenerAeropuerto(pedido.getAeropuertoOrigenCodigo()).getCiudad();
        for (int i = 0; i < ruta.size(); i++) {
            Vuelo vuelo = ruta.get(i);
            String aeropuertoActualCodigo = vuelo.getAeropuertoOrigen().getCodigoIATA();
            if (ruta.size() == 1 && !aeropuertoActualCodigo.equals(pedido.getAeropuertoDestinoCodigo())) {
                return false;
            } else if (i != 0) {
                // verificamos vuelos
                if (!aeropuertoActualCodigo.equals(ruta.get(i - 1).getAeropuertoDestino().getCodigoIATA()))
                    return false;
            }
        }

        // Verificar deadline
        boolean deadlineOk = seRespetaDeadline(pedido, ruta);
        // System.out.println(" ✅ Deadline respetado: " + deadlineOk);

        return deadlineOk;
    }
    /**
     * PATCH: Versión con cantidad específica de productos
     */

    // private boolean esRutaValida(Pedido pedido, ArrayList<Vuelo> ruta, int
    // cantidad) {
    // System.out.println(" 🛂 VALIDANDO RUTA para pedido " + pedido.getId());
    //
    // if (ruta == null || ruta.isEmpty()) {
    // System.out.println(" ❌ Ruta nula o vacía");
    // boolean mismoAeropuerto =
    // obtenerAeropuerto(pedido.getAeropuertoOrigenCodigo()).getCiudad().
    // equals(obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo()).getCiudad());
    // System.out.println(" Mismo aeropuerto: " + mismoAeropuerto);
    // return mismoAeropuerto;
    // }
    //
    // System.out.println(" Ruta con " + ruta.size() + " vuelos:");
    // for (int i = 0; i < ruta.size(); i++) {
    // Vuelo v = ruta.get(i);
    // System.out.println(" Vuelo " + (i+1) + ": " +
    // v.getAeropuertoOrigen().getCodigoIATA() + " → " +
    // v.getAeropuertoDestino().getCodigoIATA());
    // }
    //
    // // Verificar capacidad de vuelos con cantidad específica
    // System.out.println(" Verificando capacidad...");
    // if (!cabeEnCapacidadRuta(ruta, cantidad)) {
    // System.out.println(" ❌ No cabe en capacidad de vuelos");
    // return false;
    // }
    // System.out.println(" ✅ Capacidad de vuelos OK");
    //
    // // Verificar continuidad de ruta
    // System.out.println(" Verificando continuidad...");
    // Ciudad ubicacionActual =
    // obtenerAeropuerto(pedido.getAeropuertoOrigenCodigo()).getCiudad();
    // System.out.println(" Ubicación inicial: " + ubicacionActual.getNombre());
    //
    // for (int i = 0; i < ruta.size(); i++) {
    // Vuelo vuelo = ruta.get(i);
    // Aeropuerto aeropuertoActual = obtenerAeropuertoPorCiudad(ubicacionActual);
    //
    // System.out.println(" Vuelo " + (i+1) + ":");
    // System.out.println(" Origen esperado: " + aeropuertoActual.getCodigoIATA());
    // System.out.println(" Origen real: " +
    // vuelo.getAeropuertoOrigen().getCodigoIATA());
    //
    // if
    // (!vuelo.getAeropuertoOrigen().getCodigoIATA().equals(aeropuertoActual.getCodigoIATA()))
    // {
    // System.out.println(" ❌ Discontinuidad en vuelo " + i +
    // ": esperado " + aeropuertoActual.getCodigoIATA() +
    // ", encontrado " + vuelo.getAeropuertoOrigen().getCodigoIATA());
    // return false;
    // }
    // ubicacionActual = obtenerCiudadPorAeropuerto(vuelo.getAeropuertoDestino());
    // System.out.println(" Nuevo ubicación: " + ubicacionActual.getNombre());
    // }
    //
    // Ciudad destinoFinal =
    // obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo()).getCiudad();
    // System.out.println(" Destino final esperado: " + destinoFinal.getNombre());
    // System.out.println(" Destino final alcanzado: " +
    // ubicacionActual.getNombre());
    //
    // if (!ubicacionActual.getNombre().equals(destinoFinal.getNombre())) {
    // System.out.println(" ❌ Destino final incorrecto: " +
    // ubicacionActual.getNombre() +
    // " vs " + destinoFinal.getNombre());
    // return false;
    // }
    // System.out.println(" ✅ Continuidad de ruta OK");
    //
    // // Verificar deadline
    // System.out.println(" Verificando deadline...");
    // boolean deadlineOk = seRespetaDeadline(pedido, ruta);
    // System.out.println(" ✅ Deadline respetado: " + deadlineOk);
    //
    // return deadlineOk;
    // }
    /**
     * PATCH: Versión original que delega calculando cantidad
     * OPTIMIZADO: Usar getCantidadProductosRapido()
     */
    private boolean esRutaValida(Pedido pedido, ArrayList<Vuelo> ruta) {
        int cantidad = pedido.getCantidadProductosRapido();
        return esRutaValida(pedido, ruta, cantidad);
    }

    /**
     * CORRECCIÓN: Aplicar las mismas correcciones que Solution.java
     */
    private boolean seRespetaDeadline(Pedido pedido, ArrayList<Vuelo> ruta) {
        // System.out.println(" Validando deadline para pedido " + pedido.getId() +
        // "...");

        double tiempoTotal = 0;

        // CORRECCIÓN: Solo usar transportTime de vuelos (sin doble conteo)
        for (Vuelo vuelo : ruta) {
            tiempoTotal += vuelo.getTiempoTransporte();
        }

        // Añadir penalización por conexiones
        if (ruta.size() > 1) {
            tiempoTotal += (ruta.size() - 1) * 2.0;
        }

        // System.out.println(" Tiempo total ruta: " + tiempoTotal + " horas");

        // CORRECCIÓN: Validar promesas MoraPack explícitamente
        Ciudad origen = obtenerAeropuerto(pedido.getAeropuertoOrigenCodigo()).getCiudad();
        Ciudad destino = obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo()).getCiudad();
        boolean rutaMismoContinente = origen.getContinente() == destino.getContinente();
        long promesaHorasMoraPack = rutaMismoContinente ? 48 : 72;

        // System.out.println(" Promesa MoraPack: " + promesaHorasMoraPack + " horas");
        // System.out.println(" Mismo continente: " + rutaMismoContinente);

        if (tiempoTotal > promesaHorasMoraPack) {
            // System.out.println(" ❌ Excede promesa MoraPack: " + tiempoTotal + " > " +
            // promesaHorasMoraPack);
            return false;
        }

        // CORRECCIÓN: Usar orderDate en lugar de "now"
        long horasHastaDeadline = ChronoUnit.HOURS.between(pedido.getFechaPedido(), pedido.getFechaLimiteEntrega());
        // System.out.println(" Horas hasta deadline: " + horasHastaDeadline);

        boolean resultado = tiempoTotal <= horasHastaDeadline;
        // System.out.println(" ✅ Deadline respetado: " + resultado + " (" + tiempoTotal
        // + " <= " + horasHastaDeadline + ")");

        return resultado;
    }

    // Métodos de búsqueda de rutas (simplificados, podrían referenciar a
    // Solution.java)
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

        // System.out.println(" ❌ No se encontró vuelo directo");
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

            if (primerVuelo == null)
                continue;

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

        if (candidatos.size() < 2)
            return null;

        Collections.shuffle(candidatos, aleatorio);
        int maxIntentos = Math.min(10, candidatos.size() - 1);

        for (int i = 0; i < maxIntentos; i++) {
            Aeropuerto primero = candidatos.get(i);
            for (int j = i + 1; j < Math.min(i + 5, candidatos.size()); j++) {
                Aeropuerto segundo = candidatos.get(j);

                ArrayList<Vuelo> ruta = intentarRutaDosEscalas(codigoOrigen, primero.getCodigoIATA(),
                        segundo.getCodigoIATA(), codigoDestino);
                if (ruta != null)
                    return ruta;

                // También probar en orden inverso
                ruta = intentarRutaDosEscalas(codigoOrigen, segundo.getCodigoIATA(),
                        primero.getCodigoIATA(), codigoDestino);
                if (ruta != null)
                    return ruta;
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

        if (vuelo1 == null)
            return null;

        // Buscar vuelo 2: primero -> segundo
        for (Vuelo vuelo : vuelos) {
            if (vuelo.getAeropuertoOrigen().getCodigoIATA().equals(primero) &&
                    vuelo.getAeropuertoDestino().getCodigoIATA().equals(segundo) &&
                    vuelo.getCapacidadUsada() < vuelo.getCapacidadMaxima()) {
                vuelo2 = vuelo;
                break;
            }
        }

        if (vuelo2 == null)
            return null;

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
        if (ciudad == null || ciudad.getNombre() == null)
            return null;

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
        if (aeropuertoDestino == null)
            return false;

        // Evitar null en el mapa
        // if (!ocupacionAlmacenes.containsKey(aeropuertoDestino)) {
        // ocupacionAlmacenes.put(aeropuertoDestino, 0);
        // }

        int capacidad = aeropuertoDestino.getCapacidadMaxima();
        // int ocupacion = ocupacionAlmacenes.getOrDefault(aeropuertoDestino, 0);
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
                // System.out.println("✅ Aeropuerto encontrado: " + codigoIATA);
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
        if (!hayDirectos)
            System.out.println("  ❌ No hay vuelos directos");

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
        for (Aeropuerto aeropuerto : aeropuertos) {
            if (aeropuerto.getCodigoIATA().equals(codigoAeropuertoDestino)) {
                int capacidadActual = aeropuerto.getCapacidadActual();
                aeropuerto.setCapacidadActual(capacidadActual + cantidad);
                break;
            }
        }
    }
}