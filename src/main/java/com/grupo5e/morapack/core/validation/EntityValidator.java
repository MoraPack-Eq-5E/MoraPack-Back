package com.grupo5e.morapack.core.validation;

import com.grupo5e.morapack.core.model.*;

/**
 * EntityValidator - Validador de entidades del dominio

 * 
 * Proporciona validaciones robustas para todas las entidades principales.
 */
public class EntityValidator {

    // ========== AEROPUERTO VALIDATION ==========
    
    /**
     * Valida un aeropuerto
     * 
     * @param aeropuerto Aeropuerto a validar
     * @throws IllegalArgumentException si la validación falla
     */
    public static void validateAeropuerto(Aeropuerto aeropuerto) {
        if (aeropuerto == null) {
            throw new IllegalArgumentException("Aeropuerto no puede ser null");
        }
        
        if (aeropuerto.getCodigoIATA() == null || aeropuerto.getCodigoIATA().isBlank()) {
            throw new IllegalArgumentException("Código IATA es requerido");
        }
        
        if (aeropuerto.getCodigoIATA().length() != 3 && aeropuerto.getCodigoIATA().length() != 4) {
            throw new IllegalArgumentException("Código IATA debe tener 3 o 4 caracteres: " + aeropuerto.getCodigoIATA());
        }
        
        if (aeropuerto.getCiudad() == null) {
            throw new IllegalArgumentException("Ciudad es requerida para aeropuerto " + aeropuerto.getCodigoIATA());
        }
        
        if (aeropuerto.getCapacidadMaxima() <= 0) {
            throw new IllegalArgumentException(
                String.format("Capacidad máxima debe ser positiva para aeropuerto %s: %d", 
                    aeropuerto.getCodigoIATA(), aeropuerto.getCapacidadMaxima())
            );
        }
        
        // Nota: Aeropuerto no tiene campo capacidadUsada en nuestra implementación
        // La capacidad se maneja a nivel de almacén temporal en el algoritmo
    }

    // ========== VUELO VALIDATION ==========
    
