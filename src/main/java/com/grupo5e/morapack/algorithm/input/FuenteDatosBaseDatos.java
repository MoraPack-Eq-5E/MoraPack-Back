package com.grupo5e.morapack.algorithm.input;

import com.grupo5e.morapack.core.enums.EstadoProducto;
import com.grupo5e.morapack.core.model.Aeropuerto;
import com.grupo5e.morapack.core.model.Cancelacion;
import com.grupo5e.morapack.core.model.Pedido;
import com.grupo5e.morapack.core.model.Producto;
import com.grupo5e.morapack.core.model.Vuelo;
import com.grupo5e.morapack.repository.AeropuertoRepository;
import com.grupo5e.morapack.repository.CancelacionRepository;
import com.grupo5e.morapack.repository.PedidoRepository;
import com.grupo5e.morapack.repository.ProductoRepository;
import com.grupo5e.morapack.repository.VueloRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementación de FuenteDatosInput que lee desde la base de datos PostgreSQL
 * usando repositorios JPA de Spring.
 * 
 * Permite al algoritmo trabajar con datos persistidos en BD en lugar de archivos.
 */
@Component
@Slf4j
public class FuenteDatosBaseDatos implements FuenteDatosInput {
    
    @Autowired
    private AeropuertoRepository aeropuertoRepository;
    
    @Autowired
    private VueloRepository vueloRepository;
    
    @Autowired
    private PedidoRepository pedidoRepository;
    
    @Autowired
    private CancelacionRepository cancelacionRepository;
    
    @Autowired
    private ProductoRepository productoRepository;
    @Override
    public void inicializar() {
        // Spring ya inicializó los repositories
    }
    
