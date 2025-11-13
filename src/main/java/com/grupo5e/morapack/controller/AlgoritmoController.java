    package com.grupo5e.morapack.controller;

    import com.grupo5e.morapack.algorithm.alns.ALNSSolver;
    import com.grupo5e.morapack.api.dto.*;
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

    import java.time.LocalDateTime;
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
                @RequestBody(required = false) AlnsRequestDTO solicitud) {

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
                ALNSSolver solver = new ALNSSolver(iteraciones, horaInicioSimulacion, horaFinSimulacion);

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
                        producto.getPedido().getNombre() : "Pedido-" + producto.getPedido().getId())
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
                return ejecutarAlgoritmo(solicitud);

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
                return ejecutarAlgoritmo(solicitud);

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
        //CONTROLES PARA EL COLAPSO
        @Operation(
                summary = "Ejecutar simulación de colapso",
                description = "Ejecuta el algoritmo ALNS en modo colapso hasta detectar condiciones críticas " +
                        "(almacenes llenos, vuelos saturados, pedidos bloqueados, etc.)"
        )
        @ApiResponses(value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Simulación de colapso ejecutada exitosamente",
                        content = @Content(schema = @Schema(implementation = ResultadoColapsoDTO.class))
                ),
                @ApiResponse(
                        responseCode = "500",
                        description = "Error durante la simulación de colapso"
                )
        })
        @PostMapping("/colapso")
        public ResponseEntity<ResultadoColapsoDTO> ejecutarSimulacionColapso(
                @RequestBody(required = false) AlnsRequestDTO solicitud) {

            LocalDateTime horaInicio = LocalDateTime.now();
            log.info("🚨 EJECUTANDO SIMULACIÓN DE COLAPSO - Inicio: {}", horaInicio);

            try {
                // Configurar similar a otros endpoints
                Boolean usarBaseDatos = (solicitud != null && solicitud.getUsarBaseDatos() != null)
                        ? solicitud.getUsarBaseDatos() : true;

                String fuente = usarBaseDatos ? "BASE_DE_DATOS" : "ARCHIVO";
                System.setProperty("MODO_FUENTE_DATOS", fuente);

                int iteraciones = (solicitud != null && solicitud.getMaxIteraciones() != null)
                        ? solicitud.getMaxIteraciones() : 1000;

                com.grupo5e.morapack.algorithm.input.FabricaFuenteDatos.setSpringContext(applicationContext);

                LocalDateTime horaInicioSimulacion = (solicitud != null) ? solicitud.getHoraInicioSimulacion() : null;
                LocalDateTime horaFinSimulacion = (solicitud != null) ? solicitud.getHoraFinSimulacion() : null;

                log.info("Creando ALNSSolver para simulación de colapso con {} iteraciones máximas", iteraciones);
                ALNSSolver solver = new ALNSSolver(iteraciones, horaInicioSimulacion, horaFinSimulacion);

                // Ejecutar en modo colapso
                Map<String, Object> metricasColapso = solver.ejecutarModoColapso();

                // Obtener solución resultante
                Map<Producto, ArrayList<Vuelo>> solucionProductos = solver.obtenerSolucionNivelProducto();

                // Convertir a DTO de resultado
                ResultadoColapsoDTO resultado = convertirMetricasColapsoAResultado(
                        metricasColapso, solucionProductos, horaInicio);

                log.info("✅ Simulación de colapso completada en {} segundos",
                        resultado.getDuracionSegundos());
                log.info("Tipo de colapso: {}", resultado.getTipoColapso());

                return ResponseEntity.ok(resultado);

            } catch (Exception e) {
                log.error("❌ Error ejecutando simulación de colapso", e);

                ResultadoColapsoDTO resultadoError = ResultadoColapsoDTO.builder()
                        .exitoso(false)
                        .mensaje("Error durante la simulación de colapso: " + e.getMessage())
                        .horaInicio(horaInicio)
                        .horaFin(LocalDateTime.now())
                        .build();

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resultadoError);
            }
        }

        private ResultadoColapsoDTO convertirMetricasColapsoAResultado(
                Map<String, Object> metricasColapso,
                Map<Producto, ArrayList<Vuelo>> solucionProductos,
                LocalDateTime horaInicio) {

            LocalDateTime horaFin = (LocalDateTime) metricasColapso.getOrDefault("horaFin", LocalDateTime.now());
            long duracionSegundos = (Long) metricasColapso.getOrDefault("duracionSegundos", 0L);

            // OBTENER TIMELINE DE COLAPSO
            LineaDeTiempoSimulacionDTO timelineColapso =
                    (LineaDeTiempoSimulacionDTO) metricasColapso.get("timelineColapso");

            // Si no hay timeline, generar uno básico con la solución final
            if (timelineColapso == null) {
                log.warn("No se encontró timeline de colapso, generando uno básico...");
                timelineColapso = generarLineaDeTiempoBasica(solucionProductos, horaInicio);
            }

            // Convertir rutas de productos a DTO
            List<RutaProductoDTO> rutasProductos = new ArrayList<>();
            for (Map.Entry<Producto, ArrayList<Vuelo>> entry : solucionProductos.entrySet()) {
                Producto producto = entry.getKey();
                ArrayList<Vuelo> vuelos = entry.getValue();

                RutaProductoDTO rutaProducto = RutaProductoDTO.builder()
                        .idProducto(producto.getId())
                        .idPedido(producto.getPedido() != null ? producto.getPedido().getId() : null)
                        .nombreProducto(producto.getNombre() != null ? producto.getNombre() : "Producto-" + producto.getId())
                        .vuelos(convertirVuelosADTO(vuelos))
                        .cantidadVuelos(vuelos.size())
                        .build();

                rutasProductos.add(rutaProducto);
            }

            // Extraer condiciones de colapso
            List<String> condicionesColapso = (List<String>) metricasColapso.getOrDefault("condicionesColapso",
                    Collections.emptyList());
            List<String> bottlenecks = (List<String>) metricasColapso.getOrDefault("bottlenecks",
                    Collections.emptyList());

            return ResultadoColapsoDTO.builder()
                    .exitoso(true)
                    .mensaje("Simulación de colapso completada")
                    .horaInicio(horaInicio)
                    .horaFin(horaFin)
                    .duracionSegundos(duracionSegundos)
                    .iteracionesTotales((Integer) metricasColapso.getOrDefault("iteracionesTotales", 0))
                    .tipoColapso((String) metricasColapso.getOrDefault("tipoColapso", "DESCONOCIDO"))
                    .condicionesColapso(condicionesColapso)
                    .bottlenecks(bottlenecks)
                    .pedidosAsignados((Integer) metricasColapso.getOrDefault("pedidosAsignadosFinal", 0))
                    .pedidosTotales((Integer) metricasColapso.getOrDefault("pedidosTotales", 0))
                    .almacenesLlenos((Integer) metricasColapso.getOrDefault("almacenesLlenos", 0))
                    .vuelosSaturados((Integer) metricasColapso.getOrDefault("vuelosSaturados", 0))
                    .rutasProductos(rutasProductos)
                    .lineaDeTiempo(timelineColapso)  // ← ESTA ES LA CLAVE: timeline para el mapa
                    .metricasDetalladas(metricasColapso)
                    .build();
        }
        /**
         * Genera un timeline básico cuando no hay datos de colapso disponibles.
         * Crea eventos simples basados en las rutas de productos.
         */
        private LineaDeTiempoSimulacionDTO generarLineaDeTiempoBasica(
                Map<Producto, ArrayList<Vuelo>> solucionProductos,
                LocalDateTime horaInicio) {

            log.info("🔄 Generando timeline básico con {} productos", solucionProductos.size());

            List<EventoLineaDeTiempoVueloDTO> eventos = new ArrayList<>();
            Set<Integer> aeropuertosSet = new HashSet<>();

            int eventoId = 0;

            // Crear eventos básicos para cada producto y sus vuelos
            for (Map.Entry<Producto, ArrayList<Vuelo>> entry : solucionProductos.entrySet()) {
                Producto producto = entry.getKey();
                ArrayList<Vuelo> vuelos = entry.getValue();

                if (vuelos == null || vuelos.isEmpty()) {
                    continue;
                }

                LocalDateTime tiempoActual = horaInicio;

                for (int i = 0; i < vuelos.size(); i++) {
                    Vuelo vuelo = vuelos.get(i);

                    // Rastrear aeropuertos
                    if (vuelo.getAeropuertoOrigen() != null && vuelo.getAeropuertoOrigen().getId() != null) {
                        aeropuertosSet.add(vuelo.getAeropuertoOrigen().getId());
                    }
                    if (vuelo.getAeropuertoDestino() != null && vuelo.getAeropuertoDestino().getId() != null) {
                        aeropuertosSet.add(vuelo.getAeropuertoDestino().getId());
                    }

                    // Calcular tiempos de salida y llegada
                    LocalDateTime horaSalida;
                    LocalDateTime horaLlegada;

                    if (vuelo.getHoraSalida() != null && vuelo.getHoraLlegada() != null) {
                        // Usar horas programadas del vuelo
                        horaSalida = tiempoActual.toLocalDate().atTime(vuelo.getHoraSalida());
                        horaLlegada = tiempoActual.toLocalDate().atTime(vuelo.getHoraLlegada());

                        // Ajustar si cruza medianoche
                        if (vuelo.getHoraLlegada().isBefore(vuelo.getHoraSalida())) {
                            horaLlegada = horaLlegada.plusDays(1);
                        }
                    } else {
                        // Fallback: usar tiempo de transporte
                        long minutosTransporte = (long) (vuelo.getTiempoTransporte() * 60);
                        horaSalida = tiempoActual;
                        horaLlegada = horaSalida.plusMinutes(minutosTransporte);
                    }

                    // Evento de SALIDA
                    EventoLineaDeTiempoVueloDTO eventoSalida = EventoLineaDeTiempoVueloDTO.builder()
                            .idEvento("BASIC-DEP-" + eventoId++)
                            .tipoEvento("DEPARTURE")
                            .horaEvento(horaSalida)
                            .idVuelo(vuelo.getId())
                            .codigoVuelo(construirCodigoVuelo(vuelo))
                            .idProducto(producto.getId())
                            .idPedido(producto.getPedido() != null ? producto.getPedido().getId() : null)
                            .ciudadOrigen(vuelo.getAeropuertoOrigen() != null ?
                                    vuelo.getAeropuertoOrigen().getCiudad().getNombre() : "Desconocido")
                            .ciudadDestino(vuelo.getAeropuertoDestino() != null ?
                                    vuelo.getAeropuertoDestino().getCiudad().getNombre() : "Desconocido")
                            .idAeropuertoOrigen(vuelo.getAeropuertoOrigen() != null ?
                                    vuelo.getAeropuertoOrigen().getId() : null)
                            .idAeropuertoDestino(vuelo.getAeropuertoDestino() != null ?
                                    vuelo.getAeropuertoDestino().getId() : null)
                            .tiempoTransporteDias(vuelo.getTiempoTransporte() / 24.0)
                            .descripcion(construirDescripcionVuelo(vuelo, "salida"))
                            .build();

                    eventos.add(eventoSalida);

                    // Evento de LLEGADA
                    EventoLineaDeTiempoVueloDTO eventoLlegada = EventoLineaDeTiempoVueloDTO.builder()
                            .idEvento("BASIC-ARR-" + eventoId++)
                            .tipoEvento("ARRIVAL")
                            .horaEvento(horaLlegada)
                            .idVuelo(vuelo.getId())
                            .codigoVuelo(construirCodigoVuelo(vuelo))
                            .idProducto(producto.getId())
                            .idPedido(producto.getPedido() != null ? producto.getPedido().getId() : null)
                            .ciudadOrigen(vuelo.getAeropuertoOrigen() != null ?
                                    vuelo.getAeropuertoOrigen().getCiudad().getNombre() : "Desconocido")
                            .ciudadDestino(vuelo.getAeropuertoDestino() != null ?
                                    vuelo.getAeropuertoDestino().getCiudad().getNombre() : "Desconocido")
                            .idAeropuertoOrigen(vuelo.getAeropuertoOrigen() != null ?
                                    vuelo.getAeropuertoOrigen().getId() : null)
                            .idAeropuertoDestino(vuelo.getAeropuertoDestino() != null ?
                                    vuelo.getAeropuertoDestino().getId() : null)
                            .tiempoTransporteDias(vuelo.getTiempoTransporte() / 24.0)
                            .descripcion(construirDescripcionVuelo(vuelo, "llegada"))
                            .build();

                    eventos.add(eventoLlegada);

                    // Actualizar tiempo para el próximo vuelo (incluir tiempo de escala)
                    tiempoActual = horaLlegada.plusMinutes(60); // 1 hora de escala
                }
            }

            // Ordenar eventos por tiempo
            eventos.sort(Comparator.comparing(EventoLineaDeTiempoVueloDTO::getHoraEvento));

            // Calcular hora de fin
            LocalDateTime horaFin = eventos.isEmpty() ?
                    horaInicio.plusHours(24) : // Fallback: 24 horas después
                    eventos.get(eventos.size() - 1).getHoraEvento();

            long duracionMinutos = ChronoUnit.MINUTES.between(horaInicio, horaFin);

            log.info("✅ Timeline básico generado: {} eventos, {} aeropuertos, duración: {} minutos",
                    eventos.size(), aeropuertosSet.size(), duracionMinutos);

            return LineaDeTiempoSimulacionDTO.builder()
                    .horaInicioSimulacion(horaInicio)
                    .horaFinSimulacion(horaFin)
                    .duracionTotalMinutos(duracionMinutos)
                    .eventos(eventos)
                    .totalEventos(eventos.size())
                    .totalAeropuertos(aeropuertosSet.size())
                    .build();
        }

        /**
         * Construye un código de vuelo legible a partir de un objeto Vuelo
         */
        private String construirCodigoVuelo(Vuelo vuelo) {
            if (vuelo.getAeropuertoOrigen() == null || vuelo.getAeropuertoDestino() == null) {
                return "VUELO-" + vuelo.getId();
            }

            String origen = vuelo.getAeropuertoOrigen().getCodigoIATA();
            String destino = vuelo.getAeropuertoDestino().getCodigoIATA();

            return "VL" + origen + "-" + destino + "-" + vuelo.getId();
        }

        /**
         * Construye una descripción legible para el evento del vuelo
         */
        private String construirDescripcionVuelo(Vuelo vuelo, String tipo) {
            String origen = vuelo.getAeropuertoOrigen() != null ?
                    vuelo.getAeropuertoOrigen().getCiudad().getNombre() : "Origen desconocido";
            String destino = vuelo.getAeropuertoDestino() != null ?
                    vuelo.getAeropuertoDestino().getCiudad().getNombre() : "Destino desconocido";

            if ("salida".equals(tipo)) {
                return String.format("Salida de %s hacia %s (%.1f horas)",
                        origen, destino, vuelo.getTiempoTransporte());
            } else {
                return String.format("Llegada a %s desde %s", destino, origen);
            }
        }
        //FIN DE LOS CONTROLES DEL COLAPSO
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
                    .codigoVuelo(vuelo.getCodigo() + " (" + grupo.idsProductos.size() + " pkgs)")
                    .idProducto(grupo.idsProductos.get(0)) // Producto representativo
                    .idPedido(grupo.idsPedidos.get(0))
                    .ciudadOrigen(vuelo.getCodigoOrigen())
                    .ciudadDestino(vuelo.getCodigoDestino())
                    .idAeropuertoOrigen(vuelo.getIdAeropuertoOrigen())
                    .idAeropuertoDestino(vuelo.getIdAeropuertoDestino())
                    .tiempoTransporteDias(vuelo.getTiempoTransporte() != null ?
                        vuelo.getTiempoTransporte() / 24.0 : 0.0)
                    .build();

                eventos.add(eventoSalida);

                // Evento de llegada
                EventoLineaDeTiempoVueloDTO eventoLlegada = EventoLineaDeTiempoVueloDTO.builder()
                    .idEvento("ARR-GROUP-" + contadorEventos)
                    .tipoEvento("ARRIVAL")
                    .horaEvento(grupo.horaLlegada)
                    .idVuelo(vuelo.getId())
                    .codigoVuelo(vuelo.getCodigo() + " (" + grupo.idsProductos.size() + " pkgs)")
                    .idProducto(grupo.idsProductos.get(0))
                    .idPedido(grupo.idsPedidos.get(0))
                    .ciudadOrigen(vuelo.getCodigoOrigen())
                    .ciudadDestino(vuelo.getCodigoDestino())
                    .idAeropuertoOrigen(vuelo.getIdAeropuertoOrigen())
                    .idAeropuertoDestino(vuelo.getIdAeropuertoDestino())
                    .tiempoTransporteDias(vuelo.getTiempoTransporte() != null ?
                        vuelo.getTiempoTransporte() / 24.0 : 0.0)
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
    }

