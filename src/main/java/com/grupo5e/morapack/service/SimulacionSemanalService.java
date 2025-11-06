package com.grupo5e.morapack.service;

import com.grupo5e.morapack.api.dto.*;
import com.grupo5e.morapack.core.enums.EstadoSimulacion;
import com.grupo5e.morapack.core.model.*;
import com.grupo5e.morapack.repository.PedidoTemporalRepository;
import com.grupo5e.morapack.repository.SimulacionAsignacionRepository;
import com.grupo5e.morapack.repository.SimulacionSemanalRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SimulacionSemanalService {

    private final SimulacionSemanalRepository simulacionRepository;
    private final SimulacionAsignacionRepository asignacionRepository;
    private final SimulacionAsyncService simulacionAsyncService;
    private final PedidoService pedidoService;
    private final PedidoTemporalRepository pedidoTemporalRepository;

    public SimulacionSemanalService(
            SimulacionSemanalRepository simulacionRepository,
            SimulacionAsignacionRepository asignacionRepository,
            SimulacionAsyncService simulacionAsyncService,
            PedidoService pedidoService, PedidoTemporalRepository pedidoTemporalRepository) {
        this.simulacionRepository = simulacionRepository;
        this.asignacionRepository = asignacionRepository;
        this.simulacionAsyncService = simulacionAsyncService;
        this.pedidoService = pedidoService;
        this.pedidoTemporalRepository = pedidoTemporalRepository;
    }

    /**
     * Inicia una simulación semanal de forma asíncrona
     */
    @Transactional
    public Long iniciarSimulacion(SimulacionSemanalRequestDTO request) {
        log.info("🚀 Iniciando nueva simulación semanal");

        // Crear registro de simulación
        SimulacionSemanal simulacion = new SimulacionSemanal();
        simulacion.setFechaInicio(LocalDateTime.now());
        simulacion.setEstado(EstadoSimulacion.INICIANDO);
        simulacion.setProgreso(0);
        simulacion.setTiempoSimuladoDias(request.getDiasSimulacion());
        simulacion.setIteracionesAlns(request.getIteracionesAlns());
        simulacion.setTiempoLimiteSegundos(request.getTiempoLimiteSegundos());
        simulacion.setSolucionValida(false);

        simulacion = simulacionRepository.save(simulacion);
        
        final Long simulacionId = simulacion.getId();
        log.info("✅ Simulación creada con ID: {} - Lanzando en background", simulacionId);

        // Ejecutar algoritmo en background usando servicio separado
        simulacionAsyncService.ejecutarAlgoritmoAsync(simulacionId, request);

        log.info("🚀 Respuesta enviada inmediatamente para simulación {}", simulacionId);
        return simulacionId;
    }


    /**
     * Obtiene el estado de una simulación
     */
    @Transactional(readOnly = true)
    public SimulacionSemanalResponseDTO obtenerEstado(Long simulacionId) {
        SimulacionSemanal simulacion = simulacionRepository.findById(simulacionId)
                .orElseThrow(() -> new RuntimeException("Simulación no encontrada: " + simulacionId));

        return convertirADTO(simulacion, false);
    }

    /**
     * Obtiene el resultado completo de una simulación
     */
    @Transactional(readOnly = true)
    public SimulacionSemanalResponseDTO obtenerResultado(Long simulacionId) {
        SimulacionSemanal simulacion = simulacionRepository.findById(simulacionId)
                .orElseThrow(() -> new RuntimeException("Simulación no encontrada: " + simulacionId));

        if (simulacion.getEstado() != EstadoSimulacion.COMPLETADA) {
            throw new RuntimeException("La simulación aún no ha completado");
        }

        return convertirADTO(simulacion, true);
    }

    /**
     * Convierte la entidad a DTO
     */
    private SimulacionSemanalResponseDTO convertirADTO(SimulacionSemanal simulacion, boolean incluirSolucion) {
        SimulacionSemanalResponseDTO dto = new SimulacionSemanalResponseDTO();
        
        dto.setSimulacionId(simulacion.getId());
        dto.setEstado(simulacion.getEstado().name());
        dto.setFechaInicio(simulacion.getFechaInicio());
        dto.setFechaFin(simulacion.getFechaFin());
        dto.setDuracionMs(simulacion.getDuracionMs());
        dto.setTotalPedidos(simulacion.getTotalPedidos());
        dto.setPedidosAsignados(simulacion.getPedidosAsignados());
        dto.setPedidosNoAsignados(simulacion.getPedidosNoAsignados());
        dto.setCostoTotal(simulacion.getCostoTotal());
        dto.setTiempoPromedioEntrega(simulacion.getTiempoPromedioEntrega());
        dto.setPesoSolucion(simulacion.getPesoSolucion());
        dto.setSolucionValida(simulacion.getSolucionValida());
        dto.setProgreso(simulacion.getProgreso());
        dto.setMensajeError(simulacion.getMensajeError());
        dto.setTiempoInicialReferencia(simulacion.getTiempoInicialReferencia());
        dto.setTiempoSimuladoDias(simulacion.getTiempoSimuladoDias());
        dto.setIteracionesAlns(simulacion.getIteracionesAlns());

        // Calcular porcentaje de asignación
        if (simulacion.getTotalPedidos() != null && simulacion.getTotalPedidos() > 0) {
            double porcentaje = (simulacion.getPedidosAsignados() * 100.0) / simulacion.getTotalPedidos();
            dto.setPorcentajeAsignacion(Math.round(porcentaje * 100.0) / 100.0);
        }

        // Formatear duración
        if (simulacion.getDuracionMs() != null) {
            dto.setDuracionFormateada(formatearDuracion(simulacion.getDuracionMs()));
        }

        // Incluir solución completa si se solicita
        if (incluirSolucion && simulacion.getEstado() == EstadoSimulacion.COMPLETADA) {
            Map<Long, List<Integer>> solucionMap = construirMapaSolucion(simulacion);
            dto.setSolucion(solucionMap);

            List<Long> noAsignadosIds = obtenerPedidosNoAsignadosIds();
            dto.setPedidosNoAsignadosIds(noAsignadosIds);

            EstadisticasSimulacionDTO estadisticas = calcularEstadisticas();
            dto.setEstadisticas(estadisticas);
        }

        return dto;
    }

    /**
     * Construye el mapa de solución: pedidoId -> [vueloIds]
     */
    private Map<Long, List<Integer>> construirMapaSolucion(SimulacionSemanal simulacion) {
        List<SimulacionAsignacion> asignaciones = 
                asignacionRepository.findAllByOrderByPedidoIdAscSecuenciaAsc();

        Map<Long, List<Integer>> solucionMap = new HashMap<>();

        for (SimulacionAsignacion asignacion : asignaciones) {
            Long pedidoId = asignacion.getPedido().getId();
            Integer vueloId = asignacion.getVuelo().getId();

            solucionMap.computeIfAbsent(pedidoId, k -> new ArrayList<>()).add(vueloId);
        }

        return solucionMap;
    }

    /**
     * Obtiene los IDs de pedidos no asignados
     */
    private List<Long> obtenerPedidosNoAsignadosIds() {
        List<SimulacionAsignacion> asignaciones = asignacionRepository.findAll();
        Set<Long> asignados = asignaciones.stream()
                .map(a -> a.getPedido().getId())
                .collect(Collectors.toSet());

        List<Pedido> todosPedidos = pedidoService.listar();
        
        return todosPedidos.stream()
                .map(Pedido::getId)
                .filter(id -> !asignados.contains(id))
                .collect(Collectors.toList());
    }

    /**
     * Calcula estadísticas de la simulación
     */
    private EstadisticasSimulacionDTO calcularEstadisticas() {
        // Obtener todas las asignaciones actuales
        List<SimulacionAsignacion> asignaciones = asignacionRepository.findAll();

        if (asignaciones.isEmpty()) {
            throw new RuntimeException("No existen asignaciones registradas para calcular estadísticas.");
        }

        // Agrupar por pedido temporal
        Map<Long, List<SimulacionAsignacion>> porPedido = asignaciones.stream()
                .collect(Collectors.groupingBy(a -> a.getPedido().getId()));

        int rutasDirectas = 0;
        int rutasUnaEscala = 0;
        int rutasDosEscalas = 0;
        int rutasMismoContinente = 0;
        int rutasIntercontinentales = 0;

        for (List<SimulacionAsignacion> ruta : porPedido.values()) {
            // Ordenar por secuencia por seguridad
            ruta.sort(Comparator.comparing(SimulacionAsignacion::getSecuencia));

            int numVuelos = ruta.size();

            if (numVuelos == 1) rutasDirectas++;
            else if (numVuelos == 2) rutasUnaEscala++;
            else if (numVuelos >= 3) rutasDosEscalas++;

            // Verificar si es mismo continente
            SimulacionAsignacion primera = ruta.get(0);
            SimulacionAsignacion ultima = ruta.get(ruta.size() - 1);

            String continenteOrigen = primera.getVuelo().getAeropuertoOrigen()
                    .getCiudad().getContinente().name();
            String continenteDestino = ultima.getVuelo().getAeropuertoDestino()
                    .getCiudad().getContinente().name();

            if (continenteOrigen.equals(continenteDestino)) {
                rutasMismoContinente++;
            } else {
                rutasIntercontinentales++;
            }
        }

        // Crear DTO
        EstadisticasSimulacionDTO stats = new EstadisticasSimulacionDTO();
        stats.setRutasDirectas(rutasDirectas);
        stats.setRutasUnaEscala(rutasUnaEscala);
        stats.setRutasDosEscalas(rutasDosEscalas);
        stats.setRutasMismoContinente(rutasMismoContinente);
        stats.setRutasIntercontinentales(rutasIntercontinentales);

        // Si no tienes una entidad "SimulacionSemanal" en este contexto,
        // puedes calcular las entregas a tiempo desde los pedidos temporales.
        List<PedidoTemporal> pedidos = pedidoTemporalRepository.findAll();

        long entregasATiempo = pedidos.stream()
                .filter(p -> p.getFechaPedido() != null && p.getFechaLimiteEntrega() != null)
                .filter(p -> {
                    double duracionHoras = calcularDuracionPedido(asignaciones, p.getId());
                    long horasDisponibles = java.time.Duration.between(
                            p.getFechaPedido(),
                            p.getFechaLimiteEntrega()
                    ).toHours();
                    return duracionHoras <= horasDisponibles;
                })
                .count();

        stats.setEntregasATiempo((int) entregasATiempo);

        if (!pedidos.isEmpty()) {
            double porcentajeATiempo = (entregasATiempo * 100.0) / pedidos.size();
            stats.setPorcentajeEntregasATiempo(Math.round(porcentajeATiempo * 100.0) / 100.0);
        }

        // Contar vuelos únicos utilizados
        long vuelosUnicos = asignaciones.stream()
                .map(a -> a.getVuelo().getId())
                .distinct()
                .count();
        stats.setVuelosUtilizados((int) vuelosUnicos);

        return stats;
    }
    private double calcularDuracionPedido(List<SimulacionAsignacion> asignaciones, Long pedidoId) {
        List<SimulacionAsignacion> ruta = asignaciones.stream()
                .filter(a -> a.getPedido().getId().equals(pedidoId))
                .sorted(Comparator.comparing(SimulacionAsignacion::getSecuencia))
                .toList();

        if (ruta.isEmpty()) return 0.0;

        SimulacionAsignacion primera = ruta.get(0);
        SimulacionAsignacion ultima = ruta.get(ruta.size() - 1);

        return (ultima.getMinutoFin() - primera.getMinutoInicio()) / 60.0; // en horas
    }

    // private EstadisticasSimulacionDTO calcularEstadisticas() {
    //     List<SimulacionAsignacion> asignaciones = asignacionRepository.findAll();

    //     // Agrupar por pedido
    //     Map<Long, List<SimulacionAsignacion>> porPedido = asignaciones.stream()
    //             .collect(Collectors.groupingBy(a -> a.getPedido().getId()));

    //     int rutasDirectas = 0;
    //     int rutasUnaEscala = 0;
    //     int rutasDosEscalas = 0;
    //     int rutasMismoContinente = 0;
    //     int rutasIntercontinentales = 0;

    //     for (List<SimulacionAsignacion> ruta : porPedido.values()) {
    //         int numVuelos = ruta.size();
            
    //         if (numVuelos == 1) rutasDirectas++;
    //         else if (numVuelos == 2) rutasUnaEscala++;
    //         else if (numVuelos >= 3) rutasDosEscalas++;

    //         // Verificar si es mismo continente
    //         SimulacionAsignacion primera = ruta.get(0);
    //         SimulacionAsignacion ultima = ruta.get(ruta.size() - 1);
            
    //         String continenteOrigen = primera.getVuelo().getAeropuertoOrigen().getCiudad().getContinente().name();
    //         String continenteDestino = ultima.getVuelo().getAeropuertoDestino().getCiudad().getContinente().name();

    //         if (continenteOrigen.equals(continenteDestino)) {
    //             rutasMismoContinente++;
    //         } else {
    //             rutasIntercontinentales++;
    //         }
    //     }

    //     EstadisticasSimulacionDTO stats = new EstadisticasSimulacionDTO();
    //     stats.setRutasDirectas(rutasDirectas);
    //     stats.setRutasUnaEscala(rutasUnaEscala);
    //     stats.setRutasDosEscalas(rutasDosEscalas);
    //     stats.setRutasMismoContinente(rutasMismoContinente);
    //     stats.setRutasIntercontinentales(rutasIntercontinentales);
    //     stats.setEntregasATiempo(simulacion.getPedidosAsignados()); // Simplificado
        
    //     if (simulacion.getPedidosAsignados() != null && simulacion.getPedidosAsignados() > 0) {
    //         double porcentajeATiempo = (stats.getEntregasATiempo() * 100.0) / simulacion.getPedidosAsignados();
    //         stats.setPorcentajeEntregasATiempo(Math.round(porcentajeATiempo * 100.0) / 100.0);
    //     }

    //     // Contar vuelos únicos utilizados
    //     long vuelosUnicos = asignaciones.stream()
    //             .map(a -> a.getVuelo().getId())
    //             .distinct()
    //             .count();
    //     stats.setVuelosUtilizados((int) vuelosUnicos);

    //     return stats;
    // }

    /**
     * Formatea la duración en milisegundos a formato HH:mm:ss
     */
    private String formatearDuracion(Long duracionMs) {
        long segundos = duracionMs / 1000;
        long horas = segundos / 3600;
        long minutos = (segundos % 3600) / 60;
        long segs = segundos % 60;

        return String.format("%02d:%02d:%02d", horas, minutos, segs);
    }

    /**
     * Lista todas las simulaciones
     */
    @Transactional(readOnly = true)
    public List<SimulacionSemanalResponseDTO> listarSimulaciones() {
        List<SimulacionSemanal> simulaciones = simulacionRepository.findByOrderByFechaInicioDesc();
        return simulaciones.stream()
                .map(s -> convertirADTO(s, false))
                .collect(Collectors.toList());
    }
}

