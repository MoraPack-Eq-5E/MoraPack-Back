package com.grupo5e.morapack.algorithm.alns;

import com.grupo5e.morapack.core.model.Pedido;
import com.grupo5e.morapack.core.model.Producto;
import com.grupo5e.morapack.core.model.Vuelo;
import com.grupo5e.morapack.core.enums.EstadoProducto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ProductTracker - Seguimiento de productos individuales
 * Siguiendo patrón de MoraPack-Backend
 * 
 * Mantiene la relación entre productos individuales y sus vuelos asignados,
 * permitiendo tracking a nivel de producto como especifica el problema:
 * "Los productos pueden llegar en distintos momentos siempre que todos lleguen
 * dentro del plazo establecido"
 */
public class ProductTracker {

    // Mapea ID de producto a su ruta de vuelos asignada
    private Map<Integer, ArrayList<Vuelo>> productoARutaMap;

    // Mapea ID de producto a su pedido padre
    private Map<Integer, Pedido> productoAPedidoMap;

    // Mapea ID de pedido a sus productos
    private Map<Integer, ArrayList<Producto>> pedidoAProductosMap;

    // NUEVO: Mapea ID de producto a su ruta con tiempos absolutos calculados por
    // ALNS
    private Map<Integer, RutaConTiempos> productoARutaConTiemposMap;

    public ProductTracker() {
        this.productoARutaMap = new HashMap<>();
        this.productoAPedidoMap = new HashMap<>();
        this.pedidoAProductosMap = new HashMap<>();
        this.productoARutaConTiemposMap = new HashMap<>();
    }

    /**
     * Inicializa el tracker con pedidos y sus productos
     */
    public void initializeFromOrders(List<Pedido> pedidos) {
        for (Pedido pedido : pedidos) {
            if (pedido.getProductos() == null || pedido.getProductos().isEmpty()) {
                // Crear producto por defecto para pedidos sin productos explícitos
                Producto productoDefault = new Producto();
                productoDefault.setId(pedido.getId() * 1000); // ID derivado del pedido
                productoDefault.setPedido(pedido);
                productoDefault.setEstado(EstadoProducto.EN_ALMACEN);

                ArrayList<Producto> productos = new ArrayList<>();
                productos.add(productoDefault);
                pedido.setProductos(productos);

                pedidoAProductosMap.put(pedido.getId(), productos);
                productoAPedidoMap.put(productoDefault.getId(), pedido);
            } else {
                // Mapear productos existentes
                pedidoAProductosMap.put(pedido.getId(), new ArrayList<>(pedido.getProductos()));
                for (Producto producto : pedido.getProductos()) {
                    // Asegurar que cada producto tenga el pedido configurado
                    if (producto.getPedido() == null) {
                        producto.setPedido(pedido);
                    }
                    productoAPedidoMap.put(producto.getId(), pedido);
                }
            }
        }
    }

    /**
     * CRÍTICO: Asigna una ruta a un producto específico
     * Implementa tracking a nivel de producto
     *
     * @param producto Producto a asignar
     * @param ruta     Ruta de vuelos para este producto
     */
    public void assignProductToRoute(Producto producto, ArrayList<Vuelo> ruta) {
        if (producto == null || producto.getId() == null) {
            return;
        }

        productoARutaMap.put(producto.getId(), ruta);
        producto.setEstado(EstadoProducto.EN_VUELO);
    }

    /**
     * NUEVO: Asigna una ruta con tiempos absolutos a un producto.
     * Esta es la versión mejorada que incluye fechas calculadas por el ALNS.
     *
     * @param producto       Producto a asignar
     * @param rutaConTiempos Ruta con tiempos absolutos calculados
     */
    public void assignProductToRouteWithTimes(Producto producto, RutaConTiempos rutaConTiempos) {
        if (producto == null || producto.getId() == null) {
            return;
        }

        productoARutaConTiemposMap.put(producto.getId(), rutaConTiempos);

        // También mantener compatibilidad con el mapa tradicional
        if (rutaConTiempos != null && rutaConTiempos.getTramos() != null) {
            ArrayList<Vuelo> vuelos = new ArrayList<>();
            for (TramoConTiempo tramo : rutaConTiempos.getTramos()) {
                if (tramo.getVuelo() != null) {
                    vuelos.add(tramo.getVuelo());
                }
            }
            productoARutaMap.put(producto.getId(), vuelos);
        }

        producto.setEstado(EstadoProducto.EN_VUELO);
    }