    /**
     * Valida un vuelo
     * 
     * @param vuelo Vuelo a validar
     * @throws IllegalArgumentException si la validación falla
     */
    public static void validateVuelo(Vuelo vuelo) {
        if (vuelo == null) {
            throw new IllegalArgumentException("Vuelo no puede ser null");
        }
        
        if (vuelo.getAeropuertoOrigen() == null) {
            throw new IllegalArgumentException("Aeropuerto origen es requerido para vuelo " + vuelo.getId());
        }
        
        if (vuelo.getAeropuertoDestino() == null) {
            throw new IllegalArgumentException("Aeropuerto destino es requerido para vuelo " + vuelo.getId());
        }
        
        // Validar que origen y destino sean diferentes
        if (vuelo.getAeropuertoOrigen().getId() != 0 && 
            vuelo.getAeropuertoDestino().getId() != 0 &&
            vuelo.getAeropuertoOrigen().getId() == vuelo.getAeropuertoDestino().getId()) {
            throw new IllegalArgumentException(
                String.format("Aeropuerto origen y destino deben ser diferentes para vuelo %d", vuelo.getId())
            );
        }
        
        if (vuelo.getCapacidadMaxima() <= 0) {
            throw new IllegalArgumentException(
                String.format("Capacidad máxima debe ser positiva para vuelo %d: %d", 
                    vuelo.getId(), vuelo.getCapacidadMaxima())
            );
        }
        
        if (vuelo.getCapacidadUsada() < 0) {
            throw new IllegalArgumentException(
                String.format("Capacidad usada no puede ser negativa para vuelo %d: %d", 
                    vuelo.getId(), vuelo.getCapacidadUsada())
            );
        }
        
        if (vuelo.getCapacidadUsada() > vuelo.getCapacidadMaxima()) {
            throw new IllegalArgumentException(
                String.format("Capacidad usada (%d) no puede exceder capacidad máxima (%d) para vuelo %d", 
                    vuelo.getCapacidadUsada(), vuelo.getCapacidadMaxima(), vuelo.getId())
            );
        }
        
        if (vuelo.getTiempoTransporte() <= 0) {
            throw new IllegalArgumentException(
                String.format("Tiempo de transporte debe ser positivo para vuelo %d: %.2f", 
                    vuelo.getId(), vuelo.getTiempoTransporte())
            );
        }
        
        if (vuelo.getFrecuenciaPorDia() <= 0) {
            throw new IllegalArgumentException(
                String.format("Frecuencia por día debe ser positiva para vuelo %d: %.2f", 
                    vuelo.getId(), vuelo.getFrecuenciaPorDia())
            );
        }
    }
    public static void validateCancelacion(Cancelacion cancelacion) {
        if (cancelacion == null) {
            throw new IllegalArgumentException("cancelacion no puede ser null");
        }

        if (cancelacion.getVuelo() == null) {
            throw new IllegalArgumentException("Vuelo es requerido para cancelacion " + cancelacion.getId());
        }

//        if (vuelo.getAeropuertoDestino() == null) {
//            throw new IllegalArgumentException("Aeropuerto destino es requerido para vuelo " + vuelo.getId());
//        }
//
//        // Validar que origen y destino sean diferentes
//        if (vuelo.getAeropuertoOrigen().getId() != 0 &&
//                vuelo.getAeropuertoDestino().getId() != 0 &&
//                vuelo.getAeropuertoOrigen().getId() == vuelo.getAeropuertoDestino().getId()) {
//            throw new IllegalArgumentException(
//                    String.format("Aeropuerto origen y destino deben ser diferentes para vuelo %d", vuelo.getId())
//            );
//        }
//
//        if (vuelo.getCapacidadMaxima() <= 0) {
//            throw new IllegalArgumentException(
//                    String.format("Capacidad máxima debe ser positiva para vuelo %d: %d",
//                            vuelo.getId(), vuelo.getCapacidadMaxima())
//            );
//        }
//
//        if (vuelo.getCapacidadUsada() < 0) {
//            throw new IllegalArgumentException(
//                    String.format("Capacidad usada no puede ser negativa para vuelo %d: %d",
//                            vuelo.getId(), vuelo.getCapacidadUsada())
//            );
//        }
//
//        if (vuelo.getCapacidadUsada() > vuelo.getCapacidadMaxima()) {
//            throw new IllegalArgumentException(
//                    String.format("Capacidad usada (%d) no puede exceder capacidad máxima (%d) para vuelo %d",
//                            vuelo.getCapacidadUsada(), vuelo.getCapacidadMaxima(), vuelo.getId())
//            );
//        }
//
//        if (vuelo.getTiempoTransporte() <= 0) {
//            throw new IllegalArgumentException(
//                    String.format("Tiempo de transporte debe ser positivo para vuelo %d: %.2f",
//                            vuelo.getId(), vuelo.getTiempoTransporte())
//            );
//        }
//
//        if (vuelo.getFrecuenciaPorDia() <= 0) {
//            throw new IllegalArgumentException(
//                    String.format("Frecuencia por día debe ser positiva para vuelo %d: %.2f",
//                            vuelo.getId(), vuelo.getFrecuenciaPorDia())
//            );
//        }
    }

    // ========== PEDIDO VALIDATION ==========
    
