package com.grupo5e.morapack.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

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

    @Schema(description = "ID del pedido representativo (para compatibilidad)")
    private Integer idPedido;

    @Schema(description = "Lista de TODOS los IDs de pedidos en este vuelo agrupado")
    private List<Integer> idsPedidos;

    @Schema(description = "Ciudad de origen")
    private String ciudadOrigen;

    @Schema(description = "Ciudad de destino")
    private String ciudadDestino;

    @Schema(description = "ID del aeropuerto de origen")
    private Integer idAeropuertoOrigen;

    @Schema(description = "ID del aeropuerto de destino")
    private Integer idAeropuertoDestino;

    @Schema(description = "Código IATA del aeropuerto de origen", example = "SKBO")
    private String codigoIATAOrigen;

    @Schema(description = "Código IATA del aeropuerto de destino", example = "EKCH")
    private String codigoIATADestino;

    @Schema(description = "Indica si este vuelo llega al destino FINAL del pedido (no una escala)")
    private Boolean esDestinoFinal;

    @Schema(description = "Tiempo de transporte en días")
    private Double tiempoTransporteDias;

    @Schema(description = "Capacidad máxima del vuelo en productos", example = "300")
    private Integer capacidadMaxima;

    @Schema(description = "Cantidad de productos en este vuelo", example = "2")
    private Integer cantidadProductos;
}
