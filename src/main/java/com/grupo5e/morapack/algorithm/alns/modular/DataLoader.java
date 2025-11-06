package com.grupo5e.morapack.algorithm.alns.modular;

import com.grupo5e.morapack.core.model.Aeropuerto;
import com.grupo5e.morapack.core.model.Pedido;
import com.grupo5e.morapack.core.model.Producto;
import com.grupo5e.morapack.core.model.Vuelo;
import com.grupo5e.morapack.service.AeropuertoService;
import com.grupo5e.morapack.service.PedidoService;
import com.grupo5e.morapack.service.VueloService;
import com.grupo5e.morapack.service.TemporaryDataStorageService;
import com.grupo5e.morapack.service.TemporarySimulationData;

import java.util.ArrayList;
import java.util.List;

//CARGA Y PREPARACION DE DATOS
public class DataLoader {
    private final AeropuertoService aeropuertoService;
    private final PedidoService pedidoService;
    private final VueloService vueloService;
    private final TemporaryDataStorageService temporaryDataStorageService;

    //Constructor de los servicios
    public DataLoader(AeropuertoService aeropuertoService, PedidoService pedidoService, 
                     VueloService vueloService, TemporaryDataStorageService temporaryDataStorageService) {
        this.aeropuertoService = aeropuertoService;
        this.pedidoService = pedidoService;
        this.vueloService = vueloService;
        this.temporaryDataStorageService = temporaryDataStorageService;
    }

    //Clase estatica para cargar los datos de las listas que se usaran para el algoritmo
    public DataLoadResult cargarDatos(boolean habilitarUnitizacion){
        return cargarDatos(habilitarUnitizacion, null);
    }
    
    /**
     * Carga datos con soporte para archivos temporales subidos
     * @param habilitarUnitizacion Si debe expandir pedidos a unidades
     * @param uploadSessionId ID de sesión de archivos subidos (null = usar BD)
     */
    public DataLoadResult cargarDatos(boolean habilitarUnitizacion, String uploadSessionId){
        List<Aeropuerto> aeropuertos;
        List<Pedido> pedidosOriginales;
        List<Vuelo> vuelos;
        
        // Si hay session ID, intentar cargar datos temporales
        if (uploadSessionId != null && !uploadSessionId.trim().isEmpty()) {
            System.out.println("📤 Intentando cargar datos temporales de sesión: " + uploadSessionId);
            TemporarySimulationData tempData = temporaryDataStorageService.getTemporaryData(uploadSessionId);
            
            if (tempData != null) {
                // Usar datos subidos si están disponibles, sino BD como fallback
                aeropuertos = tempData.getAeropuertos() != null ? 
                    new ArrayList<>(tempData.getAeropuertos()) : 
                    new ArrayList<>(aeropuertoService.listar());
                    
                vuelos = tempData.getVuelos() != null ? 
                    new ArrayList<>(tempData.getVuelos()) : 
                    new ArrayList<>(vueloService.listar());
                    
                pedidosOriginales = tempData.getPedidos() != null ? 
                    new ArrayList<>(tempData.getPedidos()) : 
                    new ArrayList<>(pedidoService.listar());
                
                System.out.println("✅ Datos cargados desde archivos temporales:");
                System.out.println("   - Aeropuertos: " + aeropuertos.size() + 
                    (tempData.getAeropuertos() != null ? " (subidos)" : " (BD)"));
                System.out.println("   - Vuelos: " + vuelos.size() + 
                    (tempData.getVuelos() != null ? " (subidos)" : " (BD)"));
                System.out.println("   - Pedidos: " + pedidosOriginales.size() + 
                    (tempData.getPedidos() != null ? " (subidos)" : " (BD)"));
            } else {
                System.out.println("⚠️ No se encontraron datos temporales, usando BD");
                aeropuertos = new ArrayList<>(aeropuertoService.listar());
                pedidosOriginales = new ArrayList<>(pedidoService.listar());
                vuelos = new ArrayList<>(vueloService.listar());
            }
        } else {
            // Carga normal desde BD
            System.out.println("💾 Cargando datos desde base de datos");
            aeropuertos = new ArrayList<>(aeropuertoService.listar());
            pedidosOriginales = new ArrayList<>(pedidoService.listar());
            vuelos = new ArrayList<>(vueloService.listar());
        }
        
        List<Pedido> pedidos;

        if(habilitarUnitizacion){
            pedidos = expandirPaquetesAUnidadesProducto(pedidosOriginales);
            System.out.println("UNITIZACIÓN APLICADA: " + pedidosOriginales.size() +
                    " → " + pedidos.size() + " unidades");
        }else {
            pedidos = new ArrayList<>(pedidosOriginales);
            System.out.println("UNITIZACIÓN DESHABILITADA");
        }

        return new DataLoadResult(aeropuertos, vuelos, pedidos, pedidosOriginales);
    }

    private List<Pedido> expandirPaquetesAUnidadesProducto(List<Pedido> paquetesOriginales) {
        List<Pedido> unidadesProducto = new ArrayList<>();
        for (Pedido pedidoOriginal : paquetesOriginales) {
            int conteoProductos = (pedidoOriginal.getProductos() != null &&
                    !pedidoOriginal.getProductos().isEmpty())
                    ? pedidoOriginal.getProductos().size() : 1;
            //por cada pedido hay que crear todos sus pedidos correspondientes
            for (int i = 0; i < conteoProductos; i++) {
                unidadesProducto.add(crearUnidadPaquete(pedidoOriginal, i));
            }
        }
        return unidadesProducto;
    }

    private Pedido crearUnidadPaquete(Pedido pedidoOriginal, int indiceUnidad) {
        // Implementación simplificada de creación de unidad
        Pedido unidad = new Pedido();
        // String idUnidadString = pedidoOriginal.getId() + "#" + indiceUnidad;
        // unidad.setId((long) idUnidadString.hashCode());
        // Generar ID único garantizado
        long idUnico = pedidoOriginal.getId() * 1000L + indiceUnidad;
        unidad.setId(idUnico);
        unidad.setCliente(pedidoOriginal.getCliente());
        unidad.setAeropuertoDestinoCodigo(pedidoOriginal.getAeropuertoDestinoCodigo());
        unidad.setAeropuertoOrigenCodigo(pedidoOriginal.getAeropuertoOrigenCodigo());
        unidad.setFechaPedido(pedidoOriginal.getFechaPedido());
        unidad.setFechaLimiteEntrega(pedidoOriginal.getFechaLimiteEntrega());
        unidad.setEstado(pedidoOriginal.getEstado());
        unidad.setPrioridad(pedidoOriginal.getPrioridad());

        ArrayList<Producto> productoUnico = new ArrayList<>();
        if (pedidoOriginal.getProductos() != null) {
            if(indiceUnidad < pedidoOriginal.getProductos().size())
                productoUnico.add(pedidoOriginal.getProductos().get(indiceUnidad));
        }
        unidad.setProductos(productoUnico);

        return unidad;
    }

    public static class DataLoadResult {
        public final List<Aeropuerto> aeropuertos;
        public final List<Vuelo> vuelos;
        public final List<Pedido> pedidos;
        public final List<Pedido> pedidosOriginales;

        public DataLoadResult(List<Aeropuerto> aeropuertos, List<Vuelo> vuelos,
                              List<Pedido> pedidos, List<Pedido> pedidosOriginales) {
            this.aeropuertos = aeropuertos;
            this.vuelos = vuelos;
            this.pedidos = pedidos;
            this.pedidosOriginales = pedidosOriginales;
        }
    }
}
