package com.grupo5e.morapack.controller;

import com.grupo5e.morapack.algorithm.alns.ALNSSolver;
import com.grupo5e.morapack.api.dto.*;
import com.grupo5e.morapack.core.model.Pedido;
import com.grupo5e.morapack.core.model.Producto;
import com.grupo5e.morapack.core.model.Vuelo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller para ejecución del algoritmo ALNS.
 * Siguiendo patrón 100% de MoraPack-Backend: ejecuta y retorna resultados inmediatamente.
 * NO persiste en base de datos, todo en memoria.
 */
@RestController
@RequestMapping("/api/algoritmo")
@Tag(name = "Algoritmo ALNS", description = "API para ejecución del algoritmo ALNS con resultados a nivel de producto")
@Slf4j
@CrossOrigin(origins = "*")
public class AlgoritmoController {
    
    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;
    
    @Operation(
        summary = "Ejecutar algoritmo ALNS",
        description = "Ejecuta el algoritmo ALNS y retorna resultados a nivel de producto en memoria. " +
                      "No persiste datos - siguiendo patrón Backend"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Algoritmo ejecutado exitosamente",
            content = @Content(schema = @Schema(implementation = ResultadoAlgoritmoDTO.class))
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Error durante la ejecución del algoritmo"
        )
    })
    @PostMapping("/ejecutar")
    public ResponseEntity<ResultadoAlgoritmoDTO> ejecutarAlgoritmo(
            @RequestBody(required = false) AlnsRequestDTO solicitud,int tipoData) {
        
        LocalDateTime horaInicio = LocalDateTime.now();
        log.info("🚀 Ejecutando algoritmo ALNS (sincrónico) - Inicio: {}", horaInicio);
        
        try {
            // 1. Configurar fuente de datos según solicitud
            log.info("📥 Solicitud recibida: {}", solicitud);
            
            Boolean usarBaseDatos = (solicitud != null && solicitud.getUsarBaseDatos() != null) 
                ? solicitud.getUsarBaseDatos() : true;
            
            String fuente = usarBaseDatos ? "BASE_DE_DATOS" : "ARCHIVO";
            
            log.info("🔧 Fuente de datos configurada: [{}]", fuente);
            log.debug("Configurando System Property MODO_FUENTE_DATOS = {}", fuente);
            
            // Configurar variable de entorno para la fuente de datos
            System.setProperty("MODO_FUENTE_DATOS", fuente);
            
            // Verificar que se guardó correctamente
            String fuenteGuardada = System.getProperty("MODO_FUENTE_DATOS");
            log.debug("System Property guardada = {}", fuenteGuardada);
            
            // 2. Crear instancia de ALNSSolver con configuración
            int iteraciones = (solicitud != null && solicitud.getMaxIteraciones() != null) 
                ? solicitud.getMaxIteraciones() : 500;
            
            // Pasar ApplicationContext para que pueda usar servicios de Spring cuando fuente=BASE_DE_DATOS
            log.debug("Configurando Spring Context en FabricaFuenteDatos");
            com.grupo5e.morapack.algorithm.input.FabricaFuenteDatos.setSpringContext(applicationContext);
            
            log.info("Iteraciones configuradas: {}", iteraciones);
            
            // Extraer ventanas de tiempo si existen
            LocalDateTime horaInicioSimulacion = (solicitud != null) ? solicitud.getHoraInicioSimulacion() : null;
            LocalDateTime horaFinSimulacion = (solicitud != null) ? solicitud.getHoraFinSimulacion() : null;
            
            // Usar constructor con soporte de ventanas de tiempo
            log.info("Creando ALNSSolver con {} iteraciones{}", iteraciones,
                (horaInicioSimulacion != null ? " y ventana de tiempo" : ""));
            ALNSSolver solver = new ALNSSolver(iteraciones, horaInicioSimulacion, horaFinSimulacion,tipoData);
            
            // 3. Ejecutar el algoritmo
            log.info("   Iteraciones configuradas: {}", iteraciones);
            solver.resolver();
            
            LocalDateTime horaFin = LocalDateTime.now();
            long segundosEjecucion = ChronoUnit.SECONDS.between(horaInicio, horaFin);
            
            // 3. Obtener solución a nivel de producto
            Map<Producto, ArrayList<Vuelo>> solucionProductos = solver.obtenerSolucionNivelProducto();
            
            // 4. Convertir a DTO
            ResultadoAlgoritmoDTO resultado = convertirSolucionAResultado(
                solucionProductos, 
                horaInicio, 
                horaFin, 
                segundosEjecucion
            );
            
            log.info("✅ Algoritmo completado exitosamente en {} segundos", segundosEjecucion);
            log.info("   Total productos asignados: {}", resultado.getTotalProductos());
            
            return ResponseEntity.ok(resultado);
            
        } catch (Exception e) {
            log.error("❌ Error ejecutando algoritmo ALNS", e);
            
            ResultadoAlgoritmoDTO resultadoError = ResultadoAlgoritmoDTO.builder()
                .exitoso(false)
                .mensaje("Error durante la ejecución: " + e.getMessage())
                .horaInicio(horaInicio)
                .horaFin(LocalDateTime.now())
                .segundosEjecucion(ChronoUnit.SECONDS.between(horaInicio, LocalDateTime.now()))
                .totalProductos(0)
                .totalPedidos(0)
                .rutasProductos(new ArrayList<>())
                .build();
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resultadoError);
        }
    }
    
    /**
     * Convierte la solución del algoritmo a ResultadoAlgoritmoDTO
     */
    private ResultadoAlgoritmoDTO convertirSolucionAResultado(
            Map<Producto, ArrayList<Vuelo>> solucionProductos,
            LocalDateTime horaInicio,
            LocalDateTime horaFin,
            long segundosEjecucion) {
        
        List<RutaProductoDTO> rutasProductos = new ArrayList<>();
        
        // Convertir cada producto y su ruta a DTO
        for (Map.Entry<Producto, ArrayList<Vuelo>> entry : solucionProductos.entrySet()) {
            Producto producto = entry.getKey();
            ArrayList<Vuelo> vuelos = entry.getValue();
            
            RutaProductoDTO rutaProducto = RutaProductoDTO.builder()
                .idProducto(producto.getId())
                .idPedido(producto.getPedido() != null ? producto.getPedido().getId() : null)
                .nombrePedido(producto.getPedido() != null ? 
                    producto.getPedido().getNombre() : (producto.getPedido() != null ? "Pedido-" + producto.getPedido().getId() : "Pedido-UNKNOWN"))
                .nombreProducto(producto.getNombre() != null ?
                    producto.getNombre() : "Producto-" + producto.getId())
                .peso(producto.getPeso())
                .volumen(producto.getVolumen())
                .codigoOrigen(producto.getPedido() != null ? 
                    producto.getPedido().getAeropuertoOrigenCodigo() : null)
                .codigoDestino(producto.getPedido() != null ? 
                    producto.getPedido().getAeropuertoDestinoCodigo() : null)
                .vuelos(convertirVuelosADTO(vuelos))
                .cantidadVuelos(vuelos.size())
                .tiempoTotalHoras(calcularTiempoTotal(vuelos))
                .estado(producto.getEstado() != null ? producto.getEstado().toString() : "DESCONOCIDO")
                .build();
            
            rutasProductos.add(rutaProducto);
        }
        
        // Calcular estadísticas
        int totalProductos = rutasProductos.size();
        int totalPedidos = rutasProductos.stream()
            .map(RutaProductoDTO::getIdPedido)
            .collect(Collectors.toSet())
            .size();
        
        // Generar timeline temporal de simulación
        log.info("Generando timeline de simulación...");
        LineaDeTiempoSimulacionDTO timeline = generarLineaDeTiempoSimulacion(
            rutasProductos, 
            horaInicio
        );
        log.info("Timeline generado con {} eventos", timeline.getEventos().size());
        
        // Calcular costo total de las rutas
        double costoTotal = rutasProductos.stream()
            .mapToDouble(ruta -> ruta.getVuelos().stream()
                .mapToDouble(v -> v.getCosto() != null ? v.getCosto() : 0.0)
                .sum())
            .sum();
        
        return ResultadoAlgoritmoDTO.builder()
            .exitoso(true)
            .mensaje("Algoritmo ejecutado exitosamente")
            .horaInicio(horaInicio)
            .horaFin(horaFin)
            .segundosEjecucion(segundosEjecucion)
            .totalProductos(totalProductos)
            .totalPedidos(totalPedidos)
            .rutasProductos(rutasProductos)
            .costoTotal(costoTotal)
            .porcentajeAsignacion(100.0) // TODO: calcular basado en pedidos totales
            .lineaDeTiempo(timeline)  // Timeline de simulación en memoria
            .build();
    }


    private VueloSimpleDTO convertirVueloAVueloDTO(Vuelo vuelo) {
        return VueloSimpleDTO.builder()
                .id(vuelo.getId())
                .codigo("Vuelo + " + vuelo.getId()) // se puede mejorar
                .codigoOrigen(
                        vuelo.getAeropuertoOrigen() != null ?
                                vuelo.getAeropuertoOrigen().getCodigoIATA() : "???"
                )
                .codigoDestino(
                        vuelo.getAeropuertoDestino() != null ?
                                vuelo.getAeropuertoDestino().getCodigoIATA() : "???"
                )
                .horaSalida(vuelo.getHoraSalida())
                .horaLlegada(vuelo.getHoraLlegada())
                .costo(vuelo.getCosto())
                .build();
    }

    private LocalDateTime reconstruirFechaHora(Pedido pedido, Vuelo vuelo, boolean esSalida) {

        // Validaciones: si falta información devolver null
        if (pedido == null || pedido.getFechaPedido() == null || vuelo == null) {
            return null;
        }

        LocalDate fechaBase = pedido.getFechaPedido().toLocalDate(); // debes tener este campo

        LocalTime hora = esSalida ? vuelo.getHoraSalida() : vuelo.getHoraLlegada();

        if (hora == null) return null;

        LocalDateTime fechaHora = LocalDateTime.of(fechaBase, hora);

        // Si es llegada y la hora llega antes de la salida → cruzó medianoche
        if (!esSalida &&
                vuelo.getHoraLlegada() != null &&
                vuelo.getHoraSalida() != null &&
                vuelo.getHoraLlegada().isBefore(vuelo.getHoraSalida())) {

            fechaHora = fechaHora.plusDays(1);
        }

        return fechaHora;
    }


    /**
     * Convierte lista de vuelos a VueloSimpleDTO evitando referencias circulares
     */
    private List<VueloSimpleDTO> convertirVuelosADTO(ArrayList<Vuelo> vuelos) {
        return vuelos.stream()
            .map(v -> {
                // Generar código de vuelo basado en ruta
                String codigoOrigen = v.getAeropuertoOrigen() != null ? 
                    v.getAeropuertoOrigen().getCodigoIATA() : "???";
                String codigoDestino = v.getAeropuertoDestino() != null ? 
                    v.getAeropuertoDestino().getCodigoIATA() : "???";
                String codigo = "VL" + codigoOrigen + "-" + codigoDestino + "-" + v.getId();
                
                return VueloSimpleDTO.builder()
                    .id(v.getId())
                    .codigo(codigo)
                    .codigoOrigen(codigoOrigen)
                    .codigoDestino(codigoDestino)
                    .idAeropuertoOrigen(v.getAeropuertoOrigen() != null ? v.getAeropuertoOrigen().getId() : null)
                    .idAeropuertoDestino(v.getAeropuertoDestino() != null ? v.getAeropuertoDestino().getId() : null)
                    .horaSalida(v.getHoraSalida())
                    .horaLlegada(v.getHoraLlegada())
                    .tiempoTransporte(v.getTiempoTransporte())
                    .costo(v.getCosto())
                    .capacidadUsada(v.getCapacidadUsada())
                    .capacidadMaxima(v.getCapacidadMaxima())
                    .build();
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Calcula tiempo total de ruta en horas
     */
    private Double calcularTiempoTotal(ArrayList<Vuelo> vuelos) {
        if (vuelos == null || vuelos.isEmpty()) {
            return 0.0;
        }
        
        return vuelos.stream()
            .mapToDouble(Vuelo::getTiempoTransporte)
            .sum();
    }
    
    /**
     * ESCENARIO DIARIO: Ejecutar algoritmo ALNS para ventana incremental de tiempo (ej: cada 30 minutos).
     * POST /api/algoritmo/diario
     * 
     * Uso típico:
     * - Simulación en tiempo real continua
     * - Frontend llama cada ~30 minutos de simulación
     * - Carga solo pedidos dentro de la ventana de tiempo
     * - Retorna rutas para este segmento de tiempo
     * - Se ejecuta indefinidamente hasta detenerse
     */
    @Operation(
        summary = "Ejecutar algoritmo ALNS - Escenario Diario",
        description = "Ejecuta el algoritmo ALNS para un escenario diario con ventanas incrementales " +
                      "(típicamente 30 minutos). Diseñado para operaciones en tiempo real."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Algoritmo ejecutado exitosamente",
            content = @Content(schema = @Schema(implementation = ResultadoAlgoritmoDTO.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Parámetros de solicitud inválidos"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Error durante la ejecución del algoritmo"
        )
    })
    @PostMapping("/diario")
    public ResponseEntity<ResultadoAlgoritmoDTO> ejecutarEscenarioDiario(
            @RequestBody(required = false) AlnsRequestDTO solicitud) {

        LocalDateTime horaInicioBackend = LocalDateTime.now();
        log.info("🚀 Ejecutando algoritmo ALNS - ESCENARIO DIARIO");

        try {
            if (solicitud == null) {
                solicitud = new AlnsRequestDTO();
                solicitud.setUsarBaseDatos(true);
            }

            if (solicitud.getHoraInicioSimulacion() == null) {
                return ResponseEntity.badRequest().body(
                        ResultadoAlgoritmoDTO.builder()
                                .exitoso(false)
                                .mensaje("horaInicioSimulacion es requerida")
                                .horaInicio(horaInicioBackend)
                                .horaFin(LocalDateTime.now())
                                .build()
                );
            }

            // ==============================
            // 1. Calcular la horaFin si no viene
            // ==============================
            if (solicitud.getHoraFinSimulacion() == null) {

                if (solicitud.getDuracionSimulacionHoras() == null &&
                        solicitud.getDuracionSimulacionDias() == null) {

                    solicitud.setDuracionSimulacionHoras(1.0); // 30 minutos por defecto
                }

                LocalDateTime horaFin;

                if (solicitud.getDuracionSimulacionHoras() != null) {
                    long minutos = (long) (solicitud.getDuracionSimulacionHoras() * 60);
                    horaFin = solicitud.getHoraInicioSimulacion().plusMinutes(minutos);

                } else {
                    horaFin = solicitud.getHoraInicioSimulacion()
                            .plusDays(solicitud.getDuracionSimulacionDias());
                }

                solicitud.setHoraFinSimulacion(horaFin);
            }

            LocalDateTime windowStart = solicitud.getHoraInicioSimulacion();
            LocalDateTime windowEnd = solicitud.getHoraFinSimulacion();

            log.info("Ventana temporal: {} -> {}", windowStart, windowEnd);

            // ==============================
            // 2. Ejecutar ALNS COMPLETO
            // ==============================
            ResultadoAlgoritmoDTO fullResult = ejecutarAlgoritmo(solicitud,1).getBody();

            if (fullResult == null || !Boolean.TRUE.equals(fullResult.getExitoso())) {
                // Si fullResult es nulo o no exitoso, retornamos el objeto tal cual (o un DTO de error si es nulo)
                if (fullResult == null) {
                    ResultadoAlgoritmoDTO resultadoError = ResultadoAlgoritmoDTO.builder()
                            .exitoso(false)
                            .mensaje("Error interno: resultado del algoritmo es nulo")
                            .horaInicio(windowStart)
                            .horaFin(windowEnd)
                            .build();
                    return ResponseEntity.ok(resultadoError);
                }
                return ResponseEntity.ok(fullResult);
            }

            // ==============================
            // 3. Filtrar SOLO productos cuya horaSalida reconstruida cae dentro de la ventana
            // ==============================
            List<RutaProductoDTO> rutasVentana = fullResult.getRutasProductos() == null ?
                    new ArrayList<>() : fullResult.getRutasProductos().stream()
                    .filter(r -> {
                        LocalDateTime salida = r.getHoraSalida();
                        return salida != null &&
                                !salida.isBefore(windowStart) &&
                                !salida.isAfter(windowEnd);
                    })
                    .toList();

            log.info("Productos en ventana: {}", rutasVentana.size());

            // Si no hay productos en la ventana, devolver resultado vacío controlado
            if (rutasVentana.isEmpty()) {
                log.info("No hay productos en la ventana - devolviendo resultado vacío");

                LineaDeTiempoSimulacionDTO emptyTimeline = generarLineaDeTiempoSimulacionVentana(
                        new ArrayList<>(), windowStart, windowEnd);

                ResultadoAlgoritmoDTO resultadoVacio = ResultadoAlgoritmoDTO.builder()
                        .exitoso(true)
                        .mensaje("No hay productos en la ventana: " + windowStart + " → " + windowEnd)
                        .horaInicio(windowStart)
                        .horaFin(windowEnd)
                        .segundosEjecucion(0L)
                        .totalProductos(0)
                        .totalPedidos(0)
                        .rutasProductos(new ArrayList<>())
                        .costoTotal(0.0)
                        .porcentajeAsignacion(0.0)
                        .lineaDeTiempo(emptyTimeline)
                        .build();

                return ResponseEntity.ok(resultadoVacio);
            }

            // ==============================
            // 4. Reconstruir mapa {Producto → Vuelos} para el conversor
            // ==============================
            Map<Producto, ArrayList<Vuelo>> mapaVentana = reconstruirMapa(rutasVentana);

            // ==============================
            // 5. Convertir solo la ventana al formato DTO completo para el frontend
            // ==============================
            ResultadoAlgoritmoDTO resultadoVentana =
                    convertirSolucionAResultadoConSalida(
                            mapaVentana,
                            windowStart,
                            windowEnd,
                            0
                    );

            resultadoVentana.setMensaje(
                    "Ventana procesada: " + windowStart + " → " + windowEnd
            );

            return ResponseEntity.ok(resultadoVentana);

        } catch (RuntimeException e) {
            log.error("❌ Error en /diario", e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResultadoAlgoritmoDTO.builder()
                        .exitoso(false)
                        .mensaje("Error: " + e.getMessage())
                        .horaInicio(horaInicioBackend)
                        .horaFin(LocalDateTime.now())
                        .build()
                );
        }
    }
    private ResultadoAlgoritmoDTO ejecutarAlgoritmoInterno(AlnsRequestDTO solicitud) {
        return ejecutarAlgoritmo(solicitud,1).getBody();
    }
    private Map<Producto, ArrayList<Vuelo>> reconstruirMapa(List<RutaProductoDTO> rutas) {

        Map<Producto, ArrayList<Vuelo>> mapa = new HashMap<>();

        for (RutaProductoDTO r : rutas) {

            Producto p = new Producto();
            p.setId(r.getIdProducto());
            p.setPeso(r.getPeso());
            p.setVolumen(r.getVolumen());
            p.setNombre(r.getNombreProducto());

            Pedido pedido = new Pedido();
            pedido.setId(r.getIdPedido());
            pedido.setAeropuertoOrigenCodigo(r.getCodigoOrigen());
            pedido.setAeropuertoDestinoCodigo(r.getCodigoDestino());
            // Nota: reconstruirMapa no dispone de fechaPedido; mantener null para evitar suposiciones
            p.setPedido(pedido);

            ArrayList<Vuelo> vuelos = r.getVuelos() == null ? new ArrayList<>() : r.getVuelos().stream()
                    .map(this::convertirVueloDTOaVuelo)
                    .collect(Collectors.toCollection(ArrayList::new));

            mapa.put(p, vuelos);
        }

        return mapa;
    }
    private Vuelo convertirVueloDTOaVuelo(VueloSimpleDTO dto) {
        Vuelo v = new Vuelo();
        v.setId(dto.getId());
        v.setHoraSalida(dto.getHoraSalida());
        v.setHoraLlegada(dto.getHoraLlegada());
        v.setCosto(dto.getCosto());
        return v;
    }
    /**
     * Versión especializada para el ESCENARIO DIARIO
     * Reconstruye fecha+hora para vuelos y genera timeline basado en esos valores.
     */
    private ResultadoAlgoritmoDTO convertirSolucionAResultadoConSalida(
            Map<Producto, ArrayList<Vuelo>> solucionProductos,
            LocalDateTime horaInicioVentana,
            LocalDateTime horaFinVentana,
            long segundosEjecucion) {

        List<RutaProductoDTO> rutasProductos = new ArrayList<>();

        // Convertir cada producto a DTO incluyendo horaSalida y horaLlegada
        for (Map.Entry<Producto, ArrayList<Vuelo>> entry : solucionProductos.entrySet()) {

            Producto producto = entry.getKey();
            ArrayList<Vuelo> vuelos = entry.getValue();

            // Validaciones defensivas
            if (vuelos == null || vuelos.isEmpty()) {
                // Crear ruta mínima sin horas si no hay vuelos
                RutaProductoDTO rutaProductoMin = RutaProductoDTO.builder()
                        .idProducto(producto.getId())
                        .idPedido(producto.getPedido() != null ? producto.getPedido().getId() : null)
                        .peso(producto.getPeso())
                        .volumen(producto.getVolumen())
                        .codigoOrigen(producto.getPedido() != null ? producto.getPedido().getAeropuertoOrigenCodigo() : null)
                        .codigoDestino(producto.getPedido() != null ? producto.getPedido().getAeropuertoDestinoCodigo() : null)
                        .vuelos(new ArrayList<>())
                        .cantidadVuelos(0)
                        .horaSalida(null)
                        .horaLlegada(null)
                        .tiempoTotalHoras(0.0)
                        .estado(producto.getEstado() != null ? producto.getEstado().toString() : "DESCONOCIDO")
                        .build();

                rutasProductos.add(rutaProductoMin);
                continue;
            }

            // =====================================================
            // 1. RECONSTRUIR FECHA-HORA REAL DE SALIDA Y LLEGADA
            // =====================================================
            LocalDateTime fechaSalidaReal = null;
            LocalDateTime fechaLlegadaReal = null;

            if (producto.getPedido() != null) {
                fechaSalidaReal = reconstruirFechaHora(producto.getPedido(), vuelos.get(0), true);
                fechaLlegadaReal = reconstruirFechaHora(producto.getPedido(), vuelos.get(vuelos.size() - 1), false);
            }

            // =====================================================
            // 2. CONVERTIR VUELOS A DTO
            // =====================================================
            List<VueloSimpleDTO> vuelosDTO = vuelos.stream()
                    .map(this::convertirVueloAVueloDTO)
                    .toList();

            // =====================================================
            // 3. CREAR RUTA DEL PRODUCTO
            // =====================================================
            RutaProductoDTO rutaProducto = RutaProductoDTO.builder()
                    .idProducto(producto.getId())
                    .idPedido(producto.getPedido() != null ? producto.getPedido().getId() : null)
                    .peso(producto.getPeso())
                    .volumen(producto.getVolumen())
                    .codigoOrigen(producto.getPedido() != null ? producto.getPedido().getAeropuertoOrigenCodigo() : null)
                    .codigoDestino(producto.getPedido() != null ? producto.getPedido().getAeropuertoDestinoCodigo() : null)
                    .vuelos(vuelosDTO)
                    .cantidadVuelos(vuelos.size())
                    .horaSalida(fechaSalidaReal)
                    .horaLlegada(fechaLlegadaReal)
                    .tiempoTotalHoras(calcularTiempoTotal(vuelos))
                    .estado(producto.getEstado() != null ? producto.getEstado().toString() : "DESCONOCIDO")
                    .build();

            rutasProductos.add(rutaProducto);
        }

        // ==========================================================
        // 4. CALCULAR ESTADÍSTICAS
        // ==========================================================
        int totalProductos = rutasProductos.size();

        int totalPedidos = rutasProductos.stream()
                .map(RutaProductoDTO::getIdPedido)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet())
                .size();

        double costoTotal = rutasProductos.stream()
                .mapToDouble(ruta -> ruta.getVuelos().stream()
                        .mapToDouble(v -> v.getCosto() != null ? v.getCosto() : 0)
                        .sum())
                .sum();

        // ==========================================================
        // 5. GENERAR TIMELINE SOLO PARA ESTA VENTANA
        // ==========================================================
        LineaDeTiempoSimulacionDTO timeline =
                generarLineaDeTiempoSimulacionVentana(rutasProductos,
                        horaInicioVentana, horaFinVentana);

        // ==========================================================
        // 6. ARMAR DTO FINAL
        // ==========================================================
        return ResultadoAlgoritmoDTO.builder()
                .exitoso(true)
                .mensaje("Resultados generados para ventana")
                .horaInicio(horaInicioVentana)
                .horaFin(horaFinVentana)
                .segundosEjecucion(segundosEjecucion)
                .totalProductos(totalProductos)
                .totalPedidos(totalPedidos)
                .rutasProductos(rutasProductos)
                .costoTotal(costoTotal)
                .porcentajeAsignacion(100.0)
                .lineaDeTiempo(timeline)
                .build();
    }
    /**
     * ESCENARIO SEMANAL: Ejecutar algoritmo ALNS para simulación de 7 días completa.
     * POST /api/algoritmo/semanal
     * 
     * Uso típico:
     * - Frontend llama una vez con rango de 7 días
     * - Carga todos los pedidos de la semana
     * - Retorna solución completa para 7 días
     * - Debería ejecutarse en 30-90 minutos (según requerimientos)
     */
    @Operation(
        summary = "Ejecutar algoritmo ALNS - Escenario Semanal",
        description = "Ejecuta el algoritmo ALNS para un escenario semanal completo de 7 días. " +
                      "Procesa toda la semana en una sola ejecución. " +
                      "Tiempo esperado: 30-90 minutos."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Algoritmo ejecutado exitosamente",
            content = @Content(schema = @Schema(implementation = ResultadoAlgoritmoDTO.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Parámetros de solicitud inválidos"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Error durante la ejecución del algoritmo"
        )
    })
    @PostMapping("/semanal")
    public ResponseEntity<ResultadoAlgoritmoDTO> ejecutarEscenarioSemanal(
            @RequestBody(required = false) AlnsRequestDTO solicitud) {
        
        LocalDateTime horaInicio = LocalDateTime.now();
        log.info("🚀 Ejecutando algoritmo ALNS - ESCENARIO SEMANAL - Inicio: {}", horaInicio);
        
        try {
            // Validar solicitud
            if (solicitud == null) {
                solicitud = AlnsRequestDTO.builder()
                    .usarBaseDatos(true)
                    .build();
            }
            
            if (solicitud.getHoraInicioSimulacion() == null) {
                ResultadoAlgoritmoDTO resultadoError = ResultadoAlgoritmoDTO.builder()
                    .exitoso(false)
                    .mensaje("Error: horaInicioSimulacion es requerida para escenario semanal")
                    .horaInicio(horaInicio)
                    .horaFin(LocalDateTime.now())
                    .build();
                return ResponseEntity.badRequest().body(resultadoError);
            }
            
            // Forzar duración de 7 días si no se especifica
            if (solicitud.getDuracionSimulacionDias() == null) {
                solicitud.setDuracionSimulacionDias(7);
            }
            
            // Calcular ventana de tiempo
            if (solicitud.getHoraFinSimulacion() == null) {
                solicitud.setHoraFinSimulacion(
                    solicitud.getHoraInicioSimulacion().plusDays(solicitud.getDuracionSimulacionDias())
                );
            }
            
            log.info("Ventana de tiempo: {} a {} ({} días)", 
                solicitud.getHoraInicioSimulacion(), 
                solicitud.getHoraFinSimulacion(),
                solicitud.getDuracionSimulacionDias());
            log.info("⚠️ ADVERTENCIA: La ejecución semanal puede tomar 30-90 minutos");
            
            // Ejecutar algoritmo
            return ejecutarAlgoritmo(solicitud,0);
            
        } catch (Exception e) {
            log.error("❌ Error ejecutando escenario semanal", e);
            
            ResultadoAlgoritmoDTO resultadoError = ResultadoAlgoritmoDTO.builder()
                .exitoso(false)
                .mensaje("Error durante la ejecución del escenario semanal: " + e.getMessage())
                .horaInicio(horaInicio)
                .horaFin(LocalDateTime.now())
                .build();
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resultadoError);
        }
    }
    
    /**
     * Genera timeline temporal de simulación con eventos de vuelos.
     * OPTIMIZADO: Agrupa vuelos por ruta para evitar duplicados.
     * 
     * @param rutasProductos Rutas de productos
     * @param horaInicio Hora de inicio de la simulación
     * @return Timeline con eventos ordenados temporalmente
     */
    private LineaDeTiempoSimulacionDTO generarLineaDeTiempoSimulacion(
            List<RutaProductoDTO> rutasProductos,
            LocalDateTime horaInicio) {
        
        List<EventoLineaDeTiempoVueloDTO> eventos = new ArrayList<>();
        Set<Integer> aeropuertosSet = new HashSet<>();
        
        // Clase auxiliar para agrupar vuelos por ruta
        class InfoGrupoVuelo {
            VueloSimpleDTO vuelo;
            LocalDateTime horaSalida;
            LocalDateTime horaLlegada;
            List<Integer> idsProductos = new ArrayList<>();
            List<Integer> idsPedidos = new ArrayList<>();
        }
        
        // Agrupar vuelos por ruta (origen-destino-tiempo) para evitar duplicados
        Map<String, InfoGrupoVuelo> gruposVuelos = new HashMap<>();
        
        // Primer paso: Agrupar vuelos por ruta y tiempo
        for (RutaProductoDTO rutaProducto : rutasProductos) {
            LocalDateTime tiempoActualProducto = horaInicio;
            
            for (int i = 0; i < rutaProducto.getVuelos().size(); i++) {
                VueloSimpleDTO vuelo = rutaProducto.getVuelos().get(i);
                
                // Rastrear aeropuertos (igual que morapack-backend líneas 57-62)
                if (vuelo.getIdAeropuertoOrigen() != null) {
                    aeropuertosSet.add(vuelo.getIdAeropuertoOrigen());
                }
                if (vuelo.getIdAeropuertoDestino() != null) {
                    aeropuertosSet.add(vuelo.getIdAeropuertoDestino());
                }
                
                // Calcular tiempos de salida y llegada
                LocalDateTime horaSalidaVuelo;
                LocalDateTime horaLlegadaVuelo;
                
                if (vuelo.getHoraSalida() != null && vuelo.getHoraLlegada() != null) {
                    // Combinar fecha de simulación con hora programada
                    horaSalidaVuelo = tiempoActualProducto.toLocalDate()
                        .atTime(vuelo.getHoraSalida());
                    
                    // Si la hora de salida ya pasó hoy, programar para mañana
                    if (horaSalidaVuelo.isBefore(tiempoActualProducto)) {
                        horaSalidaVuelo = horaSalidaVuelo.plusDays(1);
                    }
                    
                    // Calcular hora de llegada (puede cruzar medianoche)
                    horaLlegadaVuelo = horaSalidaVuelo.toLocalDate()
                        .atTime(vuelo.getHoraLlegada());
                    if (vuelo.getHoraLlegada().isBefore(vuelo.getHoraSalida())) {
                        horaLlegadaVuelo = horaLlegadaVuelo.plusDays(1);
                    }
                } else {
                    // Fallback: usar tiempo de transporte
                    long minutosTransporte = (long) ((vuelo.getTiempoTransporte() != null ?
                        vuelo.getTiempoTransporte() : 1.0) * 60);
                    horaSalidaVuelo = tiempoActualProducto;
                    horaLlegadaVuelo = horaSalidaVuelo.plusMinutes(minutosTransporte);
                }
                
                // Crear clave única para este vuelo (ruta + hora redondeada)
                LocalDateTime horaSalidaRedondeada = horaSalidaVuelo
                    .withMinute(0).withSecond(0).withNano(0);
                String claveVuelo = vuelo.getCodigoOrigen() + "-" +
                                   vuelo.getCodigoDestino() + "-" +
                                   horaSalidaRedondeada.toString();
                
                InfoGrupoVuelo grupoInfo = gruposVuelos.get(claveVuelo);
                if (grupoInfo == null) {
                    grupoInfo = new InfoGrupoVuelo();
                    grupoInfo.vuelo = vuelo;
                    grupoInfo.horaSalida = horaSalidaVuelo;
                    grupoInfo.horaLlegada = horaLlegadaVuelo;
                    gruposVuelos.put(claveVuelo, grupoInfo);
                }
                
                // Agregar este producto al grupo
                grupoInfo.idsProductos.add(rutaProducto.getIdProducto());
                grupoInfo.idsPedidos.add(rutaProducto.getIdPedido());
                
                // Próximo vuelo: esperar 1 hora de layover después de llegada
                tiempoActualProducto = horaLlegadaVuelo.plusMinutes(60);
            }
        }
        
        log.info("Agrupados {} rutas de productos en {} grupos de vuelos únicos",
                 rutasProductos.size(), gruposVuelos.size());
        
        // Segundo paso: Crear eventos para vuelos agrupados
        int contadorEventos = 0;
        for (Map.Entry<String, InfoGrupoVuelo> entry : gruposVuelos.entrySet()) {
            InfoGrupoVuelo grupo = entry.getValue();
            VueloSimpleDTO vuelo = grupo.vuelo;
            
            // Evento de salida
            EventoLineaDeTiempoVueloDTO eventoSalida = EventoLineaDeTiempoVueloDTO.builder()
                .idEvento("DEP-GROUP-" + contadorEventos)
                .tipoEvento("DEPARTURE")
                .horaEvento(grupo.horaSalida)
                .idVuelo(vuelo.getId())
                .codigoVuelo(vuelo.getCodigo())
                .cantidadProductos(grupo.idsProductos.size())
                .idProducto(grupo.idsProductos.get(0)) // Producto representativo
                .idPedido(grupo.idsPedidos.get(0))
                .ciudadOrigen(vuelo.getCodigoOrigen())
                .ciudadDestino(vuelo.getCodigoDestino())
                .idAeropuertoOrigen(vuelo.getIdAeropuertoOrigen())
                .idAeropuertoDestino(vuelo.getIdAeropuertoDestino())
                .tiempoTransporteDias(vuelo.getTiempoTransporte() != null ? 
                    vuelo.getTiempoTransporte() / 24.0 : 0.0)
                .capacidadMaxima(vuelo.getCapacidadMaxima())
                .build();
            
            eventos.add(eventoSalida);
            
            // Evento de llegada
            EventoLineaDeTiempoVueloDTO eventoLlegada = EventoLineaDeTiempoVueloDTO.builder()
                .idEvento("ARR-GROUP-" + contadorEventos)
                .tipoEvento("ARRIVAL")
                .horaEvento(grupo.horaLlegada)
                .idVuelo(vuelo.getId())
                .codigoVuelo(vuelo.getCodigo())
                .cantidadProductos(grupo.idsProductos.size())
                .idProducto(grupo.idsProductos.get(0))
                .idPedido(grupo.idsPedidos.get(0))
                .ciudadOrigen(vuelo.getCodigoOrigen())
                .ciudadDestino(vuelo.getCodigoDestino())
                .idAeropuertoOrigen(vuelo.getIdAeropuertoOrigen())
                .idAeropuertoDestino(vuelo.getIdAeropuertoDestino())
                .tiempoTransporteDias(vuelo.getTiempoTransporte() != null ? 
                    vuelo.getTiempoTransporte() / 24.0 : 0.0)
                .capacidadMaxima(vuelo.getCapacidadMaxima())
                .build();
            
            eventos.add(eventoLlegada);
            contadorEventos++;
        }
        
        // Ordenar eventos por tiempo
        eventos.sort(Comparator.comparing(EventoLineaDeTiempoVueloDTO::getHoraEvento));
        
        // Encontrar tiempo de fin de simulación
        LocalDateTime horaFin = eventos.isEmpty() ? horaInicio :
            eventos.get(eventos.size() - 1).getHoraEvento();
        
        long duracionMinutos = ChronoUnit.MINUTES.between(horaInicio, horaFin);
        
        log.info("Timeline generado: {} eventos, duración {} minutos",
                 eventos.size(), duracionMinutos);
        
        return LineaDeTiempoSimulacionDTO.builder()
            .horaInicioSimulacion(horaInicio)
            .horaFinSimulacion(horaFin)
            .duracionTotalMinutos(duracionMinutos)
            .eventos(eventos)
            .totalEventos(eventos.size())  // ✅ Total de eventos para el frontend
            .rutasProductos(rutasProductos)
            .totalProductos(rutasProductos.size())
            .totalVuelos(gruposVuelos.size())
            .totalAeropuertos(aeropuertosSet.size())
            .build();
    }

    private LineaDeTiempoSimulacionDTO generarLineaDeTiempoSimulacionVentana(
            List<RutaProductoDTO> rutas,
            LocalDateTime windowStart,
            LocalDateTime windowEnd
    ) {

        List<EventoLineaDeTiempoVueloDTO> eventos = new ArrayList<>();

        for (RutaProductoDTO r : rutas) {

            if (r.getVuelos() == null || r.getVuelos().isEmpty()) continue;

            // Evento de salida
            LocalDateTime salida = r.getHoraSalida();
            if (salida != null &&
                    !salida.isBefore(windowStart) &&
                    !salida.isAfter(windowEnd)) {

                eventos.add(EventoLineaDeTiempoVueloDTO.builder()
                        .idEvento("SAL-" + r.getIdProducto())
                        .tipoEvento("DEPARTURE")
                        .horaEvento(salida)
                        .idProducto(r.getIdProducto())
                        .idPedido(r.getIdPedido())
                        .codigoVuelo(r.getVuelos().get(0).getCodigo())
                        .build());
            }

            // Evento de llegada (opcional)
            LocalDateTime llegada = r.getHoraLlegada();
            if (llegada != null &&
                    !llegada.isBefore(windowStart) &&
                    !llegada.isAfter(windowEnd)) {

                eventos.add(EventoLineaDeTiempoVueloDTO.builder()
                        .idEvento("ARR-" + r.getIdProducto())
                        .tipoEvento("ARRIVAL")
                        .horaEvento(llegada)
                        .idProducto(r.getIdProducto())
                        .idPedido(r.getIdPedido())
                        .codigoVuelo(r.getVuelos().get(r.getVuelos().size() - 1).getCodigo())
                        .build());
            }
        }

        eventos.sort(Comparator.comparing(EventoLineaDeTiempoVueloDTO::getHoraEvento));

        return LineaDeTiempoSimulacionDTO.builder()
                .horaInicioSimulacion(windowStart)
                .horaFinSimulacion(windowEnd)
                .duracionTotalMinutos(Duration.between(windowStart, windowEnd).toMinutes())
                .eventos(eventos)
                .totalEventos(eventos.size())
                .totalProductos(rutas.size())
                .build();
    }
}

