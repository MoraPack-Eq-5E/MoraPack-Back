package com.grupo5e.morapack.service;

import com.grupo5e.morapack.api.dto.FileValidationResult;
import com.grupo5e.morapack.core.enums.Continente;
import com.grupo5e.morapack.core.enums.EstadoAeropuerto;
import com.grupo5e.morapack.core.model.Aeropuerto;
import com.grupo5e.morapack.core.model.Ciudad;
import com.grupo5e.morapack.core.model.Pedido;
import com.grupo5e.morapack.core.model.Vuelo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio para parsear archivos de simulación desde bytes
 */
@Service
@Slf4j
public class FileParsingService {
    
    private final AeropuertoService aeropuertoService;
    
    public FileParsingService(AeropuertoService aeropuertoService) {
        this.aeropuertoService = aeropuertoService;
    }
    
    /**
     * Valida y parsea archivo de aeropuertos
     */
    public FileValidationResult validateAndParseAeropuertos(byte[] content) {
        FileValidationResult result = FileValidationResult.builder()
                .fileType("AEROPUERTOS")
                .success(true)
                .build();
        
        try {
            List<Aeropuerto> aeropuertos = parseAeropuertosFromBytes(content);
            
            if (aeropuertos.isEmpty()) {
                result.addError("No se encontraron aeropuertos válidos en el archivo");
                return result;
            }
            
            result.setParsedCount(aeropuertos.size());
            result.setInfo(String.format("Se encontraron %d aeropuertos", aeropuertos.size()));
            result.setParsedAeropuertos(aeropuertos); // ✅ GUARDAR LOS DATOS PARSEADOS
            
            log.info("✅ Aeropuertos parseados: {}", aeropuertos.size());
            
        } catch (Exception e) {
            result.addError("Error al parsear archivo: " + e.getMessage());
            log.error("Error parseando aeropuertos", e);
        }
        
        return result;
    }
    
    /**
     * Valida y parsea archivo de vuelos
     */
    public FileValidationResult validateAndParseVuelos(byte[] content, List<Aeropuerto> aeropuertosFromFile) {
        FileValidationResult result = FileValidationResult.builder()
                .fileType("VUELOS")
                .success(true)
                .build();
        
        try {
            // Si no hay archivo de aeropuertos, usar BD
            List<Aeropuerto> aeropuertos;
            if (aeropuertosFromFile != null && !aeropuertosFromFile.isEmpty()) {
                aeropuertos = aeropuertosFromFile;
                result.addWarning("Se usarán los aeropuertos subidos previamente");
            } else {
                aeropuertos = aeropuertoService.listar();
                result.addWarning("Se usarán los aeropuertos de la base de datos");
            }
            
            List<Vuelo> vuelos = parseVuelosFromBytes(content, aeropuertos);
            
            if (vuelos.isEmpty()) {
                result.addError("No se encontraron vuelos válidos en el archivo");
                return result;
            }
            
            result.setParsedCount(vuelos.size());
            result.setInfo(String.format("Se encontraron %d vuelos", vuelos.size()));
            result.setParsedVuelos(vuelos); // ✅ GUARDAR LOS DATOS PARSEADOS
            
            log.info("✅ Vuelos parseados: {}", vuelos.size());
            
        } catch (Exception e) {
            result.addError("Error al parsear archivo: " + e.getMessage());
            log.error("Error parseando vuelos", e);
        }
        
        return result;
    }
    
