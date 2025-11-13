package com.grupo5e.morapack.utils;

import com.grupo5e.morapack.core.enums.EstadoProducto;
import com.grupo5e.morapack.core.enums.EstadoPedido;
import com.grupo5e.morapack.core.enums.Rol;
import com.grupo5e.morapack.core.model.*;
import com.grupo5e.morapack.service.ClienteService;
import com.grupo5e.morapack.service.PedidoService;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lector de Pedidos V2 - Formato del MoraPack-Backend
 * 
 * Lee archivos con patrón: _pedidos_{AIRPORT}_.txt
 * Formato de línea: id_pedido-aaaammdd-hh-mm-dest-###-IdClien
 * Ejemplo: 000000001-20250102-01-18-SPIM-003-0027081
 * 
 * Diferencias con V1:
 * - 7 campos separados por guiones (no 6 con TAB)
 * - Múltiples archivos por aeropuerto de origen
 * - Fechas completas (aaaammdd) no días de prioridad
 * - ID de pedido explícito en el archivo
 */
public class LectorPedidosV2 {
    private final String directorioDatos;
    private final ArrayList<Aeropuerto> aeropuertos;
    private final Map<String, Aeropuerto> mapaAeropuertos;

    // Servicios necesarios
    private final PedidoService pedidoService;
    private final ClienteService clienteService;

    // Caché de clientes para evitar búsquedas repetidas
    private final Map<String, Cliente> cacheClientes = new HashMap<>();

    public LectorPedidosV2(String directorioDatos,
                          ArrayList<Aeropuerto> aeropuertos,
                          PedidoService pedidoService,
                          ClienteService clienteService) {
        this.directorioDatos = directorioDatos;
        this.aeropuertos = aeropuertos;
        this.pedidoService = pedidoService;
        this.clienteService = clienteService;
        this.mapaAeropuertos = crearMapaAeropuertos();
    }

    private Map<String, Aeropuerto> crearMapaAeropuertos() {
        Map<String, Aeropuerto> mapa = new HashMap<>();
        for (Aeropuerto a : aeropuertos) {
            if (a.getCodigoIATA() != null) {
                mapa.put(a.getCodigoIATA().trim().toUpperCase(), a);
            }
        }
        return mapa;
    }

    /**
     * Lee y guarda pedidos desde todos los archivos _pedidos_{AIRPORT}_
     * 
     * @param horaInicioSimulacion Opcional: solo cargar pedidos después de esta hora
     * @param horaFinSimulacion Opcional: solo cargar pedidos antes de esta hora
     * @return Resultado con estadísticas de la carga
     */
    public ResultadoCargaPedidos leerYGuardarPedidos(
            LocalDateTime horaInicioSimulacion,
            LocalDateTime horaFinSimulacion,
            ModoSimulacion modoSimulacion) {
        
        ResultadoCargaPedidos resultado = new ResultadoCargaPedidos();
        File directorio = new File(directorioDatos);

        if (!directorio.exists() || !directorio.isDirectory()) {
            resultado.exito = false;
            resultado.mensajeError = "Directorio no encontrado: " + directorioDatos;
            System.err.println("ERROR: " + resultado.mensajeError);
            return resultado;
        }

        // Buscar todos los archivos con patrón _pedidos_{AIRPORT}_ o _pedidos_{AIRPORT}_.txt
        File[] archivosPedidos = directorio.listFiles((dir, nombre) ->
                nombre.startsWith("_pedidos_") && 
                (nombre.endsWith("_") || nombre.endsWith("_.txt") || nombre.endsWith(".txt"))
        );

        if (archivosPedidos == null || archivosPedidos.length == 0) {
            resultado.exito = false;
            resultado.mensajeError = "No se encontraron archivos con patrón _pedidos_{AIRPORT}_";
            System.err.println("WARNING: " + resultado.mensajeError);
            return resultado;
        }

        System.out.println("========================================");
        System.out.println("CARGANDO PEDIDOS DESDE ARCHIVOS V2");
        System.out.println("Directorio: " + directorioDatos);
        System.out.println("Archivos encontrados: " + archivosPedidos.length);
        if (horaInicioSimulacion != null && horaFinSimulacion != null) {
            System.out.println("Ventana de tiempo: " + horaInicioSimulacion + " a " + horaFinSimulacion);
        } else {
            System.out.println("Ventana de tiempo: TODOS LOS PEDIDOS (sin filtrado)");
        }
        System.out.println("========================================");

        LocalDateTime tiempoInicio = LocalDateTime.now();

        // Procesar cada archivo
        for (File archivo : archivosPedidos) {
            String nombreArchivo = archivo.getName();
            // Extraer código de aeropuerto del nombre
            // Ejemplo: _pedidos_SPIM_ -> SPIM o _pedidos_EBCI_.txt -> EBCI
            String codigoAeropuertoOrigen = nombreArchivo
                    .replace("_pedidos_", "")
                    .replace(".txt", "")
                    .replace("_", "")
                    .trim()
                    .toUpperCase();

            Aeropuerto aeropuertoOrigen = mapaAeropuertos.get(codigoAeropuertoOrigen);
            if (aeropuertoOrigen == null) {
                System.err.println("WARNING: Aeropuerto origen desconocido: " + codigoAeropuertoOrigen + " en " + nombreArchivo);
                resultado.erroresArchivos++;
                continue;
            }

            System.out.println("\nProcesando archivo: " + nombreArchivo + " (origen: " + codigoAeropuertoOrigen + ")");

            try {
                procesarArchivoPedidos(archivo, aeropuertoOrigen, horaInicioSimulacion, horaFinSimulacion, resultado,
                        modoSimulacion);
            } catch (Exception e) {
                System.err.println("ERROR procesando archivo " + nombreArchivo + ": " + e.getMessage());
                e.printStackTrace();
                resultado.erroresArchivos++;
            }
        }

        resultado.tiempoFin = LocalDateTime.now();
        resultado.duracionSegundos = ChronoUnit.SECONDS.between(tiempoInicio, resultado.tiempoFin);
        resultado.exito = resultado.erroresArchivos == 0 && resultado.pedidosCargados > 0;

        System.out.println("\n========================================");
        System.out.println("RESUMEN DE CARGA DE PEDIDOS");
        System.out.println("Total de pedidos cargados: " + resultado.pedidosCargados);
        System.out.println("Total de pedidos creados: " + resultado.pedidosCreados);
        System.out.println("Pedidos filtrados (fuera de ventana): " + resultado.pedidosFiltrados);
        System.out.println("Errores de parseo: " + resultado.erroresParseo);
        System.out.println("Errores de archivos: " + resultado.erroresArchivos);
        System.out.println("Duración: " + resultado.duracionSegundos + " segundos");
        System.out.println("========================================");

        return resultado;
    }

