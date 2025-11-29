package com.grupo5e.morapack.controller;

import com.grupo5e.morapack.core.enums.EstadoProducto;
import com.grupo5e.morapack.core.model.Producto;
import com.grupo5e.morapack.service.DataLoadService;
import com.grupo5e.morapack.core.model.Pedido;
import com.grupo5e.morapack.service.PedidoService;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API REST para cargar datos desde archivos hacia la base de datos.
 * Implementa arquitectura Option A: separación entre carga de datos y ejecución del algoritmo.
 *
 * Flujo típico:
 * 1. POST /api/datos/cargar-pedidos (carga archivos de pedidos a BD)
 * 2. POST /api/algoritmo/daily o /weekly (algoritmo lee desde BD)
 */
@Slf4j
@RestController
@RequestMapping("/api/datos")
@RequiredArgsConstructor
@Tag(name = "Carga de Datos", description = "Endpoints para cargar datos desde archivos a la base de datos")
@CrossOrigin(origins = "*")
public class CargaDatosController {

    private final DataLoadService dataLoadService;
    private final PedidoService pedidoService;

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
            LocalDateTime horaFin) {

        long inicioTiempo = System.currentTimeMillis();

        try {
            log.info("========================================");
            log.info("API: SOLICITUD DE CARGA DE PEDIDOS RECIBIDA");
            log.info("========================================");

            String rutaDirectorio = directorioArchivos != null ? directorioArchivos : obtenerDirectorioPredeterminado();

            log.info("Directorio: {}", rutaDirectorio);
            if (horaInicio != null && horaFin != null) {
                log.info("Ventana de tiempo: {} a {}", horaInicio, horaFin);
            }

            DataLoadService.ResultadoCargaDatos resultado =
                    dataLoadService.cargarPedidosDesdeArchivos(rutaDirectorio, horaInicio, horaFin);

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


    @PostMapping("/insertar-pedidos")
    @Operation(
            summary = "Insertar pedidos manualmente",
            description = "Inserta pedidos enviados en el cuerpo de la petición, sin leer archivos. " +
                    "Devuelve estadísticas compatibles con /cargar-pedidos."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Pedidos insertados exitosamente",
                    content = @Content(schema = @Schema(implementation = Map.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "No se enviaron pedidos"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error durante la inserción de pedidos"
            )
    })
    public ResponseEntity<Map<String, Object>> insertarPedidosDesdeApi(
            @Parameter(description = "Lista de pedidos a insertar", required = true)
            @RequestBody List<Pedido> pedidos
    ) {

        log.info("Se llamó al endpoint de insertarPedidosDesdeApi");

        long inicioMillis = System.currentTimeMillis();
        LocalDateTime tiempoInicio = LocalDateTime.now();

        try {
            log.info("========================================");
            log.info("API: SOLICITUD DE INSERCIÓN DIRECTA DE PEDIDOS");
            log.info("========================================");
            log.info("Cantidad de pedidos recibidos: {}",
                    (pedidos != null ? pedidos.size() : 0));

            if (pedidos == null || pedidos.isEmpty()) {
                Map<String, Object> respuestaVacia = new HashMap<>();
                respuestaVacia.put("exito", false);
                respuestaVacia.put("mensaje", "No se recibieron pedidos para insertar");
                return ResponseEntity.badRequest().body(respuestaVacia);
            }

            // crear el arreglo de Productos para cada Pedido
            // se define la cantidad de productos de forma estática (solo para la demostración)
            // FALTA MEJORAR ESTO
            int cantProds = 4;
            for (Pedido ped : pedidos) {
                ArrayList<Producto> prods = new ArrayList<>();
                for (int i=0; i<cantProds; i++) {
                    Producto producto = new Producto();
                    producto.setNombre("PRODUCT-" + (i + 1)); // Nombre del producto
                    producto.setPeso(1.0); // Peso por defecto (como en el Backend de ejemplo)
                    producto.setVolumen(1.0); // Volumen por defecto (como en el Backend de ejemplo)
                    producto.setEstado(EstadoProducto.EN_ALMACEN);
                    producto.setPedido(ped);
                    prods.add(producto);
                }
                ped.setProductos(prods);
            }

            var pedidosCreados = pedidoService.insertarBulk(pedidos);

            LocalDateTime tiempoFin = LocalDateTime.now();
            long duracionSegundos = ChronoUnit.SECONDS.between(tiempoInicio, tiempoFin);
            long tiempoTotalMs = System.currentTimeMillis() - inicioMillis;

            int totalCreados = (pedidosCreados != null ? pedidosCreados.size() : 0);

            Map<String, Object> respuesta = new HashMap<>();
            respuesta.put("exito", true);
            respuesta.put("mensaje", "Pedidos insertados exitosamente desde API");

            respuesta.put("estadisticas", Map.of(
                    "pedidosCargados", totalCreados,
                    "pedidosCreados", totalCreados,
                    "pedidosFiltrados", 0,
                    "erroresParseo", 0,
                    "erroresArchivos", 0,
                    "duracionSegundos", duracionSegundos
            ));

            respuesta.put("tiempoInicio", tiempoInicio.toString());
            respuesta.put("tiempoFin", tiempoFin.toString());
            respuesta.put("tiempoEjecucionMs", tiempoTotalMs);

            return ResponseEntity.ok(respuesta);

        } catch (Exception e) {
            log.error("Error al insertar pedidos desde API", e);
            Map<String, Object> respuestaError = new HashMap<>();
            respuestaError.put("exito", false);
            respuestaError.put("mensaje", "Error al insertar pedidos: " + e.getMessage());
            respuestaError.put("error", e.getClass().getSimpleName());
            return ResponseEntity.internalServerError().body(respuestaError);
        }
    }

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

    private String obtenerDirectorioPredeterminado() {
        String directorioProyecto = System.getProperty("user.dir");
        File dataDirRelativo = new File(directorioProyecto, "data/pedidos");

        if (dataDirRelativo.exists() && dataDirRelativo.isDirectory()) {
            return dataDirRelativo.getAbsolutePath();
        }

        dataDirRelativo = new File(directorioProyecto, "data");
        if (dataDirRelativo.exists() && dataDirRelativo.isDirectory()) {
            return dataDirRelativo.getAbsolutePath();
        }

        return directorioProyecto;
    }

    @PostMapping("/load-for-daily")
    @Operation(
            summary = "Cargar pedidos para simulación diaria",
            description = "Carga pedidos dentro de una ventana de 10 minutos para simulación en tiempo real")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pedidos cargados exitosamente"),
            @ApiResponse(responseCode = "400", description = "Parámetros inválidos"),
            @ApiResponse(responseCode = "500", description = "Error durante la carga")
    })
    public ResponseEntity<Map<String, Object>> cargarParaSimulacionDiaria(
            @Parameter(description = "Hora de inicio (ISO 8601)", example = "2025-01-02T00:00:00")
            @RequestParam String startTime) {
        
        try {
            // Parse ISO 8601 con timezone (ej: 2025-11-28T05:00:00.000Z)
            LocalDateTime horaInicio;
            if (startTime.endsWith("Z") || startTime.contains("+") || startTime.matches(".*[+-]\\d{2}:\\d{2}$")) {
                // Tiene zona horaria - usar Instant y convertir a LocalDateTime
                horaInicio = java.time.Instant.parse(startTime)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime();
            } else {
                // Formato sin zona horaria
                horaInicio = LocalDateTime.parse(startTime);
            }
            LocalDateTime horaFin = horaInicio.plusMinutes(10);
            
            log.info("Cargando pedidos para simulación diaria: {} a {}", horaInicio, horaFin);
            
            String directorioData = obtenerDirectorioPredeterminado();
            
            DataLoadService.ResultadoCargaDatos resultado = 
                dataLoadService.cargarPedidosDesdeArchivos(directorioData, horaInicio, horaFin);
            
            if (!resultado.getExito()) {
                return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", resultado.getMensajeError() != null ? resultado.getMensajeError() : "Error desconocido"
                ));
            }
            
            resultado.calcularDuracion();
            Long duracionSeg = resultado.getDuracionSegundos() != null ? resultado.getDuracionSegundos() : 0L;
            
            Map<String, Object> respuesta = new HashMap<>();
            respuesta.put("success", true);
            respuesta.put("message", "Pedidos cargados para simulación diaria");
            respuesta.put("statistics", Map.of(
                "ordersLoaded", resultado.getPedidosCargados() != null ? resultado.getPedidosCargados() : 0,
                "ordersCreated", resultado.getPedidosCreados() != null ? resultado.getPedidosCreados() : 0,
                "ordersFiltered", resultado.getPedidosFiltrados() != null ? resultado.getPedidosFiltrados() : 0,
                "customersCreated", 0,
                "parseErrors", resultado.getErroresParseo() != null ? resultado.getErroresParseo() : 0,
                "fileErrors", resultado.getErroresArchivos() != null ? resultado.getErroresArchivos() : 0,
                "durationSeconds", duracionSeg
            ));
            respuesta.put("timeWindow", Map.of(
                "startTime", horaInicio.toString(),
                "endTime", horaFin.toString(),
                "durationMinutes", 10
            ));
            
            return ResponseEntity.ok(respuesta);
            
        } catch (Exception e) {
            log.error("Error cargando pedidos para simulación diaria", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error: " + e.getMessage()
            ));
        }
    }

}
