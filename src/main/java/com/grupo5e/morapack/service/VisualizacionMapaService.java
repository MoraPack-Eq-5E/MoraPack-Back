package com.grupo5e.morapack.service;

import com.grupo5e.morapack.api.dto.*;
import com.grupo5e.morapack.core.model.*;
import com.grupo5e.morapack.repository.SimulacionAsignacionRepository;
import com.grupo5e.morapack.repository.SimulacionSemanalRepository;
import com.grupo5e.morapack.utils.CoordenadasUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class VisualizacionMapaService {

    private final SimulacionSemanalRepository simulacionRepository;
    private final SimulacionAsignacionRepository asignacionRepository;
    private final AeropuertoService aeropuertoService;

    // Aeropuertos principales de MoraPack
    private static final Set<String> AEROPUERTOS_PRINCIPALES = 
            Set.of("SPIM", "UBBB", "EBCI"); // Lima, Baku, Bruselas

    public VisualizacionMapaService(
            SimulacionSemanalRepository simulacionRepository,
            SimulacionAsignacionRepository asignacionRepository,
            AeropuertoService aeropuertoService) {
        this.simulacionRepository = simulacionRepository;
        this.asignacionRepository = asignacionRepository;
        this.aeropuertoService = aeropuertoService;
    }

    /**
     * Obtiene todos los aeropuertos con sus ubicaciones para el mapa
     */
    @Transactional(readOnly = true)
    public List<AeropuertoUbicacionDTO> obtenerAeropuertos() {
        List<Aeropuerto> aeropuertos = aeropuertoService.listar();

        return aeropuertos.stream()
                .map(this::convertirAeropuertoADTO)
                .collect(Collectors.toList());
    }

    /**
     * Convierte un aeropuerto a DTO de ubicación
     */
    private AeropuertoUbicacionDTO convertirAeropuertoADTO(Aeropuerto aeropuerto) {
        AeropuertoUbicacionDTO dto = new AeropuertoUbicacionDTO();
        
        dto.setId(aeropuerto.getId());
        dto.setCodigoIATA(aeropuerto.getCodigoIATA());
        dto.setZonaHorariaUTC(aeropuerto.getZonaHorariaUTC());
        dto.setCapacidadActual(aeropuerto.getCapacidadActual());
        dto.setCapacidadMaxima(aeropuerto.getCapacidadMaxima());
        dto.setEstado(aeropuerto.getEstado() != null ? aeropuerto.getEstado().name() : "DESCONOCIDO");

        // Convertir coordenadas a decimal
        double latitud = CoordenadasUtils.parsearCoordenada(aeropuerto.getLatitud());
        double longitud = CoordenadasUtils.parsearCoordenada(aeropuerto.getLongitud());
        dto.setLatitud(latitud);
        dto.setLongitud(longitud);

        // Información de la ciudad
        if (aeropuerto.getCiudad() != null) {
            dto.setNombreCiudad(aeropuerto.getCiudad().getNombre());
            dto.setContinente(aeropuerto.getCiudad().getContinente() != null ? 
                    aeropuerto.getCiudad().getContinente().name() : "DESCONOCIDO");
        }

        // Calcular porcentaje de ocupación
        if (aeropuerto.getCapacidadMaxima() > 0) {
            double porcentaje = (aeropuerto.getCapacidadActual() * 100.0) / aeropuerto.getCapacidadMaxima();
            dto.setPorcentajeOcupacion(Math.round(porcentaje * 100.0) / 100.0);
        }

        // Verificar si es aeropuerto principal
        dto.setEsPrincipal(AEROPUERTOS_PRINCIPALES.contains(aeropuerto.getCodigoIATA()));

        return dto;
    }

    /**
     * Obtiene los vuelos activos en un minuto específico de la simulación
     */
    @Transactional(readOnly = true)
    public List<VueloActivoDTO> obtenerVuelosActivos(Integer minutoActual) {

        // Obtener todas las asignaciones activas en ese minuto
        List<SimulacionAsignacion> asignacionesActivas = asignacionRepository
                .findByMinutoInicioLessThanEqualAndMinutoFinGreaterThanEqual(minutoActual, minutoActual);

        // Agrupar por vuelo para evitar duplicados
        Map<Integer, List<SimulacionAsignacion>> porVuelo = asignacionesActivas.stream()
                .collect(Collectors.groupingBy(a -> a.getVuelo().getId()));

        List<VueloActivoDTO> vuelosActivos = new ArrayList<>();

        for (Map.Entry<Integer, List<SimulacionAsignacion>> entry : porVuelo.entrySet()) {
            List<SimulacionAsignacion> asignaciones = entry.getValue();
            SimulacionAsignacion primeraAsignacion = asignaciones.get(0);

            VueloActivoDTO vueloDTO = crearVueloActivoDTO(primeraAsignacion, minutoActual, asignaciones);
            vuelosActivos.add(vueloDTO);
        }

        return vuelosActivos;
    }

    // @Transactional(readOnly = true)
    // public List<VueloActivoDTO> obtenerVuelosActivos(Long simulacionId, Integer minutoActual) {
    //     // Verificar que la simulación existe
    //     simulacionRepository.findById(simulacionId)
    //             .orElseThrow(() -> new RuntimeException("Simulación no encontrada: " + simulacionId));

    //     // Obtener asignaciones activas en este minuto
    //     List<SimulacionAsignacion> asignacionesActivas = 
    //             asignacionRepository.findAsignacionesActivasEnMinuto(simulacionId, minutoActual);

    //     // Agrupar por vuelo para evitar duplicados
    //     Map<Integer, List<SimulacionAsignacion>> porVuelo = asignacionesActivas.stream()
    //             .collect(Collectors.groupingBy(a -> a.getVuelo().getId()));

    //     List<VueloActivoDTO> vuelosActivos = new ArrayList<>();

    //     for (Map.Entry<Integer, List<SimulacionAsignacion>> entry : porVuelo.entrySet()) {
    //         List<SimulacionAsignacion> asignaciones = entry.getValue();
    //         SimulacionAsignacion primeraAsignacion = asignaciones.get(0);

    //         VueloActivoDTO vueloDTO = crearVueloActivoDTO(primeraAsignacion, minutoActual, asignaciones);
    //         vuelosActivos.add(vueloDTO);
    //     }

    //     return vuelosActivos;
    // }

    /**
     * Crea un DTO de vuelo activo calculando su posición actual
     */
    private VueloActivoDTO crearVueloActivoDTO(SimulacionAsignacion asignacion, 
                                                Integer minutoActual,
                                                List<SimulacionAsignacion> todasAsignaciones) {
        Vuelo vuelo = asignacion.getVuelo();
        Aeropuerto origen = vuelo.getAeropuertoOrigen();
        Aeropuerto destino = vuelo.getAeropuertoDestino();

        VueloActivoDTO dto = new VueloActivoDTO();
        
        dto.setVueloId(vuelo.getId());
        dto.setCodigoVuelo("MP-" + vuelo.getId());
        dto.setCodigoOrigen(origen.getCodigoIATA());
        dto.setCodigoDestino(destino.getCodigoIATA());
        dto.setCiudadOrigen(origen.getCiudad() != null ? origen.getCiudad().getNombre() : "");
        dto.setCiudadDestino(destino.getCiudad() != null ? destino.getCiudad().getNombre() : "");

        // Coordenadas origen y destino
        double latOrigen = asignacion.getLatitudInicio();
        double lonOrigen = asignacion.getLongitudInicio();
        double latDestino = asignacion.getLatitudFin();
        double lonDestino = asignacion.getLongitudFin();

        dto.setLatitudOrigen(latOrigen);
        dto.setLongitudOrigen(lonOrigen);
        dto.setLatitudDestino(latDestino);
        dto.setLongitudDestino(lonDestino);

        // Calcular progreso y posición actual
        int minutoInicio = asignacion.getMinutoInicio();
        int minutoFin = asignacion.getMinutoFin();
        int duracionTotal = minutoFin - minutoInicio;

        double progreso = 0.0;
        if (duracionTotal > 0) {
            progreso = (double) (minutoActual - minutoInicio) / duracionTotal;
            progreso = Math.max(0.0, Math.min(1.0, progreso)); // Clamp entre 0 y 1
        }

        dto.setProgreso(progreso);
        dto.setMinutoInicio(minutoInicio);
        dto.setMinutoFin(minutoFin);

        // Interpolar posición actual
        double latActual = CoordenadasUtils.interpolar(latOrigen, latDestino, progreso);
        double lonActual = CoordenadasUtils.interpolar(lonOrigen, lonDestino, progreso);
        dto.setLatitudActual(latActual);
        dto.setLongitudActual(lonActual);

        // Estado del vuelo
        if (progreso < 0.1) {
            dto.setEstado("CONFIRMADO");
        } else if (progreso < 1.0) {
            dto.setEstado("EN_CAMINO");
        } else {
            dto.setEstado("FINALIZADO");
        }

        // Paquetes a bordo
        List<Long> paquetesIds = todasAsignaciones.stream()
                .map(a -> a.getPedido().getId())
                .distinct()
                .collect(Collectors.toList());
        dto.setPaquetesABordo(paquetesIds);

        // Capacidad
        dto.setCapacidadMaxima(vuelo.getCapacidadMaxima());
        dto.setCapacidadUsada(vuelo.getCapacidadUsada());

        if (vuelo.getCapacidadMaxima() > 0) {
            double porcentaje = (vuelo.getCapacidadUsada() * 100.0) / vuelo.getCapacidadMaxima();
            dto.setPorcentajeOcupacion(Math.round(porcentaje * 100.0) / 100.0);
        }

        return dto;
    }

    /**
     * Obtiene la ruta completa de un paquete específico
     */
    @Transactional(readOnly = true)
    public RutaPaqueteDTO obtenerRutaPaquete(Long pedidoId) {
        // Buscar todas las asignaciones que pertenecen al pedido
        List<SimulacionAsignacion> asignaciones = asignacionRepository.findByPedidoId(pedidoId)
                .stream()
                .sorted(Comparator.comparing(SimulacionAsignacion::getSecuencia))
                .collect(Collectors.toList());

        if (asignaciones.isEmpty()) {
            throw new RuntimeException("No se encontró ruta para el pedido: " + pedidoId);
        }

        // Obtener pedido desde la primera asignación
        PedidoTemporal pedido = asignaciones.get(0).getPedido();

        RutaPaqueteDTO dto = new RutaPaqueteDTO();
        dto.setPedidoId(pedidoId);
        dto.setCodigoOrigen(pedido.getAeropuertoOrigen());
        dto.setCodigoDestino(pedido.getAeropuertoDestino());
        dto.setCantidadProductos(pedido.getCantidadProductos());
        dto.setEstadoPedido(pedido.getEstado() != null ? pedido.getEstado().name() : "DESCONOCIDO");

        // Crear tramos
        List<TramoRutaDTO> tramos = new ArrayList<>();
        double duracionTotalHoras = 0.0;

        for (SimulacionAsignacion asignacion : asignaciones) {
            TramoRutaDTO tramo = crearTramoDTO(asignacion);
            tramos.add(tramo);
            duracionTotalHoras += tramo.getDuracionHoras();
        }

        dto.setTramos(tramos);
        dto.setDuracionTotalHoras(Math.round(duracionTotalHoras * 100.0) / 100.0);

        // Verificar si está a tiempo
        if (pedido.getFechaPedido() != null && pedido.getFechaLimiteEntrega() != null) {
            long horasDisponibles = java.time.Duration.between(
                    pedido.getFechaPedido(),
                    pedido.getFechaLimiteEntrega()
            ).toHours();

            dto.setATiempo(duracionTotalHoras <= horasDisponibles);
        }

        return dto;
    }

    // @Transactional(readOnly = true)
    // public RutaPaqueteDTO obtenerRutaPaquete(Long simulacionId, Long pedidoId) {
    //     SimulacionSemanal simulacion = simulacionRepository.findById(simulacionId)
    //             .orElseThrow(() -> new RuntimeException("Simulación no encontrada: " + simulacionId));

    //     // Obtener todas las asignaciones de este paquete
    //     List<SimulacionAsignacion> asignaciones = asignacionRepository.findBySimulacion(simulacion)
    //             .stream()
    //             .filter(a -> a.getPedido().getId().equals(pedidoId))
    //             .sorted(Comparator.comparing(SimulacionAsignacion::getSecuencia))
    //             .collect(Collectors.toList());

    //     if (asignaciones.isEmpty()) {
    //         throw new RuntimeException("No se encontró ruta para el pedido: " + pedidoId);
    //     }

    //     //Pedido pedido = asignaciones.get(0).getPedido();
    //     PedidoTemporal pedido = asignaciones.get(0).getPedido();
        
    //     RutaPaqueteDTO dto = new RutaPaqueteDTO();
    //     dto.setPedidoId(pedidoId);
    //     dto.setCodigoOrigen(pedido.getAeropuertoOrigen());
    //     dto.setCodigoDestino(pedido.getAeropuertoDestino());
    //     dto.setCantidadProductos(pedido.getCantidadProductos());
    //     dto.setEstadoPedido(pedido.getEstado() != null ? pedido.getEstado().name() : "DESCONOCIDO");

    //     // if (pedido.getIdCliente() != null) {
    //     //     String nombreCliente = pedido.getIdCliente().getNombres() != null ? 
    //     //             pedido.getIdCliente().getNombres() + " " + 
    //     //             (pedido.getIdCliente().getApellidos() != null ? pedido.getCliente().getApellidos() : "") 
    //     //             : "Cliente #" + pedido.getIdCliente().getId();
    //     //     dto.setNombreCliente(nombreCliente.trim());
    //     // }

    //     // Crear tramos
    //     List<TramoRutaDTO> tramos = new ArrayList<>();
    //     double duracionTotalHoras = 0.0;

    //     for (SimulacionAsignacion asignacion : asignaciones) {
    //         TramoRutaDTO tramo = crearTramoDTO(asignacion);
    //         tramos.add(tramo);
    //         duracionTotalHoras += tramo.getDuracionHoras();
    //     }

    //     dto.setTramos(tramos);
    //     dto.setDuracionTotalHoras(Math.round(duracionTotalHoras * 100.0) / 100.0);

    //     // Verificar si está a tiempo
    //     if (pedido.getFechaPedido() != null && pedido.getFechaLimiteEntrega() != null) {
    //         long horasDisponibles = java.time.Duration.between(
    //                 pedido.getFechaPedido(), 
    //                 pedido.getFechaLimiteEntrega()
    //         ).toHours();
            
    //         dto.setATiempo(duracionTotalHoras <= horasDisponibles);
    //     }

    //     return dto;
    // }

    /**
     * Crea un DTO de tramo de ruta
     */
    private TramoRutaDTO crearTramoDTO(SimulacionAsignacion asignacion) {
        Vuelo vuelo = asignacion.getVuelo();
        Aeropuerto origen = vuelo.getAeropuertoOrigen();
        Aeropuerto destino = vuelo.getAeropuertoDestino();

        TramoRutaDTO tramo = new TramoRutaDTO();
        tramo.setSecuencia(asignacion.getSecuencia());
        tramo.setVueloId(vuelo.getId());
        tramo.setCodigoOrigen(origen.getCodigoIATA());
        tramo.setCodigoDestino(destino.getCodigoIATA());
        tramo.setCiudadOrigen(origen.getCiudad() != null ? origen.getCiudad().getNombre() : "");
        tramo.setCiudadDestino(destino.getCiudad() != null ? destino.getCiudad().getNombre() : "");
        tramo.setMinutoInicio(asignacion.getMinutoInicio());
        tramo.setMinutoFin(asignacion.getMinutoFin());
        
        double duracionHoras = (asignacion.getMinutoFin() - asignacion.getMinutoInicio()) / 60.0;
        tramo.setDuracionHoras(Math.round(duracionHoras * 100.0) / 100.0);

        tramo.setLatitudOrigen(asignacion.getLatitudInicio());
        tramo.setLongitudOrigen(asignacion.getLongitudInicio());
        tramo.setLatitudDestino(asignacion.getLatitudFin());
        tramo.setLongitudDestino(asignacion.getLongitudFin());

        return tramo;
    }
}

