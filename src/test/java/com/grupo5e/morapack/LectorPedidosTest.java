package com.grupo5e.morapack;

import com.grupo5e.morapack.core.enums.EstadoPedido;
import com.grupo5e.morapack.core.enums.EstadoProducto;
import com.grupo5e.morapack.core.model.*;
import com.grupo5e.morapack.service.AeropuertoService;
import com.grupo5e.morapack.service.ClienteService;
import com.grupo5e.morapack.service.PedidoService;
import com.grupo5e.morapack.service.ProductoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.BufferedReader;
import java.io.FileReader;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Test para leer pedidos desde un archivo y guardarlos en la BD.
 *
 * Formato del archivo:
 * [diasPrioridad] [hora] [minuto] [codigoDestino] [cantidadProductos] [idCliente]
 *
 * Ejemplo:
 * 04 16 22 EDDI 100 6084676
 */
@SpringBootTest
public class LectorPedidosTest {

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private AeropuertoService aeropuertoService;

    @Autowired
    private ClienteService clienteService;

    private final String RUTA_ARCHIVO = "src/test/resources/pedidos.txt";
    @Autowired
    private ProductoService productoService;

    @Test
    void cargarPedidosDesdeArchivo() {
        try (BufferedReader reader = new BufferedReader(new FileReader(RUTA_ARCHIVO))) {

            String linea;
            List<Aeropuerto> aeropuertos = aeropuertoService.listar();
            if (aeropuertos.isEmpty()) {
                System.err.println("[ERROR] No hay aeropuertos cargados en la base de datos.");
                return;
            }

            Map<String, Aeropuerto> mapaAeropuertos = crearMapaAeropuertos(aeropuertos);
            Random random = new Random();

            int totalPedidos = 0;

            while ((linea = reader.readLine()) != null) {
                if (linea.trim().isEmpty()) continue;

                String[] partes = linea.trim().split("\\s+");
                if (partes.length != 6) {
                    System.err.println("[WARN] Línea ignorada (formato inválido): " + linea);
                    continue;
                }

                int diasPrioridad = Integer.parseInt(partes[0]);
                int hora = Integer.parseInt(partes[1]);
                int minuto = Integer.parseInt(partes[2]);
                String codigoDestino = partes[3].trim().toUpperCase();
                int cantidadProductos = Integer.parseInt(partes[4]);
                Long idCliente = Long.parseLong(partes[5]);

                Aeropuerto aeropuertoDestino = mapaAeropuertos.get(codigoDestino);
                if (aeropuertoDestino == null) {
                    System.err.println("[WARN] Aeropuerto destino no encontrado: " + codigoDestino);
                    continue;
                }

                Cliente cliente = clienteService.buscarPorId(idCliente,0);
                if (cliente == null) {
                    System.err.println("[WARN] Cliente no encontrado con ID: " + idCliente);
                    continue;
                }

                // --- Fechas ---
                LocalDateTime fechaPedido = calcularFechaPedido(hora, minuto);
                LocalDateTime fechaEntrega = fechaPedido.plusDays(diasPrioridad);

                // --- Crear Pedido ---
                Pedido pedido = new Pedido();
                pedido.setCliente(cliente);
                pedido.setAeropuertoDestinoCodigo(codigoDestino);
                pedido.setFechaPedido(fechaPedido);
                pedido.setFechaLimiteEntrega(fechaEntrega);
                pedido.setEstado(EstadoPedido.PENDIENTE);
                pedido.setPrioridad(calcularPrioridad(fechaPedido, fechaEntrega));
                pedido.setCantidadProductos(cantidadProductos);
//                // --- Aeropuerto actual aleatorio (distinto continente) ---
//                Aeropuerto aeropuertoActual = obtenerAeropuertoAlmacenAleatorio(
//                        aeropuertos, aeropuertoDestino.getCiudad().getContinente(), random);
//                pedido.setAeropuertoActualCodigo(aeropuertoActual.getCodigoIATA());

                // --- Crear productos ---
                ArrayList<Producto> productos = new ArrayList<>();
                Integer idProducto = 0;
                for (int i = 0; i < cantidadProductos; i++) {
                    Producto producto = new Producto();
                    producto.setEstado(EstadoProducto.EN_ALMACEN);
                    producto.setPedido(pedido);
                    productos.add(producto);
                    try{
                        idProducto = productoService.insertar(producto);
                        System.out.printf("Se inserto el producto con ID = %d\n",idProducto);
                    }catch(Exception e){
                        System.err.println("[ERROR] Falló al guardar producto " + idProducto + ": " + e.getMessage());
                    }

                }
                pedido.setProductos(productos);

                // --- Guardar en BD ---
                try {
                    Integer idGuardado = pedidoService.insertar(pedido);
                    totalPedidos++;
                    System.out.printf("[OK] Pedido guardado ID=%d | Cliente=%s | Destino=%s%n",
                            idGuardado, cliente.getNombres(), aeropuertoDestino.getCodigoIATA());
                } catch (Exception e) {
                    System.err.println("[ERROR] Falló al guardar pedido del cliente " + idCliente + ": " + e.getMessage());
                }
            }

            System.out.println("\n✅ Total de pedidos insertados: " + totalPedidos);

        } catch (Exception e) {
            System.err.println("[ERROR] Falló la lectura del archivo: " + e.getMessage());
        }
    }

    // ================== MÉTODOS AUXILIARES ==================

    private Map<String, Aeropuerto> crearMapaAeropuertos(List<Aeropuerto> aeropuertos) {
        Map<String, Aeropuerto> mapa = new HashMap<>();
        for (Aeropuerto a : aeropuertos) {
            if (a.getCodigoIATA() != null)
                mapa.put(a.getCodigoIATA().trim().toUpperCase(), a);
        }
        return mapa;
    }

    private LocalDateTime calcularFechaPedido(int hora, int minuto) {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime fecha = ahora.withHour(hora).withMinute(minuto).withSecond(0).withNano(0);
        return fecha.isBefore(ahora) ? fecha.plusDays(1) : fecha;
    }

    private double calcularPrioridad(LocalDateTime inicio, LocalDateTime fin) {
        long horas = ChronoUnit.HOURS.between(inicio, fin);
        if (horas <= 24) return 1.0;
        if (horas <= 96) return 0.75;
        if (horas <= 288) return 0.5;
        return 0.25;
    }

    private Aeropuerto obtenerAeropuertoAlmacenAleatorio(
            List<Aeropuerto> aeropuertos,
            com.grupo5e.morapack.core.enums.Continente continenteDestino,
            Random random) {

        List<Aeropuerto> almacenes = aeropuertos.stream()
                .filter(a -> {
                    String ciudad = a.getCiudad().getNombre().toLowerCase();
                    return (ciudad.contains("lima") || ciudad.contains("bruselas") || ciudad.contains("baku"))
                            && a.getCiudad().getContinente() != continenteDestino;
                }).toList();

        return almacenes.isEmpty()
                ? aeropuertos.get(random.nextInt(aeropuertos.size()))
                : almacenes.get(random.nextInt(almacenes.size()));
    }
}
