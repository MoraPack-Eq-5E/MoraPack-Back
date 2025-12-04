package com.grupo5e.morapack.controller;

import com.grupo5e.morapack.algorithm.alns.ALNSSolver;
import com.grupo5e.morapack.algorithm.alns.RutaConTiempos;
import com.grupo5e.morapack.algorithm.alns.TramoConTiempo;
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
 * Controller para ejecución del algoritmo ALNS. *
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

    @Operation(summary = "Ejecutar algoritmo ALNS", description = "Ejecuta el algoritmo ALNS y retorna resultados a nivel de producto en memoria. "
            +
            "No persiste datos - siguiendo patrón Backend")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Algoritmo ejecutado exitosamente", content = @Content(schema = @Schema(implementation = ResultadoAlgoritmoDTO.class))),
            @ApiResponse(responseCode = "500", description = "Error durante la ejecución del algoritmo")
    })
    @PostMapping("/ejecutar")
    public ResponseEntity<ResultadoAlgoritmoDTO> ejecutarAlgoritmo(
            @RequestBody(required = false) AlnsRequestDTO solicitud, int tipoData) {

        LocalDateTime horaInicio = LocalDateTime.now();
        log.info("🚀 Ejecutando algoritmo ALNS (sincrónico) - Inicio: {}", horaInicio);

        try {
            // 1. Configurar fuente de datos según solicitud
            log.info("📥 Solicitud recibida: {}", solicitud);

            Boolean usarBaseDatos = (solicitud != null && solicitud.getUsarBaseDatos() != null)
                    ? solicitud.getUsarBaseDatos()
                    : true;

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
                    ? solicitud.getMaxIteraciones()
                    : 500;

            // Pasar ApplicationContext para que pueda usar servicios de Spring cuando
            // fuente=BASE_DE_DATOS
            log.debug("Configurando Spring Context en FabricaFuenteDatos");
            com.grupo5e.morapack.algorithm.input.FabricaFuenteDatos.setSpringContext(applicationContext);

            log.info("Iteraciones configuradas: {}", iteraciones);

            // Extraer ventanas de tiempo si existen
            LocalDateTime horaInicioSimulacion = (solicitud != null) ? solicitud.getHoraInicioSimulacion() : null;
            LocalDateTime horaFinSimulacion = (solicitud != null) ? solicitud.getHoraFinSimulacion() : null;

            // Usar constructor con soporte de ventanas de tiempo
            log.info("Creando ALNSSolver con {} iteraciones{}", iteraciones,
                    (horaInicioSimulacion != null ? " y ventana de tiempo" : ""));
            ALNSSolver solver = new ALNSSolver(iteraciones, horaInicioSimulacion, horaFinSimulacion, tipoData);

            // 3. Ejecutar el algoritmo
            log.info("   Iteraciones configuradas: {}", iteraciones);
            solver.resolver();

            LocalDateTime horaFin = LocalDateTime.now();
            long segundosEjecucion = ChronoUnit.SECONDS.between(horaInicio, horaFin);

            // 3. Obtener solución a nivel de producto
            Map<Producto, ArrayList<Vuelo>> solucionProductos = solver.obtenerSolucionNivelProducto();
            // 3.1 NUEVO: Obtener solución CON tiempos calculados por ALNS
            Map<Producto, RutaConTiempos> solucionConTiempos = solver.obtenerSolucionConTiempos();
            log.info("Solución con tiempos obtenida: {} productos", solucionConTiempos.size());
            // 4. Convertir a DTO ANTES
            // ResultadoAlgoritmoDTO resultado = convertirSolucionAResultado(
            // solucionProductos,
            // horaInicio,
            // horaFin,
            // segundosEjecucion
            // );
            // 4. Convertir a DTO
            // IMPORTANTE: Pasar horaInicioSimulacion para el timeline, no la hora de
            // ejecución
            LocalDateTime horaInicioParaTimeline = (horaInicioSimulacion != null) ? horaInicioSimulacion
                    : horaInicio;
            ResultadoAlgoritmoDTO resultado = convertirSolucionAResultado(
                    solucionProductos,
                    solucionConTiempos, // NUEVO: pasar tiempos
                    horaInicio,
                    horaFin,
                    segundosEjecucion,
                    horaInicioParaTimeline // Hora de inicio para el timeline de simulación
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
            Map<Producto, RutaConTiempos> solucionConTiempos,
            LocalDateTime horaInicio,
            LocalDateTime horaFin,
            long segundosEjecucion,
            LocalDateTime horaInicioSimulacion) {

        List<RutaProductoDTO> rutasProductos = new ArrayList<>();

        // Convertir cada producto y su ruta a DTO
        for (Map.Entry<Producto, ArrayList<Vuelo>> entry : solucionProductos.entrySet()) {
            Producto producto = entry.getKey();
            ArrayList<Vuelo> vuelos = entry.getValue();

            // NUEVO: Obtener tiempos calculados por ALNS si están disponibles
            RutaConTiempos rutaConTiempos = (solucionConTiempos != null)
                    ? solucionConTiempos.get(producto)
                    : null;

            // Convertir vuelos a DTO, incluyendo tiempos si están disponibles
            List<VueloSimpleDTO> vuelosDTO = convertirVuelosADTOConTiempos(vuelos, rutaConTiempos);

            RutaProductoDTO rutaProducto = RutaProductoDTO.builder()
                    .idProducto(producto.getId())
                    .idPedido(producto.getPedido() != null ? producto.getPedido().getId() : null)
                    .fechaPedido(producto.getPedido() != null
                            ? producto.getPedido().getFechaPedido()
                            : null)
                    .nombrePedido(producto.getPedido() != null ? producto.getPedido().getNombre()
                            : "Pedido-" + producto.getPedido().getId())
                    .nombreProducto(
                            producto.getNombre() != null ? producto.getNombre()
                                    : "Producto-" + producto.getId())
                    .peso(producto.getPeso())
                    .volumen(producto.getVolumen())
                    .codigoOrigen(
                            producto.getPedido() != null
                                    ? producto.getPedido()
                                            .getAeropuertoOrigenCodigo()
                                    : null)
                    .codigoDestino(
                            producto.getPedido() != null
                                    ? producto.getPedido()
                                            .getAeropuertoDestinoCodigo()
                                    : null)
                    .vuelos(vuelosDTO)
                    .cantidadVuelos(vuelos.size())
                    .tiempoTotalHoras(rutaConTiempos != null ? rutaConTiempos.getTiempoTotalHoras()
                            : calcularTiempoTotal(vuelos))
                    .estado(producto.getEstado() != null ? producto.getEstado().toString()
                            : "DESCONOCIDO")
                    // NUEVOS campos con tiempos del ALNS
                    .horaEntregaEstimada(
                            rutaConTiempos != null ? rutaConTiempos.getHoraFinRuta() : null)
                    .llegoATiempo(rutaConTiempos != null ? rutaConTiempos.llegoATiempo() : null)
                    .margenHoras(rutaConTiempos != null ? rutaConTiempos.getMargenHoras() : null)
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
        // IMPORTANTE: Usar horaInicioSimulacion (fecha de la simulación) en vez de
        // horaInicio (momento de ejecución)
        log.info("Generando timeline de simulación desde: {}", horaInicioSimulacion);
        LineaDeTiempoSimulacionDTO timeline = generarLineaDeTiempoSimulacion(
                rutasProductos,
                horaInicioSimulacion // Usar la hora de inicio de SIMULACIÓN, no la de ejecución
        );
        log.info("Timeline generado con {} eventos, duración: {} minutos",
                timeline.getEventos().size(), timeline.getDuracionTotalMinutos());

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
                .lineaDeTiempo(timeline) // Timeline de simulación en memoria
                .build();
    }

    // private ResultadoAlgoritmoDTO convertirSolucionAResultado(
    // Map<Producto, ArrayList<Vuelo>> solucionProductos,
    // LocalDateTime horaInicio,
    // LocalDateTime horaFin,
    // long segundosEjecucion) {
    //
    // List<RutaProductoDTO> rutasProductos = new ArrayList<>();
    //
    // // Convertir cada producto y su ruta a DTO
    // for (Map.Entry<Producto, ArrayList<Vuelo>> entry :
    // solucionProductos.entrySet()) {
    // Producto producto = entry.getKey();
    // ArrayList<Vuelo> vuelos = entry.getValue();
    //
    // RutaProductoDTO rutaProducto = RutaProductoDTO.builder()
    // .idProducto(producto.getId())
    // .idPedido(producto.getPedido() != null ? producto.getPedido().getId() : null)
    // .nombrePedido(producto.getPedido() != null ?
    // producto.getPedido().getNombre() : "Pedido-" + producto.getPedido().getId())
    // .nombreProducto(producto.getNombre() != null ?
    // producto.getNombre() : "Producto-" + producto.getId())
    // .peso(producto.getPeso())
    // .volumen(producto.getVolumen())
    // .codigoOrigen(producto.getPedido() != null ?
    // producto.getPedido().getAeropuertoOrigenCodigo() : null)
    // .codigoDestino(producto.getPedido() != null ?
    // producto.getPedido().getAeropuertoDestinoCodigo() : null)
    // .vuelos(convertirVuelosADTO(vuelos))
    // .cantidadVuelos(vuelos.size())
    // .tiempoTotalHoras(calcularTiempoTotal(vuelos))
    // .estado(producto.getEstado() != null ? producto.getEstado().toString() :
    // "DESCONOCIDO")
    // .build();
    //
    // rutasProductos.add(rutaProducto);
    // }
    //
    // // Calcular estadísticas
    // int totalProductos = rutasProductos.size();
    // int totalPedidos = rutasProductos.stream()
    // .map(RutaProductoDTO::getIdPedido)
    // .collect(Collectors.toSet())
    // .size();
    //
    // // Generar timeline temporal de simulación
    // log.info("Generando timeline de simulación...");
    // LineaDeTiempoSimulacionDTO timeline = generarLineaDeTiempoSimulacion(
    // rutasProductos,
    // horaInicio
    // );
    // log.info("Timeline generado con {} eventos", timeline.getEventos().size());
    //
    // // Calcular costo total de las rutas
    // double costoTotal = rutasProductos.stream()
    // .mapToDouble(ruta -> ruta.getVuelos().stream()
    // .mapToDouble(v -> v.getCosto() != null ? v.getCosto() : 0.0)
    // .sum())
    // .sum();
    //
    // return ResultadoAlgoritmoDTO.builder()
    // .exitoso(true)
    // .mensaje("Algoritmo ejecutado exitosamente")
    // .horaInicio(horaInicio)
    // .horaFin(horaFin)
    // .segundosEjecucion(segundosEjecucion)
    // .totalProductos(totalProductos)
    // .totalPedidos(totalPedidos)
    // .rutasProductos(rutasProductos)
    // .costoTotal(costoTotal)
    // .porcentajeAsignacion(100.0) // TODO: calcular basado en pedidos totales
    // .lineaDeTiempo(timeline) // Timeline de simulación en memoria
    // .build();
    // }
    private List<VueloSimpleDTO> convertirVuelosADTOConTiempos(ArrayList<Vuelo> vuelos,
            RutaConTiempos rutaConTiempos) {
        List<VueloSimpleDTO> resultado = new ArrayList<>();

        for (int i = 0; i < vuelos.size(); i++) {
            Vuelo v = vuelos.get(i);

            // Generar código de vuelo basado en ruta
            String codigoOrigen = v.getAeropuertoOrigen() != null
                    ? v.getAeropuertoOrigen().getCodigoIATA()
                    : "???";
            String codigoDestino = v.getAeropuertoDestino() != null
                    ? v.getAeropuertoDestino().getCodigoIATA()
                    : "???";
            String codigo = "VL" + codigoOrigen + "-" + codigoDestino + "-" + v.getId();

            // Obtener tiempos del ALNS si están disponibles
            LocalDateTime horaSalidaReal = null;
            LocalDateTime horaLlegadaReal = null;

            if (rutaConTiempos != null && rutaConTiempos.getTramos() != null
                    && i < rutaConTiempos.getTramos().size()) {
                TramoConTiempo tramo = rutaConTiempos.getTramos().get(i);
                horaSalidaReal = tramo.getHoraSalidaReal();
                horaLlegadaReal = tramo.getHoraLlegadaReal();
            }

            VueloSimpleDTO dto = VueloSimpleDTO.builder()
                    .id(v.getId())
                    .codigo(codigo)
                    .codigoOrigen(codigoOrigen)
                    .codigoDestino(codigoDestino)
                    .idAeropuertoOrigen(
                            v.getAeropuertoOrigen() != null
                                    ? v.getAeropuertoOrigen().getId()
                                    : null)
                    .idAeropuertoDestino(
                            v.getAeropuertoDestino() != null
                                    ? v.getAeropuertoDestino().getId()
                                    : null)
                    .horaSalida(v.getHoraSalida())
                    .horaLlegada(v.getHoraLlegada())
                    .tiempoTransporte(v.getTiempoTransporte())
                    .costo(v.getCosto())
                    .capacidadUsada(v.getCapacidadUsada())
                    .capacidadMaxima(v.getCapacidadMaxima())
                    // NUEVOS campos con tiempos absolutos del ALNS
                    .horaSalidaReal(horaSalidaReal)
                    .horaLlegadaReal(horaLlegadaReal)
                    .build();

            resultado.add(dto);
        }

        return resultado;
    }

    private VueloSimpleDTO convertirVueloAVueloDTO(Vuelo vuelo) {
        return VueloSimpleDTO.builder()
                .id(vuelo.getId())
                .codigo("Vuelo + " + vuelo.getId()) // se puede mejorar
                .codigoOrigen(
                        vuelo.getAeropuertoOrigen() != null ? vuelo.getAeropuertoOrigen().getCodigoIATA() : "???")
                .codigoDestino(
                        vuelo.getAeropuertoDestino() != null ? vuelo.getAeropuertoDestino().getCodigoIATA() : "???")
                .horaSalida(vuelo.getHoraSalida())
                .horaLlegada(vuelo.getHoraLlegada())
                .costo(vuelo.getCosto())
                .build();
    }

    private LocalDateTime reconstruirFechaHora(Pedido pedido, Vuelo vuelo, boolean esSalida) {

        LocalDate fechaBase = pedido.getFechaPedido().toLocalDate(); // debes tener este campo

        LocalTime hora = esSalida ? vuelo.getHoraSalida() : vuelo.getHoraLlegada();

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
                    String codigoOrigen = v.getAeropuertoOrigen() != null ? v.getAeropuertoOrigen().getCodigoIATA()
                            : "???";
                    String codigoDestino = v.getAeropuertoDestino() != null ? v.getAeropuertoDestino().getCodigoIATA()
                            : "???";
                    String codigo = "VL" + codigoOrigen + "-" + codigoDestino + "-" + v.getId();

                    return VueloSimpleDTO.builder()
                            .id(v.getId())
                            .codigo(codigo)
                            .codigoOrigen(codigoOrigen)
                            .codigoDestino(codigoDestino)
                            .idAeropuertoOrigen(
                                    v.getAeropuertoOrigen() != null ? v.getAeropuertoOrigen().getId() : null)
                            .idAeropuertoDestino(
                                    v.getAeropuertoDestino() != null ? v.getAeropuertoDestino().getId() : null)
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
     * ESCENARIO DIARIO: Ejecutar algoritmo ALNS para ventana incremental de tiempo
     * (ej: cada 30 minutos).
     * POST /api/algoritmo/diario
     * 
     * Uso típico:
     * - Simulación en tiempo real continua
     * - Frontend llama cada ~30 minutos de simulación
     * - Carga solo pedidos dentro de la ventana de tiempo
     * - Retorna rutas para este segmento de tiempo
     * - Se ejecuta indefinidamente hasta detenerse
     */
    @Operation(summary = "Ejecutar algoritmo ALNS - Escenario Diario", description = "Ejecuta el algoritmo ALNS para un escenario diario con ventanas incrementales "
            +
            "(típicamente 30 minutos). Diseñado para operaciones en tiempo real.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Algoritmo ejecutado exitosamente", content = @Content(schema = @Schema(implementation = ResultadoAlgoritmoDTO.class))),
            @ApiResponse(responseCode = "400", description = "Parámetros de solicitud inválidos"),
            @ApiResponse(responseCode = "500", description = "Error durante la ejecución del algoritmo")
    })
    @PostMapping("/diario")
    public ResponseEntity<ResultadoAlgoritmoDTO> ejecutarEscenarioDiario(
            @RequestBody(required = false) AlnsRequestDTO solicitud) {

        LocalDateTime horaInicio = LocalDateTime.now();
        log.info("🚀 Ejecutando algoritmo ALNS - ESCENARIO DIARIO - Inicio: {}", horaInicio);

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
                        .mensaje("Error: horaInicioSimulacion es requerida para escenario diario")
                        .horaInicio(horaInicio)
                        .horaFin(LocalDateTime.now())
                        .build();
                return ResponseEntity.badRequest().body(resultadoError);
            }

            // Calcular ventana de tiempo (por defecto 30 minutos si no se especifica)
            if (solicitud.getHoraFinSimulacion() == null) {
                if (solicitud.getDuracionSimulacionHoras() == null && solicitud.getDuracionSimulacionDias() == null) {
                    solicitud.setDuracionSimulacionHoras(0.5); // 30 minutos por defecto
                }

                LocalDateTime horaFin;
                if (solicitud.getDuracionSimulacionHoras() != null) {
                    long minutos = (long) (solicitud.getDuracionSimulacionHoras() * 60);
                    horaFin = solicitud.getHoraInicioSimulacion().plusMinutes(minutos);
                } else {
                    horaFin = solicitud.getHoraInicioSimulacion().plusDays(solicitud.getDuracionSimulacionDias());
                }
                solicitud.setHoraFinSimulacion(horaFin);
            }

            log.info("Ventana de tiempo: {} a {}",
                    solicitud.getHoraInicioSimulacion(), solicitud.getHoraFinSimulacion());

            // Ejecutar algoritmo
            return ejecutarAlgoritmo(solicitud, 1);

        } catch (Exception e) {
            log.error("❌ Error ejecutando escenario diario", e);

            ResultadoAlgoritmoDTO resultadoError = ResultadoAlgoritmoDTO.builder()
                    .exitoso(false)
                    .mensaje("Error durante la ejecución del escenario diario: " + e.getMessage())
                    .horaInicio(horaInicio)
                    .horaFin(LocalDateTime.now())
                    .build();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resultadoError);
        }
    }
    // public ResponseEntity<ResultadoAlgoritmoDTO> ejecutarEscenarioDiario(
    // @RequestBody(required = false) AlnsRequestDTO solicitud) {
    //
    // LocalDateTime horaInicioBackend = LocalDateTime.now();
    // log.info("🚀 Ejecutando algoritmo ALNS - ESCENARIO DIARIO");
    //
    // try {
    // if (solicitud == null) {
    // solicitud = new AlnsRequestDTO();
    // solicitud.setUsarBaseDatos(true);
    // }
    //
    // if (solicitud.getHoraInicioSimulacion() == null) {
    // return ResponseEntity.badRequest().body(
    // ResultadoAlgoritmoDTO.builder()
    // .exitoso(false)
    // .mensaje("horaInicioSimulacion es requerida")
    // .horaInicio(horaInicioBackend)
    // .horaFin(LocalDateTime.now())
    // .build()
    // );
    // }
    //
    // // ==============================
    // // 1. Calcular la horaFin si no viene
    // // ==============================
    // if (solicitud.getHoraFinSimulacion() == null) {
    //
    // if (solicitud.getDuracionSimulacionHoras() == null &&
    // solicitud.getDuracionSimulacionDias() == null) {
    //
    // solicitud.setDuracionSimulacionHoras(1.0); // 30 minutos por defecto
    // }
    //
    // LocalDateTime horaFin;
    //
    // if (solicitud.getDuracionSimulacionHoras() != null) {
    // long minutos = (long) (solicitud.getDuracionSimulacionHoras() * 60);
    // horaFin = solicitud.getHoraInicioSimulacion().plusMinutes(minutos);
    //
    // } else {
    // horaFin = solicitud.getHoraInicioSimulacion()
    // .plusDays(solicitud.getDuracionSimulacionDias());
    // }
    //
    // solicitud.setHoraFinSimulacion(horaFin);
    // }
    //
    // LocalDateTime windowStart = solicitud.getHoraInicioSimulacion();
    // LocalDateTime windowEnd = solicitud.getHoraFinSimulacion();
    //
    // log.info("Ventana temporal: {} -> {}", windowStart, windowEnd);
    //
    // // ==============================
    // // 2. Ejecutar ALNS COMPLETO
    // // ==============================
    // ResultadoAlgoritmoDTO fullResult = ejecutarAlgoritmoInterno(solicitud);
    //
    // if (!fullResult.getExitoso()) {
    // return ResponseEntity.ok(fullResult);
    // }
    //
    // // ==============================
    // // 3. Filtrar SOLO productos cuya horaSalida reconstruida cae dentro de la
    // ventana
    // // ==============================
    // List<RutaProductoDTO> rutasVentana = fullResult.getRutasProductos().stream()
    // .filter(r -> {
    // LocalDateTime salida = r.getHoraSalida();
    // return salida != null &&
    // !salida.isBefore(windowStart) &&
    // !salida.isAfter(windowEnd);
    // })
    // .toList();
    //
    // log.info("Productos en ventana: {}", rutasVentana.size());
    //
    // // ==============================
    // // 4. Reconstruir mapa {Producto → Vuelos} para el conversor
    // // ==============================
    // Map<Producto, ArrayList<Vuelo>> mapaVentana = reconstruirMapa(rutasVentana);
    //
    // // ==============================
    // // 5. Convertir solo la ventana al formato DTO completo para el frontend
    // // ==============================
    // ResultadoAlgoritmoDTO resultadoVentana =
    // convertirSolucionAResultadoConSalida(
    // mapaVentana,
    // windowStart,
    // windowEnd,
    // 0
    // );
    //
    // resultadoVentana.setMensaje(
    // "Ventana procesada: " + windowStart + " → " + windowEnd
    // );
    //
    // return ResponseEntity.ok(resultadoVentana);
    //
    // } catch (RuntimeException e) {
    // log.error("❌ Error en /diario", e);
    //
    // return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
    // .body(ResultadoAlgoritmoDTO.builder()
    // .exitoso(false)
    // .mensaje("Error: " + e.getMessage())
    // .horaInicio(horaInicioBackend)
    // .horaFin(LocalDateTime.now())
    // .build()
    // );
    // }
    // }

    // private ResultadoAlgoritmoDTO ejecutarAlgoritmoInterno(AlnsRequestDTO
    // solicitud) {
    // return ejecutarAlgoritmo(solicitud,1).getBody();
    // }
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
            p.setPedido(pedido);

            ArrayList<Vuelo> vuelos = r.getVuelos().stream()
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

            // =====================================================
            // 1. RECONSTRUIR FECHA-HORA REAL DE SALIDA Y LLEGADA
            // =====================================================
            LocalDateTime fechaSalidaReal = reconstruirFechaHora(producto.getPedido(), vuelos.get(0), true);

            LocalDateTime fechaLlegadaReal = reconstruirFechaHora(
                    producto.getPedido(),
                    vuelos.get(vuelos.size() - 1),
                    false);

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
                    .codigoOrigen(producto.getPedido().getAeropuertoOrigenCodigo())
                    .codigoDestino(producto.getPedido().getAeropuertoDestinoCodigo())
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
        LineaDeTiempoSimulacionDTO timeline = generarLineaDeTiempoSimulacionVentana(rutasProductos,
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
     * ESCENARIO SEMANAL: Ejecutar algoritmo ALNS para simulación de 7 días
     * completa.
     * POST /api/algoritmo/semanal
     * 
     * Uso típico:
     * - Frontend llama una vez con rango de 7 días
     * - Carga todos los pedidos de la semana
     * - Retorna solución completa para 7 días
     * - Debería ejecutarse en 30-90 minutos (según requerimientos)
     */
    @Operation(summary = "Ejecutar algoritmo ALNS - Escenario Semanal", description = "Ejecuta el algoritmo ALNS para un escenario semanal completo de 7 días. "
            +
            "Procesa toda la semana en una sola ejecución. " +
            "Tiempo esperado: 30-90 minutos.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Algoritmo ejecutado exitosamente", content = @Content(schema = @Schema(implementation = ResultadoAlgoritmoDTO.class))),
            @ApiResponse(responseCode = "400", description = "Parámetros de solicitud inválidos"),
            @ApiResponse(responseCode = "500", description = "Error durante la ejecución del algoritmo")
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
                        solicitud.getHoraInicioSimulacion().plusDays(solicitud.getDuracionSimulacionDias()));
            }

            log.info("Ventana de tiempo: {} a {} ({} días)",
                    solicitud.getHoraInicioSimulacion(),
                    solicitud.getHoraFinSimulacion(),
                    solicitud.getDuracionSimulacionDias());
            log.info("⚠️ ADVERTENCIA: La ejecución semanal puede tomar 30-90 minutos");

            // Ejecutar algoritmo
            return ejecutarAlgoritmo(solicitud, 0);

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
     * @param horaInicio     Hora de inicio de la simulación
     * @return Timeline con eventos ordenados temporalmente
     */
    private LineaDeTiempoSimulacionDTO generarLineaDeTiempoSimulacion(
            List<RutaProductoDTO> rutasProductos,
            LocalDateTime horaInicio) {

        List<EventoLineaDeTiempoVueloDTO> eventos = new ArrayList<>();
        Set<Integer> aeropuertosSet = new HashSet<>();

        // Clase para agrupar por vuelo real (mismo idVuelo + misma hora de salida)
        class InfoVueloReal {
            VueloSimpleDTO vuelo;
            LocalDateTime horaSalida;
            LocalDateTime horaLlegada;
            double horasTransporte;
            Set<Integer> idsProductos = new HashSet<>();
            Set<Integer> idsPedidos = new HashSet<>();
        }

        // Mapa: clave = "idVuelo-horaSalida" → info del vuelo
        Map<String, InfoVueloReal> vuelosReales = new HashMap<>();

        // NUEVO: Mapa de idPedido -> codigoDestinoFinal para determinar si un vuelo es
        // destino final
        Map<Integer, String> pedidoDestinoFinal = new HashMap<>();
        for (RutaProductoDTO ruta : rutasProductos) {
            if (ruta.getIdPedido() != null && ruta.getCodigoDestino() != null) {
                pedidoDestinoFinal.put(ruta.getIdPedido(), ruta.getCodigoDestino());
            }
        }

        // Paso 1: Calcular tiempos para cada producto y agrupar por vuelo real
        for (RutaProductoDTO rutaProducto : rutasProductos) {
            LocalDateTime tiempoActualProducto = (rutaProducto.getFechaPedido() != null)
                    ? rutaProducto.getFechaPedido()
                    : horaInicio;

            for (int i = 0; i < rutaProducto.getVuelos().size(); i++) {
                VueloSimpleDTO vuelo = rutaProducto.getVuelos().get(i);

                // Rastrear aeropuertos
                if (vuelo.getIdAeropuertoOrigen() != null) {
                    aeropuertosSet.add(vuelo.getIdAeropuertoOrigen());
                }
                if (vuelo.getIdAeropuertoDestino() != null) {
                    aeropuertosSet.add(vuelo.getIdAeropuertoDestino());
                }

                // Calcular tiempo de transporte
                double horasTransporte = (vuelo.getTiempoTransporte() != null
                        && vuelo.getTiempoTransporte() > 0)
                                ? vuelo.getTiempoTransporte()
                                : 2.0;

                // Calcular hora de salida
                LocalDateTime horaSalidaVuelo;
                if (vuelo.getHoraSalida() != null) {
                    horaSalidaVuelo = tiempoActualProducto.toLocalDate()
                            .atTime(vuelo.getHoraSalida());
                    if (horaSalidaVuelo.isBefore(tiempoActualProducto)) {
                        horaSalidaVuelo = horaSalidaVuelo.plusDays(1);
                    }
                } else {
                    horaSalidaVuelo = tiempoActualProducto;
                }

                // Calcular hora de llegada
                long minutosTransporte = (long) (horasTransporte * 60);
                LocalDateTime horaLlegadaVuelo = horaSalidaVuelo.plusMinutes(minutosTransporte);

                // Clave única: idVuelo + hora de salida (redondeada al minuto)
                String claveVuelo = vuelo.getId() + "-" +
                        horaSalidaVuelo.withSecond(0).withNano(0).toString();

                // Agregar o actualizar grupo
                InfoVueloReal info = vuelosReales.get(claveVuelo);
                if (info == null) {
                    info = new InfoVueloReal();
                    info.vuelo = vuelo;
                    info.horaSalida = horaSalidaVuelo;
                    info.horaLlegada = horaLlegadaVuelo;
                    info.horasTransporte = horasTransporte;
                    vuelosReales.put(claveVuelo, info);
                }
                info.idsProductos.add(rutaProducto.getIdProducto());
                info.idsPedidos.add(rutaProducto.getIdPedido());

                // Próximo vuelo del producto
                tiempoActualProducto = horaLlegadaVuelo.plusMinutes(60);
            }
        }

        log.info("Procesados {} productos → {} vuelos reales únicos",
                rutasProductos.size(), vuelosReales.size());

        // Paso 2: Crear eventos para cada vuelo real
        int contadorEventos = 0;
        for (Map.Entry<String, InfoVueloReal> entry : vuelosReales.entrySet()) {
            InfoVueloReal info = entry.getValue();
            VueloSimpleDTO vuelo = info.vuelo;
            List<Integer> pedidosList = new ArrayList<>(info.idsPedidos);

            // Calcular si este vuelo es destino final para ALGÚN pedido
            // Es destino final si el codigoDestino del vuelo coincide con el destino final
            // del pedido
            String destinoVuelo = vuelo.getCodigoDestino();
            boolean esDestinoFinal = pedidosList.stream()
                    .anyMatch(idPedido -> {
                        String destinoFinalPedido = pedidoDestinoFinal.get(idPedido);
                        return destinoFinalPedido != null
                                && destinoFinalPedido.equals(destinoVuelo);
                    });

            // Evento de llegada
            // Evento de salida
            EventoLineaDeTiempoVueloDTO eventoSalida = EventoLineaDeTiempoVueloDTO.builder()
                    .idEvento("DEP-" + contadorEventos)
                    .tipoEvento("DEPARTURE")
                    .horaEvento(info.horaSalida)
                    .idVuelo(vuelo.getId())
                    .codigoVuelo(vuelo.getCodigo())
                    .cantidadProductos(info.idsProductos.size())
                    .idProducto(info.idsProductos.iterator().next())
                    .idPedido(pedidosList.get(0))
                    .idsPedidos(pedidosList)
                    .ciudadOrigen(vuelo.getCodigoOrigen())
                    .ciudadDestino(vuelo.getCodigoDestino())
                    .codigoIATAOrigen(vuelo.getCodigoOrigen())
                    .codigoIATADestino(vuelo.getCodigoDestino())
                    .idAeropuertoOrigen(vuelo.getIdAeropuertoOrigen())
                    .idAeropuertoDestino(vuelo.getIdAeropuertoDestino())
                    .tiempoTransporteDias(info.horasTransporte / 24.0)
                    .capacidadMaxima(vuelo.getCapacidadMaxima())
                    .build();
            eventos.add(eventoSalida);

            EventoLineaDeTiempoVueloDTO eventoLlegada = EventoLineaDeTiempoVueloDTO.builder()
                    .idEvento("ARR-" + contadorEventos)
                    .tipoEvento("ARRIVAL")
                    .horaEvento(info.horaLlegada)
                    .idVuelo(vuelo.getId())
                    .codigoVuelo(vuelo.getCodigo())
                    .cantidadProductos(info.idsProductos.size())
                    .idProducto(info.idsProductos.iterator().next())
                    .idPedido(pedidosList.get(0))
                    .idsPedidos(pedidosList)
                    .ciudadOrigen(vuelo.getCodigoOrigen())
                    .ciudadDestino(vuelo.getCodigoDestino())
                    .codigoIATAOrigen(vuelo.getCodigoOrigen())
                    .codigoIATADestino(vuelo.getCodigoDestino())
                    .esDestinoFinal(esDestinoFinal)
                    .idAeropuertoOrigen(vuelo.getIdAeropuertoOrigen())
                    .idAeropuertoDestino(vuelo.getIdAeropuertoDestino())
                    .tiempoTransporteDias(info.horasTransporte / 24.0)
                    .capacidadMaxima(vuelo.getCapacidadMaxima())
                    .build();
            eventos.add(eventoLlegada);

            contadorEventos++;
        }

        // Ordenar eventos por tiempo
        eventos.sort(Comparator.comparing(EventoLineaDeTiempoVueloDTO::getHoraEvento));

        // Calcular duración
        LocalDateTime horaFin = eventos.isEmpty() ? horaInicio
                : eventos.get(eventos.size() - 1).getHoraEvento();
        long duracionMinutos = ChronoUnit.MINUTES.between(horaInicio, horaFin);

        log.info("Timeline: {} eventos, {} vuelos, duración {} horas (~{} días)",
                eventos.size(), vuelosReales.size(),
                duracionMinutos / 60, duracionMinutos / (60 * 24));

        return LineaDeTiempoSimulacionDTO.builder()
                .horaInicioSimulacion(horaInicio)
                .horaFinSimulacion(horaFin)
                .duracionTotalMinutos(duracionMinutos)
                .eventos(eventos)
                .totalEventos(eventos.size())
                .rutasProductos(rutasProductos)
                .totalProductos(rutasProductos.size())
                .totalVuelos(vuelosReales.size())
                .totalAeropuertos(aeropuertosSet.size())
                .build();
    }

    private LineaDeTiempoSimulacionDTO generarLineaDeTiempoSimulacionVentana(
            List<RutaProductoDTO> rutas,
            LocalDateTime windowStart,
            LocalDateTime windowEnd) {

        List<EventoLineaDeTiempoVueloDTO> eventos = new ArrayList<>();

        for (RutaProductoDTO r : rutas) {

            if (r.getVuelos() == null || r.getVuelos().isEmpty())
                continue;

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

// @Autowired
// private org.springframework.context.ApplicationContext applicationContext;

// @Operation(summary = "Ejecutar algoritmo ALNS", description = "Ejecuta el
// algoritmo ALNS y retorna resultados a nivel de producto en memoria. "
// +
// "No persiste datos - siguiendo patrón Backend")
// @ApiResponses(value = {
// @ApiResponse(responseCode = "200", description = "Algoritmo ejecutado
// exitosamente", content = @Content(schema = @Schema(implementation =
// ResultadoAlgoritmoDTO.class))),
// @ApiResponse(responseCode = "500", description = "Error durante la ejecución
// del algoritmo")
// })
// @PostMapping("/ejecutar")
// public ResponseEntity<ResultadoAlgoritmoDTO> ejecutarAlgoritmo(
// @RequestBody(required = false) AlnsRequestDTO solicitud) {

// LocalDateTime horaInicio = LocalDateTime.now();
// log.info("🚀 Ejecutando algoritmo ALNS (sincrónico) - Inicio: {}",
// horaInicio);

// try {
// // 1. Configurar fuente de datos según solicitud
// log.info("📥 Solicitud recibida: {}", solicitud);

// Boolean usarBaseDatos = (solicitud != null && solicitud.getUsarBaseDatos() !=
// null)
// ? solicitud.getUsarBaseDatos()
// : true;

// String fuente = usarBaseDatos ? "BASE_DE_DATOS" : "ARCHIVO";

// log.info("🔧 Fuente de datos configurada: [{}]", fuente);
// log.debug("Configurando System Property MODO_FUENTE_DATOS = {}", fuente);

// // Configurar variable de entorno para la fuente de datos
// System.setProperty("MODO_FUENTE_DATOS", fuente);

// // Verificar que se guardó correctamente
// String fuenteGuardada = System.getProperty("MODO_FUENTE_DATOS");
// log.debug("System Property guardada = {}", fuenteGuardada);

// // 2. Crear instancia de ALNSSolver con configuración
// int iteraciones = (solicitud != null && solicitud.getMaxIteraciones() !=
// null)
// ? solicitud.getMaxIteraciones()
// : 500;

// // Pasar ApplicationContext para que pueda usar servicios de Spring cuando
// // fuente=BASE_DE_DATOS
// log.debug("Configurando Spring Context en FabricaFuenteDatos");
// com.grupo5e.morapack.algorithm.input.FabricaFuenteDatos.setSpringContext(applicationContext);

// log.info("Iteraciones configuradas: {}", iteraciones);

// // Extraer ventanas de tiempo si existen
// LocalDateTime horaInicioSimulacion = (solicitud != null) ?
// solicitud.getHoraInicioSimulacion()
// : null;
// LocalDateTime horaFinSimulacion = (solicitud != null) ?
// solicitud.getHoraFinSimulacion() : null;

// // Usar constructor con soporte de ventanas de tiempo
// log.info("Creando ALNSSolver con {} iteraciones{}", iteraciones,
// (horaInicioSimulacion != null ? " y ventana de tiempo" : ""));
// ALNSSolver solver = new ALNSSolver(iteraciones, horaInicioSimulacion,
// horaFinSimulacion);

// // 3. Ejecutar el algoritmo
// log.info(" Iteraciones configuradas: {}", iteraciones);
// solver.resolver();

// LocalDateTime horaFin = LocalDateTime.now();
// long segundosEjecucion = ChronoUnit.SECONDS.between(horaInicio, horaFin);

// // 3. Obtener solución a nivel de producto (mantener compatibilidad)
// Map<Producto, ArrayList<Vuelo>> solucionProductos =
// solver.obtenerSolucionNivelProducto();

// // 3.1 NUEVO: Obtener solución CON tiempos calculados por ALNS
// Map<Producto, RutaConTiempos> solucionConTiempos =
// solver.obtenerSolucionConTiempos();
// log.info("Solución con tiempos obtenida: {} productos",
// solucionConTiempos.size());

// // 4. Convertir a DTO
// // IMPORTANTE: Pasar horaInicioSimulacion para el timeline, no la hora de
// // ejecución
// LocalDateTime horaInicioParaTimeline = (horaInicioSimulacion != null) ?
// horaInicioSimulacion
// : horaInicio;
// ResultadoAlgoritmoDTO resultado = convertirSolucionAResultado(
// solucionProductos,
// solucionConTiempos, // NUEVO: pasar tiempos
// horaInicio,
// horaFin,
// segundosEjecucion,
// horaInicioParaTimeline // Hora de inicio para el timeline de simulación
// );

// log.info("✅ Algoritmo completado exitosamente en {} segundos",
// segundosEjecucion);
// log.info(" Total productos asignados: {}", resultado.getTotalProductos());

// return ResponseEntity.ok(resultado);

// } catch (Exception e) {
// log.error("❌ Error ejecutando algoritmo ALNS", e);

// ResultadoAlgoritmoDTO resultadoError = ResultadoAlgoritmoDTO.builder()
// .exitoso(false)
// .mensaje("Error durante la ejecución: " + e.getMessage())
// .horaInicio(horaInicio)
// .horaFin(LocalDateTime.now())
// .segundosEjecucion(ChronoUnit.SECONDS.between(horaInicio,
// LocalDateTime.now()))
// .totalProductos(0)
// .totalPedidos(0)
// .rutasProductos(new ArrayList<>())
// .build();

// return
// ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resultadoError);
// }
// }

// /**
// * Convierte la solución del algoritmo a ResultadoAlgoritmoDTO
// *
// * @param solucionProductos Mapa de productos a vuelos (compatibilidad)
// * @param solucionConTiempos NUEVO: Mapa de productos a rutas con tiempos
// * calculados
// * @param horaInicioSimulacion Hora de inicio para el timeline de simulación
// * (puede ser diferente a horaInicio de ejecución)
// */
//

// /**
// * Convierte lista de vuelos a VueloSimpleDTO evitando referencias circulares
// */
// private List<VueloSimpleDTO> convertirVuelosADTO(ArrayList<Vuelo> vuelos) {
// return vuelos.stream()
// .map(v -> {
// // Generar código de vuelo basado en ruta
// String codigoOrigen = v.getAeropuertoOrigen() != null
// ? v.getAeropuertoOrigen().getCodigoIATA()
// : "???";
// String codigoDestino = v.getAeropuertoDestino() != null
// ? v.getAeropuertoDestino().getCodigoIATA()
// : "???";
// String codigo = "VL" + codigoOrigen + "-" + codigoDestino + "-" + v.getId();

// return VueloSimpleDTO.builder()
// .id(v.getId())
// .codigo(codigo)
// .codigoOrigen(codigoOrigen)
// .codigoDestino(codigoDestino)
// .idAeropuertoOrigen(
// v.getAeropuertoOrigen() != null
// ? v.getAeropuertoOrigen()
// .getId()
// : null)
// .idAeropuertoDestino(
// v.getAeropuertoDestino() != null
// ? v.getAeropuertoDestino()
// .getId()
// : null)
// .horaSalida(v.getHoraSalida())
// .horaLlegada(v.getHoraLlegada())
// .tiempoTransporte(v.getTiempoTransporte())
// .costo(v.getCosto())
// .capacidadUsada(v.getCapacidadUsada())
// .capacidadMaxima(v.getCapacidadMaxima())
// .build();
// })
// .collect(Collectors.toList());
// }

// /**
// * NUEVO: Convierte lista de vuelos a VueloSimpleDTO CON tiempos absolutos del
// * ALNS.
// * Si hay RutaConTiempos disponible, incluye horaSalidaReal y horaLlegadaReal.
// */

// /**
// * Calcula tiempo total de ruta en horas
// */
// private Double calcularTiempoTotal(ArrayList<Vuelo> vuelos) {
// if (vuelos == null || vuelos.isEmpty()) {
// return 0.0;
// }

// return vuelos.stream()
// .mapToDouble(Vuelo::getTiempoTransporte)
// .sum();
// }

// /**
// * ESCENARIO DIARIO: Ejecutar algoritmo ALNS para ventana incremental de
// tiempo
// * (ej: cada 30 minutos).
// * POST /api/algoritmo/diario
// *
// * Uso típico:
// * - Simulación en tiempo real continua
// * - Frontend llama cada ~30 minutos de simulación
// * - Carga solo pedidos dentro de la ventana de tiempo
// * - Retorna rutas para este segmento de tiempo
// * - Se ejecuta indefinidamente hasta detenerse
// */
// @Operation(summary = "Ejecutar algoritmo ALNS - Escenario Diario",
// description = "Ejecuta el algoritmo ALNS para un escenario diario con
// ventanas incrementales "
// +
// "(típicamente 30 minutos). Diseñado para operaciones en tiempo real.")
// @ApiResponses(value = {
// @ApiResponse(responseCode = "200", description = "Algoritmo ejecutado
// exitosamente", content = @Content(schema = @Schema(implementation =
// ResultadoAlgoritmoDTO.class))),
// @ApiResponse(responseCode = "400", description = "Parámetros de solicitud
// inválidos"),
// @ApiResponse(responseCode = "500", description = "Error durante la ejecución
// del algoritmo")
// })
// @PostMapping("/diario")
// public ResponseEntity<ResultadoAlgoritmoDTO> ejecutarEscenarioDiario(
// @RequestBody(required = false) AlnsRequestDTO solicitud) {

// LocalDateTime horaInicio = LocalDateTime.now();
// log.info("🚀 Ejecutando algoritmo ALNS - ESCENARIO DIARIO - Inicio: {}",
// horaInicio);

// try {
// // Validar solicitud
// if (solicitud == null) {
// solicitud = AlnsRequestDTO.builder()
// .usarBaseDatos(true)
// .build();
// }

// if (solicitud.getHoraInicioSimulacion() == null) {
// ResultadoAlgoritmoDTO resultadoError = ResultadoAlgoritmoDTO.builder()
// .exitoso(false)
// .mensaje("Error: horaInicioSimulacion es requerida para escenario diario")
// .horaInicio(horaInicio)
// .horaFin(LocalDateTime.now())
// .build();
// return ResponseEntity.badRequest().body(resultadoError);
// }

// // Calcular ventana de tiempo (por defecto 30 minutos si no se especifica)
// if (solicitud.getHoraFinSimulacion() == null) {
// if (solicitud.getDuracionSimulacionHoras() == null
// && solicitud.getDuracionSimulacionDias() == null) {
// solicitud.setDuracionSimulacionHoras(0.5); // 30 minutos por defecto
// }

// LocalDateTime horaFin;
// if (solicitud.getDuracionSimulacionHoras() != null) {
// long minutos = (long) (solicitud.getDuracionSimulacionHoras() * 60);
// horaFin = solicitud.getHoraInicioSimulacion().plusMinutes(minutos);
// } else {
// horaFin = solicitud.getHoraInicioSimulacion()
// .plusDays(solicitud.getDuracionSimulacionDias());
// }
// solicitud.setHoraFinSimulacion(horaFin);
// }

// log.info("Ventana de tiempo: {} a {}",
// solicitud.getHoraInicioSimulacion(), solicitud.getHoraFinSimulacion());

// // Ejecutar algoritmo
// return ejecutarAlgoritmo(solicitud);

// } catch (Exception e) {
// log.error("❌ Error ejecutando escenario diario", e);

// ResultadoAlgoritmoDTO resultadoError = ResultadoAlgoritmoDTO.builder()
// .exitoso(false)
// .mensaje("Error durante la ejecución del escenario diario: " +
// e.getMessage())
// .horaInicio(horaInicio)
// .horaFin(LocalDateTime.now())
// .build();

// return
// ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resultadoError);
// }
// }

// /**
// * ESCENARIO SEMANAL: Ejecutar algoritmo ALNS para simulación de 7 días
// * completa.
// * POST /api/algoritmo/semanal
// *
// * Uso típico:
// * - Frontend llama una vez con rango de 7 días
// * - Carga todos los pedidos de la semana
// * - Retorna solución completa para 7 días
// * - Debería ejecutarse en 30-90 minutos (según requerimientos)
// */
// @Operation(summary = "Ejecutar algoritmo ALNS - Escenario Semanal",
// description = "Ejecuta el algoritmo ALNS para un escenario semanal completo
// de 7 días. "
// +
// "Procesa toda la semana en una sola ejecución. " +
// "Tiempo esperado: 30-90 minutos.")
// @ApiResponses(value = {
// @ApiResponse(responseCode = "200", description = "Algoritmo ejecutado
// exitosamente", content = @Content(schema = @Schema(implementation =
// ResultadoAlgoritmoDTO.class))),
// @ApiResponse(responseCode = "400", description = "Parámetros de solicitud
// inválidos"),
// @ApiResponse(responseCode = "500", description = "Error durante la ejecución
// del algoritmo")
// })
// @PostMapping("/semanal")
// public ResponseEntity<ResultadoAlgoritmoDTO> ejecutarEscenarioSemanal(
// @RequestBody(required = false) AlnsRequestDTO solicitud) {

// LocalDateTime horaInicio = LocalDateTime.now();
// log.info("🚀 Ejecutando algoritmo ALNS - ESCENARIO SEMANAL - Inicio: {}",
// horaInicio);

// try {
// // Validar solicitud
// if (solicitud == null) {
// solicitud = AlnsRequestDTO.builder()
// .usarBaseDatos(true)
// .build();
// }

// if (solicitud.getHoraInicioSimulacion() == null) {
// ResultadoAlgoritmoDTO resultadoError = ResultadoAlgoritmoDTO.builder()
// .exitoso(false)
// .mensaje("Error: horaInicioSimulacion es requerida para escenario semanal")
// .horaInicio(horaInicio)
// .horaFin(LocalDateTime.now())
// .build();
// return ResponseEntity.badRequest().body(resultadoError);
// }

// // Forzar duración de 7 días si no se especifica
// if (solicitud.getDuracionSimulacionDias() == null) {
// solicitud.setDuracionSimulacionDias(7);
// }

// // Calcular ventana de tiempo
// if (solicitud.getHoraFinSimulacion() == null) {
// solicitud.setHoraFinSimulacion(
// solicitud.getHoraInicioSimulacion()
// .plusDays(solicitud.getDuracionSimulacionDias()));
// }

// log.info("Ventana de tiempo: {} a {} ({} días)",
// solicitud.getHoraInicioSimulacion(),
// solicitud.getHoraFinSimulacion(),
// solicitud.getDuracionSimulacionDias());
// log.info("⚠️ ADVERTENCIA: La ejecución semanal puede tomar 30-90 minutos");

// // Ejecutar algoritmo
// return ejecutarAlgoritmo(solicitud);

// } catch (Exception e) {
// log.error("❌ Error ejecutando escenario semanal", e);

// ResultadoAlgoritmoDTO resultadoError = ResultadoAlgoritmoDTO.builder()
// .exitoso(false)
// .mensaje("Error durante la ejecución del escenario semanal: " +
// e.getMessage())
// .horaInicio(horaInicio)
// .horaFin(LocalDateTime.now())
// .build();

// return
// ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resultadoError);
// }
// }

// /**
// * Genera timeline temporal de simulación con eventos de vuelos.
// * AGRUPAMIENTO POR VUELO REAL: Un evento por cada instancia de vuelo (idVuelo
// +
// * hora),
// * con la lista completa de todos los pedidos que van en ese vuelo.
// *
// * @param rutasProductos Rutas de productos
// * @param horaInicio Hora de inicio de la simulación
// * @return Timeline con eventos ordenados temporalmente
// */
// private LineaDeTiempoSimulacionDTO generarLineaDeTiempoSimulacion(
// List<RutaProductoDTO> rutasProductos,
// LocalDateTime horaInicio) {

// List<EventoLineaDeTiempoVueloDTO> eventos = new ArrayList<>();
// Set<Integer> aeropuertosSet = new HashSet<>();

// // Clase para agrupar por vuelo real (mismo idVuelo + misma hora de salida)
// class InfoVueloReal {
// VueloSimpleDTO vuelo;
// LocalDateTime horaSalida;
// LocalDateTime horaLlegada;
// double horasTransporte;
// Set<Integer> idsProductos = new HashSet<>();
// Set<Integer> idsPedidos = new HashSet<>();
// }

// // Mapa: clave = "idVuelo-horaSalida" → info del vuelo
// Map<String, InfoVueloReal> vuelosReales = new HashMap<>();

// // NUEVO: Mapa de idPedido -> codigoDestinoFinal para determinar si un vuelo
// es
// // destino final
// Map<Integer, String> pedidoDestinoFinal = new HashMap<>();
// for (RutaProductoDTO ruta : rutasProductos) {
// if (ruta.getIdPedido() != null && ruta.getCodigoDestino() != null) {
// pedidoDestinoFinal.put(ruta.getIdPedido(), ruta.getCodigoDestino());
// }
// }

// // Paso 1: Calcular tiempos para cada producto y agrupar por vuelo real
// for (RutaProductoDTO rutaProducto : rutasProductos) {
// LocalDateTime tiempoActualProducto = (rutaProducto.getFechaPedido() != null)
// ? rutaProducto.getFechaPedido()
// : horaInicio;

// for (int i = 0; i < rutaProducto.getVuelos().size(); i++) {
// VueloSimpleDTO vuelo = rutaProducto.getVuelos().get(i);

// // Rastrear aeropuertos
// if (vuelo.getIdAeropuertoOrigen() != null) {
// aeropuertosSet.add(vuelo.getIdAeropuertoOrigen());
// }
// if (vuelo.getIdAeropuertoDestino() != null) {
// aeropuertosSet.add(vuelo.getIdAeropuertoDestino());
// }

// // Calcular tiempo de transporte
// double horasTransporte = (vuelo.getTiempoTransporte() != null
// && vuelo.getTiempoTransporte() > 0)
// ? vuelo.getTiempoTransporte()
// : 2.0;

// // Calcular hora de salida
// LocalDateTime horaSalidaVuelo;
// if (vuelo.getHoraSalida() != null) {
// horaSalidaVuelo = tiempoActualProducto.toLocalDate()
// .atTime(vuelo.getHoraSalida());
// if (horaSalidaVuelo.isBefore(tiempoActualProducto)) {
// horaSalidaVuelo = horaSalidaVuelo.plusDays(1);
// }
// } else {
// horaSalidaVuelo = tiempoActualProducto;
// }

// // Calcular hora de llegada
// long minutosTransporte = (long) (horasTransporte * 60);
// LocalDateTime horaLlegadaVuelo =
// horaSalidaVuelo.plusMinutes(minutosTransporte);

// // Clave única: idVuelo + hora de salida (redondeada al minuto)
// String claveVuelo = vuelo.getId() + "-" +
// horaSalidaVuelo.withSecond(0).withNano(0).toString();

// // Agregar o actualizar grupo
// InfoVueloReal info = vuelosReales.get(claveVuelo);
// if (info == null) {
// info = new InfoVueloReal();
// info.vuelo = vuelo;
// info.horaSalida = horaSalidaVuelo;
// info.horaLlegada = horaLlegadaVuelo;
// info.horasTransporte = horasTransporte;
// vuelosReales.put(claveVuelo, info);
// }
// info.idsProductos.add(rutaProducto.getIdProducto());
// info.idsPedidos.add(rutaProducto.getIdPedido());

// // Próximo vuelo del producto
// tiempoActualProducto = horaLlegadaVuelo.plusMinutes(60);
// }
// }

// log.info("Procesados {} productos → {} vuelos reales únicos",
// rutasProductos.size(), vuelosReales.size());

// // Paso 2: Crear eventos para cada vuelo real
// int contadorEventos = 0;
// for (Map.Entry<String, InfoVueloReal> entry : vuelosReales.entrySet()) {
// InfoVueloReal info = entry.getValue();
// VueloSimpleDTO vuelo = info.vuelo;
// List<Integer> pedidosList = new ArrayList<>(info.idsPedidos);

// // Evento de salida
// EventoLineaDeTiempoVueloDTO eventoSalida =
// EventoLineaDeTiempoVueloDTO.builder()
// .idEvento("DEP-" + contadorEventos)
// .tipoEvento("DEPARTURE")
// .horaEvento(info.horaSalida)
// .idVuelo(vuelo.getId())
// .codigoVuelo(vuelo.getCodigo())
// .cantidadProductos(info.idsProductos.size())
// .idProducto(info.idsProductos.iterator().next())
// .idPedido(pedidosList.get(0))
// .idsPedidos(pedidosList)
// .ciudadOrigen(vuelo.getCodigoOrigen())
// .ciudadDestino(vuelo.getCodigoDestino())
// .codigoIATAOrigen(vuelo.getCodigoOrigen())
// .codigoIATADestino(vuelo.getCodigoDestino())
// .idAeropuertoOrigen(vuelo.getIdAeropuertoOrigen())
// .idAeropuertoDestino(vuelo.getIdAeropuertoDestino())
// .tiempoTransporteDias(info.horasTransporte / 24.0)
// .capacidadMaxima(vuelo.getCapacidadMaxima())
// .build();
// eventos.add(eventoSalida);

// // Calcular si este vuelo es destino final para ALGÚN pedido
// // Es destino final si el codigoDestino del vuelo coincide con el destino
// final
// // del pedido
// String destinoVuelo = vuelo.getCodigoDestino();
// boolean esDestinoFinal = pedidosList.stream()
// .anyMatch(idPedido -> {
// String destinoFinalPedido = pedidoDestinoFinal.get(idPedido);
// return destinoFinalPedido != null
// && destinoFinalPedido.equals(destinoVuelo);
// });

// // Evento de llegada
// EventoLineaDeTiempoVueloDTO eventoLlegada =
// EventoLineaDeTiempoVueloDTO.builder()
// .idEvento("ARR-" + contadorEventos)
// .tipoEvento("ARRIVAL")
// .horaEvento(info.horaLlegada)
// .idVuelo(vuelo.getId())
// .codigoVuelo(vuelo.getCodigo())
// .cantidadProductos(info.idsProductos.size())
// .idProducto(info.idsProductos.iterator().next())
// .idPedido(pedidosList.get(0))
// .idsPedidos(pedidosList)
// .ciudadOrigen(vuelo.getCodigoOrigen())
// .ciudadDestino(vuelo.getCodigoDestino())
// .codigoIATAOrigen(vuelo.getCodigoOrigen())
// .codigoIATADestino(vuelo.getCodigoDestino())
// .esDestinoFinal(esDestinoFinal)
// .idAeropuertoOrigen(vuelo.getIdAeropuertoOrigen())
// .idAeropuertoDestino(vuelo.getIdAeropuertoDestino())
// .tiempoTransporteDias(info.horasTransporte / 24.0)
// .capacidadMaxima(vuelo.getCapacidadMaxima())
// .build();
// eventos.add(eventoLlegada);

// contadorEventos++;
// }

// // Ordenar eventos por tiempo
// eventos.sort(Comparator.comparing(EventoLineaDeTiempoVueloDTO::getHoraEvento));

// // Calcular duración
// LocalDateTime horaFin = eventos.isEmpty() ? horaInicio
// : eventos.get(eventos.size() - 1).getHoraEvento();
// long duracionMinutos = ChronoUnit.MINUTES.between(horaInicio, horaFin);

// log.info("Timeline: {} eventos, {} vuelos, duración {} horas (~{} días)",
// eventos.size(), vuelosReales.size(),
// duracionMinutos / 60, duracionMinutos / (60 * 24));

// return LineaDeTiempoSimulacionDTO.builder()
// .horaInicioSimulacion(horaInicio)
// .horaFinSimulacion(horaFin)
// .duracionTotalMinutos(duracionMinutos)
// .eventos(eventos)
// .totalEventos(eventos.size())
// .rutasProductos(rutasProductos)
// .totalProductos(rutasProductos.size())
// .totalVuelos(vuelosReales.size())
// .totalAeropuertos(aeropuertosSet.size())
// .build();
// }
// }