    private void procesarArchivoPedidos(
            File archivo,
            Aeropuerto aeropuertoOrigen,
            LocalDateTime horaInicio,
            LocalDateTime horaFin,
            ResultadoCargaPedidos resultado,
            ModoSimulacion modoSimulacion) throws IOException {

        try (BufferedReader reader = new BufferedReader(new FileReader(archivo))) {
            String linea;
            int numeroLinea = 0;
            List<Pedido> pedidosPorCrear = new ArrayList<>();

            while ((linea = reader.readLine()) != null) {
                numeroLinea++;
                linea = linea.trim();

                if (linea.isEmpty()) {
                    continue;
                }

                try {
                    Pedido pedido = parsearLineaPedido(linea, aeropuertoOrigen);
                    resultado.pedidosCargados++;

                    // 👇 Solo filtrar si NO es modo COLAPSO
                    if (modoSimulacion != ModoSimulacion.COLAPSO &&
                            horaInicio != null && horaFin != null) {
                        if (pedido.getFechaPedido().isBefore(horaInicio) ||
                                pedido.getFechaPedido().isAfter(horaFin)) {
                            resultado.pedidosFiltrados++;
                            continue;
                        }
                    }

                    // Filtrar por ventana de tiempo si se especificó
//                    if (horaInicio != null && horaFin != null) {
//                        if (pedido.getFechaPedido().isBefore(horaInicio) ||
//                                pedido.getFechaPedido().isAfter(horaFin)) {
//                            resultado.pedidosFiltrados++;
//                            continue;
//                        }
//                    }

                    pedidosPorCrear.add(pedido);

                    // Guardar en lotes de 100 para mejor rendimiento
                    if (pedidosPorCrear.size() >= 100) {
                        guardarLotePedidos(pedidosPorCrear, resultado);
                        pedidosPorCrear.clear();
                    }

                } catch (Exception e) {
                    resultado.erroresParseo++;
                    System.err.println("Error parseando línea " + numeroLinea + ": " + e.getMessage());
                }
            }

            // Guardar pedidos restantes
            if (!pedidosPorCrear.isEmpty()) {
                guardarLotePedidos(pedidosPorCrear, resultado);
            }

            System.out.println("  Líneas procesadas: " + numeroLinea);
        }
    }

