package com.grupo5e.morapack.controller;

import com.grupo5e.morapack.service.DataImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller para importar datos desde archivos .txt
 * Sigue el patrón de MoraPack-Backend: cada archivo se procesa y guarda en BD inmediatamente
 */
@RestController
@RequestMapping("/api/data-import")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Importación de Datos", description = "API para importar aeropuertos, vuelos y pedidos desde archivos")
@Slf4j
public class DataImportController {

    private final DataImportService dataImportService;

    /**
     * Importar aeropuertos desde directorio predeterminado
     * POST /api/data-import/airports (sin archivo)
     */
    @Operation(
        summary = "Cargar aeropuertos desde directorio",
        description = "Carga aeropuertos desde data/aeropuertosinfo.txt automáticamente"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Aeropuertos cargados exitosamente"),
        @ApiResponse(responseCode = "400", description = "Error en el procesamiento")
    })
    @PostMapping("/airports")
    public ResponseEntity<Map<String, Object>> loadAirportsFromDirectory() {
        log.info("📤 Cargando aeropuertos desde directorio predeterminado");
        
        Map<String, Object> result = dataImportService.importAirportsFromDirectory();
        
        boolean success = (boolean) result.get("success");
        HttpStatus status = success ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        
        if (success) {
            log.info("✅ Aeropuertos cargados: {}", result.get("count"));
        } else {
            log.error("❌ Error cargando aeropuertos: {}", result.get("message"));
        }
        
        return ResponseEntity.status(status).body(result);
    }

    /**
     * Importar aeropuertos desde archivo .txt subido
     * POST /api/data-import/airports/upload
     * 
     * Formato esperado: aeropuertosinfo.txt
     */
    @Operation(
        summary = "Subir archivo de aeropuertos",
        description = "Importa aeropuertos desde archivo .txt subido y los guarda en BD inmediatamente"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Aeropuertos importados exitosamente"),
        @ApiResponse(responseCode = "400", description = "Archivo inválido o error en el procesamiento")
    })
    @PostMapping(value = "/airports/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadAirports(
            @Parameter(description = "Archivo aeropuertosinfo.txt")
            @RequestParam("file") MultipartFile file) {
        
        String filename = file.getOriginalFilename();
        log.info("📤 Recibida solicitud de importación de aeropuertos: {}", filename);
        
        // Validar archivo básico
        if (file.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "El archivo está vacío");
            log.warn("❌ Archivo vacío rechazado");
            return ResponseEntity.badRequest().body(error);
        }
        
        if (filename == null || !filename.endsWith(".txt")) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "El archivo debe ser formato .txt");
            log.warn("❌ Formato de archivo inválido: {}", filename);
            return ResponseEntity.badRequest().body(error);
        }
        
        // Procesar e insertar en BD
        Map<String, Object> result = dataImportService.importAirports(file);
        
        boolean success = (boolean) result.get("success");
        HttpStatus status = success ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        
        if (success) {
            log.info("✅ Aeropuertos importados: {}", result.get("count"));
        } else {
            log.error("❌ Error importando aeropuertos: {}", result.get("message"));
        }
        
        return ResponseEntity.status(status).body(result);
    }

    /**
     * Cargar vuelos desde directorio predeterminado
     * POST /api/data-import/flights (sin archivo)
     */
    @Operation(
        summary = "Cargar vuelos desde directorio",
        description = "Carga vuelos desde data/vuelos.txt automáticamente"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Vuelos cargados exitosamente"),
        @ApiResponse(responseCode = "400", description = "Error en el procesamiento")
    })
    @PostMapping("/flights")
    public ResponseEntity<Map<String, Object>> loadFlightsFromDirectory() {
        log.info("📤 Cargando vuelos desde directorio predeterminado");
        
        Map<String, Object> result = dataImportService.importFlightsFromDirectory();
        
        boolean success = (boolean) result.get("success");
        HttpStatus status = success ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        
        if (success) {
            log.info("✅ Vuelos cargados: {}", result.get("count"));
        } else {
            log.error("❌ Error cargando vuelos: {}", result.get("message"));
        }
        
        return ResponseEntity.status(status).body(result);
    }

    /**
     * Importar vuelos desde archivo .txt subido
     * POST /api/data-import/flights/upload
     * 
     * Formato esperado: vuelos.txt (ORIGEN-DESTINO-SALIDA-LLEGADA-CAPACIDAD)
     * Requiere que existan aeropuertos en BD
     */
    @Operation(
        summary = "Subir archivo de vuelos",
        description = "Importa vuelos desde archivo .txt subido y los guarda en BD inmediatamente. Requiere aeropuertos previamente importados."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Vuelos importados exitosamente"),
        @ApiResponse(responseCode = "400", description = "Archivo inválido, error en el procesamiento o aeropuertos no encontrados")
    })
    @PostMapping(value = "/flights/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadFlights(
            @Parameter(description = "Archivo vuelos.txt")
            @RequestParam("file") MultipartFile file) {
        
        String filename = file.getOriginalFilename();
        log.info("📤 Recibida solicitud de importación de vuelos: {}", filename);
        
        // Validar archivo básico
        if (file.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "El archivo está vacío");
            log.warn("❌ Archivo vacío rechazado");
            return ResponseEntity.badRequest().body(error);
        }
        
        if (filename == null || !filename.endsWith(".txt")) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "El archivo debe ser formato .txt");
            log.warn("❌ Formato de archivo inválido: {}", filename);
            return ResponseEntity.badRequest().body(error);
        }
        
        // Procesar e insertar en BD
        Map<String, Object> result = dataImportService.importFlights(file);
        
        boolean success = (boolean) result.get("success");
        HttpStatus status = success ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        
        if (success) {
            log.info("✅ Vuelos importados: {}", result.get("count"));
        } else {
            log.error("❌ Error importando vuelos: {}", result.get("message"));
        }
        
        return ResponseEntity.status(status).body(result);
    }
    @Operation(
            summary = "Importar cancelaciones",
            description = "Importa cancelaciones desde archivo .txt y los guarda en BD inmediatamente. Requiere vuelos previamente importados."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cancelaciones importados exitosamente"),
            @ApiResponse(responseCode = "400", description = "Archivo inválido, error en el procesamiento o vuelos no encontrados")
    })
    @PostMapping(value="/cancelaciones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String,Object>> importCancelaciones(
            @Parameter(description = "Archivo cancelaciones.txt")
            @RequestParam("file") MultipartFile cancelacionesFile) {

        if (cancelacionesFile == null) {
            Map<String,Object> resp = Map.of(
                    "success", false,
                    "message", "Debe proporcionar el archivo de cancelaciones"
            );
            return ResponseEntity.badRequest().body(resp);
        }

        Map<String,Object> result = dataImportService.importCancelaciones(cancelacionesFile);
        boolean success = Boolean.TRUE.equals(result.get("success"));
        return ResponseEntity.status(success ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(result);
    }
    /**
     * Importar pedidos desde archivo .txt
     * POST /api/data-import/orders
     * 
     * Formato esperado: pedidos.txt (id-fecha-hora-minuto-destino-cantidad-cliente)
     * Requiere que existan aeropuertos en BD
     */
    @Operation(
        summary = "Importar pedidos",
        description = "Importa pedidos desde archivo .txt y los guarda en BD inmediatamente. " +
                      "Opcionalmente filtra por ventana de tiempo para escenarios diario/semanal. " +
                      "Requiere aeropuertos previamente importados."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Pedidos importados exitosamente"),
        @ApiResponse(responseCode = "400", description = "Archivo inválido, error en el procesamiento o aeropuertos no encontrados")
    })
    @PostMapping(value = "/orders", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadOrders(
            @Parameter(description = "Archivo pedidos.txt o _pedidos_{AIRPORT}_.txt")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Hora de inicio para filtrar pedidos (ISO 8601, opcional)", example = "2025-01-02T00:00:00")
            @RequestParam(required = false) String horaInicio,
            @Parameter(description = "Hora de fin para filtrar pedidos (ISO 8601, opcional)", example = "2025-01-09T00:00:00")
            @RequestParam(required = false) String horaFin) {
        
        String filename = file.getOriginalFilename();
        log.info("📤 Recibida solicitud de importación de pedidos: {}", filename);
        
        if (horaInicio != null && horaFin != null) {
            log.info("   🕒 Ventana de tiempo: {} a {}", horaInicio, horaFin);
        }
        
        // Validar archivo básico
        if (file.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "El archivo está vacío");
            log.warn("❌ Archivo vacío rechazado");
            return ResponseEntity.badRequest().body(error);
        }
        
        if (filename == null || !filename.endsWith(".txt")) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "El archivo debe ser formato .txt");
            log.warn("❌ Formato de archivo inválido: {}", filename);
            return ResponseEntity.badRequest().body(error);
        }
        
        // Parsear fechas si se proporcionaron
        LocalDateTime horaInicioDateTime = null;
        LocalDateTime horaFinDateTime = null;
        
        try {
            if (horaInicio != null && !horaInicio.isEmpty()) {
                horaInicioDateTime = LocalDateTime.parse(horaInicio);
            }
            if (horaFin != null && !horaFin.isEmpty()) {
                horaFinDateTime = LocalDateTime.parse(horaFin);
            }
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Formato de fecha inválido. Use ISO 8601: yyyy-MM-ddTHH:mm:ss");
            log.warn("❌ Error parseando fechas: {}", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
        
        // Procesar e insertar en BD con filtrado opcional
        Map<String, Object> result = dataImportService.importOrders(file, horaInicioDateTime, horaFinDateTime);
        
        boolean success = (boolean) result.get("success");
        HttpStatus status = success ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        
        if (success) {
            log.info("✅ Pedidos importados: {}", result.get("count"));
            if (result.containsKey("pedidosFiltrados")) {
                log.info("   📊 Pedidos filtrados (fuera de ventana): {}", result.get("pedidosFiltrados"));
            }
        } else {
            log.error("❌ Error importando pedidos: {}", result.get("message"));
        }
        
        return ResponseEntity.status(status).body(result);
    }

    /**
     * Importar múltiples archivos de pedidos en batch
     * POST /api/data-import/orders/batch
     * 
     * Acepta múltiples archivos de pedidos (e.g., _pedidos_EBCI_.txt, _pedidos_EDDI_.txt)
     * y los procesa en secuencia, generando externalId único por archivo
     */
    @Operation(
        summary = "Importar pedidos en batch",
        description = "Importa múltiples archivos de pedidos y los guarda en BD. " +
                      "Cada archivo se procesa con su propio aeropuerto de origen para evitar colisiones de ID. " +
                      "Opcionalmente filtra por ventana de tiempo."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Pedidos importados exitosamente"),
        @ApiResponse(responseCode = "400", description = "Error en el procesamiento de archivos")
    })
    @PostMapping(value = "/orders/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadOrdersBatch(
            @Parameter(description = "Múltiples archivos de pedidos (_pedidos_{AIRPORT}_.txt)")
            @RequestParam("files") MultipartFile[] files,
            @Parameter(description = "Hora de inicio para filtrar pedidos (ISO 8601, opcional)", example = "2025-01-02T00:00:00")
            @RequestParam(required = false) String horaInicio,
            @Parameter(description = "Hora de fin para filtrar pedidos (ISO 8601, opcional)", example = "2025-01-09T00:00:00")
            @RequestParam(required = false) String horaFin) {
        
        log.info("📦 Batch import de {} archivos de pedidos", files.length);
        
        // Parsear fechas opcionales
        LocalDateTime horaInicioDateTime = null;
        LocalDateTime horaFinDateTime = null;
        try {
            if (horaInicio != null && !horaInicio.isEmpty()) {
                horaInicioDateTime = LocalDateTime.parse(horaInicio);
            }
            if (horaFin != null && !horaFin.isEmpty()) {
                horaFinDateTime = LocalDateTime.parse(horaFin);
            }
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Formato de fecha inválido. Use ISO 8601: yyyy-MM-ddTHH:mm:ss");
            log.warn("Error parseando fechas: {}", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
        
        // Procesar archivos en batch
        Map<String, Object> result = dataImportService.importOrdersBatch(files, horaInicioDateTime, horaFinDateTime);
        
        boolean success = (boolean) result.get("success");
        HttpStatus status = success ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        
        if (success) {
            log.info("✅ Batch import completado: {} pedidos de {} archivos", 
                result.get("totalOrders"), result.get("filesProcessed"));
        } else {
            log.error("Error en batch import: {}", result.get("message"));
        }
        
        return ResponseEntity.status(status).body(result);
    }

    /**
     * Obtener estadísticas de la base de datos
     * GET /api/data-import/stats
     */
    @Operation(
        summary = "Estadísticas de base de datos",
        description = "Obtiene conteos actuales de aeropuertos, vuelos, pedidos y productos en la BD"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Estadísticas obtenidas exitosamente")
    })
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDatabaseStats() {
        log.info("📊 Solicitud de estadísticas de BD");
        Map<String, Object> stats = dataImportService.getDatabaseStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Limpiar TODA la base de datos (respeta foreign keys)
     * DELETE /api/data-import/clear-all
     * 
     * ADVERTENCIA: Elimina TODOS los datos en orden correcto
     */
    @Operation(
        summary = "Limpiar toda la base de datos",
        description = "ADVERTENCIA: Elimina TODOS los datos de la BD (simulaciones, asignaciones, pedidos, productos, vuelos, aeropuertos, ciudades)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Base de datos limpiada exitosamente"),
        @ApiResponse(responseCode = "500", description = "Error al limpiar base de datos")
    })
    @DeleteMapping("/clear-all")
    public ResponseEntity<Map<String, Object>> clearAllData() {
        log.warn("🗑️ LIMPIEZA COMPLETA de base de datos solicitada");
        Map<String, Object> result = dataImportService.clearAllData();
        
        boolean success = (boolean) result.get("success");
        HttpStatus status = success ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR;
        
        if (success) {
            log.info("✅ Base de datos limpiada exitosamente");
        } else {
            log.error("❌ Error limpiando base de datos: {}", result.get("message"));
        }
        
        return ResponseEntity.status(status).body(result);
    }

    /**
     * Limpiar solo pedidos y productos
     * DELETE /api/data-import/clear-orders
     */
    @Operation(
        summary = "Limpiar solo pedidos",
        description = "Elimina pedidos, productos y sus asignaciones/simulaciones relacionadas"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Pedidos eliminados exitosamente"),
        @ApiResponse(responseCode = "500", description = "Error al eliminar pedidos")
    })
    @DeleteMapping("/clear-orders")
    public ResponseEntity<Map<String, Object>> clearOrders() {
        log.warn("🗑️ Limpieza de PEDIDOS solicitada");
        Map<String, Object> result = dataImportService.clearOrders();
        
        boolean success = (boolean) result.get("success");
        HttpStatus status = success ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR;
        
        if (success) {
            log.info("✅ Pedidos eliminados: {}", result.get("deleted"));
        } else {
            log.error("❌ Error eliminando pedidos: {}", result.get("message"));
        }
        
        return ResponseEntity.status(status).body(result);
    }

    /**
     * Limpiar solo vuelos
     * DELETE /api/data-import/clear-flights
     */
    @Operation(
        summary = "Limpiar solo vuelos",
        description = "Elimina vuelos y sus asignaciones/simulaciones relacionadas"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Vuelos eliminados exitosamente"),
        @ApiResponse(responseCode = "500", description = "Error al eliminar vuelos")
    })
    @DeleteMapping("/clear-flights")
    public ResponseEntity<Map<String, Object>> clearFlights() {
        log.warn("🗑️ Limpieza de VUELOS solicitada");
        Map<String, Object> result = dataImportService.clearFlights();
        
        boolean success = (boolean) result.get("success");
        HttpStatus status = success ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR;
        
        if (success) {
            log.info("✅ Vuelos eliminados: {}", result.get("deleted"));
        } else {
            log.error("❌ Error eliminando vuelos: {}", result.get("message"));
        }
        
        return ResponseEntity.status(status).body(result);
    }

    /**
     * Limpiar aeropuertos y ciudades
     * DELETE /api/data-import/clear-airports
     * 
     * ADVERTENCIA: También elimina vuelos, pedidos y simulaciones dependientes
     */
    @Operation(
        summary = "Limpiar aeropuertos",
        description = "ADVERTENCIA: Elimina aeropuertos, ciudades y TODOS los datos dependientes (vuelos, pedidos, simulaciones)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Aeropuertos eliminados exitosamente"),
        @ApiResponse(responseCode = "500", description = "Error al eliminar aeropuertos")
    })
    @DeleteMapping("/clear-airports")
    public ResponseEntity<Map<String, Object>> clearAirports() {
        log.warn("🗑️ Limpieza de AEROPUERTOS solicitada (incluye datos dependientes)");
        Map<String, Object> result = dataImportService.clearAirports();
        
        boolean success = (boolean) result.get("success");
        HttpStatus status = success ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR;
        
        if (success) {
            log.info("✅ Aeropuertos eliminados: {}", result.get("deleted"));
        } else {
            log.error("❌ Error eliminando aeropuertos: {}", result.get("message"));
        }
        
        return ResponseEntity.status(status).body(result);
    }

    /**
     * Limpiar solo simulaciones (mantiene datos base)
     * DELETE /api/data-import/clear-simulations
     */
    @Operation(
        summary = "Limpiar solo simulaciones",
        description = "Elimina resultados de simulaciones pero mantiene datos base (aeropuertos, vuelos, pedidos)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Simulaciones eliminadas exitosamente"),
        @ApiResponse(responseCode = "500", description = "Error al eliminar simulaciones")
    })
    @DeleteMapping("/clear-simulations")
    public ResponseEntity<Map<String, Object>> clearSimulations() {
        log.warn("🗑️ Limpieza de SIMULACIONES solicitada");
        Map<String, Object> result = dataImportService.clearSimulations();
        
        boolean success = (boolean) result.get("success");
        HttpStatus status = success ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR;
        
        if (success) {
            log.info("✅ Simulaciones eliminadas: {}", result.get("deleted"));
        } else {
            log.error("❌ Error eliminando simulaciones: {}", result.get("message"));
        }
        
        return ResponseEntity.status(status).body(result);
    }

    /**
     * Obtener estado de la importación
     * GET /api/data-import/status
     */
    @Operation(
        summary = "Estado de importación",
        description = "Obtiene información sobre los endpoints de importación disponibles"
    )
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getImportStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("message", "Endpoints de importación operacionales");
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("airports", "/api/data-import/airports (POST)");
        endpoints.put("flights", "/api/data-import/flights (POST)");
        endpoints.put("orders", "/api/data-import/orders (POST)");
        endpoints.put("orders-batch", "/api/data-import/orders/batch (POST)");
        endpoints.put("stats", "/api/data-import/stats (GET)");
        endpoints.put("clear-all", "/api/data-import/clear-all (DELETE)");
        endpoints.put("clear-orders", "/api/data-import/clear-orders (DELETE)");
        endpoints.put("clear-flights", "/api/data-import/clear-flights (DELETE)");
        endpoints.put("clear-airports", "/api/data-import/clear-airports (DELETE)");
        endpoints.put("clear-simulations", "/api/data-import/clear-simulations (DELETE)");
        status.put("endpoints", endpoints);
        return ResponseEntity.ok(status);
    }
}

