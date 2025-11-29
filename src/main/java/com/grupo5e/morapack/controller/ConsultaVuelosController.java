package com.grupo5e.morapack.controller;

import com.grupo5e.morapack.core.model.Vuelo;
import com.grupo5e.morapack.core.model.Aeropuerto;
import com.grupo5e.morapack.service.VueloService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/query/flights")
@RequiredArgsConstructor
@Tag(name = "Consulta Vuelos", description = "Endpoints para consultar estado de vuelos para simulación")
@CrossOrigin(origins = "*")
public class ConsultaVuelosController {

    private final VueloService vueloService;

    @GetMapping("/status")
    @Operation(summary = "Obtener estado de todos los vuelos para el mapa de simulación")
    public ResponseEntity<Map<String, Object>> obtenerEstadoVuelos() {
        try {
            List<Vuelo> vuelos = vueloService.listar();
            List<Map<String, Object>> vuelosDTO = new ArrayList<>();

            int capacidadTotal = 0;
            int capacidadUsadaTotal = 0;

            for (Vuelo vuelo : vuelos) {
                Map<String, Object> vueloMap = construirVueloDTO(vuelo);
                vuelosDTO.add(vueloMap);

                capacidadTotal += vuelo.getCapacidadMaxima();
                capacidadUsadaTotal += vuelo.getCapacidadUsada();
            }

            double utilizacionPromedio = capacidadTotal > 0 
                ? (double) capacidadUsadaTotal / capacidadTotal * 100 
                : 0;

            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("success", true);
            respuesta.put("totalFlights", vuelos.size());
            respuesta.put("flights", vuelosDTO);
            respuesta.put("statistics", Map.of(
                "totalCapacity", capacidadTotal,
                "totalUsedCapacity", capacidadUsadaTotal,
                "averageUtilization", Math.round(utilizacionPromedio * 100.0) / 100.0
            ));

            return ResponseEntity.ok(respuesta);

        } catch (Exception e) {
            log.error("Error obteniendo estado de vuelos", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/{codigoVuelo}/orders")
    @Operation(summary = "Obtener órdenes asignadas a un vuelo específico")
    public ResponseEntity<Map<String, Object>> obtenerOrdenesPorVuelo(
            @PathVariable String codigoVuelo) {
        try {
            List<Vuelo> vuelos = vueloService.listar();
            Vuelo vuelo = vuelos.stream()
                .filter(v -> codigoVuelo.equals(v.getIdentificadorVuelo()))
                .findFirst()
                .orElse(null);

            if (vuelo == null) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("success", true);
            respuesta.put("flightCode", codigoVuelo);
            respuesta.put("totalOrders", 0); // TODO: conectar con productos asignados
            respuesta.put("orders", Collections.emptyList());
            respuesta.put("flight", Map.of(
                "code", codigoVuelo,
                "usedCapacity", vuelo.getCapacidadUsada(),
                "maxCapacity", vuelo.getCapacidadMaxima()
            ));

            return ResponseEntity.ok(respuesta);

        } catch (Exception e) {
            log.error("Error obteniendo órdenes del vuelo {}", codigoVuelo, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error: " + e.getMessage()
            ));
        }
    }

    private Map<String, Object> construirVueloDTO(Vuelo vuelo) {
        Map<String, Object> dto = new LinkedHashMap<>();
        
        dto.put("id", vuelo.getId());
        dto.put("code", vuelo.getIdentificadorVuelo());
        
        // Aeropuerto origen
        dto.put("originAirport", construirAeropuertoDTO(vuelo.getAeropuertoOrigen()));
        
        // Aeropuerto destino
        dto.put("destinationAirport", construirAeropuertoDTO(vuelo.getAeropuertoDestino()));
        
        // Capacidades
        int capacidadMax = vuelo.getCapacidadMaxima();
        int capacidadUsada = vuelo.getCapacidadUsada();
        int capacidadDisponible = Math.max(0, capacidadMax - capacidadUsada);
        double utilizacion = capacidadMax > 0 ? (double) capacidadUsada / capacidadMax * 100 : 0;
        
        dto.put("maxCapacity", capacidadMax);
        dto.put("usedCapacity", capacidadUsada);
        dto.put("availableCapacity", capacidadDisponible);
        dto.put("utilizationPercentage", Math.round(utilizacion * 100.0) / 100.0);
        
        // Tiempo y frecuencia
        dto.put("transportTimeDays", vuelo.getTiempoTransporte() / 24.0); // horas a días
        dto.put("dailyFrequency", (int) vuelo.getFrecuenciaPorDia());
        
        // Productos asignados (placeholder)
        dto.put("assignedProducts", capacidadUsada);
        dto.put("assignedOrders", 0);
        
        return dto;
    }

    private Map<String, Object> construirAeropuertoDTO(Aeropuerto aeropuerto) {
        if (aeropuerto == null) {
            return Map.of(
                "codeIATA", "N/A",
                "city", Map.of("name", "Desconocido"),
                "latitude", 0.0,
                "longitude", 0.0
            );
        }

        String nombreCiudad = aeropuerto.getCiudad() != null 
            ? aeropuerto.getCiudad().getNombre() 
            : aeropuerto.getAlias();

        double latitud = 0.0;
        double longitud = 0.0;
        try {
            if (aeropuerto.getLatitud() != null) latitud = Double.parseDouble(aeropuerto.getLatitud());
            if (aeropuerto.getLongitud() != null) longitud = Double.parseDouble(aeropuerto.getLongitud());
        } catch (NumberFormatException ignored) {}

        return Map.of(
            "codeIATA", aeropuerto.getCodigoIATA() != null ? aeropuerto.getCodigoIATA() : "N/A",
            "city", Map.of("name", nombreCiudad != null ? nombreCiudad : "Desconocido"),
            "latitude", latitud,
            "longitude", longitud
        );
    }
}

