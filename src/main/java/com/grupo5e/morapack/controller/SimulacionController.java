package com.grupo5e.morapack.controller;

import com.grupo5e.morapack.core.enums.EstadoProducto;
import com.grupo5e.morapack.core.model.Aeropuerto;
import com.grupo5e.morapack.core.model.Almacen;
import com.grupo5e.morapack.core.model.Producto;
import com.grupo5e.morapack.service.AeropuertoService;
import com.grupo5e.morapack.service.AlmacenService;
import com.grupo5e.morapack.service.ProductoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/simulation")
@RequiredArgsConstructor
@Tag(name = "Simulación", description = "Endpoints para control de simulación diaria")
@CrossOrigin(origins = "*")
public class SimulacionController {

    private final ProductoService productoService;
    private final AlmacenService almacenService;
    private final AeropuertoService aeropuertoService;

    @PostMapping("/update-states")
    @Operation(summary = "Actualizar estados de productos según tiempo de simulación")
    public ResponseEntity<Map<String, Object>> actualizarEstados(
            @RequestBody Map<String, String> request) {
        try {
            String currentTimeStr = request.get("currentTime");
            if (currentTimeStr == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "currentTime es requerido"
                ));
            }

            LocalDateTime tiempoActual = LocalDateTime.parse(currentTimeStr);
            
            int enAlmacenAEnVuelo = 0;
            int enVueloAEntregado = 0;
            List<String> errores = new ArrayList<>();
            
            // Obtener todos los productos
            List<Producto> todosProductos = productoService.listar();
            
            // 1. EN_ALMACEN -> EN_VUELO: productos cuyo vuelo ya despegó
            for (Producto producto : todosProductos) {
                if (producto.getEstado() == EstadoProducto.EN_ALMACEN) {
                    // TODO: verificar hora de salida del vuelo asignado
                    // Por ahora, lógica simplificada para simulación
                    EstadoProducto estadoAnterior = producto.getEstado();
                    producto.setEstado(EstadoProducto.EN_VUELO);
                    productoService.actualizar(producto.getId(), producto);
                    
                    actualizarCapacidadAlmacen(producto, estadoAnterior, EstadoProducto.EN_VUELO, errores);
                    enAlmacenAEnVuelo++;
                }
            }
            
            // 2. EN_VUELO -> ENTREGADO: productos cuyo vuelo ya aterrizó
            for (Producto producto : todosProductos) {
                if (producto.getEstado() == EstadoProducto.EN_VUELO) {
                    // TODO: verificar hora de llegada del vuelo asignado
                    // Por ahora, lógica simplificada para simulación
                    EstadoProducto estadoAnterior = producto.getEstado();
                    producto.setEstado(EstadoProducto.ENTREGADO);
                    productoService.actualizar(producto.getId(), producto);
                    
                    actualizarCapacidadAlmacen(producto, estadoAnterior, EstadoProducto.ENTREGADO, errores);
                    enVueloAEntregado++;
                }
            }
            
            int totalTransiciones = enAlmacenAEnVuelo + enVueloAEntregado;
            
            if (!errores.isEmpty()) {
                log.warn("Errores durante actualización de capacidades: {}", errores);
            }
            
            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("success", true);
            respuesta.put("currentSimulationTime", tiempoActual.toString());
            respuesta.put("transitions", Map.of(
                "pendingToInTransit", enAlmacenAEnVuelo,
                "inTransitToArrived", enVueloAEntregado,
                "arrivedToDelivered", 0,
                "total", totalTransiciones
            ));
            
            return ResponseEntity.ok(respuesta);
            
        } catch (Exception e) {
            log.error("Error actualizando estados", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error: " + e.getMessage()
            ));
        }
    }
    
    private void actualizarCapacidadAlmacen(
            Producto producto, 
            EstadoProducto estadoAnterior, 
            EstadoProducto estadoNuevo,
            List<String> errores) {
        
        try {
            // EN_ALMACEN -> EN_VUELO: Sale del origen (liberar capacidad)
            if (estadoAnterior == EstadoProducto.EN_ALMACEN && estadoNuevo == EstadoProducto.EN_VUELO) {
                Integer aeropuertoOrigenId = obtenerAeropuertoOrigenId(producto);
                if (aeropuertoOrigenId != null) {
                    Optional<Almacen> almacenOpt = almacenService.obtenerPorAeropuerto(aeropuertoOrigenId);
                    if (almacenOpt.isPresent()) {
                        almacenService.liberar(almacenOpt.get().getId(), 1);
                        log.debug("Liberado 1 espacio en almacén origen (aeropuerto {})", aeropuertoOrigenId);
                    }
                }
            }
            // EN_VUELO -> ENTREGADO: Llega al destino (asignar capacidad)
            else if (estadoAnterior == EstadoProducto.EN_VUELO && estadoNuevo == EstadoProducto.ENTREGADO) {
                Integer aeropuertoDestinoId = obtenerAeropuertoDestinoId(producto);
                if (aeropuertoDestinoId != null) {
                    Optional<Almacen> almacenOpt = almacenService.obtenerPorAeropuerto(aeropuertoDestinoId);
                    if (almacenOpt.isPresent()) {
                        almacenService.asignar(almacenOpt.get().getId(), 1);
                        log.debug("Asignado 1 espacio en almacén destino (aeropuerto {})", aeropuertoDestinoId);
                    }
                }
            }
        } catch (Exception e) {
            String error = "Error actualizando capacidad para producto " + producto.getId() + ": " + e.getMessage();
            errores.add(error);
            log.error(error, e);
        }
    }
    
    private Integer obtenerAeropuertoOrigenId(Producto producto) {
        try {
            if (producto.getPedido() != null && 
                producto.getPedido().getAeropuertoOrigenCodigo() != null) {
                String codigoIATA = producto.getPedido().getAeropuertoOrigenCodigo();
                Optional<Aeropuerto> aeropuertoOpt = aeropuertoService.buscarPorCodigoIATA(codigoIATA);
                if (aeropuertoOpt.isPresent()) {
                    return aeropuertoOpt.get().getId();
                }
            }
        } catch (Exception e) {
            log.warn("Error obteniendo aeropuerto origen para producto {}: {}", 
                producto.getId(), e.getMessage());
        }
        return null;
    }
    
    private Integer obtenerAeropuertoDestinoId(Producto producto) {
        try {
            if (producto.getPedido() != null && 
                producto.getPedido().getAeropuertoDestinoCodigo() != null) {
                String codigoIATA = producto.getPedido().getAeropuertoDestinoCodigo();
                Optional<Aeropuerto> aeropuertoOpt = aeropuertoService.buscarPorCodigoIATA(codigoIATA);
                if (aeropuertoOpt.isPresent()) {
                    return aeropuertoOpt.get().getId();
                }
            }
        } catch (Exception e) {
            log.warn("Error obteniendo aeropuerto destino para producto {}: {}", 
                producto.getId(), e.getMessage());
        }
        return null;
    }
}