    /**
     * Valida un pedido
     * 
     * @param pedido Pedido a validar
     * @throws IllegalArgumentException si la validación falla
     */
    public static void validatePedido(Pedido pedido) {
        if (pedido == null) {
            throw new IllegalArgumentException("Pedido no puede ser null");
        }
        
        if (pedido.getAeropuertoOrigenCodigo() == null || pedido.getAeropuertoOrigenCodigo().isBlank()) {
            throw new IllegalArgumentException("Código de aeropuerto origen es requerido para pedido " + pedido.getId());
        }
        
        if (pedido.getAeropuertoDestinoCodigo() == null || pedido.getAeropuertoDestinoCodigo().isBlank()) {
            throw new IllegalArgumentException("Código de aeropuerto destino es requerido para pedido " + pedido.getId());
        }
        
        // Validar que origen y destino sean diferentes
        if (pedido.getAeropuertoOrigenCodigo().equals(pedido.getAeropuertoDestinoCodigo())) {
            throw new IllegalArgumentException(
                String.format("Aeropuerto origen y destino deben ser diferentes para pedido %d: %s", 
                    pedido.getId(), pedido.getAeropuertoOrigenCodigo())
            );
        }
        
        if (pedido.getCantidadProductos() <= 0) {
            throw new IllegalArgumentException(
                String.format("Cantidad de productos debe ser positiva para pedido %d: %d", 
                    pedido.getId(), pedido.getCantidadProductos())
            );
        }
        
        if (pedido.getFechaPedido() == null) {
            throw new IllegalArgumentException("Fecha de pedido es requerida para pedido " + pedido.getId());
        }
        
        if (pedido.getFechaLimiteEntrega() == null) {
            throw new IllegalArgumentException("Fecha límite de entrega es requerida para pedido " + pedido.getId());
        }
        
        if (pedido.getFechaLimiteEntrega().isBefore(pedido.getFechaPedido())) {
            throw new IllegalArgumentException(
                String.format("Fecha límite de entrega no puede ser anterior a fecha de pedido para pedido %d", 
                    pedido.getId())
            );
        }
        
        if (pedido.getPrioridad() <= 0) {
            throw new IllegalArgumentException("Prioridad debe ser positiva para pedido " + pedido.getId());
        }
    }

    // ========== CIUDAD VALIDATION ==========
    
    /**
     * Valida una ciudad
     * 
     * @param ciudad Ciudad a validar
     * @throws IllegalArgumentException si la validación falla
     */
    public static void validateCiudad(Ciudad ciudad) {
        if (ciudad == null) {
            throw new IllegalArgumentException("Ciudad no puede ser null");
        }
        
        if (ciudad.getNombre() == null || ciudad.getNombre().isBlank()) {
            throw new IllegalArgumentException("Nombre de ciudad es requerido");
        }
        
        if (ciudad.getContinente() == null) {
            throw new IllegalArgumentException("Continente es requerido para ciudad " + ciudad.getNombre());
        }
    }

    // ========== BATCH VALIDATION ==========
    
    /**
     * Valida una lista de aeropuertos
     * 
     * @param aeropuertos Lista de aeropuertos a validar
     * @return Número de validaciones exitosas
     * @throws IllegalArgumentException si alguna validación falla
     */
    public static int validateAeropuertos(Iterable<Aeropuerto> aeropuertos) {
        int count = 0;
        for (Aeropuerto aeropuerto : aeropuertos) {
            validateAeropuerto(aeropuerto);
            count++;
        }
        return count;
    }

    /**
     * Valida una lista de vuelos
     * 
     * @param vuelos Lista de vuelos a validar
     * @return Número de validaciones exitosas
     * @throws IllegalArgumentException si alguna validación falla
     */
    public static int validateVuelos(Iterable<Vuelo> vuelos) {
        int count = 0;
        for (Vuelo vuelo : vuelos) {
            validateVuelo(vuelo);
            count++;
        }
        return count;
    }
    public static int validateCancelaciones(Iterable<Cancelacion> cancelacions) {
        int count = 0;
        for (Cancelacion cancelacion : cancelacions) {
            validateCancelacion(cancelacion);
            count++;
        }
        return count;
    }

    /**
     * Valida una lista de pedidos
     * 
     * @param pedidos Lista de pedidos a validar
     * @return Número de validaciones exitosas
     * @throws IllegalArgumentException si alguna validación falla
     */
    public static int validatePedidos(Iterable<Pedido> pedidos) {
        int count = 0;
        for (Pedido pedido : pedidos) {
            validatePedido(pedido);
            count++;
        }
        return count;
    }
}

