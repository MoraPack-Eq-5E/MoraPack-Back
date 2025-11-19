package com.grupo5e.morapack.algorithm.input;

import com.grupo5e.morapack.core.model.Aeropuerto;
import com.grupo5e.morapack.core.model.Cancelacion;
import com.grupo5e.morapack.core.model.Pedido;
import com.grupo5e.morapack.core.model.Vuelo;
import com.grupo5e.morapack.utils.LectorAeropuerto;
import com.grupo5e.morapack.utils.LectorCancelaciones;
import com.grupo5e.morapack.utils.LectorVuelos;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementación de FuenteDatosInput que lee desde archivos .txt
 * en el directorio data/
 * 
 * Usa los lectores existentes: LectorAeropuerto, LectorVuelos, LectorPedidos
 */
@Component
public class FuenteDatosArchivo implements FuenteDatosInput {
    
    private static final String RUTA_AEROPUERTOS = "data/aeropuertosinfo.txt";
    private static final String RUTA_VUELOS = "data/vuelos.txt";
    private static final String RUTA_CANCELACIONES = "data/cancelaciones.txt";
    
    @Override
    public void inicializar() {
        // No necesita inicialización para archivos
    }
    
    @Override
    public String obtenerNombreFuente() {
        return "ARCHIVO";
    }
    
    @Override
    public List<Aeropuerto> cargarAeropuertos() {
        try {
            LectorAeropuerto lector = new LectorAeropuerto(RUTA_AEROPUERTOS);
            ArrayList<Aeropuerto> aeropuertos = lector.leerAeropuertos();
            System.out.println("✓ Cargados " + aeropuertos.size() + " aeropuertos desde archivo");
            return aeropuertos;
        } catch (Exception e) {
            System.err.println("✗ Error cargando aeropuertos desde archivo: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Vuelo> cargarVuelos(List<Aeropuerto> aeropuertos) {
        try {
            // LectorVuelos requiere ArrayList específicamente
            ArrayList<Aeropuerto> aeropuertosArray = aeropuertos instanceof ArrayList ? 
                (ArrayList<Aeropuerto>) aeropuertos : new ArrayList<>(aeropuertos);
            
            LectorVuelos lector = new LectorVuelos(RUTA_VUELOS, aeropuertosArray);
            ArrayList<Vuelo> vuelos = lector.leerVuelos();
            System.out.println("✓ Cargados " + vuelos.size() + " vuelos desde archivo");
            return vuelos;
        } catch (Exception e) {
            System.err.println("✗ Error cargando vuelos desde archivo: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    @Override
    public List<Cancelacion> cargarCancelaciones(List<Vuelo> vuelos) {
        try {
            // LectorVuelos requiere ArrayList específicamente
            ArrayList<Vuelo> vuelosArray = vuelos instanceof ArrayList ?
                    (ArrayList<Vuelo>) vuelos : new ArrayList<>(vuelos);

            LectorCancelaciones lector = new LectorCancelaciones(RUTA_CANCELACIONES, vuelosArray);
            List<Cancelacion> cancelaciones = lector.leerCancelaciones();
            System.out.println("✓ Cargados " + cancelaciones.size() + " cancelaciones desde archivo");
            return cancelaciones;
        } catch (Exception e) {
            System.err.println("✗ Error cargando cancelaciones desde archivo: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @Override
    public List<Pedido> cargarPedidos(List<Aeropuerto> aeropuertos) {
        // NOTA: LectorPedidos requiere PedidoService (Spring), no es usable en modo ARCHIVO puro
        // Para modo ARCHIVO, ALNSSolver usa sus propios métodos de carga
        System.out.println("⚠️ Carga de pedidos desde archivo requiere modo BASEDATOS o ALNSSolver directo");
        System.out.println("   Use FuenteDatosBaseDatos o los constructores existentes de ALNSSolver");
        return new ArrayList<>();
    }
}

