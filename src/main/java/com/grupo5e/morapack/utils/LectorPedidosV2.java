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
    
    // Lista de clientes nuevos pendientes de guardar en batch
    private final List<Cliente> clientesNuevosPendientes = new ArrayList<>();
    
    // Lista temporal de pedidos por crear (para actualizar referencias de clientes)
    private List<Pedido> pedidosPorCrear = new ArrayList<>();

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
            // Reusar la lista de la clase para permitir actualización de referencias
            pedidosPorCrear = new ArrayList<>();

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

                    // NO guardamos clientes ni pedidos durante el loop
                    // Los acumulamos todos para guardar al final en orden correcto

                } catch (Exception e) {
                    resultado.erroresParseo++;
                    System.err.println("Error parseando línea " + numeroLinea + ": " + e.getMessage());
                }
            }

            System.out.println("  Líneas procesadas: " + numeroLinea);
            
            // PASO 1: Guardar TODOS los clientes primero
            if (!clientesNuevosPendientes.isEmpty()) {
                System.out.println("  📊 Total clientes nuevos a guardar: " + clientesNuevosPendientes.size());
                guardarClientesPendientesSinActualizarPedidos();
            }
            
            // PASO 2: Guardar TODOS los pedidos en lotes (ahora todos los clientes ya están persistidos)
            if (!pedidosPorCrear.isEmpty()) {
                System.out.println("  📦 Total pedidos a guardar: " + pedidosPorCrear.size());
                guardarTodosLosPedidosEnLotes(resultado);
            }
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
        
        // Generar externalId compuesto: {AIRPORT_ORIGIN}-{FILE_ORDER_ID}
        String externalId = aeropuertoOrigen.getCodigoIATA() + "-" + idPedidoStr;
        pedido.setExternalId(externalId);
        
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
     * Guarda todos los clientes pendientes en batch y actualiza el cache
     * CRÍTICO: Debe llamarse ANTES de guardar pedidos para evitar errrores
     */
    private void guardarClientesPendientesSinActualizarPedidos() {
        if (clientesNuevosPendientes.isEmpty()) {
            return;
        }
        
        System.out.println("  💾 Guardando " + clientesNuevosPendientes.size() + " clientes nuevos en batch...");
        try {
            // Crear un mapa temporal para relacionar ID -> instancia antigua
            Map<Long, Cliente> clientesAntiguos = new HashMap<>();
            for (Cliente clienteNuevo : clientesNuevosPendientes) {
                clientesAntiguos.put(clienteNuevo.getId(), clienteNuevo);
            }
            
            // Guardar en batch
            List<Cliente> clientesGuardados = clienteService.insertarBulk(clientesNuevosPendientes);
            
            // CRÍTICO: Actualizar TODAS las referencias en los pedidos acumulados
            System.out.println("  🔄 Actualizando referencias de clientes en " + pedidosPorCrear.size() + " pedidos...");
            for (Cliente clientePersistido : clientesGuardados) {
                String idStr = String.valueOf(clientePersistido.getId());
                cacheClientes.put(idStr, clientePersistido);
                
                // Reemplazar referencias en TODOS los pedidos acumulados
                Cliente clienteAntiguo = clientesAntiguos.get(clientePersistido.getId());
                if (clienteAntiguo != null) {
                    for (Pedido pedido : pedidosPorCrear) {
                        if (pedido.getCliente() == clienteAntiguo) {
                            pedido.setCliente(clientePersistido);
                        }
                    }
                }
            }
            
            System.out.println("  ✅ " + clientesGuardados.size() + " clientes guardados y referencias actualizadas");
        } catch (Exception e) {
            System.err.println("  ❌ Error guardando clientes en batch: " + e.getMessage());
            e.printStackTrace();
        }
        clientesNuevosPendientes.clear();
    }
    
    /**
     * Guarda todos los pedidos acumulados en lotes de 1000 para mejor rendimiento
     * Se debe llamar DESPUÉS de guardar todos los clientes
     */
    private void guardarTodosLosPedidosEnLotes(ResultadoCargaPedidos resultado) {
        final int BATCH_SIZE = 1000;
        int totalPedidos = pedidosPorCrear.size();
        int procesados = 0;
        
        while (procesados < totalPedidos) {
            int endIndex = Math.min(procesados + BATCH_SIZE, totalPedidos);
            List<Pedido> lote = pedidosPorCrear.subList(procesados, endIndex);
            
            System.out.println("  💾 Guardando lote de pedidos " + (procesados + 1) + "-" + endIndex + " de " + totalPedidos);
            guardarLotePedidos(new ArrayList<>(lote), resultado);
            
            procesados = endIndex;
        }
        
        pedidosPorCrear.clear();
    }
    
    /**
     * Obtiene o crea un cliente con caché para evitar búsquedas repetidas
     * OPTIMIZADO: Acumula clientes nuevos para guardarlos en batch antes de los pedidos
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

        // Crear nuevo cliente (sin persistir aún)
        Cliente nuevoCliente = new Cliente();
        nuevoCliente.setId(idCliente);
        nuevoCliente.setNombres("Cliente " + idCliente);
        nuevoCliente.setCorreo("cliente" + idCliente + "@morapack.com");
        nuevoCliente.setCiudadRecojo(ciudadRecojo);
        nuevoCliente.setRol(Rol.CLIENTE);
        nuevoCliente.setUsernameOrEmail("cliente" + idCliente);
        nuevoCliente.setPassword("temporal");
        nuevoCliente.setActivo(true);

        // OPTIMIZACIÓN: Agregar a lista de pendientes en lugar de insertar inmediatamente
        clientesNuevosPendientes.add(nuevoCliente);
        
        // Guardar en caché (aunque aún no tenga ID de BD, está ok para referencias)
        cacheClientes.put(idClienteStr, nuevoCliente);
        
        return nuevoCliente;
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
        if (pedidos == null || pedidos.isEmpty()) {
            return;
        }
        
        try {
            // OPTIMIZACIÓN: Usar batch insert en lugar de insertar uno por uno
            // Esto reduce de N queries a ~N/1000 queries (según batch_size configurado)
            List<Pedido> pedidosGuardados = pedidoService.insertarBulk(pedidos);
            resultado.pedidosCreados += pedidosGuardados.size();
            System.out.println("  ✅ Lote de " + pedidosGuardados.size() + " pedidos guardados en batch");
        } catch (Exception e) {
            System.err.println("  ❌ Error guardando lote de pedidos: " + e.getMessage());
            e.printStackTrace();
            // Intentar guardar uno por uno como fallback
            System.out.println("  ⚠️ Intentando guardar pedidos individualmente como fallback...");
            int guardadosIndividualmente = 0;
            for (Pedido pedido : pedidos) {
                try {
                    pedidoService.insertar(pedido);
                    guardadosIndividualmente++;
                } catch (Exception ex) {
                    System.err.println("    Error guardando pedido individual: " + ex.getMessage());
                }
            }
            resultado.pedidosCreados += guardadosIndividualmente;
            System.out.println("  ✅ " + guardadosIndividualmente + " pedidos guardados individualmente");
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