    @Override
    public String obtenerNombreFuente() {
        return "BASEDATOS";
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Aeropuerto> cargarAeropuertos() {
        try {
            //List<Aeropuerto> aeropuertos = aeropuertoRepository.findAll();
            List<Aeropuerto> aeropuertos = aeropuertoRepository.listarPorTipoData(1);
            
            // ✅ Forzar inicialización de relaciones LAZY para evitar LazyInitializationException
            // La anotación @Transactional mantiene la sesión de Hibernate abierta
            for (Aeropuerto aeropuerto : aeropuertos) {
                if (aeropuerto.getCiudad() != null) {
                    // Acceder al nombre fuerza la carga del proxy
                    aeropuerto.getCiudad().getNombre();
                }
                if (aeropuerto.getAlmacen() != null) {
                    // Forzar carga del almacén
                    aeropuerto.getAlmacen().getCapacidadMaxima();
                }
            }
            
            System.out.println("✓ Cargados " + aeropuertos.size() + " aeropuertos desde BD (con relaciones inicializadas)");
            return aeropuertos;
        } catch (Exception e) {
            System.err.println("✗ Error cargando aeropuertos desde BD: " + e.getMessage());
            e.printStackTrace();
            return List.of();
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Vuelo> cargarVuelos(List<Aeropuerto> aeropuertos) {
        try {
            //List<Vuelo> vuelos = vueloRepository.findAll();
            List<Vuelo> vuelos = vueloRepository.listarPorTipoData(1);
            // ✅ Forzar inicialización de relaciones LAZY
            // La anotación @Transactional mantiene la sesión de Hibernate abierta
            for (Vuelo vuelo : vuelos) {
                if (vuelo.getAeropuertoOrigen() != null) {
                    vuelo.getAeropuertoOrigen().getCodigoIATA();
                    if (vuelo.getAeropuertoOrigen().getCiudad() != null) {
                        vuelo.getAeropuertoOrigen().getCiudad().getNombre();
                    }
                }
                if (vuelo.getAeropuertoDestino() != null) {
                    vuelo.getAeropuertoDestino().getCodigoIATA();
                    if (vuelo.getAeropuertoDestino().getCiudad() != null) {
                        vuelo.getAeropuertoDestino().getCiudad().getNombre();
                    }
                }
            }
            
            System.out.println("✓ Cargados " + vuelos.size() + " vuelos desde BD (con relaciones inicializadas)");
            return vuelos;
        } catch (Exception e) {
            System.err.println("✗ Error cargando vuelos desde BD: " + e.getMessage());
            e.printStackTrace();
            return List.of();
        }
    }
    @Override
    @Transactional(readOnly = true)
    public List<Cancelacion> cargarCancelaciones(List<Vuelo> vuelos) {
        try {
            List<Cancelacion> cancelaciones = cancelacionRepository.findAll();

            // ✅ Forzar inicialización de relaciones LAZY
            int conVuelo = 0;
            for (Cancelacion c : cancelaciones) {
                if (c.getVuelo() != null) {
                    conVuelo++;
                    // Forzar carga de vuelo y sus aeropuertos
                    Vuelo v = c.getVuelo();
                    v.getId(); // ID del vuelo
                    if (v.getAeropuertoOrigen() != null) {
                        v.getAeropuertoOrigen().getCodigoIATA();
                    }
                    if (v.getAeropuertoDestino() != null) {
                        v.getAeropuertoDestino().getCodigoIATA();
                    }
                }
            }

            System.out.println("✓ Cargadas " + cancelaciones.size() + " cancelaciones desde BD");
            System.out.println("✓ Cancelaciones con vuelo asociado: " + conVuelo);

            // Log chiquito de ejemplo
            cancelaciones.stream().limit(5).forEach(c -> {
                String vueloInfo = (c.getVuelo() != null)
                        ? "VueloID=" + c.getVuelo().getId()
                        : "Vuelo=NULL";
                System.out.println("   - Cancelación: dia=" + c.getDiasCancelado() +
                        " " + c.getCodigoIATAOrigen() + "→" + c.getCodigoIATADestino() +
                        " " + String.format("%02d:%02d", c.getHora(), c.getMinuto()) +
                        " | " + vueloInfo);
            });

            return cancelaciones;
        } catch (Exception e) {
            System.err.println("✗ Error cargando cancelaciones desde BD: " + e.getMessage());
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pedido> cargarPedidos(List<Aeropuerto> aeropuertos) {
        try {
            // ⚠️ ADVERTENCIA: Cargar TODOS los pedidos puede ser lento con datasets grandes
            // Considera usar cargarPedidosPorVentanaDeTiempo() en su lugar
            
            List<Pedido> pedidos = pedidoRepository.findAll();
            
            // ✅ Forzar inicialización de relaciones LAZY NECESARIAS
            // La anotación @Transactional mantiene la sesión de Hibernate abierta
            int totalProductos = 0;
            for (Pedido pedido : pedidos) {
                // Forzar carga de cliente (necesario)
                if (pedido.getCliente() != null) {
                    pedido.getCliente().getNombres();
                    if (pedido.getCliente().getCiudadRecojo() != null) {
                        pedido.getCliente().getCiudadRecojo().getNombre();
                    }
                }
                
                // ⚡ OPTIMIZACIÓN: NO cargar productos aquí
                // Usar cantidadProductos directo (campo en tabla pedidos)
                int cantProductos = pedido.getCantidadProductosRapido();
                totalProductos += cantProductos;
            }
            
            System.out.println("✓ Cargados " + pedidos.size() + " pedidos desde BD (con relaciones inicializadas)");
            System.out.println("✓ Total productos en todos los pedidos: " + totalProductos + " (sin cargar lista)");
            
            return pedidos;
        } catch (Exception e) {
            System.err.println("✗ Error cargando pedidos desde BD: " + e.getMessage());
            e.printStackTrace();
            return List.of();
        }
    }
    
    /**
     * 🚀 OPTIMIZADO: Implementación optimizada para cargar pedidos por ventana de tiempo
     * Usa query custom en lugar de cargar todo y filtrar en memoria
     */
    @Override
    @Transactional(readOnly = true)
    public List<Pedido> cargarPedidosPorVentanaDeTiempo(
            List<Aeropuerto> aeropuertos,
            LocalDateTime horaInicio,
            LocalDateTime horaFin,
            int tipoData) {
        if(tipoData == 1){
            horaFin = horaInicio;
            horaInicio = horaFin.minusDays(1);
        }
        try {
            System.out.println("========================================");
            System.out.println("CARGANDO PEDIDOS CON VENTANA DE TIEMPO (OPTIMIZADO)");
            System.out.println("Hora inicio: " + horaInicio);
            System.out.println("Hora fin: " + horaFin);
            System.out.println("========================================");
            
            long startTime = System.currentTimeMillis();
            
            // ⚡ OPTIMIZACIÓN 1: Query optimizada - solo pedidos en ventana
            List<Pedido> pedidosTotales = pedidoRepository.findByFechaPedidoBetween(horaInicio, horaFin);
            List<Pedido> pedidos = pedidosTotales.stream()
                    .filter(p -> p.getTipoData() == tipoData)
                    .collect(Collectors.toList());

            long queryTime = System.currentTimeMillis();
            System.out.println("⏱️  Query ejecutada en " + (queryTime - startTime) + "ms");
            
            // ⚡ OPTIMIZACIÓN 2: Contar productos SIN cargarlos
            // Usar el campo cantidadProductos directo
            Long totalProductosQuery = pedidoRepository.sumarCantidadProductosEnRango(horaInicio, horaFin);
            int totalProductos = (totalProductosQuery != null) ? totalProductosQuery.intValue() : 0;
            
            long countTime = System.currentTimeMillis();
            System.out.println("⏱️  Conteo de productos en " + (countTime - queryTime) + "ms");
            
            // ✅ Forzar inicialización solo de relaciones NECESARIAS (cliente, ciudad)
            for (Pedido pedido : pedidos) {
                // Forzar carga de cliente (necesario para el algoritmo)
                if (pedido.getCliente() != null) {
                    pedido.getCliente().getNombres();
                    if (pedido.getCliente().getCiudadRecojo() != null) {
                        pedido.getCliente().getCiudadRecojo().getNombre();
                    }
                }
                // ⚡ NO cargar productos - se cargarán LAZY solo si se necesitan
            }
            
            long initTime = System.currentTimeMillis();
            System.out.println("⏱️  Inicialización de relaciones en " + (initTime - countTime) + "ms");
            
            System.out.println("========================================");
            System.out.println("✅ CARGA COMPLETADA");
            System.out.println("✓ Pedidos cargados: " + pedidos.size());
            System.out.println("✓ Total productos: " + totalProductos + " (conteo optimizado)");
            System.out.println("✓ Tiempo total: " + (initTime - startTime) + "ms");
            System.out.println("========================================");
            
            return pedidos;
        } catch (Exception e) {
            System.err.println("✗ Error cargando pedidos por ventana de tiempo: " + e.getMessage());
            e.printStackTrace();
            return List.of();
        }
    }
    
    /**
     * Carga asignaciones de productos existentes para soporte de re-ejecución.
     * CRÍTICO: Permite que ventanas consecutivas construyan sobre asignaciones previas.
     * 
     * Este método es fundamental para el sistema de ventanas temporales incrementales.
     * Cuando se ejecuta una nueva ventana de simulación, el algoritmo necesita conocer
     * qué productos ya fueron asignados en ventanas anteriores para:
     * 
     * 1. Actualizar capacidades de vuelos (usedCapacity)
     * 2. Pre-llenar ocupación de almacenes
     * 3. Evitar sobre-asignación de recursos
     * 4. Construir sobre soluciones previas en lugar de empezar desde cero
     * 
     * @param horaInicioSim Inicio de ventana de simulación (puede ser null)
     * @param horaFinSim Fin de ventana de simulación (puede ser null)
     * @return Mapa de instancia de vuelo -> lista de productos asignados
     */
    @Transactional(readOnly = true)
    public Map<String, List<Producto>> cargarAsignacionesExistentes(
            LocalDateTime horaInicioSim,
            LocalDateTime horaFinSim) {
        
        log.info("========================================");
        log.info("[PREFILL] CARGANDO ASIGNACIONES EXISTENTES");
        log.info("Ventana: {} a {}", horaInicioSim, horaFinSim);
        log.info("========================================");
        
        Map<String, List<Producto>> mapaAsignaciones = new HashMap<>();
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Obtener todos los productos con vuelos asignados
            // Solo cargar productos EN_ALMACEN o EN_VUELO (no completados/entregados)
            List<EstadoProducto> estadosRelevantes = List.of(
                EstadoProducto.EN_ALMACEN,
                EstadoProducto.EN_VUELO
            );
            
            List<Producto> productos = productoRepository
                .findByInstanciaVueloAsignadaNotNullAndEstadoIn(estadosRelevantes);
            
            long queryTime = System.currentTimeMillis();
            log.info("⏱️  Query ejecutada en {}ms", (queryTime - startTime));
            
            int cargados = 0;
            int omitidos = 0;
            
            // Agrupar productos por instancia de vuelo
            for (Producto producto : productos) {
                String idInstancia = producto.getInstanciaVueloAsignada();
                
                if (idInstancia == null || idInstancia.trim().isEmpty()) {
                    omitidos++;
                    continue;
                }
                
                // Forzar inicialización de relaciones necesarias
                if (producto.getPedido() != null) {
                    producto.getPedido().getId(); // Forzar carga
                    if (producto.getPedido().getCliente() != null) {
                        producto.getPedido().getCliente().getNombres();
                    }
                }
                
                // Agrupar por instancia de vuelo
                mapaAsignaciones.computeIfAbsent(idInstancia, k -> new ArrayList<>())
                               .add(producto);
                cargados++;
            }
            
            long processTime = System.currentTimeMillis();
            log.info("⏱️  Procesamiento completado en {}ms", (processTime - queryTime));
            
            log.info("========================================");
            log.info("✅ PREFILL COMPLETADO");
            log.info("✓ Productos cargados: {}", cargados);
            log.info("✓ Productos omitidos: {} (sin instancia asignada)", omitidos);
            log.info("✓ Instancias de vuelo únicas: {}", mapaAsignaciones.size());
            log.info("✓ Tiempo total: {}ms", (processTime - startTime));
            log.info("========================================");
            
            // Log de ejemplo de algunas instancias
            if (!mapaAsignaciones.isEmpty()) {
                log.info("\nEjemplos de instancias cargadas:");
                mapaAsignaciones.entrySet().stream()
                    .limit(5)
                    .forEach(entry -> {
                        log.info("  - {}: {} productos",
                            entry.getKey(),
                            entry.getValue().size()
                        );
                    });
            }
            
            return mapaAsignaciones;
            
        } catch (Exception e) {
            log.error("✗ Error cargando asignaciones existentes desde BD: {}", e.getMessage(), e);
            return new HashMap<>(); // Retornar mapa vacío en caso de error
        }
    }
}

