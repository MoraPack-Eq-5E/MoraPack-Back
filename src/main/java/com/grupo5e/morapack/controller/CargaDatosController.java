package com.grupo5e.morapack.controller;

import com.grupo5e.morapack.service.DataLoadService;
import com.grupo5e.morapack.utils.ModoSimulacion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * API REST para cargar datos desde archivos hacia la base de datos.
 * Implementa arquitectura Option A: separación entre carga de datos y ejecución del algoritmo.
 *
 * Flujo típico:
 * 1. POST /api/datos/cargar-pedidos (carga archivos de pedidos a BD)
 * 2. POST /api/algoritmo/daily o /weekly (algoritmo lee desde BD)
 * 
 * Equivalente a DataLoadAPI.java del Backend.
 */
@Slf4j
@RestController
@RequestMapping("/api/datos")
@RequiredArgsConstructor
@Tag(name = "Carga de Datos", description = "Endpoints para cargar datos desde archivos a la base de datos")
@CrossOrigin(origins = "*")
public class CargaDatosController {

    private final DataLoadService dataLoadService;

    /**
     * Cargar pedidos desde archivos _pedidos_{AEROPUERTO}_ hacia la base de datos.
     *
     * @param directorioArchivos Opcional: ruta personalizada al directorio de datos
     * @param horaInicio Opcional: solo cargar pedidos después de esta hora
     * @param horaFin Opcional: solo cargar pedidos antes de esta hora
     * @return Estadísticas sobre pedidos cargados
     *
     * Ejemplos:
     *   POST /api/datos/cargar-pedidos
     *   POST /api/datos/cargar-pedidos?horaInicio=2025-01-02T00:00:00&horaFin=2025-01-02T01:00:00
     *   POST /api/datos/cargar-pedidos?directorioArchivos=/ruta/personalizada
     */
    @PostMapping("/cargar-pedidos")
    @Operation(
        summary = "Cargar pedidos desde archivos",
        description = "Carga pedidos desde archivos con patrón _pedidos_{AEROPUERTO}_ a la base de datos. " +
                      "Opcionalmente filtra por ventana de tiempo para escenarios diario/semanal. " +
                      "\n\nFormato de archivo: id_pedido-aaaammdd-hh-mm-dest-###-IdClien" +
                      "\n\nEjemplo: 000000001-20250102-01-38-EBCI-006-0007729"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Pedidos cargados exitosamente",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Error durante la carga de pedidos"
        )
    })
    public ResponseEntity<Map<String, Object>> cargarPedidos(
            @Parameter(description = "Ruta personalizada al directorio de datos (opcional)", example = "data/pedidos")
            @RequestParam(required = false) String directorioArchivos,
            @Parameter(description = "Hora de inicio para filtrar pedidos (ISO 8601)", example = "2025-01-02T00:00:00")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime horaInicio,
            @Parameter(description = "Hora de fin para filtrar pedidos (ISO 8601)", example = "2025-01-02T01:00:00")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime horaFin,
            @Parameter(description = "Modo de simulación: SEMANAL o COLAPSO", example = "SEMANAL")
            @RequestParam(required = false, defaultValue = "SEMANAL") String modo){

        // OPTIMIZACIÓN: Agregar logging específico sin sobrecarga
        long inicioTiempo = System.currentTimeMillis();
        
        try {
            log.info("========================================");
            log.info("API: SOLICITUD DE CARGA DE PEDIDOS RECIBIDA");
            log.info("========================================");

            // Usar directorio predeterminado si no se especifica
            String rutaDirectorio = directorioArchivos != null ? directorioArchivos : obtenerDirectorioPredeterminado();

            log.info("Directorio: {}", rutaDirectorio);
            if (horaInicio != null && horaFin != null) {
                log.info("Ventana de tiempo: {} a {}", horaInicio, horaFin);
            }

            ModoSimulacion modoSimulacion = ModoSimulacion.valueOf(modo);
            // Cargar pedidos
            DataLoadService.ResultadoCargaDatos resultado =
                dataLoadService.cargarPedidosDesdeArchivos(rutaDirectorio, horaInicio, horaFin,modoSimulacion);

            // Construir respuesta
            Map<String, Object> respuesta = new HashMap<>();
            respuesta.put("exito", resultado.getExito());
            respuesta.put("mensaje", resultado.getExito() ?
                "Pedidos cargados exitosamente" : resultado.getMensajeError());
            respuesta.put("estadisticas", Map.of(
                "pedidosCargados", resultado.getPedidosCargados() != null ? resultado.getPedidosCargados() : 0,
                "pedidosCreados", resultado.getPedidosCreados() != null ? resultado.getPedidosCreados() : 0,
                "pedidosFiltrados", resultado.getPedidosFiltrados() != null ? resultado.getPedidosFiltrados() : 0,
                "erroresParseo", resultado.getErroresParseo() != null ? resultado.getErroresParseo() : 0,
                "erroresArchivos", resultado.getErroresArchivos() != null ? resultado.getErroresArchivos() : 0,
                "duracionSegundos", resultado.getDuracionSegundos() != null ? resultado.getDuracionSegundos() : 0
            ));
            respuesta.put("tiempoInicio", resultado.getTiempoInicio());
            respuesta.put("tiempoFin", resultado.getTiempoFin());

            if (horaInicio != null && horaFin != null) {
                respuesta.put("ventanaTiempo", Map.of(
                    "horaInicio", horaInicio.toString(),
                    "horaFin", horaFin.toString()
                ));
            }
            
            // PERFORMANCE: Agregar tiempo de ejecución en respuesta
            long tiempoTotal = System.currentTimeMillis() - inicioTiempo;
            respuesta.put("tiempoEjecucionMs", tiempoTotal);
            log.info("Carga completada en {} ms", tiempoTotal);

            return resultado.getExito() ?
                ResponseEntity.ok(respuesta) :
                ResponseEntity.internalServerError().body(respuesta);

        } catch (Exception e) {
            log.error("Error crítico en carga de pedidos", e);
            Map<String, Object> respuestaError = new HashMap<>();
            respuestaError.put("exito", false);
            respuestaError.put("mensaje", "Error al cargar pedidos: " + e.getMessage());
            respuestaError.put("error", e.getClass().getSimpleName());
            return ResponseEntity.internalServerError().body(respuestaError);
        }
    }

    /**
     * Obtener estado de los datos en la base de datos.
     * Muestra cuántos pedidos hay actualmente en la BD.
     *
     * Ejemplo: GET /api/datos/estado
     */
    @GetMapping("/estado")
    @Operation(
        summary = "Obtener estado de datos",
        description = "Retorna estadísticas sobre los datos actualmente cargados en la base de datos"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Estado obtenido exitosamente",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Error al obtener estado"
        )
    })
    public ResponseEntity<Map<String, Object>> obtenerEstado() {
        try {
            DataLoadService.EstadoDatos estado = dataLoadService.obtenerEstadoDatos();

            Map<String, Object> respuesta = new HashMap<>();
            respuesta.put("exito", estado.getExito());
            if (estado.getExito()) {
                respuesta.put("mensaje", "Estado de datos obtenido exitosamente");
                respuesta.put("directorioArchivos", obtenerDirectorioPredeterminado());
                respuesta.put("estadisticas", Map.of(
                    "totalAeropuertos", estado.getTotalAeropuertos() != null ? estado.getTotalAeropuertos() : 0,
                    "totalPedidos", estado.getTotalPedidos() != null ? estado.getTotalPedidos() : 0,
                    "pedidosPendientes", estado.getPedidosPendientes() != null ? estado.getPedidosPendientes() : 0
                ));
            } else {
                respuesta.put("mensaje", estado.getMensajeError());
            }

            return ResponseEntity.ok(respuesta);

        } catch (Exception e) {
            Map<String, Object> respuestaError = new HashMap<>();
            respuestaError.put("exito", false);
            respuestaError.put("mensaje", "Error al obtener estado: " + e.getMessage());
            return ResponseEntity.internalServerError().body(respuestaError);
        }
    }

    /**
     * Obtener ruta del directorio predeterminado de datos.
     * Usa la ubicación del directorio 'data' en el proyecto.
     */
    private String obtenerDirectorioPredeterminado() {
        // Intentar obtener directorio del proyecto
        String directorioProyecto = System.getProperty("user.dir");
        File dataDirRelativo = new File(directorioProyecto, "data/pedidos");
        
        if (dataDirRelativo.exists() && dataDirRelativo.isDirectory()) {
            return dataDirRelativo.getAbsolutePath();
        }
        
        // Fallback: directorio data en raíz del proyecto
        dataDirRelativo = new File(directorioProyecto, "data");
        if (dataDirRelativo.exists() && dataDirRelativo.isDirectory()) {
            return dataDirRelativo.getAbsolutePath();
        }
        
        // Último fallback: usar directorio del proyecto
        return directorioProyecto;
    }
}