    /**
     * Parsea una línea del archivo en formato V2
     * Formato: id_pedido-aaaammdd-hh-mm-dest-###-IdClien
     * Ejemplo: 000000001-20250102-01-18-SPIM-003-0027081
     */
    private Pedido parsearLineaPedido(String linea, Aeropuerto aeropuertoOrigen) {
        String[] partes = linea.split("-");
        if (partes.length != 7) {
            throw new IllegalArgumentException("Formato inválido: esperado 7 campos, encontrado " + partes.length);
        }

        // Parsear campos
        String idPedidoStr = partes[0];
        String fechaStr = partes[1];  // aaaammdd
        int hora = Integer.parseInt(partes[2]);
        int minuto = Integer.parseInt(partes[3]);
        String codigoAeropuertoDestino = partes[4].trim().toUpperCase();
        int cantidadProductos = Integer.parseInt(partes[5]);
        String idClienteStr = partes[6];

        // Parsear fecha (aaaammdd -> LocalDateTime)
        int anio = Integer.parseInt(fechaStr.substring(0, 4));
        int mes = Integer.parseInt(fechaStr.substring(4, 6));
        int dia = Integer.parseInt(fechaStr.substring(6, 8));
        LocalDateTime fechaPedido = LocalDateTime.of(anio, mes, dia, hora, minuto, 0);

        // Buscar aeropuerto destino
        Aeropuerto aeropuertoDestino = mapaAeropuertos.get(codigoAeropuertoDestino);
        if (aeropuertoDestino == null) {
            throw new IllegalArgumentException("Aeropuerto destino desconocido: " + codigoAeropuertoDestino);
        }

        // Calcular plazo de entrega (regla simple: +3 días)
        // TODO: Implementar lógica basada en continentes como en el Backend
        LocalDateTime fechaLimiteEntrega = fechaPedido.plusDays(3);

        // Obtener o crear cliente
        Cliente cliente = obtenerOCrearCliente(idClienteStr, aeropuertoDestino.getCiudad());

        // Crear pedido
        Pedido pedido = new Pedido();
        pedido.setNombre("PEDIDO-" + idPedidoStr + "-" + codigoAeropuertoDestino);
        pedido.setCliente(cliente);
        pedido.setAeropuertoOrigenCodigo(aeropuertoOrigen.getCodigoIATA());
        pedido.setAeropuertoDestinoCodigo(aeropuertoDestino.getCodigoIATA());
        pedido.setFechaPedido(fechaPedido);
        pedido.setFechaLimiteEntrega(fechaLimiteEntrega);
        pedido.setEstado(EstadoPedido.PENDIENTE);

        // Calcular prioridad
        double prioridad = calcularPrioridad(fechaPedido, fechaLimiteEntrega);
        pedido.setPrioridad(prioridad);

        // Crear productos
        ArrayList<Producto> productos = crearProductos(cantidadProductos, pedido);
        pedido.setProductos(productos);

        return pedido;
    }

    /**
     * Obtiene o crea un cliente con caché para evitar búsquedas repetidas
     */
    private Cliente obtenerOCrearCliente(String idClienteStr, Ciudad ciudadRecojo) {
        // Verificar caché primero
        if (cacheClientes.containsKey(idClienteStr)) {
            return cacheClientes.get(idClienteStr);
        }

        // Intentar buscar en BD
        Long idCliente = Long.parseLong(idClienteStr);
        try {
            Cliente clienteExistente = clienteService.buscarPorId(idCliente);
            if (clienteExistente != null) {
                cacheClientes.put(idClienteStr, clienteExistente);
                return clienteExistente;
            }
        } catch (Exception e) {
            // Cliente no existe, continuar para crearlo
        }

        // Crear nuevo cliente
        Cliente nuevoCliente = new Cliente();
        nuevoCliente.setId(idCliente);
        nuevoCliente.setNombres("Cliente " + idCliente);
        nuevoCliente.setCorreo("cliente" + idCliente + "@morapack.com");
        nuevoCliente.setCiudadRecojo(ciudadRecojo);
        nuevoCliente.setRol(Rol.CLIENTE);
        nuevoCliente.setUsernameOrEmail("cliente" + idCliente);
        nuevoCliente.setPassword("temporal");
        nuevoCliente.setActivo(true);

        // Persistir
        Long clienteId = clienteService.insertar(nuevoCliente);
        Cliente clientePersistido = clienteService.buscarPorId(clienteId);
        
        // Guardar en caché
        cacheClientes.put(idClienteStr, clientePersistido);
        
        return clientePersistido;
    }

    private ArrayList<Producto> crearProductos(int cantidad, Pedido pedido) {
        ArrayList<Producto> productos = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            Producto producto = new Producto();
            producto.setNombre("PRODUCT-" + (i + 1));
            producto.setPeso(1.0);  // Peso por defecto
            producto.setVolumen(1.0);  // Volumen por defecto
            producto.setEstado(EstadoProducto.EN_ALMACEN);
            producto.setPedido(pedido);
            productos.add(producto);
        }
        return productos;
    }

    private double calcularPrioridad(LocalDateTime fechaPedido, LocalDateTime plazoEntrega) {
        long horas = ChronoUnit.HOURS.between(fechaPedido, plazoEntrega);

        if (horas <= 24) {
            return 1.0;
        } else if (horas <= 96) {
            return 0.75;
        } else if (horas <= 288) {
            return 0.5;
        } else {
            return 0.25;
        }
    }

    private void guardarLotePedidos(List<Pedido> pedidos, ResultadoCargaPedidos resultado) {
        for (Pedido pedido : pedidos) {
            try {
                pedidoService.insertar(pedido);
                resultado.pedidosCreados++;
            } catch (Exception e) {
                System.err.println("Error guardando pedido: " + e.getMessage());
                // No incrementar contador de error, ya se cargó correctamente del archivo
            }
        }
    }

    /**
     * Clase para almacenar resultados de la carga
     */
    public static class ResultadoCargaPedidos {
        public boolean exito;
        public String mensajeError;
        public LocalDateTime tiempoInicio;
        public LocalDateTime tiempoFin;
        public long duracionSegundos;
        public int pedidosCargados;
        public int pedidosCreados;
        public int pedidosFiltrados;
        public int erroresParseo;
        public int erroresArchivos;
    }
}

