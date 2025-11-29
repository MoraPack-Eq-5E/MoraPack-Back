package com.grupo5e.morapack.service;

import com.grupo5e.morapack.api.dto.FileValidationResult;
import com.grupo5e.morapack.core.model.*;
import com.grupo5e.morapack.core.validation.EntityValidator;
import com.grupo5e.morapack.core.validation.PACKTimeValidator;
import com.grupo5e.morapack.repository.CancelacionRepository;
import com.grupo5e.morapack.repository.CiudadRepository;
import com.grupo5e.morapack.repository.ClienteRepository;
import com.grupo5e.morapack.repository.ProductoRepository;
import com.grupo5e.morapack.utils.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio para importar datos desde archivos .txt
 * Reutiliza los Lectores existentes (LectorAeropuerto, LectorVuelos, LectorPedidos)
 * y guarda los datos directamente en BD (sin IDs temporales ni sessionId)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataImportService {

    private final AeropuertoService aeropuertoService;
    private final VueloService vueloService;
    private final PedidoService pedidoService;
    private final ClienteService clienteService;
    private final CiudadRepository ciudadRepository;
    private final ClienteRepository clienteRepository;
    private final ProductoRepository productoRepository;
    private final CancelacionRepository cancelacionRepository;
    private final com.grupo5e.morapack.repository.AlmacenRepository almacenRepository;
    private final CancelacionService cancelacionService;
    private final com.grupo5e.morapack.service.impl.BatchService batchService;

    /**
     * Guarda MultipartFile a archivo temporal
     */
    private Path guardarArchivoTemporal(MultipartFile file) throws IOException {
        Path tempFile = Files.createTempFile("morapack-import-", ".txt");
        Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
        return tempFile;
    }
    
    /**
     * Elimina archivo temporal
     */
    private void eliminarArchivoTemporal(Path path) {
        try {
            if (path != null && Files.exists(path)) {
                Files.delete(path);
            }
        } catch (IOException e) {
            log.warn("No se pudo eliminar archivo temporal: {}", path, e);
        }
    }

    /**
     * Importa aeropuertos desde archivo .txt y los guarda en BD inmediatamente
     * 
     * @param file Archivo aeropuertosinfo.txt
     * @return Map con success, message, count, cities
     */
    @Transactional
    public Map<String, Object> importAirports(MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        
        Path tempFile = null;
        try {
            log.info("🏢 Iniciando importación de aeropuertos...");
            
            // 1. Guardar archivo temporal y usar LectorAeropuerto
            tempFile = guardarArchivoTemporal(file);
            LectorAeropuerto lector = new LectorAeropuerto(tempFile.toString());
            ArrayList<Aeropuerto> aeropuertos = lector.leerAeropuertos();
            
            if (aeropuertos.isEmpty()) {
                result.put("success", false);
                result.put("message", "No se encontraron aeropuertos en el archivo");
                result.put("count", 0);
                log.warn("❌ No se encontraron aeropuertos en el archivo");
                return result;
            }
            
            log.info("   Aeropuertos parseados: {}", aeropuertos.size());
            
            // 2. Validar aeropuertos antes de guardar
            try {
                EntityValidator.validateAeropuertos(aeropuertos);
                log.info("   ✅ Validación de aeropuertos exitosa");
            } catch (IllegalArgumentException e) {
                log.error("   ❌ Error de validación en aeropuertos: {}", e.getMessage());
                result.put("success", false);
                result.put("message", "Error de validación: " + e.getMessage());
                result.put("count", 0);
                return result;
            }
            
            // 3. Extraer ciudades únicas, validar y guardar en BD
            Set<Ciudad> ciudadesUnicas = aeropuertos.stream()
                .map(Aeropuerto::getCiudad)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            
            // Validar ciudades
            for (Ciudad ciudad : ciudadesUnicas) {
                EntityValidator.validateCiudad(ciudad);
            }
            
            List<Ciudad> ciudadesGuardadas = ciudadRepository.saveAll(new ArrayList<>(ciudadesUnicas));
            log.info("   ✅ {} ciudades guardadas en BD", ciudadesGuardadas.size());
            
            // 4. Actualizar referencias de ciudad en aeropuertos con IDs reales de BD
            Map<String, Ciudad> mapaCiudades = ciudadesGuardadas.stream()
                .collect(Collectors.toMap(
                    c -> c.getNombre() + "-" + c.getContinente(),
                    c -> c
                ));
            
            for (Aeropuerto aeropuerto : aeropuertos) {
                Ciudad ciudad = aeropuerto.getCiudad();
                if (ciudad != null) {
                    String key = ciudad.getNombre() + "-" + ciudad.getContinente();
                    Ciudad ciudadGuardada = mapaCiudades.get(key);
                    if (ciudadGuardada != null) {
                        aeropuerto.setCiudad(ciudadGuardada);
                    }
                }
            }
            
            // 5. Verificar si ya existen aeropuertos y filtrar duplicados
            log.info("   🔍 Verificando aeropuertos existentes...");
            List<Aeropuerto> aeropuertosExistentes = aeropuertoService.listar();
            Set<String> codigosExistentes = aeropuertosExistentes.stream()
                .map(Aeropuerto::getCodigoIATA)
                .collect(Collectors.toSet());
            
            List<Aeropuerto> aeropuertosNuevos = aeropuertos.stream()
                .filter(a -> !codigosExistentes.contains(a.getCodigoIATA()))
                .collect(Collectors.toList());
            
            if (aeropuertosNuevos.isEmpty()) {
                log.warn("   ⚠️ Todos los aeropuertos ya existen en BD. Saltando inserción.");
                log.info("   ℹ️ Si deseas re-importar, primero limpia la base de datos.");
                
                // Retornar resultado exitoso sin insertar duplicados
                result.put("success", true);
                result.put("message", "Aeropuertos ya existen en base de datos. No se insertaron duplicados.");
                result.put("count", aeropuertosExistentes.size());
                result.put("cities", (int) ciudadesGuardadas.stream().count());
                
                return result;
            }
            
            log.info("   ✅ {} aeropuertos nuevos a insertar (de {} totales)", 
                aeropuertosNuevos.size(), aeropuertos.size());
            
            // 6. Extraer y guardar almacenes de aeropuertos nuevos
            // NOTA: LectorAeropuerto ya creó los almacenes con capacidades correctas
            List<com.grupo5e.morapack.core.model.Almacen> almacenes = new ArrayList<>();
            for (Aeropuerto aeropuerto : aeropuertosNuevos) {
                Almacen almacen = aeropuerto.getAlmacen();
                if (almacen != null) {
                    // Asegurarse de que el almacén no tiene ID (será generado por BD)
                    almacen.setId(null);
                    almacenes.add(almacen);
                } else {
                    // Fallback: crear almacén con capacidad por defecto si no existe
                    log.warn("   ⚠️ Aeropuerto {} no tiene almacén, creando con capacidad por defecto", 
                        aeropuerto.getCodigoIATA());
                    Almacen almacenNuevo = Almacen.builder()
                        .nombre("Almacen " + aeropuerto.getCodigoIATA())
                        .capacidadMaxima(1000)
                        .capacidadUsada(0)
                        .esAlmacenPrincipal(false)
                        .build();
                    almacenes.add(almacenNuevo);
                    aeropuerto.setAlmacen(almacenNuevo);
                }
            }
            
            // 7. Guardar almacenes en BD (esto les asigna IDs)
            List<com.grupo5e.morapack.core.model.Almacen> almacenesGuardados = almacenRepository.saveAll(almacenes);
            log.info("   ✅ {} almacenes guardados (con capacidades del archivo)", almacenesGuardados.size());
            
            // 8. Actualizar aeropuertos con referencias a almacenes guardados (con IDs)
            for (int i = 0; i < aeropuertosNuevos.size(); i++) {
                Aeropuerto aero = aeropuertosNuevos.get(i);
                com.grupo5e.morapack.core.model.Almacen alm = almacenesGuardados.get(i);
                // Establecer relación bidireccional
                aero.setAlmacen(alm);
                alm.setAeropuerto(aero);
            }
            
            // 9. Guardar aeropuertos con almacenes asociados
            List<Aeropuerto> aeropuertosGuardados = aeropuertoService.insertarBulk(aeropuertosNuevos);
            
            // 10. Re-guardar almacenes para persistir la relación bidireccional
            almacenRepository.saveAll(almacenesGuardados);
            
            result.put("success", true);
            result.put("message", "Aeropuertos y almacenes importados exitosamente");
            result.put("count", aeropuertosGuardados.size());
            result.put("cities", ciudadesGuardadas.size());
            result.put("almacenes", almacenesGuardados.size());
            
            log.info("✅ {} aeropuertos y {} almacenes importados con IDs: {}", 
                     aeropuertosGuardados.size(),
                     almacenesGuardados.size(),
                     aeropuertosGuardados.stream()
                         .limit(3)
                         .map(a -> a.getId() + ":" + a.getCodigoIATA())
                         .collect(Collectors.joining(", ")));
            
        } catch (Exception e) {
            log.error("❌ Error importando aeropuertos", e);
            result.put("success", false);
            result.put("message", "Error: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
        } finally {
            eliminarArchivoTemporal(tempFile);
        }
        
        return result;
    }

    /**
     * Importa vuelos desde archivo .txt y los guarda en BD inmediatamente
     * Requiere que existan aeropuertos en BD
     * 
     * @param file Archivo vuelos.txt
     * @return Map con success, message, count
     */
    @Transactional
    public Map<String, Object> importFlights(MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.info("✈️ Iniciando importación de vuelos...");
            
            // 1. Verificar que existan aeropuertos en BD
            List<Aeropuerto> aeropuertos = aeropuertoService.listar();
            if (aeropuertos.isEmpty()) {
                result.put("success", false);
                result.put("message", "Debe importar aeropuertos primero antes de importar vuelos");
                result.put("count", 0);
                log.warn("❌ No hay aeropuertos en BD. Importe aeropuertos primero.");
                return result;
            }
            
            log.info("   Aeropuertos disponibles en BD: {}", aeropuertos.size());
            
            // 2. OPTIMIZADO: Leer vuelos directamente desde InputStream (sin archivo temporal)
            // Esto mejora el rendimiento evitando I/O de disco innecesario
            List<Vuelo> vuelos = LectorVuelos.leerVuelosDesdeStream(
                file.getInputStream(),
                aeropuertos
            );
            
            if (vuelos.isEmpty()) {
                result.put("success", false);
                result.put("message", "No se encontraron vuelos en el archivo");
                result.put("count", 0);
                log.warn("❌ No se encontraron vuelos en el archivo");
                return result;
            }
            
            log.info("   Vuelos parseados: {}", vuelos.size());
            
            // 3. Validar vuelos antes de guardar
            try {
                EntityValidator.validateVuelos(vuelos);
                log.info("   ✅ Validación de vuelos exitosa");
            } catch (IllegalArgumentException e) {
                log.error("   ❌ Error de validación en vuelos: {}", e.getMessage());
                result.put("success", false);
                result.put("message", "Error de validación: " + e.getMessage());
                result.put("count", 0);
                return result;
            }
            
            // 5. OPTIMIZADO: Validar tiempos PACK en paralelo (solo informativo, no bloquea)
            long vuelosPACKCompliant = vuelos.parallelStream()
                .filter(PACKTimeValidator::validateFullPACKCompliance)
                .count();
            log.info("   📊 {} de {} vuelos cumplen estándares PACK ({}%)", 
                vuelosPACKCompliant, vuelos.size(), 
                vuelos.size() > 0 ? (vuelosPACKCompliant * 100 / vuelos.size()) : 0);
            
            // 4. OPTIMIZADO: Guardar vuelos en BD con batch real
            int vuelosInsertados = batchService.insertarVuelosEnBatch(vuelos);
            
            result.put("success", true);
            result.put("message", "Vuelos importados exitosamente");
            result.put("count", vuelosInsertados);
            
            log.info("✅ {} vuelos importados exitosamente", vuelosInsertados);
            
        } catch (Exception e) {
            log.error("❌ Error importando vuelos", e);
            result.put("success", false);
            result.put("message", "Error: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
        }
        // Ya no necesitamos limpiar archivo temporal porque leemos directamente del stream
        
        return result;
    }
    @Transactional
    public Map<String, Object> importCancelaciones(MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        Path tempFile = null;

        try {
            log.info("✈️ Iniciando importación de vuelos...");

            // 1. Verificar que existan aeropuertos en BD
            List<Vuelo> vuelos = vueloService.listar();
            if (vuelos.isEmpty()) {
                result.put("success", false);
                result.put("message", "Debe importar vuelos primero antes de importar cancelaciones");
                result.put("count", 0);
                log.warn("❌ No hay vuelos en BD. Importe vuelos primero.");
                return result;
            }

            log.info("   Vuelos disponibles en BD: {}", vuelos.size());

            // 2. Guardar archivo temporal y usar LectorVuelos
            tempFile = guardarArchivoTemporal(file);
            LectorCancelaciones lector = new LectorCancelaciones(
                    tempFile.toString(),
                    new ArrayList<>(vuelos)
            );
            ArrayList<Cancelacion> cancelaciones = (ArrayList<Cancelacion>) lector.leerCancelaciones();

            if (cancelaciones.isEmpty()) {
                result.put("success", false);
                result.put("message", "No se encontraron cancelaciones en el archivo");
                result.put("count", 0);
                log.warn("❌ No se encontraron cancelaciones en el archivo");
                return result;
            }

            log.info("   Cancelaciones parseados: {}", cancelaciones.size());
            // 🧩 IMPRIMIR TODAS LAS CANCELACIONES PARSEADAS
            log.info("────────────────────────────────────────────────────────────");
            for (int i = 0; i < cancelaciones.size(); i++) {
                Cancelacion c = cancelaciones.get(i);
                log.info("   [{}] Día={} | {}-{} | {}:{} | VueloID={} | FechaCancelacion={}",
                        i + 1,
                        c.getDiasCancelado(),
                        c.getCodigoIATAOrigen(),
                        c.getCodigoIATADestino(),
                        c.getHora(),
                        c.getMinuto(),
                        (c.getVuelo() != null ? c.getVuelo().getId() : "NULL"),
                        (c.getFechaHoraCancelacion() != null ? c.getFechaHoraCancelacion() : "NULL")
                );
            }
            log.info("────────────────────────────────────────────────────────────");
            // 3. Validar cancelaciones antes de guardar
            try {
                EntityValidator.validateCancelaciones(cancelaciones);
                log.info("   ✅ Validación de cancelaciones exitosa");
            } catch (IllegalArgumentException e) {
                log.error("   ❌ Error de validación en cancelaciones: {}", e.getMessage());
                result.put("success", false);
                result.put("message", "Error de validación: " + e.getMessage());
                result.put("count", 0);
                return result;
            }

            // 4. Borrar cancelaciones previas en BD antes de insertar las nuevas
            log.info(" Eliminando cancelaciones previas en BD...");
            cancelacionRepository.deleteAllInBatch(); // o deleteAll()
            log.info("Tabla de cancelaciones limpia.");

            // 5. Guardar cancelaciones en BD (IDs auto-generados)
            List<Cancelacion> cancelacionesGuardados = cancelacionService.insertarBulk(cancelaciones);
            cancelacionRepository.flush();

            long total = cancelacionRepository.count();
            log.info("📊 Total de cancelaciones en BD ahora: {}", total);
            result.put("success", true);
            result.put("message", "Cancelaciones importados exitosamente");
            result.put("count", cancelacionesGuardados.size());

            log.info("✅ {} Cancelaciones importados con IDs: {}",
                    cancelacionesGuardados.size(),
                    cancelacionesGuardados.stream()
                            .limit(30)
                            .map(v -> v.getId() + ":" + v.getCodigoIATAOrigen() + "-" + v.getCodigoIATADestino() + "-" + v.getVuelo().getAeropuertoOrigen().getCodigoIATA() + "-" + v.getVuelo().getAeropuertoDestino().getCodigoIATA())
                            .collect(Collectors.joining(", ")));

        } catch (Exception e) {
            log.error("❌ Error importando cancelaciones", e);
            result.put("success", false);
            result.put("message", "Error: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
        } finally {
            eliminarArchivoTemporal(tempFile);
        }

        return result;
    }
    /**
     * Importa pedidos desde archivo .txt y los guarda en BD inmediatamente
     * Requiere que existan aeropuertos en BD
     * 
     * SOPORTA DOS FORMATOS:
     * - Formato V1: Líneas separadas por espacios (LectorPedidos)
     * - Formato V2: id_pedido-aaaammdd-hh-mm-dest-###-IdClien (LectorPedidosV2)
     * 
     * @param file Archivo pedidos.txt o _pedidos_{AIRPORT}_.txt
     * @param horaInicio Opcional: solo cargar pedidos después de esta hora
     * @param horaFin Opcional: solo cargar pedidos antes de esta hora
     * @return Map con success, message, count
     */
    @Transactional
    public Map<String, Object> importOrders(MultipartFile file, LocalDateTime horaInicio, LocalDateTime horaFin) {
        Map<String, Object> result = new HashMap<>();
        Path tempFile = null;
        Path tempDir = null;
        
        try {
            log.info("📦 Iniciando importación de pedidos desde frontend...");
            log.info("   Archivo: {} ({} MB)", file.getOriginalFilename(), file.getSize() / (1024.0 * 1024.0));
            
            // 1. Verificar que existan aeropuertos en BD
            List<Aeropuerto> aeropuertos = aeropuertoService.listar();
            if (aeropuertos.isEmpty()) {
                result.put("success", false);
                result.put("message", "Debe importar aeropuertos primero antes de importar pedidos");
                result.put("count", 0);
                log.warn("❌ No hay aeropuertos en BD. Importe aeropuertos primero.");
                return result;
            }
            
            log.info("   Aeropuertos disponibles en BD: {}", aeropuertos.size());
            
            // 2. Contar pedidos antes de importar
            int countAntes = pedidoService.listar().size();
            log.info("   Pedidos en BD antes de importar: {}", countAntes);
            
            // 3. Guardar archivo temporal
            tempFile = guardarArchivoTemporal(file);
            
            // 4. DETECTAR FORMATO del archivo leyendo la primera línea
            String primeraLinea = detectarFormatoArchivo(tempFile);
            boolean esFormatoV2 = primeraLinea.contains("-") && primeraLinea.split("-").length >= 6;
            
            if (esFormatoV2) {
                log.info("   📋 Formato detectado: V2 (id-fecha-hora-dest-cant-cliente)");
                log.info("   ⚠️ Archivo V2 detectado, usando LectorPedidosV2...");
                
                if (horaInicio != null && horaFin != null) {
                    log.info("   🕒 Filtrando pedidos por ventana de tiempo:");
                    log.info("      Inicio: {}", horaInicio);
                    log.info("      Fin: {}", horaFin);
                } else {
                    log.info("   📦 Cargando TODOS los pedidos del archivo (sin filtrado)");
                }
                
                // Crear directorio temporal y copiar archivo ahí
                tempDir = Files.createTempDirectory("morapack-pedidos-");
                Path archivoEnDir = tempDir.resolve(file.getOriginalFilename());
                Files.copy(tempFile, archivoEnDir, StandardCopyOption.REPLACE_EXISTING);
                
                // Usar LectorPedidosV2 que parsea fechas correctamente
                LectorPedidosV2 lectorV2 = new LectorPedidosV2(
                    tempDir.toString(),
                    new ArrayList<>(aeropuertos),
                    pedidoService,
                    clienteService,
                    batchService
                );
                
                // Cargar con filtros de tiempo si se especificaron
                LectorPedidosV2.ResultadoCargaPedidos resultadoCarga = 
                    lectorV2.leerYGuardarPedidos(horaInicio, horaFin);
                
                if (!resultadoCarga.exito) {
                    result.put("success", false);
                    result.put("message", "Error cargando pedidos: " + resultadoCarga.mensajeError);
                    result.put("count", 0);
                    log.error("❌ {}", resultadoCarga.mensajeError);
                    return result;
                }
                
                result.put("success", true);
                result.put("message", "Pedidos importados exitosamente (Formato V2 con fechas)");
                result.put("count", resultadoCarga.pedidosCreados);
                result.put("pedidosCargados", resultadoCarga.pedidosCargados);
                result.put("pedidosFiltrados", resultadoCarga.pedidosFiltrados);
                result.put("erroresParseo", resultadoCarga.erroresParseo);
                
                log.info("✅ {} pedidos importados (V2)", resultadoCarga.pedidosCreados);
                log.info("   📊 Cargados: {}, Filtrados: {}, Errores: {}", 
                    resultadoCarga.pedidosCargados, 
                    resultadoCarga.pedidosFiltrados,
                    resultadoCarga.erroresParseo);
                
            } else {
                log.info("   📋 Formato detectado: V1 (separado por espacios)");
                
                // Usar LectorPedidos (formato viejo)
                LectorPedidos lector = new LectorPedidos(
                    tempFile.toString(),
                    new ArrayList<>(aeropuertos),
                    pedidoService,
                    clienteService
                );
                lector.leerYGuardarProductos();
                
                // 4. Contar pedidos después de importar
                int countDespues = pedidoService.listar().size();
                int pedidosImportados = countDespues - countAntes;
                
                log.info("   Pedidos en BD después de importar: {}", countDespues);
                
                result.put("success", true);
                result.put("message", "Pedidos importados exitosamente (Formato V1)");
                result.put("count", pedidosImportados);
                
                log.info("✅ {} pedidos importados (V1)", pedidosImportados);
            }
            
        } catch (Exception e) {
            log.error("❌ Error importando pedidos", e);
            result.put("success", false);
            result.put("message", "Error: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
        } finally {
            eliminarArchivoTemporal(tempFile);
            if (tempDir != null) {
                try {
                    // Eliminar archivos dentro del directorio temporal
                    Files.walk(tempDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                log.warn("No se pudo eliminar archivo temporal: {}", path);
                            }
                        });
                } catch (IOException e) {
                    log.warn("Error limpiando directorio temporal: {}", e.getMessage());
                }
            }
        }
        
        return result;
    }
    
    /**
     * Importa múltiples archivos de pedidos en batch
     * Procesa cada archivo secuencialmente y genera externalId único por archivo
     * 
     * @param files Array de archivos de pedidos
     * @param horaInicio Opcional: solo cargar pedidos después de esta hora
     * @param horaFin Opcional: solo cargar pedidos antes de esta hora
     * @return Map con resultados del batch
     */
    @Transactional
    public Map<String, Object> importOrdersBatch(MultipartFile[] files, LocalDateTime horaInicio, LocalDateTime horaFin) {
        Map<String, Object> batchResult = new HashMap<>();
        List<Map<String, Object>> fileResults = new ArrayList<>();
        
        int totalOrders = 0;
        int filesProcessed = 0;
        int filesWithErrors = 0;
        List<String> errors = new ArrayList<>();
        
        try {
            log.info("📦 Iniciando batch import de {} archivos de pedidos", files.length);
            
            // Verificar que existan aeropuertos
            List<Aeropuerto> aeropuertos = aeropuertoService.listar();
            if (aeropuertos.isEmpty()) {
                batchResult.put("success", false);
                batchResult.put("message", "Debe importar aeropuertos primero antes de importar pedidos");
                batchResult.put("totalOrders", 0);
                batchResult.put("filesProcessed", 0);
                return batchResult;
            }
            
            // Procesar cada archivo
            for (int i = 0; i < files.length; i++) {
                MultipartFile file = files[i];
                String filename = file.getOriginalFilename();
                
                log.info("📄 Procesando archivo {}/{}: {}", (i + 1), files.length, filename);
                
                Map<String, Object> fileResult = new HashMap<>();
                fileResult.put("filename", filename);
                
                try {
                    // Importar archivo individual
                    Map<String, Object> importResult = importOrders(file, horaInicio, horaFin);
                    
                    boolean success = (boolean) importResult.get("success");
                    fileResult.put("success", success);
                    
                    if (success) {
                        int count = (int) importResult.get("count");
                        fileResult.put("orders", count);
                        totalOrders += count;
                        filesProcessed++;
                        
                        // Incluir información de filtrado si existe
                        if (importResult.containsKey("pedidosCargados")) {
                            fileResult.put("loaded", importResult.get("pedidosCargados"));
                            fileResult.put("filtered", importResult.get("pedidosFiltrados"));
                        }
                        
                        log.info("✅ Archivo {}: {} pedidos importados", filename, count);
                    } else {
                        filesWithErrors++;
                        String errorMsg = (String) importResult.get("message");
                        fileResult.put("error", errorMsg);
                        errors.add(filename + ": " + errorMsg);
                        log.error("❌ Error en archivo {}: {}", filename, errorMsg);
                    }
                    
                } catch (Exception e) {
                    filesWithErrors++;
                    String errorMsg = e.getMessage();
                    fileResult.put("success", false);
                    fileResult.put("error", errorMsg);
                    errors.add(filename + ": " + errorMsg);
                    log.error("❌ Excepción procesando archivo {}: {}", filename, e.getMessage(), e);
                }
                
                fileResults.add(fileResult);
            }
            
            // Construir resultado final
            boolean overallSuccess = filesWithErrors == 0;
            
            batchResult.put("success", overallSuccess);
            batchResult.put("totalOrders", totalOrders);
            batchResult.put("filesProcessed", filesProcessed);
            batchResult.put("totalFiles", files.length);
            batchResult.put("filesWithErrors", filesWithErrors);
            batchResult.put("fileResults", fileResults);
            
            if (!errors.isEmpty()) {
                batchResult.put("errors", errors);
            }
            
            String message = String.format(
                "Batch import completado: %d pedidos importados de %d/%d archivos",
                totalOrders, filesProcessed, files.length
            );
            
            if (filesWithErrors > 0) {
                message += String.format(" (%d archivos con errores)", filesWithErrors);
            }
            
            batchResult.put("message", message);
            
            log.info("🎉 Batch import finalizado: {} pedidos de {}/{} archivos", 
                totalOrders, filesProcessed, files.length);
            
        } catch (Exception e) {
            log.error("❌ Error crítico en batch import", e);
            batchResult.put("success", false);
            batchResult.put("message", "Error crítico: " + e.getMessage());
            batchResult.put("totalOrders", totalOrders);
            batchResult.put("filesProcessed", filesProcessed);
        }
        
        return batchResult;
    }
    
    /**
     * Detecta el formato del archivo leyendo la primera línea no vacía
     */
    private String detectarFormatoArchivo(Path archivo) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(archivo)) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                linea = linea.trim();
                if (!linea.isEmpty()) {
                    return linea;
                }
            }
        }
        return "";
    }

    // ========== DATABASE STATISTICS ==========
    
    /**
     * Obtiene estadísticas actuales de la base de datos
     */
    public Map<String, Object> getDatabaseStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            long aeropuertos = aeropuertoService.listar().size();
            long vuelos = vueloService.listar().size();
            long pedidos = pedidoService.listar().size();
            long ciudades = ciudadRepository.count();
            long clientes = clienteRepository.count();
            long productos = productoRepository.count();
            long cancelaciones = cancelacionRepository.count();
            stats.put("aeropuertos", aeropuertos);
            stats.put("vuelos", vuelos);
            stats.put("pedidos", pedidos);
            stats.put("ciudades", ciudades);
            stats.put("clientes", clientes);
            stats.put("productos", productos);
            stats.put("cancelaciones", cancelaciones);
            stats.put("timestamp", LocalDateTime.now());
            
        } catch (Exception e) {
            log.error("Error obteniendo estadísticas: {}", e.getMessage(), e);
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }

    // ========== DATABASE CLEANUP METHODS ==========
    
    /**
     * Limpia TODA la base de datos (respetando foreign keys)
     * Orden: Simulaciones → Productos → Pedidos → Vuelos → Aeropuertos → Ciudades
     */
    @Transactional
    public Map<String, Object> clearAllData() {
        Map<String, Object> result = new HashMap<>();
        int totalDeleted = 0;
        
        try {
            log.warn("⚠️ Iniciando limpieza COMPLETA de base de datos");
            
            // 1. Limpiar simulaciones primero (tienen FK a pedidos y vuelos)
            long simDeleted = clearSimulationsOnly();
            totalDeleted += simDeleted;
            log.info("  - Simulaciones eliminadas: {}", simDeleted);
            
            // 2. Limpiar productos (tienen FK a pedidos)
            long prodDeleted = productoRepository.count();
            productoRepository.deleteAll();
            totalDeleted += prodDeleted;
            log.info("  - Productos eliminados: {}", prodDeleted);
            
            // 3. Limpiar pedidos (tienen FK a aeropuertos/ciudades)
            long pedDeleted = pedidoService.listar().size();
            pedidoService.listar().forEach(pedido -> pedidoService.eliminar(pedido.getId()));
            totalDeleted += pedDeleted;
            log.info("  - Pedidos eliminados: {}", pedDeleted);
            
            // 4. Limpiar vuelos (tienen FK a aeropuertos)
            long vuelosDeleted = vueloService.listar().size();
            vueloService.listar().forEach(vuelo -> vueloService.eliminar(vuelo.getId()));
            totalDeleted += vuelosDeleted;
            log.info("  - Vuelos eliminados: {}", vuelosDeleted);
            
            // 5. Limpiar aeropuertos (tienen FK a ciudades)
            long aeroDeleted = aeropuertoService.listar().size();
            aeropuertoService.listar().forEach(aero -> aeropuertoService.eliminar(aero.getId()));
            totalDeleted += aeroDeleted;
            log.info("  - Aeropuertos eliminados: {}", aeroDeleted);
            
            // 6. Limpiar ciudades (no tienen FK)
            long ciudadesDeleted = ciudadRepository.count();
            ciudadRepository.deleteAll();
            totalDeleted += ciudadesDeleted;
            log.info("  - Ciudades eliminadas: {}", ciudadesDeleted);
            
            // 7. Limpiar clientes
            long clientesDeleted = clienteRepository.count();
            clienteRepository.deleteAll();
            totalDeleted += clientesDeleted;
            log.info("  - Clientes eliminados: {}", clientesDeleted);
            
            result.put("success", true);
            result.put("message", "Base de datos limpiada completamente");
            result.put("totalDeleted", totalDeleted);
            result.put("breakdown", Map.of(
                "simulaciones", simDeleted,
                "productos", prodDeleted,
                "pedidos", pedDeleted,
                "vuelos", vuelosDeleted,
                "aeropuertos", aeroDeleted,
                "ciudades", ciudadesDeleted,
                "clientes", clientesDeleted
            ));
            
            log.warn("✅ Limpieza COMPLETA finalizada. Total registros eliminados: {}", totalDeleted);
            
        } catch (Exception e) {
            log.error("❌ Error durante limpieza completa: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "Error al limpiar base de datos: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
        }
        
        return result;
    }
    
    /**
     * Limpia solo pedidos y productos
     */
    @Transactional
    public Map<String, Object> clearOrders() {
        Map<String, Object> result = new HashMap<>();
        int totalDeleted = 0;
        
        try {
            log.warn("⚠️ Iniciando limpieza de PEDIDOS");
            
            // 1. Primero limpiar simulaciones que dependen de pedidos
            long simDeleted = clearSimulationsOnly();
            totalDeleted += simDeleted;
            
            // 2. Limpiar productos
            long prodDeleted = productoRepository.count();
            productoRepository.deleteAll();
            totalDeleted += prodDeleted;
            
            // 3. Limpiar pedidos
            long pedDeleted = pedidoService.listar().size();
            pedidoService.listar().forEach(pedido -> pedidoService.eliminar(pedido.getId()));
            totalDeleted += pedDeleted;
            
            result.put("success", true);
            result.put("message", "Pedidos eliminados exitosamente");
            result.put("deleted", totalDeleted);
            result.put("breakdown", Map.of(
                "simulaciones", simDeleted,
                "productos", prodDeleted,
                "pedidos", pedDeleted
            ));
            
            log.info("✅ Pedidos eliminados. Total: {}", totalDeleted);
            
        } catch (Exception e) {
            log.error("❌ Error eliminando pedidos: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "Error al eliminar pedidos: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
        }
        
        return result;
    }
    
    /**
     * Limpia solo vuelos
     */
    @Transactional
    public Map<String, Object> clearFlights() {
        Map<String, Object> result = new HashMap<>();
        int totalDeleted = 0;
        
        try {
            log.warn("⚠️ Iniciando limpieza de VUELOS");
            
            // 1. Primero limpiar simulaciones que dependen de vuelos
            long simDeleted = clearSimulationsOnly();
            totalDeleted += simDeleted;
            
            // 2. Limpiar vuelos
            long vuelosDeleted = vueloService.listar().size();
            vueloService.listar().forEach(vuelo -> vueloService.eliminar(vuelo.getId()));
            totalDeleted += vuelosDeleted;
            
            result.put("success", true);
            result.put("message", "Vuelos eliminados exitosamente");
            result.put("deleted", totalDeleted);
            result.put("breakdown", Map.of(
                "simulaciones", simDeleted,
                "vuelos", vuelosDeleted
            ));
            
            log.info("✅ Vuelos eliminados. Total: {}", totalDeleted);
            
        } catch (Exception e) {
            log.error("❌ Error eliminando vuelos: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "Error al eliminar vuelos: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
        }
        
        return result;
    }
    
    /**
     * Limpia aeropuertos (y todo lo que depende de ellos)
     */
    @Transactional
    public Map<String, Object> clearAirports() {
        Map<String, Object> result = new HashMap<>();
        int totalDeleted = 0;
        
        try {
            log.warn("⚠️ Iniciando limpieza de AEROPUERTOS (incluye datos dependientes)");
            
            // 1. Limpiar simulaciones
            long simDeleted = clearSimulationsOnly();
            totalDeleted += simDeleted;
            
            // 2. Limpiar productos
            long prodDeleted = productoRepository.count();
            productoRepository.deleteAll();
            totalDeleted += prodDeleted;
            
            // 3. Limpiar pedidos
            long pedDeleted = pedidoService.listar().size();
            pedidoService.listar().forEach(pedido -> pedidoService.eliminar(pedido.getId()));
            totalDeleted += pedDeleted;
            
            // 4. Limpiar vuelos
            long vuelosDeleted = vueloService.listar().size();
            vueloService.listar().forEach(vuelo -> vueloService.eliminar(vuelo.getId()));
            totalDeleted += vuelosDeleted;
            
            // 5. Limpiar aeropuertos
            long aeroDeleted = aeropuertoService.listar().size();
            aeropuertoService.listar().forEach(aero -> aeropuertoService.eliminar(aero.getId()));
            totalDeleted += aeroDeleted;
            
            // 6. Limpiar ciudades
            long ciudadesDeleted = ciudadRepository.count();
            ciudadRepository.deleteAll();
            totalDeleted += ciudadesDeleted;
            
            result.put("success", true);
            result.put("message", "Aeropuertos y datos dependientes eliminados exitosamente");
            result.put("deleted", totalDeleted);
            result.put("breakdown", Map.of(
                "simulaciones", simDeleted,
                "productos", prodDeleted,
                "pedidos", pedDeleted,
                "vuelos", vuelosDeleted,
                "aeropuertos", aeroDeleted,
                "ciudades", ciudadesDeleted
            ));
            
            log.info("✅ Aeropuertos y dependencias eliminados. Total: {}", totalDeleted);
            
        } catch (Exception e) {
            log.error("❌ Error eliminando aeropuertos: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "Error al eliminar aeropuertos: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
        }
        
        return result;
    }
    
    /**
     * Limpia solo simulaciones (mantiene datos base)
     */
    @Transactional
    public Map<String, Object> clearSimulations() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.warn("⚠️ Iniciando limpieza de SIMULACIONES");
            
            long deleted = clearSimulationsOnly();
            
            result.put("success", true);
            result.put("message", "Simulaciones eliminadas exitosamente");
            result.put("deleted", deleted);
            
            log.info("✅ Simulaciones eliminadas. Total: {}", deleted);
            
        } catch (Exception e) {
            log.error("❌ Error eliminando simulaciones: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "Error al eliminar simulaciones: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
        }
        
        return result;
    }
    
    /**
     * Método auxiliar para limpiar simulaciones
     * Usado internamente por otros métodos de limpieza
     */
    private long clearSimulationsOnly() {
        // Aquí irían las llamadas a los repositorios de SimulacionSemanal y SimulacionAsignacion
        // Por ahora retorna 0 hasta que identifiquemos los repositorios correctos
        // TODO: Agregar cuando tengamos acceso a SimulacionSemanalRepository y SimulacionAsignacionRepository
        log.warn("TODO: Implementar limpieza de simulaciones cuando tengamos los repositorios");
        return 0;
    }

    /**
     * Importa aeropuertos desde directorio predeterminado (data/aeropuertosinfo.txt)
     */
    @Transactional
    public Map<String, Object> importAirportsFromDirectory() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.info("🏢 Cargando aeropuertos desde directorio predeterminado");
            
            // Obtener ruta del archivo
            String directorioProyecto = System.getProperty("user.dir");
            Path archivoAeropuertos = Path.of(directorioProyecto, "data", "aeropuertosinfo.txt");
            
            if (!Files.exists(archivoAeropuertos)) {
                result.put("success", false);
                result.put("message", "No se encontró el archivo: " + archivoAeropuertos);
                result.put("count", 0);
                log.error("❌ Archivo no encontrado: {}", archivoAeropuertos);
                return result;
            }
            
            // Usar LectorAeropuerto
            LectorAeropuerto lector = new LectorAeropuerto(archivoAeropuertos.toString());
            ArrayList<Aeropuerto> aeropuertos = lector.leerAeropuertos();
            
            if (aeropuertos.isEmpty()) {
                result.put("success", false);
                result.put("message", "No se encontraron aeropuertos en el archivo");
                result.put("count", 0);
                log.warn("❌ No se encontraron aeropuertos en el archivo");
                return result;
            }
            
            // Guardar en BD
            HashSet<String> ciudadesYaGuardadas = new HashSet<>();
            int aeropuertosGuardados = 0;
            
            for (Aeropuerto aeropuerto : aeropuertos) {
                Ciudad ciudad = aeropuerto.getCiudad();
                
                if (ciudad != null && !ciudadesYaGuardadas.contains(ciudad.getNombre())) {
                    ciudadRepository.save(ciudad);
                    ciudadesYaGuardadas.add(ciudad.getNombre());
                }
                
                Integer aeropuertoId = aeropuertoService.insertar(aeropuerto);
                aeropuerto.setId(aeropuertoId);
                
                // Crear almacén para cada aeropuerto
                Almacen almacen = new Almacen();
                almacen.setAeropuerto(aeropuerto);
                almacen.setNombre("Almacén " + (aeropuerto.getAlias() != null ? 
                    aeropuerto.getAlias() : aeropuerto.getCodigoIATA()));
                almacen.setCapacidadMaxima(aeropuerto.getCapacidadMaxima() != null ? 
                    aeropuerto.getCapacidadMaxima() : 1000);
                almacen.setCapacidadUsada(aeropuerto.getCapacidadActual() != null ? 
                    aeropuerto.getCapacidadActual() : 0);
                almacen.setEsAlmacenPrincipal(false);
                almacenRepository.save(almacen);
                
                // Actualizar aeropuerto con almacén
                aeropuerto.setAlmacen(almacen);
                aeropuertoService.actualizar(aeropuertoId, aeropuerto);
                
                aeropuertosGuardados++;
            }
            
            result.put("success", true);
            result.put("message", "Aeropuertos importados desde directorio");
            result.put("count", aeropuertosGuardados);
            result.put("cities", ciudadesYaGuardadas.size());
            
            log.info("✅ Aeropuertos importados: {}, Ciudades: {}", aeropuertosGuardados, ciudadesYaGuardadas.size());
            
        } catch (Exception e) {
            log.error("❌ Error importando aeropuertos desde directorio: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "Error al importar: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
        }
        
        return result;
    }

    /**
     * Importa vuelos desde directorio predeterminado (data/vuelos.txt)
     */
    @Transactional
    public Map<String, Object> importFlightsFromDirectory() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.info("✈️ Cargando vuelos desde directorio predeterminado");
            
            // Obtener ruta del archivo
            String directorioProyecto = System.getProperty("user.dir");
            Path archivoVuelos = Path.of(directorioProyecto, "data", "vuelos.txt");
            
            if (!Files.exists(archivoVuelos)) {
                result.put("success", false);
                result.put("message", "No se encontró el archivo: " + archivoVuelos);
                result.put("count", 0);
                log.error("❌ Archivo no encontrado: {}", archivoVuelos);
                return result;
            }
            
            // Cargar aeropuertos de BD
            List<Aeropuerto> aeropuertosList = aeropuertoService.listar();
            if (aeropuertosList.isEmpty()) {
                result.put("success", false);
                result.put("message", "No hay aeropuertos en la base de datos. Debe cargar aeropuertos primero.");
                result.put("count", 0);
                log.error("❌ No hay aeropuertos en BD");
                return result;
            }
            
            // Convertir List a ArrayList para LectorVuelos
            ArrayList<Aeropuerto> aeropuertosArray = new ArrayList<>(aeropuertosList);
            
            // Usar LectorVuelos
            LectorVuelos lector = new LectorVuelos(archivoVuelos.toString(), aeropuertosArray);
            ArrayList<Vuelo> vuelos = lector.leerVuelos();
            
            if (vuelos.isEmpty()) {
                result.put("success", false);
                result.put("message", "No se encontraron vuelos en el archivo");
                result.put("count", 0);
                log.warn("❌ No se encontraron vuelos en el archivo");
                return result;
            }
            
            // Guardar en BD
            int vuelosGuardados = 0;
            for (Vuelo vuelo : vuelos) {
                vueloService.insertar(vuelo);
                vuelosGuardados++;
            }
            
            result.put("success", true);
            result.put("message", "Vuelos importados desde directorio");
            result.put("count", vuelosGuardados);
            
            log.info("✅ Vuelos importados: {}", vuelosGuardados);
            
        } catch (Exception e) {
            log.error("❌ Error importando vuelos desde directorio: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "Error al importar: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
        }
        
        return result;
    }
}