    /**
     * NUEVO: Obtiene la ruta con tiempos de un producto.
     *
     * @param producto Producto a buscar
     * @return RutaConTiempos o null si no existe
     */
    public RutaConTiempos getRouteWithTimesForProduct(Producto producto) {
        if (producto == null || producto.getId() == null) {
            return null;
        }
        return productoARutaConTiemposMap.get(producto.getId());
    }

    /**
     * NUEVO: Verifica si un producto tiene ruta con tiempos asignada.
     *
     * @param producto Producto a verificar
     * @return true si tiene ruta con tiempos
     */
    public boolean hasRouteWithTimes(Producto producto) {
        if (producto == null || producto.getId() == null) {
            return false;
        }
        return productoARutaConTiemposMap.containsKey(producto.getId());
    }

    /**
     * Obtiene la ruta asignada a un producto
     */
    public ArrayList<Vuelo> getRouteForProduct(Producto producto) {
        if (producto == null || producto.getId() == null) {
            return null;
        }
        return productoARutaMap.get(producto.getId());
    }

    /**
     * Obtiene el pedido padre de un producto
     */
    public Pedido getOrderForProduct(Producto producto) {
        if (producto == null || producto.getId() == null) {
            return null;
        }
        return productoAPedidoMap.get(producto.getId());
    }

    /**
     * Obtiene todos los productos de un pedido
     */
    public ArrayList<Producto> getProductsForOrder(Pedido pedido) {
        if (pedido == null || pedido.getId() == null) {
            return new ArrayList<>();
        }
        return pedidoAProductosMap.getOrDefault(pedido.getId(), new ArrayList<>());
    }

    /**
     * Verifica si un producto está asignado
     */
    public boolean isProductAssigned(Producto producto) {
        if (producto == null || producto.getId() == null) {
            return false;
        }
        return productoARutaMap.containsKey(producto.getId());
    }

