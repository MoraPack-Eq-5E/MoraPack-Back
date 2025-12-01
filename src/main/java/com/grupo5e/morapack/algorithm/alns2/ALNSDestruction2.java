package com.grupo5e.morapack.algorithm.alns2;

import com.grupo5e.morapack.core.enums.Continente;
import com.grupo5e.morapack.core.model.Aeropuerto;
import com.grupo5e.morapack.core.model.Pedido;
import com.grupo5e.morapack.core.model.Vuelo;
import com.grupo5e.morapack.service.AeropuertoService;

import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Clase que implementa operadores de destrucción para el algoritmo ALNS
 * (Adaptive Large Neighborhood Search) específicamente diseñados para el problema
 * de logística MoraPack.
 *
 * Los operadores están diseñados para preservar la prioridad de entregas a tiempo.
 */
public class ALNSDestruction2 {

    private ArrayList<Aeropuerto> aeropuertos;
    private Random aleatorio;
    private final AeropuertoService aeropuertoService;

    // Añadir campo
    private final CapacidadInstanciasManager capacidadManager;

    // Nuevo constructor (reemplaza o sobrecarga los existentes)
    public ALNSDestruction2(ArrayList<Aeropuerto> aeropuertos, CapacidadInstanciasManager capacidadManager) {
        this.aeropuertos = aeropuertos;
        this.aleatorio = new Random(System.currentTimeMillis());
        this.aeropuertoService = null;
        this.capacidadManager = capacidadManager;
    }

    /**
     * CORRECCIÓN: Helpers para cálculos precisos de slack y productos
     */
    private static double horasRuta(ArrayList<Vuelo> ruta) {
        if (ruta == null || ruta.isEmpty()) return Double.POSITIVE_INFINITY;
        double h = 0;
        for (Vuelo f : ruta) h += f.getTiempoTransporte();
        if (ruta.size() > 1) h += (ruta.size() - 1) * 2.0; // conexiones de 2h
        return h;
    }

    private static int productosDe(Pedido pedido) {
        return (pedido.getProductos() != null && !pedido.getProductos().isEmpty()) ? pedido.getProductos().size() : 1;
    }

    /**
     * CORRECCIÓN: Slack real - horas disponibles desde fechaPedido menos horas de la ruta actual
     * REFINAMIENTO: Clampar slack negativo por deadlines raros (deadline < fechaPedido)
     */
    private static double slackHoras(Pedido pedido, ArrayList<Vuelo> ruta) {
        long presupuesto = ChronoUnit.HOURS.between(pedido.getFechaPedido(), pedido.getFechaLimiteEntrega());
        // REFINAMIENTO: Clampar budget negativo a 0 para evitar data mala
        if (presupuesto < 0) {
            presupuesto = 0; // Deadline en el pasado o mal configurado
        }
        double slack = presupuesto - horasRuta(ruta);
        return Math.max(slack, 0.0); // REFINAMIENTO: Clampar slack final a >= 0
    }

    /**
     * REFINAMIENTO: Verificar si un paquete ya está en destino (ruta null/empty)
     * Estos paquetes no liberan capacidad de vuelo
     */
    private static boolean yaEstaEnDestino(ArrayList<Vuelo> ruta) {
        return ruta == null || ruta.isEmpty();
    }

