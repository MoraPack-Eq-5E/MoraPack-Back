package com.grupo5e.morapack.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * DTO simplificado para vuelo dentro de una ruta de producto.
 * Evita referencias circulares y minimiza datos transferidos.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Información simplificada de un vuelo en la ruta")
public class VueloSimpleDTO {

    @Schema(description = "ID del vuelo", example = "789")
    private Integer id;

    @Schema(description = "Código del vuelo", example = "VL123")
    private String codigo;

    @Schema(description = "Código IATA del aeropuerto de origen", example = "SKBO")
    private String codigoOrigen;

    @Schema(description = "Código IATA del aeropuerto de destino", example = "EDDM")
    private String codigoDestino;

    @Schema(description = "ID del aeropuerto de origen")
    private Integer idAeropuertoOrigen;

    @Schema(description = "ID del aeropuerto de destino")
    private Integer idAeropuertoDestino;

    @Schema(description = "Hora de salida", example = "10:30")
    private LocalTime horaSalida;

    @Schema(description = "Hora de llegada", example = "14:45")
    private LocalTime horaLlegada;

    @Schema(description = "Tiempo de transporte en horas", example = "4.25")
    private Double tiempoTransporte;

    @Schema(description = "Costo del vuelo", example = "1250.50")
    private Double costo;

    @Schema(description = "Capacidad usada del vuelo", example = "150")
    private Integer capacidadUsada;
    
    @Schema(description = "Capacidad máxima del vuelo", example = "300")
    private Integer capacidadMaxima;

    // NUEVOS CAMPOS: Fechas absolutas calculadas por ALNS
    
    @Schema(description = "Fecha y hora real de salida calculada por ALNS", example = "2025-01-05T10:30:00")
    private LocalDateTime horaSalidaReal;
    
    @Schema(description = "Fecha y hora real de llegada calculada por ALNS", example = "2025-01-05T14:45:00")
    private LocalDateTime horaLlegadaReal;
}
