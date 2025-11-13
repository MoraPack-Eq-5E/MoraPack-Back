package com.grupo5e.morapack.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Evento temporal en la simulación de vuelos.
 * Representa un evento de salida o llegada de vuelo en el timeline.
 * Siguiendo patrón de MoraPack-Backend: FlightTimelineEvent
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Evento temporal en la simulación de vuelos")
public class EventoLineaDeTiempoVueloDTO {
    
    @Schema(description = "ID único del evento")
    private String idEvento;
    
    @Schema(description = "Tipo de evento: DEPARTURE o ARRIVAL")
    private String tipoEvento;
    
    @Schema(description = "Momento del evento")
    private LocalDateTime horaEvento;
    
    @Schema(description = "ID del vuelo")
    private Integer idVuelo;
    
    @Schema(description = "Código del vuelo")
    private String codigoVuelo;
    
    @Schema(description = "ID del producto representativo")
    private Integer idProducto;
    
    @Schema(description = "ID del pedido")
    private Integer idPedido;
    
    @Schema(description = "Ciudad de origen")
    private String ciudadOrigen;
    
    @Schema(description = "Ciudad de destino")
    private String ciudadDestino;
    
    @Schema(description = "ID del aeropuerto de origen")
    private Integer idAeropuertoOrigen;
    
    @Schema(description = "ID del aeropuerto de destino")
    private Integer idAeropuertoDestino;
    
    @Schema(description = "Tiempo de transporte en días")
    private Double tiempoTransporteDias;

    @Schema(description = "Descripción legible del evento")
    private String descripcion;

    private Map<String, Object> metricas;
}

