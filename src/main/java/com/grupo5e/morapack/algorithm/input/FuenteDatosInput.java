package com.grupo5e.morapack.algorithm.input;

import com.grupo5e.morapack.algorithm.alns.TramoConTiempo;
import com.grupo5e.morapack.core.model.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Interfaz para abstraer las fuentes de datos del algoritmo ALNS.
 * Permite cambiar entre modo ARCHIVO (data/*.txt) y BASEDATOS (PostgreSQL/H2)
 * sin modificar la lógica del algoritmo.
 * 
 * Soporte para ventanas de tiempo para escenarios diarios/semanales.
 */
public interface FuenteDatosInput {
    
    /**
     * Inicializa recursos necesarios por la fuente de datos
     */
    void inicializar();
    
    /**
     * Obtiene el nombre identificador de esta fuente de datos
     * @return "ARCHIVO" o "BASEDATOS"
     */
    String obtenerNombreFuente();
    
    /**
     * Carga todos los aeropuertos desde la fuente de datos
     * @return Lista de aeropuertos con información completa
     */
    List<Aeropuerto> cargarAeropuertos();
    
    /**
     * Carga todos los vuelos desde la fuente de datos
     * @param aeropuertos Lista de aeropuertos para vincular vuelos
     * @return Lista de vuelos con información completa
     */
    List<Vuelo> cargarVuelos(List<Aeropuerto> aeropuertos);
    
    /**
     * Carga todos los pedidos desde la fuente de datos
     * @param aeropuertos Lista de aeropuertos para vincular pedidos
     * @return Lista de pedidos con información completa (incluyendo productos)
     */
    List<Pedido> cargarPedidos(List<Aeropuerto> aeropuertos);
    List<Cancelacion> cargarCancelaciones(List<Vuelo> vuelos);
    /**
     * Carga pedidos filtrados por ventana de tiempo (para escenarios diarios/semanales)
     * 
     * @param aeropuertos Lista de aeropuertos para vincular pedidos
     * @param horaInicio Hora de inicio de la ventana de simulación
     * @param horaFin Hora de fin de la ventana de simulación
     * @return Lista de pedidos dentro de la ventana de tiempo especificada
     */
    //ESTO NO SIRVE ES EL DEFAULT
    default List<Pedido> cargarPedidosPorVentanaDeTiempo(
            List<Aeropuerto> aeropuertos,
            LocalDateTime horaInicio,
            LocalDateTime horaFin,
            int tipoData) {
        //tipo data 1 = diario (ventana 1 hora)
        //tipo data 0 = semanal (ventana 7 dias)
        //tipo data 2 = colapso (ventana X dias)

        if(tipoData == 1){
            //Se capturan pedidos de la última hora
            //ventana de 1 hora: 10am - 11am -> pedidos de 9am - 10am
            horaFin = horaFin.minusHours(1);
            horaInicio = horaFin.minusHours(1);
        }
        LocalDateTime finalHoraInicio = horaInicio;
        LocalDateTime finalHoraFin = horaFin;
        return cargarPedidos(aeropuertos).stream()
                .filter(p -> {
                    LocalDateTime fechaPedido = p.getFechaPedido();
                    return fechaPedido != null &&
                            !fechaPedido.isBefore(finalHoraInicio) &&
                            !fechaPedido.isAfter(finalHoraFin);
                })
                .toList();
    }
    String cargarInstanciaVuelo(TramoConTiempo tramo);
    Map<String, InstanciaVuelo> inicializarCacheinstancia();
    Map<String, ProductAssignment> inicializarCacheAsignacion();

    void guardarAsignacionesProductos(List<ProductAssignment> asignaciones);
    void guadarInstanciasVuelos(List<InstanciaVuelo> instancias);
}