    /**
     * CORRECCIÓN: Destrucción aleatoria mejorada - sesgo por mayor slack y más productos
     */
    public ResultadoDestruccion destruccionAleatoria(
            HashMap<Pedido, ArrayList<Vuelo>> solucionActual,
            double ratioDestruccion,
            int minDestruir,
            int maxDestruir) {

        //copiar la solucion
        HashMap<Pedido, ArrayList<Vuelo>> solucionParcial = new HashMap<>(solucionActual);

        if (solucionActual.isEmpty()) {
            return new ResultadoDestruccion(solucionParcial, new ArrayList<>());
        }

        // CORRECCIÓN: Construir lista con score = w1*slack + w2*productos
        class Candidato {
            Pedido paquete;
            ArrayList<Vuelo> ruta;
            double puntuacion;
        }

        ArrayList<Candidato> candidatos = new ArrayList<>();
        for (Map.Entry<Pedido, ArrayList<Vuelo>> e : solucionActual.entrySet()) {
            Pedido p = e.getKey();
            ArrayList<Vuelo> r = e.getValue();
            //Holgura de tiempo: cuantas Cuántas horas de sobra tiene el pedido antes de su deadline.
            //Cuanto más slack, menos urgente es.
            double slack = slackHoras(p, r);
            int productos = productosDe(p);

            // REFINAMIENTO: Penalizar fuertemente paquetes ya en destino (no liberan capacidad de vuelo)
            if (yaEstaEnDestino(r)) {
                slack = slack * 0.1; // Reducir significativamente su prioridad
            }
            //Los pedidos con mas slack obtienen mayor puntuacion, por lo tanto es mas probable que sean destruidos
            double puntuacion = 1.0 * slack + 0.2 * productos; // pesos: slack y productos

            Candidato c = new Candidato();
            c.paquete = p;
            c.ruta = r;
            c.puntuacion = puntuacion;
            candidatos.add(c);
        }

        // CORRECCIÓN: Ordenar por puntuacion desc y destruir los top-k con diversidad
        //Se ordenan de mayor a menor puntuación:
        //Los primeros del listado son los más “prescindibles” (porque tienen mucho margen o son grandes).
        candidatos.sort((a, b) -> Double.compare(b.puntuacion, a.puntuacion));

        //Determinar cuántos destruir
        int numDestruir = Math.min(
            Math.max((int)(solucionActual.size() * ratioDestruccion), minDestruir),
            Math.min(maxDestruir, solucionActual.size())
        );

        ArrayList<Map.Entry<Pedido, ArrayList<Vuelo>>> destruidos = new ArrayList<>();
        int tomados = 0, i = 0;

        while (tomados < numDestruir && i < candidatos.size()) {
            // CORRECCIÓN: 10% probabilidad de saltar para diversidad
            //Usa una pequeña aleatoriedad (10%) para no ser 100% determinista:
            if (aleatorio.nextDouble() < 0.10 && i + 1 < candidatos.size()) {
                i++;
            }

            Pedido seleccionado = candidatos.get(i).paquete;
            ArrayList<Vuelo> ruta = solucionActual.get(seleccionado);
            if (ruta == null) ruta = new ArrayList<>(); // Protección contra nulos

            destruidos.add(new AbstractMap.SimpleEntry<>(seleccionado, new ArrayList<>(ruta)));
            if (capacidadManager != null) {
                capacidadManager.liberarCapacidadYAssignments(seleccionado, ruta);
            }
            solucionParcial.remove(seleccionado);
            tomados++;
            i++;
        }

        return new ResultadoDestruccion(solucionParcial, destruidos);
    }

    /**
     * Destrucción por zona geográfica: elimina paquetes de un continente específico.
     * Útil para liberar capacidad en rutas intercontinentales.
     */
    public ResultadoDestruccion destruccionGeografica(
            HashMap<Pedido, ArrayList<Vuelo>> solucionActual,
            double ratioDestruccion,
            int minDestruir,
            int maxDestruir) {

        //Creando copia de la solucion actual
        HashMap<Pedido, ArrayList<Vuelo>> solucionParcial = new HashMap<>(solucionActual);

        if (solucionActual.isEmpty()) {
            return new ResultadoDestruccion(solucionParcial, new ArrayList<>());
        }

        // Contar paquetes por continente (origen y destino)
        Map<Continente, ArrayList<Pedido>> paquetesPorContinenteOrigen = new HashMap<>();
        Map<Continente, ArrayList<Pedido>> paquetesPorContinenteDestino = new HashMap<>();

        for (Pedido pedido : solucionActual.keySet()) {
            Continente continenteOrigen = obtenerAeropuerto(pedido.getAeropuertoOrigenCodigo()).getCiudad().getContinente();
            Continente continenteDestino = obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo()).getCiudad().getContinente();

            paquetesPorContinenteOrigen.computeIfAbsent(continenteOrigen, k -> new ArrayList<>()).add(pedido);
            paquetesPorContinenteDestino.computeIfAbsent(continenteDestino, k -> new ArrayList<>()).add(pedido);
        }