    /**
     * Verifica si un pedido está completamente asignado (todos sus productos)
     */
    public boolean isOrderFullyAssigned(Pedido pedido) {
        ArrayList<Producto> productos = getProductsForOrder(pedido);
        if (productos.isEmpty()) {
            return false;
        }

        for (Producto producto : productos) {
            if (!isProductAssigned(producto)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Obtiene estadísticas de tracking
     */
    public ProductTrackingStats getStats() {
        ProductTrackingStats stats = new ProductTrackingStats();

        // Contar productos totales y asignados
        stats.totalProducts = productoAPedidoMap.size();
        stats.assignedProducts = productoARutaMap.size();
        stats.unassignedProducts = stats.totalProducts - stats.assignedProducts;

        // Contar pedidos totales y asignados
        stats.totalOrders = pedidoAProductosMap.size();

        for (Integer pedidoId : pedidoAProductosMap.keySet()) {
            Pedido pedido = new Pedido();
            pedido.setId(pedidoId);

            ArrayList<Producto> productos = getProductsForOrder(pedido);
            int productosAsignados = 0;

            for (Producto producto : productos) {
                if (isProductAssigned(producto)) {
                    productosAsignados++;
                }
            }

            if (productosAsignados == productos.size() && productos.size() > 0) {
                stats.ordersFullyAssigned++;
            } else if (productosAsignados > 0) {
                stats.ordersPartiallyAssigned++;
            }
        }

        return stats;
    }

    /**
     * Actualiza el estado de un producto basado en su posición en la ruta
     * Se llamaría durante la simulación para tracking en tiempo real
     */
    public void updateProductStatus(Producto producto, int currentFlightIndex) {
        ArrayList<Vuelo> ruta = getRouteForProduct(producto);
        if (ruta == null || ruta.isEmpty()) {
            producto.setEstado(EstadoProducto.ENTREGADO);
            return;
        }

        if (currentFlightIndex < 0) {
            producto.setEstado(EstadoProducto.EN_ALMACEN);
        } else if (currentFlightIndex >= ruta.size()) {
            producto.setEstado(EstadoProducto.ENTREGADO);
        } else {
            producto.setEstado(EstadoProducto.EN_VUELO);
        }
    }

    /**
     * Genera un mapa de solución a nivel de producto
     * Retorna: Map<Producto, List<Vuelo>>
     */
    public Map<Producto, ArrayList<Vuelo>> getProductLevelSolution() {
        Map<Producto, ArrayList<Vuelo>> solucion = new HashMap<>();

        for (Map.Entry<Integer, ArrayList<Vuelo>> entry : productoARutaMap.entrySet()) {
            Integer productoId = entry.getKey();
            ArrayList<Vuelo> ruta = entry.getValue();

            // Encontrar el objeto producto
            for (ArrayList<Producto> productos : pedidoAProductosMap.values()) {
                for (Producto producto : productos) {
                    if (producto.getId() != null && producto.getId().equals(productoId)) {
                        solucion.put(producto, ruta);
                        break;
                    }
                }
            }
        }

        return solucion;
    }

    /**
     * NUEVO: Genera un mapa de solución con tiempos a nivel de producto.
     * Esta versión incluye fechas absolutas calculadas por el ALNS.
     * 
     * @return Map<Producto, RutaConTiempos> con la solución completa
     */
    public Map<Producto, RutaConTiempos> getSolucionConTiempos() {
        Map<Producto, RutaConTiempos> solucion = new HashMap<>();

        for (Map.Entry<Integer, RutaConTiempos> entry : productoARutaConTiemposMap.entrySet()) {
            Integer productoId = entry.getKey();
            RutaConTiempos rutaConTiempos = entry.getValue();

            // Encontrar el objeto producto
            for (ArrayList<Producto> productos : pedidoAProductosMap.values()) {
                for (Producto producto : productos) {
                    if (producto.getId() != null && producto.getId().equals(productoId)) {
                        solucion.put(producto, rutaConTiempos);
                        break;
                    }
                }
            }
        }

        return solucion;
    }

    /**
     * Limpia todos los datos de tracking
     */
    public void clear() {
        productoARutaMap.clear();
        productoAPedidoMap.clear();
        pedidoAProductosMap.clear();
        productoARutaConTiemposMap.clear();
    }

    /**
     * Imprime resumen de tracking para debugging
     */
    public void printTrackingSummary() {
        ProductTrackingStats stats = getStats();
        System.out.println("\n=== RESUMEN DE TRACKING DE PRODUCTOS ===");
        System.out.println("Total Productos: " + stats.totalProducts);
        System.out.println("Productos Asignados: " + stats.assignedProducts);
        System.out.println("Productos No Asignados: " + stats.unassignedProducts);
        System.out.println("Total Pedidos: " + stats.totalOrders);
        System.out.println("Pedidos Completamente Asignados: " + stats.ordersFullyAssigned);
        System.out.println("Pedidos Parcialmente Asignados: " + stats.ordersPartiallyAssigned);
        if (stats.totalProducts > 0) {
            System.out.println("Tasa de Asignación: " +
                    String.format("%.2f%%", (stats.assignedProducts * 100.0 / stats.totalProducts)));
        }
        System.out.println("=========================================\n");
    }

    /**
     * Clase interna para estadísticas de tracking
     */
    public static class ProductTrackingStats {
        public int totalProducts;
        public int assignedProducts;
        public int unassignedProducts;
        public int totalOrders;
        public int ordersFullyAssigned;
        public int ordersPartiallyAssigned;
    }
}
