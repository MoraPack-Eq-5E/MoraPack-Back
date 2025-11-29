package com.grupo5e.morapack.controller;

import com.grupo5e.morapack.core.model.Almacen;
import com.grupo5e.morapack.service.AlmacenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/almacenes")
@RequiredArgsConstructor
@Tag(name = "Almacenes", description = "Gestión de almacenes y capacidades")
@CrossOrigin(origins = "*")
public class AlmacenController {

    private final AlmacenService almacenService;

    @GetMapping
    @Operation(summary = "Listar almacenes con filtros opcionales")
    public ResponseEntity<?> listar(
            @Parameter(description = "ID del aeropuerto para filtrar")
            @RequestParam(required = false) Integer aeropuertoId,
            @Parameter(description = "Solo almacenes principales")
            @RequestParam(required = false) Boolean principal) {
        
        try {
            // Filtrar por aeropuerto
            if (aeropuertoId != null) {
                Optional<Almacen> almacen = almacenService.obtenerPorAeropuerto(aeropuertoId);
                if (almacen.isPresent()) {
                    return ResponseEntity.ok(almacen.get());
                } else {
                    return ResponseEntity.ok(Map.of(
                        "message", "No se encontró almacén para el aeropuerto " + aeropuertoId
                    ));
                }
            }
            
            // Filtrar por principal
            if (Boolean.TRUE.equals(principal)) {
                List<Almacen> almacenes = almacenService.listarPrincipales();
                return ResponseEntity.ok(almacenes);
            }
            
            // Listar todos
            List<Almacen> almacenes = almacenService.listar();
            return ResponseEntity.ok(almacenes);
            
        } catch (Exception e) {
            log.error("Error listando almacenes", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener almacén por ID")
    public ResponseEntity<?> obtenerPorId(@PathVariable Integer id) {
        try {
            Almacen almacen = almacenService.obtenerPorId(id);
            return ResponseEntity.ok(almacen);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error obteniendo almacén {}", id, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/estadisticas")
    @Operation(summary = "Obtener estadísticas generales de almacenes")
    public ResponseEntity<?> obtenerEstadisticas() {
        try {
            List<Almacen> almacenes = almacenService.listar();
            
            int capacidadTotalMaxima = almacenes.stream()
                .mapToInt(Almacen::getCapacidadMaxima)
                .sum();
            
            int capacidadTotalUsada = almacenes.stream()
                .mapToInt(Almacen::getCapacidadUsada)
                .sum();
            
            double porcentajeUsoPromedio = capacidadTotalMaxima > 0 
                ? (double) capacidadTotalUsada / capacidadTotalMaxima * 100 
                : 0;
            
            Map<String, Object> estadisticas = new HashMap<>();
            estadisticas.put("totalAlmacenes", almacenes.size());
            estadisticas.put("capacidadTotalMaxima", capacidadTotalMaxima);
            estadisticas.put("capacidadTotalUsada", capacidadTotalUsada);
            estadisticas.put("capacidadDisponible", capacidadTotalMaxima - capacidadTotalUsada);
            estadisticas.put("porcentajeUsoPromedio", Math.round(porcentajeUsoPromedio * 100.0) / 100.0);
            
            return ResponseEntity.ok(estadisticas);
            
        } catch (Exception e) {
            log.error("Error obteniendo estadísticas", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping
    @Operation(summary = "Crear nuevo almacén")
    public ResponseEntity<?> crear(@RequestBody Almacen almacen) {
        try {
            Almacen creado = almacenService.crear(almacen);
            return ResponseEntity.ok(creado);
        } catch (Exception e) {
            log.error("Error creando almacén", e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar almacén existente")
    public ResponseEntity<?> actualizar(
            @PathVariable Integer id,
            @RequestBody Almacen almacen) {
        try {
            Almacen actualizado = almacenService.actualizar(id, almacen);
            return ResponseEntity.ok(actualizado);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error actualizando almacén {}", id, e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }
}