        // Seleccionar continente con más paquetes intercontinentales
        Continente continenteSeleccionado = null;
        int maxPaquetesIntercontinentales = 0;

        for (Continente continente : Continente.values()) {
            ArrayList<Pedido> paquetesOrigen = paquetesPorContinenteOrigen.getOrDefault(continente, new ArrayList<>());
            int conteoIntercontinental = 0;

            for (Pedido pedido : paquetesOrigen) {
                if (obtenerAeropuerto(pedido.getAeropuertoOrigenCodigo()).getCiudad().getContinente()
                        != obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo()).getCiudad().getContinente()) {
                    conteoIntercontinental++;
                }
            }

            if (conteoIntercontinental > maxPaquetesIntercontinentales) {
                maxPaquetesIntercontinentales = conteoIntercontinental;
                continenteSeleccionado = continente;
            }
        }

        if (continenteSeleccionado == null) {
            return destruccionAleatoria(solucionActual, ratioDestruccion, minDestruir, maxDestruir);
        }

        // Encontrar paquetes del continente seleccionado y se agregar a la lista de zona afectada
        ArrayList<Pedido> paquetesCandidatoes = new ArrayList<>();
        for (Pedido pedido : solucionActual.keySet()) {
            if (obtenerAeropuerto(pedido.getAeropuertoOrigenCodigo()).getCiudad().getContinente() == continenteSeleccionado ||
                    obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo()).getCiudad().getContinente() == continenteSeleccionado) {
                paquetesCandidatoes.add(pedido);
            }
        }
        //si la cantidad de paquetes es menor entonces no es suficiente este criterio y mejor se usa el aleatorio
        //OJO CON ESTO SI ENTRA ACA COMO EL ALGORITMO SABE QUE SE USO PARA AUMENTAR ESA COMBINACION
        if (paquetesCandidatoes.size() < minDestruir) {
            return destruccionAleatoria(solucionActual, ratioDestruccion, minDestruir, maxDestruir);
        }

        int numDestruir = Math.min(
            Math.max((int)(paquetesCandidatoes.size() * ratioDestruccion), minDestruir),
            Math.min(maxDestruir, paquetesCandidatoes.size())
        );

        ArrayList<Map.Entry<Pedido, ArrayList<Vuelo>>> paquetesDestruidos = new ArrayList<>();

        // REFINAMIENTO: Precomputar slack y productos para evitar recalcular en comparator
        class InformacionCandidato {
            Pedido paquete;
            boolean intercontinental;
            double slack;
            int productos;
            boolean enDestino;
        }

        ArrayList<InformacionCandidato> candidatos = new ArrayList<>();
        for (Pedido pedido : paquetesCandidatoes) {
            InformacionCandidato info = new InformacionCandidato();
            info.paquete = pedido;
            info.intercontinental = obtenerAeropuerto(pedido.getAeropuertoOrigenCodigo()).getCiudad().getContinente()
                    != obtenerAeropuerto(pedido.getAeropuertoDestinoCodigo()).getCiudad().getContinente();

            ArrayList<Vuelo> ruta = solucionActual.get(pedido);
            info.slack = slackHoras(pedido, ruta);
            info.productos = productosDe(pedido);
            info.enDestino = yaEstaEnDestino(ruta);

            candidatos.add(info);
        }

        //Ordenar los candidatos según prioridad
        //Se eliminan primero los pedidos grandes, no urgentes y que cruzan continentes.

        // Priorizar: 1) intercontinental, 2) NO en destino, 3) mayor slack, 4) más productos
        candidatos.sort((c1, c2) -> {
            // Primero: intercontinental vs continental
            if (c1.intercontinental != c2.intercontinental) {
                return Boolean.compare(c2.intercontinental, c1.intercontinental);
            }

            // REFINAMIENTO: Segundo: evitar paquetes ya en destino
            if (c1.enDestino != c2.enDestino) {
                return Boolean.compare(c1.enDestino, c2.enDestino);
            }

            // Tercero: mayor slack
            int comparacionSlack = Double.compare(c2.slack, c1.slack);
            if (comparacionSlack != 0) {
                return comparacionSlack;
            }

            // REFINAMIENTO: Cuarto: tie-break por más productos (liberar más capacidad)
            return Integer.compare(c2.productos, c1.productos);
        });

        // Extraer los paquetes ordenados
        paquetesCandidatoes.clear();
        for (InformacionCandidato info : candidatos) {
            paquetesCandidatoes.add(info.paquete);
        }

        // Seleccionar paquetes con sesgo hacia los intercontinentales
        for (int i = 0; i < numDestruir; i++) {
            Pedido pedidoSeleccionado = paquetesCandidatoes.get(i);
            // REFINAMIENTO: Consistencia - usar siempre solucionActual como fuente
            ArrayList<Vuelo> ruta = solucionActual.get(pedidoSeleccionado);
            if (ruta == null) ruta = new ArrayList<>(); // Protección contra nulos

            paquetesDestruidos.add(new AbstractMap.SimpleEntry<>(
                    pedidoSeleccionado,
                new ArrayList<>(ruta)
            ));
            if (capacidadManager != null) {
                capacidadManager.liberarCapacidadYAssignments(pedidoSeleccionado, ruta);
            }
            solucionParcial.remove(pedidoSeleccionado);
        }

        System.out.println("Destrucción geográfica: " + numDestruir +
                          " paquetes eliminados del continente " + continenteSeleccionado);

        return new ResultadoDestruccion(solucionParcial, paquetesDestruidos);
    }

    /**
     * Destrucción por tiempo: elimina paquetes con deadlines en un rango específico.
     * Útil para rebalancear la carga temporal.
     */
    public ResultadoDestruccion destruccionBasadaEnTiempo(
            HashMap<Pedido, ArrayList<Vuelo>> solucionActual,
            double ratioDestruccion,
            int minDestruir,
            int maxDestruir) {

        //COPIA DE SOLUCION ACTUAL
        HashMap<Pedido, ArrayList<Vuelo>> solucionParcial = new HashMap<>(solucionActual);

        if (solucionActual.isEmpty()) {
            return new ResultadoDestruccion(solucionParcial, new ArrayList<>());
        }

        // CORRECCIÓN: Agrupar por slack real, no por "horas a deadline"
        ArrayList<Pedido> slackBajo = new ArrayList<>();    // slack ≤ 8 h (no tocar si es posible)
        ArrayList<Pedido> slackMedio = new ArrayList<>();    // 8–32 h
        ArrayList<Pedido> slackAlto = new ArrayList<>();   // > 32 h
        ArrayList<Pedido> enDestino = new ArrayList<>(); // REFINAMIENTO: Separar paquetes ya en destino

        //CLASIFICA CADA PEDIDOS SEGUN SEA EL TIPO
        for (Map.Entry<Pedido, ArrayList<Vuelo>> e : solucionActual.entrySet()) {
            Pedido pedido = e.getKey();
            ArrayList<Vuelo> ruta = e.getValue();

            // REFINAMIENTO: Separar paquetes ya en destino (fallback only)
            if (yaEstaEnDestino(ruta)) {
                enDestino.add(pedido);
                continue;
            }

            //CALCULA EL SLACK DE HORAS
            double s = slackHoras(pedido, ruta);
            if (s <= 8) {
                slackBajo.add(pedido);
            } else if (s <= 32) {
                slackMedio.add(pedido);
            } else {
                slackAlto.add(pedido);
            }
        }

        // REFINAMIENTO: Elige grupo prioritariamente, usando enDestino como último recurso
        ArrayList<Pedido> grupoSeleccionado;
        String nombreGrupo;

        if (!slackAlto.isEmpty()) {
            grupoSeleccionado = slackAlto;
            nombreGrupo = "alto slack";
        } else if (!slackMedio.isEmpty()) {
            grupoSeleccionado = slackMedio;
            nombreGrupo = "slack medio";
        } else if (!slackBajo.isEmpty()) {
            grupoSeleccionado = slackBajo;
            nombreGrupo = "bajo slack";
        } else {
            grupoSeleccionado = enDestino;
            nombreGrupo = "ya en destino (fallback)";
        }

        if (grupoSeleccionado.size() < minDestruir) {
            return destruccionAleatoria(solucionActual, ratioDestruccion, minDestruir, maxDestruir);
        }

        // REFINAMIENTO: Ordenar por productos desc para tie-break (más productos = más capacidad liberada)
        if (grupoSeleccionado != enDestino) { // Solo si no son paquetes en destino
            grupoSeleccionado.sort((p1, p2) -> {
                int productosP1 = productosDe(p1);
                int productosP2 = productosDe(p2);
                return Integer.compare(productosP2, productosP1); // Más productos primero
            });
        }

        // Barajar parcialmente para diversidad (mantener bias hacia más productos en el top)
        if (grupoSeleccionado.size() > 10) {
            // Solo barajar los últimos elementos, mantener los primeros (más productos) intactos
            Collections.shuffle(grupoSeleccionado.subList(5, grupoSeleccionado.size()), aleatorio);
        } else {
            Collections.shuffle(grupoSeleccionado, aleatorio);
        }

        int numDestruir = Math.min(
            Math.max((int)(grupoSeleccionado.size() * ratioDestruccion), minDestruir),
            Math.min(maxDestruir, grupoSeleccionado.size())
        );

        ArrayList<Map.Entry<Pedido, ArrayList<Vuelo>>> destruidos = new ArrayList<>();
        for (int i = 0; i < numDestruir; i++) {
            Pedido seleccionado = grupoSeleccionado.get(i);
            ArrayList<Vuelo> ruta = solucionActual.get(seleccionado);
            if (ruta == null) ruta = new ArrayList<>(); // Protección contra nulos

            destruidos.add(new AbstractMap.SimpleEntry<>(seleccionado, new ArrayList<>(ruta)));
            // liberar capacidad y assignments usando el manager (si está inyectado)
            if (capacidadManager != null) {
                capacidadManager.liberarCapacidadYAssignments(seleccionado, ruta);
            }
            solucionParcial.remove(seleccionado);
        }

        System.out.println("Destrucción temporal por slack: " + numDestruir + " paquetes del grupo " + nombreGrupo);

        return new ResultadoDestruccion(solucionParcial, destruidos);
    }

    /**
     * CORRECCIÓN COMPLETA: Destrucción de rutas congestionadas con scoring por
     * vuelo crítico + productos - urgencia
     */
    public ResultadoDestruccion destruccionRutaCongestionada(
            HashMap<Pedido, ArrayList<Vuelo>> solucionActual,
            double ratioDestruccion,
            int minDestruir,
            int maxDestruir) {
        //HACE UNA COPIA A LA SOLUCION ACTUAL
        HashMap<Pedido, ArrayList<Vuelo>> solucionParcial = new HashMap<>(solucionActual);
        if (solucionActual.isEmpty()) {
            return new ResultadoDestruccion(solucionParcial, new ArrayList<>());
        }

        // CORRECCIÓN: Parámetros de scoring mejorados
        final double UMBRAL_UTILIZACION = 0.85;   // umbral de "crítico"
        final double PESO_UTILIZACION = 1.0;            // peso de congestión
        final double PESO_PRODUCTOS = 0.25;          // peso por productos
        final double PENALIZACION_SLACK = 0.5;   // penaliza baja holgura

        // CORRECCIÓN: Score por paquete basado en congestión crítica + productos - urgencia
        class Candidato {
            Pedido paquete;
            ArrayList<Vuelo> ruta;
            double puntuacion;
        }

        ArrayList<Candidato> candidatos = new ArrayList<>();

        for (Map.Entry<Pedido, ArrayList<Vuelo>> e : solucionActual.entrySet()) {
            Pedido p = e.getKey();
            ArrayList<Vuelo> r = e.getValue();
            if (r == null || r.isEmpty()) continue;

            int productos = productosDe(p);

            // CORRECCIÓN: Congestión acumulada en vuelos por encima del umbral
            double congestion = 0.0;
            for (Vuelo f : r) {
                double utilizacion = (f.getCapacidadMaxima() > 0) ?
                    ((double) f.getCapacidadUsada() / f.getCapacidadMaxima()) : 0.0;
                if (utilizacion > UMBRAL_UTILIZACION) {
                    congestion += (utilizacion - UMBRAL_UTILIZACION);
                }
            }

            // CORRECCIÓN: Penalizar quitar paquetes con poca holgura
            double slack = slackHoras(p, r);
            double penalizacionSlack = (slack <= 8) ? (8 - Math.max(slack, 0)) : 0.0;

            double puntuacion = PESO_UTILIZACION * congestion + PESO_PRODUCTOS * productos - PENALIZACION_SLACK * penalizacionSlack;
            if (puntuacion > 0) {
                Candidato c = new Candidato();
                c.paquete = p;
                c.ruta = r;
                c.puntuacion = puntuacion;
                candidatos.add(c);
            }
        }

        if (candidatos.size() < minDestruir) {
            return destruccionAleatoria(solucionActual, ratioDestruccion, minDestruir, maxDestruir);
        }

        // CORRECCIÓN: Ordenar por puntuacion desc (más alivio esperado primero)
        candidatos.sort((a, b) -> Double.compare(b.puntuacion, a.puntuacion));

        int numDestruir = Math.min(
            Math.max((int)(candidatos.size() * ratioDestruccion), minDestruir),
            Math.min(maxDestruir, candidatos.size())
        );

        ArrayList<Map.Entry<Pedido, ArrayList<Vuelo>>> destruidos = new ArrayList<>();
        for (int i = 0; i < numDestruir; i++) {
            Pedido seleccionado = candidatos.get(i).paquete;
            // REFINAMIENTO: Consistencia - usar solucionActual como fuente
            ArrayList<Vuelo> ruta = solucionActual.get(seleccionado);
            if (ruta == null) ruta = new ArrayList<>(); // Protección contra nulos

            destruidos.add(new AbstractMap.SimpleEntry<>(seleccionado, new ArrayList<>(ruta)));
            if (capacidadManager != null) {
                capacidadManager.liberarCapacidadYAssignments(seleccionado, ruta);
            }
            solucionParcial.remove(seleccionado);
        }

        System.out.println("Destrucción por congestión (mejorada): " + numDestruir + " paquetes");
        return new ResultadoDestruccion(solucionParcial, destruidos);
    }

    /**
     * Clase para encapsular el resultado de una operación de destrucción
     */
    public static class ResultadoDestruccion {
        private HashMap<Pedido, ArrayList<Vuelo>> solucionParcial;
        private ArrayList<Map.Entry<Pedido, ArrayList<Vuelo>>> paquetesDestruidos;

        public ResultadoDestruccion(
                HashMap<Pedido, ArrayList<Vuelo>> solucionParcial,
                ArrayList<Map.Entry<Pedido, ArrayList<Vuelo>>> paquetesDestruidos) {
            this.solucionParcial = solucionParcial;
            this.paquetesDestruidos = paquetesDestruidos;
        }

        public HashMap<Pedido, ArrayList<Vuelo>> getSolucionParcial() {
            return solucionParcial;
        }

        public ArrayList<Map.Entry<Pedido, ArrayList<Vuelo>>> getPaquetesDestruidos() {
            return paquetesDestruidos;
        }

        public int getNumPaquetesDestruidos() {
            return paquetesDestruidos.size();
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
}