    /**
     * Valida y parsea archivo de pedidos
     */
    public FileValidationResult validateAndParsePedidos(byte[] content, List<Aeropuerto> aeropuertosFromFile) {
        FileValidationResult result = FileValidationResult.builder()
                .fileType("PEDIDOS")
                .success(true)
                .build();
        
        try {
            // Si no hay archivo de aeropuertos, usar BD
            List<Aeropuerto> aeropuertos;
            if (aeropuertosFromFile != null && !aeropuertosFromFile.isEmpty()) {
                aeropuertos = aeropuertosFromFile;
                result.addWarning("Se usarán los aeropuertos subidos previamente");
            } else {
                aeropuertos = aeropuertoService.listar();
                result.addWarning("Se usarán los aeropuertos de la base de datos");
            }
            
            List<Pedido> pedidos = parsePedidosFromBytes(content, aeropuertos);
            
            if (pedidos.isEmpty()) {
                result.addError("No se encontraron pedidos válidos en el archivo");
                return result;
            }
            
            result.setParsedCount(pedidos.size());
            result.setInfo(String.format("Se encontraron %d pedidos", pedidos.size()));
            result.setParsedPedidos(pedidos); // ✅ GUARDAR LOS DATOS PARSEADOS
            
            log.info("✅ Pedidos parseados: {}", pedidos.size());
            
        } catch (Exception e) {
            result.addError("Error al parsear archivo: " + e.getMessage());
            log.error("Error parseando pedidos", e);
        }
        
        return result;
    }
    
