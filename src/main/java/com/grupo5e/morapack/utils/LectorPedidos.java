package com.grupo5e.morapack.utils;

import com.grupo5e.morapack.core.enums.Continente;
import com.grupo5e.morapack.core.enums.EstadoProducto;
import com.grupo5e.morapack.core.enums.EstadoPedido;
import com.grupo5e.morapack.core.enums.Rol;
import com.grupo5e.morapack.core.model.*;
import com.grupo5e.morapack.service.ClienteService;
import com.grupo5e.morapack.service.PedidoService;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class LectorPedidos {
    private final String rutaArchivo;
    private final ArrayList<Aeropuerto> aeropuertos;
    private final Map<String, Aeropuerto> mapaAeropuertos;
    private final Random aleatorio;

    // Servicios necesarios
    private final PedidoService pedidoService;
    private final ClienteService clienteService;

    public LectorPedidos(String rutaArchivo,
                         ArrayList<Aeropuerto> aeropuertos,
                         PedidoService pedidoService,
                         ClienteService clienteService) {
        this.rutaArchivo = rutaArchivo;
        this.aeropuertos = aeropuertos;
        this.pedidoService = pedidoService;
        this.clienteService = clienteService;
        this.aleatorio = new Random();
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

    public void leerYGuardarProductos() {
        List<Pedido> pedidosAcumulados = new ArrayList<>();
        int BATCH_SIZE = 1000; // Guardar cada 1000 pedidos
        
        try (BufferedReader reader = new BufferedReader(new FileReader(rutaArchivo))) {
            String linea;

            while ((linea = reader.readLine()) != null) {
                if (linea.trim().isEmpty()) {
                    continue;
                }

                String[] partes = linea.trim().split("\\s+");
                if (partes.length >= 6) {
                    Pedido pedido = procesarLineaProducto(partes);
                    if (pedido != null) {
                        pedidosAcumulados.add(pedido);
                        
                        // Guardar en lotes para evitar usar demasiada memoria
                        if (pedidosAcumulados.size() >= BATCH_SIZE) {
                            guardarLotePedidos(pedidosAcumulados);
                            pedidosAcumulados.clear();
                        }
                    }
                }
            }

            // Guardar pedidos restantes
            if (!pedidosAcumulados.isEmpty()) {
                guardarLotePedidos(pedidosAcumulados);
            }

            System.out.println("Proceso de carga de paquetes completado exitosamente.");

        } catch (IOException e) {
            System.err.println("Error leyendo datos de productos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Pedido procesarLineaProducto(String[] partes) {
        try {
            int diasPrioridad = Integer.parseInt(partes[0]);
            int hora = Integer.parseInt(partes[1]);
            int minuto = Integer.parseInt(partes[2]);
            String codigoAeropuertoDestino = partes[3].trim().toUpperCase();
            int cantidadProductos = Integer.parseInt(partes[4]);
            Long idCliente = (long) Integer.parseInt(partes[5]);

            Aeropuerto aeropuertoDestino = mapaAeropuertos.get(codigoAeropuertoDestino);

            if (aeropuertoDestino != null) {
                // Obtener o crear cliente (debe estar persistido ANTES de asociarlo al pedido)
                Cliente cliente = obtenerOCrearCliente(idCliente, aeropuertoDestino.getCiudad());

                // Calcular fechas
                LocalDateTime fechaPedido = calcularFechaPedido(hora, minuto);
                LocalDateTime plazoEntrega = calcularPlazoEntrega(diasPrioridad, fechaPedido);

                // Crear pedido
                Pedido pedido = crearPedido(cliente, aeropuertoDestino, fechaPedido, plazoEntrega);

                // Crear productos
                ArrayList<Producto> productos = crearProductos(cantidadProductos, pedido);
                pedido.setProductos(productos);

                return pedido;
            } else {
                System.err.println("Aeropuerto no encontrado: " + codigoAeropuertoDestino);
                return null;
            }
        } catch (Exception e) {
            System.err.println("Error procesando línea: " + e.getMessage());
            return null;
        }
    }
    
    private void guardarLotePedidos(List<Pedido> pedidos) {
        if (pedidos == null || pedidos.isEmpty()) {
            return;
        }
        
        try {
            // OPTIMIZACIÓN: Usar batch insert en lugar de insertar uno por uno
            List<Pedido> pedidosGuardados = pedidoService.insertarBulk(pedidos);
            System.out.println("  ✅ Lote de " + pedidosGuardados.size() + " pedidos guardados en batch");
        } catch (Exception e) {
            System.err.println("  ❌ Error guardando lote: " + e.getMessage());
            e.printStackTrace();
            // Fallback: guardar uno por uno
            System.out.println("  ⚠️ Intentando guardar individualmente como fallback...");
            int guardados = 0;
            for (Pedido pedido : pedidos) {
                try {
                    pedidoService.insertar(pedido);
                    guardados++;
                } catch (Exception ex) {
                    System.err.println("    Error guardando pedido: " + ex.getMessage());
                }
            }
            System.out.println("  ✅ " + guardados + " pedidos guardados individualmente");
        }
    }

    /**
     * Obtiene o crea un cliente. Si no existe en BD, lo crea y persiste.
     * (Siguiendo el patrón del Backend de ejemplo)
     */
    private Cliente obtenerOCrearCliente(Long idCliente, Ciudad ciudadRecojo) {
        // Intentar buscar el cliente existente en BD
        try {
            Cliente clienteExistente = clienteService.buscarPorId(idCliente);
            if (clienteExistente != null) {
                return clienteExistente;
            }
        } catch (Exception e) {
            // Cliente no existe, continuar para crearlo
        }
        
        // Si no existe, crear uno nuevo y persistirlo
        Cliente nuevoCliente = new Cliente();
        nuevoCliente.setId(idCliente);
        nuevoCliente.setNombres("Cliente " + idCliente);
        nuevoCliente.setCorreo("cliente" + idCliente + "@ejemplo.com");
        nuevoCliente.setCiudadRecojo(ciudadRecojo);
        nuevoCliente.setRol(Rol.CLIENTE);
        nuevoCliente.setUsernameOrEmail("cliente" + idCliente);
        nuevoCliente.setPassword("temporal"); // Password temporal
        nuevoCliente.setActivo(true);
        
        // Persistir el cliente ANTES de usarlo
        Long clienteId = clienteService.insertar(nuevoCliente);
        
        // Retornar el cliente recién persistido
        return clienteService.buscarPorId(clienteId);
    }

    private LocalDateTime calcularFechaPedido(int hora, int minuto) {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime fechaPedido = ahora.withHour(hora).withMinute(minuto).withSecond(0).withNano(0);

        if (fechaPedido.isBefore(ahora)) {
            fechaPedido = fechaPedido.plusDays(1);
        }

        return fechaPedido;
    }

    private LocalDateTime calcularPlazoEntrega(int diasPrioridad, LocalDateTime fechaPedido) {
        switch (diasPrioridad) {
            case 1:  return fechaPedido.plus(1, ChronoUnit.DAYS);
            case 4:  return fechaPedido.plus(4, ChronoUnit.DAYS);
            case 12: return fechaPedido.plus(12, ChronoUnit.DAYS);
            case 24: return fechaPedido.plus(24, ChronoUnit.DAYS);
            default: return fechaPedido.plus(7, ChronoUnit.DAYS);
        }
    }

    private Pedido crearPedido(Cliente cliente, Aeropuerto aeropuertoDestino,
                                 LocalDateTime fechaPedido, LocalDateTime plazoEntrega) {
        Pedido pedido = new Pedido();
        // Generar nombre automático (como en el Backend de ejemplo)
        pedido.setNombre("PEDIDO-" + System.currentTimeMillis() + "-" + aleatorio.nextInt(1000));
        pedido.setCliente(cliente);
        pedido.setAeropuertoDestinoCodigo(aeropuertoDestino.getCodigoIATA());
        pedido.setFechaPedido(fechaPedido);
        pedido.setFechaLimiteEntrega(plazoEntrega);
        pedido.setEstado(EstadoPedido.PENDIENTE);

        // Establecer aeropuerto actual (almacén inicial)
        Aeropuerto aeropuertoActual = obtenerAeropuertoAlmacenAleatorio(aeropuertoDestino.getCiudad().getContinente());
        pedido.setAeropuertoOrigenCodigo(aeropuertoActual.getCodigoIATA());

        // Calcular y establecer prioridad
        double prioridad = calcularPrioridad(fechaPedido, plazoEntrega);
        pedido.setPrioridad(prioridad);

        return pedido;
    }

    private ArrayList<Producto> crearProductos(int cantidad, Pedido pedido) {
        ArrayList<Producto> productos = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            Producto producto = new Producto();
            producto.setNombre("PRODUCT-" + (i + 1)); // Nombre del producto
            producto.setPeso(1.0); // Peso por defecto (como en el Backend de ejemplo)
            producto.setVolumen(1.0); // Volumen por defecto (como en el Backend de ejemplo)
            producto.setEstado(EstadoProducto.EN_ALMACEN);
            producto.setPedido(pedido);
            productos.add(producto);
        }
        return productos;
    }

    private Aeropuerto obtenerAeropuertoAlmacenAleatorio(Continente continenteDestino) {
        ArrayList<Aeropuerto> almacenesMoraPack = new ArrayList<>();

        for (Aeropuerto aeropuerto : aeropuertos) {
            Ciudad ciudad = aeropuerto.getCiudad();
            String nombreCiudad = ciudad.getNombre().toLowerCase();

            if (nombreCiudad.contains("lima") || nombreCiudad.contains("bruselas") || nombreCiudad.contains("baku")) {
                if (ciudad.getContinente() != continenteDestino) {
                    almacenesMoraPack.add(aeropuerto);
                }
            }
        }

        if (almacenesMoraPack.isEmpty()) {
            for (Aeropuerto aeropuerto : aeropuertos) {
                Ciudad ciudad = aeropuerto.getCiudad();
                String nombreCiudad = ciudad.getNombre().toLowerCase();

                if (nombreCiudad.contains("lima") || nombreCiudad.contains("bruselas") || nombreCiudad.contains("baku")) {
                    almacenesMoraPack.add(aeropuerto);
                }
            }
        }

        if (almacenesMoraPack.isEmpty()) {
            System.err.println("Advertencia: No se encontraron almacenes MoraPack, usando respaldo");
            for (Aeropuerto aeropuerto : aeropuertos) {
                if (aeropuerto.getCiudad().getNombre().toLowerCase().contains("lima")) {
                    return aeropuerto;
                }
            }
            return aeropuertos.get(0); // Último respaldo
        }

        return almacenesMoraPack.get(aleatorio.nextInt(almacenesMoraPack.size()));
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

    // Método adicional para verificar los paquetes guardados
    public void listarPedidosGuardados() {
        try {
            var pedidos = pedidoService.listar();
            System.out.println("Total de pedidos en sistema: " + pedidos.size());
            for (Pedido pedido : pedidos) {
                System.out.println("Paquete ID: " + pedido.getId() +
                        ", Cliente: " + pedido.getCliente().getNombres() +
                        ", Estado: " + pedido.getEstado());
            }
        } catch (Exception e) {
            System.err.println("Error al listar paquetes: " + e.getMessage());
        }
    }
}