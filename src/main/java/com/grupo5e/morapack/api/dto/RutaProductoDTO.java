package com.grupo5e.morapack.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * DTO para la ruta completa de un producto individual.
 * Permite tracking a nivel de producto como especifica el problema:
 * "Los productos pueden llegar en distintos momentos siempre que todos lleguen dentro del plazo establecido"
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Ruta asignada a un producto individual con su secuencia de vuelos")
public class RutaProductoDTO {
    
    @Schema(description = "ID del producto", example = "1234")
    private Integer idProducto;
    
    @Schema(description = "ID del pedido padre", example = "45")
    private Integer idPedido;
    
    @Schema(description = "Nombre del pedido", example = "Pedido-45")
    private String nombrePedido;
    
    @Schema(description = "Nombre del producto", example = "Producto-1234")
    private String nombreProducto;
    
    @Schema(description = "Peso del producto en kg", example = "5.5")
    private Double peso;
    
    @Schema(description = "Volumen del producto en m³", example = "0.25")
    private Double volumen;
    
    @Schema(description = "Código IATA del aeropuerto de origen", example = "SKBO")
    private String codigoOrigen;
    
    @Schema(description = "Código IATA del aeropuerto de destino", example = "EDDI")
    private String codigoDestino;
    
    @Schema(description = "Secuencia de vuelos asignados a este producto")
    private List<VueloSimpleDTO> vuelos;
    
    @Schema(description = "Cantidad de vuelos en la ruta", example = "2")
    private Integer cantidadVuelos;
    
    @Schema(description = "Tiempo total estimado de transporte en horas", example = "12.5")
    private Double tiempoTotalHoras;
    
    @Schema(description = "Estado del producto", example = "EN_TRANSITO")
    private String estado;

    @Schema(description = "Fecha y hora de salida del primer vuelo del producto")
    private LocalDateTime horaSalida;

    @Schema(description = "Fecha y hora de llegada del primer vuelo del producto")
    private LocalDateTime horaLlegada;

    private LocalDateTime fechaPedido;
}