    /**
     * Parsea aeropuertos desde bytes (adaptado de LectorAeropuerto)
     */
    public List<Aeropuerto> parseAeropuertosFromBytes(byte[] content) throws Exception {
        List<Aeropuerto> aeropuertos = new ArrayList<>();
        Map<String, Ciudad> mapaCiudades = new HashMap<>();
        Continente continenteActual = null;
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8))) {
            
            String linea;
            int lineNumber = 0;
            
            // Saltar las primeras dos líneas (header)
            reader.readLine();
            reader.readLine();
            lineNumber = 2;
            
            while ((linea = reader.readLine()) != null) {
                lineNumber++;
                
                if (linea.trim().isEmpty()) {
                    continue;
                }
                
                // Verificar si es una línea de header de continente
                if (linea.contains("America") || linea.contains("Europa") || linea.contains("Asia")) {
                    if (linea.contains("America")) {
                        continenteActual = Continente.AMERICA;
                    } else if (linea.contains("Europa")) {
                        continenteActual = Continente.EUROPA;
                    } else if (linea.contains("Asia")) {
                        continenteActual = Continente.ASIA;
                    }
                    continue;
                }
                
                // Parsear datos del aeropuerto
                String[] partes = linea.trim().split("\\s+");
                if (partes.length >= 7) {
                    try {
                        String codigoIATA = partes[1];
                        
                        // Extraer nombre de la ciudad
                        int finNombreCiudad = 3;
                        while (finNombreCiudad < partes.length && 
                               !partes[finNombreCiudad].contains("GMT") && 
                               !Character.isDigit(partes[finNombreCiudad].charAt(0))) {
                            finNombreCiudad++;
                        }
                        
                        StringBuilder constructorNombreCiudad = new StringBuilder(partes[2]);
                        for (int i = 3; i < finNombreCiudad; i++) {
                            constructorNombreCiudad.append(" ").append(partes[i]);
                        }
                        String nombreCiudad = constructorNombreCiudad.toString();
                        
                        String nombrePais = partes[finNombreCiudad];
                        int capacidadMaxima = Integer.parseInt(partes[6]);
                        
                        // Extraer coordenadas
                        String latitudStr = "";
                        String longitudStr = "";
                        int indiceLat = linea.indexOf("Latitude:");
                        int indiceLong = linea.indexOf("Longitude:");
                        
                        if (indiceLat != -1 && indiceLong != -1) {
                            latitudStr = linea.substring(indiceLat + 10, indiceLong).trim();
                            longitudStr = linea.substring(indiceLong + 11).trim();
                            latitudStr = latitudStr.replaceAll("[°'\"NSEW]", "").trim();
                            longitudStr = longitudStr.replaceAll("[°'\"NSEW]", "").trim();
                        }
                        
                        // Crear Ciudad
                        String claveCiudad = nombreCiudad + "-" + nombrePais;
                        Ciudad ciudad = mapaCiudades.get(claveCiudad);
                        if (ciudad == null) {
                            ciudad = new Ciudad();
                            ciudad.setNombre(nombreCiudad);
                            ciudad.setContinente(continenteActual);
                            mapaCiudades.put(claveCiudad, ciudad);
                        }
                        
                        // Crear Aeropuerto
                        Aeropuerto aeropuerto = new Aeropuerto();
                        aeropuerto.setCodigoIATA(codigoIATA);
                        aeropuerto.setZonaHorariaUTC(0);
                        aeropuerto.setLatitud(latitudStr);
                        aeropuerto.setLongitud(longitudStr);
                        aeropuerto.setCapacidadActual(0);
                        aeropuerto.setCapacidadMaxima(capacidadMaxima);
                        aeropuerto.setCiudad(ciudad);
                        aeropuerto.setEstado(EstadoAeropuerto.DISPONIBLE);
                        
                        aeropuertos.add(aeropuerto);
                        
                    } catch (Exception e) {
                        log.warn("Error parseando línea {}: {}", lineNumber, e.getMessage());
                    }
                }
            }
        }
        
        return aeropuertos;
    }
    
    /**
     * Parsea vuelos desde bytes (adaptado de LectorVuelos)
     */
    public List<Vuelo> parseVuelosFromBytes(byte[] content, List<Aeropuerto> aeropuertos) throws Exception {
        List<Vuelo> vuelos = new ArrayList<>();
        Map<String, Aeropuerto> mapaAeropuertos = createAeropuertosMap(aeropuertos);
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8))) {
            
            String linea;
            int lineNumber = 0;
            
            while ((linea = reader.readLine()) != null) {
                lineNumber++;
                
                if (linea.trim().isEmpty()) {
                    continue;
                }
                
                // Formato: ORIGEN-DESTINO-SALIDA-LLEGADA-CAPACIDAD
                String[] partes = linea.split("-");
                if (partes.length == 5) {
                    try {
                        String codigoOrigen = partes[0].trim();
                        String codigoDestino = partes[1].trim();
                        String horaSalida = partes[2].trim();
                        String horaLlegada = partes[3].trim();
                        int capacidadMaxima = Integer.parseInt(partes[4].trim());
                        
                        Aeropuerto aeropuertoOrigen = mapaAeropuertos.get(codigoOrigen);
                        Aeropuerto aeropuertoDestino = mapaAeropuertos.get(codigoDestino);
                        
                        if (aeropuertoOrigen == null) {
                            log.warn("Línea {}: Aeropuerto origen no encontrado: {}", lineNumber, codigoOrigen);
                            continue;
                        }
                        
                        if (aeropuertoDestino == null) {
                            log.warn("Línea {}: Aeropuerto destino no encontrado: {}", lineNumber, codigoDestino);
                            continue;
                        }
                        
                        LocalTime horaSalidaParsed = parsearHora(horaSalida);
                        LocalTime horaLlegadaParsed = parsearHora(horaLlegada);
                        double tiempoTransporte = calcularTiempoTransporte(horaSalidaParsed, horaLlegadaParsed);
                        
                        Vuelo vuelo = new Vuelo();
                        vuelo.setFrecuenciaPorDia(1.0);
                        vuelo.setAeropuertoOrigen(aeropuertoOrigen);
                        vuelo.setAeropuertoDestino(aeropuertoDestino);
                        vuelo.setCapacidadMaxima(capacidadMaxima);
                        vuelo.setCapacidadUsada(0);
                        vuelo.setTiempoTransporte(tiempoTransporte);
                        vuelo.setCosto(100.0); // Costo por defecto
                        vuelo.setHoraSalida(horaSalidaParsed);
                        vuelo.setHoraLlegada(horaLlegadaParsed);
                        
                        vuelos.add(vuelo);
                        
                    } catch (Exception e) {
                        log.warn("Error parseando línea {}: {}", lineNumber, e.getMessage());
                    }
                }
            }
        }
        
        return vuelos;
    }
    
    /**
     * Parsea pedidos desde bytes (adaptado de LectorPedidos)
     */
    public List<Pedido> parsePedidosFromBytes(byte[] content, List<Aeropuerto> aeropuertos) throws Exception {
        List<Pedido> pedidos = new ArrayList<>();
        Map<String, Aeropuerto> mapaAeropuertos = createAeropuertosMap(aeropuertos);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8))) {

            String linea;
            int lineNumber = 0;

            while ((linea = reader.readLine()) != null) {
                lineNumber++;

                if (linea.trim().isEmpty()) {
                    continue;
                }

                // Nuevo formato: id_pedido-aaaammdd-hh-mm-dest-###-idCliente
                String[] partes = linea.trim().split("-");
                if (partes.length == 7) {
                    try {
                        String idPedidoStr = partes[0].trim(); // no usado internamente, puede almacenarse si existe campo
                        String fechaStr = partes[1].trim();    // yyyyMMdd
                        String horaStr = partes[2].trim();     // HH
                        String minutoStr = partes[3].trim();   // mm
                        String codigoAeropuertoDestino = partes[4].trim().toUpperCase();
                        String cantidadStr = partes[5].trim(); // ### como cadena
                        String idClienteStr = partes[6].trim(); // 7 dígitos

                        // Validaciones básicas
                        if (fechaStr.length() != 8) {
                            log.warn("Línea {}: Fecha con formato incorrecto: {}", lineNumber, fechaStr);
                            continue;
                        }
                        if (horaStr.length() != 2 || minutoStr.length() != 2) {
                            log.warn("Línea {}: Hora/minuto con formato incorrecto: {}:{}", lineNumber, horaStr, minutoStr);
                            continue;
                        }
                        if (cantidadStr.length() != 3) {
                            log.warn("Línea {}: Cantidad con formato incorrecto: {}", lineNumber, cantidadStr);
                            continue;
                        }
                        if (idClienteStr.length() != 7) {
                            log.warn("Línea {}: IdCliente con formato incorrecto: {}", lineNumber, idClienteStr);
                            continue;
                        }

                        // Parsear aeropuerto destino
                        Aeropuerto aeropuertoDestino = mapaAeropuertos.get(codigoAeropuertoDestino);
                        if (aeropuertoDestino == null) {
                            log.warn("Línea {}: Aeropuerto destino no encontrado: {}", lineNumber, codigoAeropuertoDestino);
                            continue;
                        }

                        // Parsear cantidad e idCliente
                        int cantidadProductos = Integer.parseInt(cantidadStr);
                        long idCliente = Long.parseLong(idClienteStr);

                        // Parsear fecha y hora
                        java.time.format.DateTimeFormatter df = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd");
                        java.time.LocalDate fecha = java.time.LocalDate.parse(fechaStr, df);
                        int hora = Integer.parseInt(horaStr);
                        int minuto = Integer.parseInt(minutoStr);
                        if (hora < 0 || hora > 23 || minuto < 0 || minuto > 59) {
                            log.warn("Línea {}: Hora/minuto fuera de rango: {}:{}", lineNumber, hora, minuto);
                            continue;
                        }
                        java.time.LocalDateTime fechaPedido = java.time.LocalDateTime.of(fecha, java.time.LocalTime.of(hora, minuto));
                        // Usar 7 días por defecto para plazo de entrega (puede ajustarse según reglas)
                        java.time.LocalDateTime plazoEntrega = calcularPlazoEntrega(7, fechaPedido);

                        // Crear cliente
                        com.grupo5e.morapack.core.model.Cliente cliente = new com.grupo5e.morapack.core.model.Cliente();
                        cliente.setId(idCliente);
                        cliente.setNombres("Cliente " + idCliente);
                        cliente.setCorreo("cliente" + idCliente + "@morapack.com");
                        cliente.setCiudadRecojo(aeropuertoDestino.getCiudad());

                        // Crear pedido
                        Pedido pedido = new Pedido();
                        pedido.setId(Long.parseLong(idPedidoStr));
                        pedido.setCliente(cliente);
                        pedido.setAeropuertoDestinoCodigo(aeropuertoDestino.getCodigoIATA());
                        pedido.setFechaPedido(fechaPedido);
                        pedido.setFechaLimiteEntrega(plazoEntrega);
                        pedido.setEstado(com.grupo5e.morapack.core.enums.EstadoPedido.PENDIENTE);

                        // Origen: almacén aleatorio en el mismo continente si es posible
                        Aeropuerto aeropuertoOrigen = obtenerAeropuertoAlmacenAleatorio(
                                aeropuertoDestino.getCiudad().getContinente(),
                                mapaAeropuertos
                        );
                        pedido.setAeropuertoOrigenCodigo(aeropuertoOrigen.getCodigoIATA());

                        // Prioridad calculada
                        double prioridad = calcularPrioridad(fechaPedido, plazoEntrega);
                        pedido.setPrioridad(prioridad);

                        // Productos
                        List<com.grupo5e.morapack.core.model.Producto> productos = crearProductos(cantidadProductos, pedido);
                        pedido.setProductos(productos);

                        pedidos.add(pedido);

                    } catch (Exception e) {
                        log.warn("Error parseando línea {}: {}", lineNumber, e.getMessage());
                    }
                } else {
                    log.warn("Línea {}: Formato inválido, se esperaban 7 partes separadas por '-': {}", lineNumber, linea);
                }
            }
        }

        return pedidos;
    }


    private Map<String, Aeropuerto> createAeropuertosMap(List<Aeropuerto> aeropuertos) {
        Map<String, Aeropuerto> mapa = new HashMap<>();
        for (Aeropuerto aeropuerto : aeropuertos) {
            if (aeropuerto.getCodigoIATA() != null) {
                mapa.put(aeropuerto.getCodigoIATA().trim().toUpperCase(), aeropuerto);
            }
        }
        return mapa;
    }
    
    private LocalTime parsearHora(String horaStr) {
        int horas = Integer.parseInt(horaStr.substring(0, 2));
        int minutos = Integer.parseInt(horaStr.substring(3, 5));
        return LocalTime.of(horas, minutos);
    }
    
    private double calcularTiempoTransporte(LocalTime salida, LocalTime llegada) {
        long minutos;
        if (llegada.isBefore(salida)) {
            // Vuelo cruza medianoche
            minutos = Duration.between(salida, LocalTime.of(23, 59, 59)).toMinutes() + 
                     Duration.between(LocalTime.of(0, 0), llegada).toMinutes() + 1;
        } else {
            minutos = Duration.between(salida, llegada).toMinutes();
        }
        return minutos / 60.0;
    }
    
    /**
     * Crea un pedido completo con todos sus campos (adaptado de LectorPedidos)
     */
    private Pedido crearPedidoCompleto(Long idCliente, int diasPrioridad, int hora, int minuto,
                                       Aeropuerto aeropuertoDestino, int cantidadProductos,
                                       Map<String, Aeropuerto> mapaAeropuertos) {
        // Crear cliente
        com.grupo5e.morapack.core.model.Cliente cliente = new com.grupo5e.morapack.core.model.Cliente();
        cliente.setId(idCliente);
        cliente.setNombres("Cliente " + idCliente);
        cliente.setCorreo("cliente" + idCliente + "@ejemplo.com");
        cliente.setCiudadRecojo(aeropuertoDestino.getCiudad());
        
        // Calcular fechas
        java.time.LocalDateTime fechaPedido = calcularFechaPedido(hora, minuto);
        java.time.LocalDateTime plazoEntrega = calcularPlazoEntrega(diasPrioridad, fechaPedido);
        
        // Crear pedido
        Pedido pedido = new Pedido();
        pedido.setCliente(cliente);
        pedido.setAeropuertoDestinoCodigo(aeropuertoDestino.getCodigoIATA());
        pedido.setFechaPedido(fechaPedido);
        pedido.setFechaLimiteEntrega(plazoEntrega);
        pedido.setEstado(com.grupo5e.morapack.core.enums.EstadoPedido.PENDIENTE);
        
        // Establecer aeropuerto origen (almacén inicial aleatorio)
        Aeropuerto aeropuertoOrigen = obtenerAeropuertoAlmacenAleatorio(
            aeropuertoDestino.getCiudad().getContinente(),
            mapaAeropuertos
        );
        pedido.setAeropuertoOrigenCodigo(aeropuertoOrigen.getCodigoIATA());
        
        // Calcular y establecer prioridad
        double prioridad = calcularPrioridad(fechaPedido, plazoEntrega);
        pedido.setPrioridad(prioridad);
        
        // Crear productos
        List<com.grupo5e.morapack.core.model.Producto> productos = crearProductos(cantidadProductos, pedido);
        pedido.setProductos(productos);
        
        return pedido;
    }
    
    private java.time.LocalDateTime calcularFechaPedido(int hora, int minuto) {
        java.time.LocalDateTime ahora = java.time.LocalDateTime.now();
        java.time.LocalDateTime fechaPedido = ahora.withHour(hora).withMinute(minuto).withSecond(0).withNano(0);
        
        if (fechaPedido.isBefore(ahora)) {
            fechaPedido = fechaPedido.plusDays(1);
        }
        
        return fechaPedido;
    }
    
    private java.time.LocalDateTime calcularPlazoEntrega(int diasPrioridad, java.time.LocalDateTime fechaPedido) {
        switch (diasPrioridad) {
            case 1:  return fechaPedido.plus(1, java.time.temporal.ChronoUnit.DAYS);
            case 4:  return fechaPedido.plus(4, java.time.temporal.ChronoUnit.DAYS);
            case 12: return fechaPedido.plus(12, java.time.temporal.ChronoUnit.DAYS);
            case 24: return fechaPedido.plus(24, java.time.temporal.ChronoUnit.DAYS);
            default: return fechaPedido.plus(7, java.time.temporal.ChronoUnit.DAYS);
        }
    }
    
    private Aeropuerto obtenerAeropuertoAlmacenAleatorio(Continente continente, Map<String, Aeropuerto> mapaAeropuertos) {
        // Filtrar aeropuertos por continente
        List<Aeropuerto> aeropuertosContinente = mapaAeropuertos.values().stream()
            .filter(a -> a.getCiudad() != null && a.getCiudad().getContinente() == continente)
            .collect(java.util.stream.Collectors.toList());
        
        if (aeropuertosContinente.isEmpty()) {
            // Si no hay aeropuertos en ese continente, usar cualquiera
            return new ArrayList<>(mapaAeropuertos.values()).get(0);
        }
        
        // Seleccionar aleatoriamente
        java.util.Random random = new java.util.Random();
        return aeropuertosContinente.get(random.nextInt(aeropuertosContinente.size()));
    }
    
    private double calcularPrioridad(java.time.LocalDateTime fechaPedido, java.time.LocalDateTime plazoEntrega) {
        long horasDisponibles = java.time.Duration.between(fechaPedido, plazoEntrega).toHours();
        if (horasDisponibles <= 0) {
            return 100.0; // Máxima prioridad
        }
        return Math.max(1.0, 100.0 / horasDisponibles);
    }
    
    private List<com.grupo5e.morapack.core.model.Producto> crearProductos(int cantidad, Pedido pedido) {
        List<com.grupo5e.morapack.core.model.Producto> productos = new ArrayList<>();
        
        for (int i = 0; i < cantidad; i++) {
            com.grupo5e.morapack.core.model.Producto producto = new com.grupo5e.morapack.core.model.Producto();
            producto.setPedido(pedido);
            producto.setEstado(com.grupo5e.morapack.core.enums.EstadoProducto.EN_ALMACEN);
            productos.add(producto);
        }
        
        return productos;
    }
}

