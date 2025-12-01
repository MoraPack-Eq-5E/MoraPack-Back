package com.grupo5e.morapack.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Timeline temporal de la simulación con eventos ordenados.
 * Siguiendo patrón de MoraPack-Backend: SimulationTimelineResult
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Timeline temporal de la simulación con eventos ordenados")
public class LineaDeTiempoSimulacionDTO {
    
    @Schema(description = "Hora de inicio de la simulación")
    private LocalDateTime horaInicioSimulacion;
    
    @Schema(description = "Hora de fin de la simulación")
    private LocalDateTime horaFinSimulacion;
    
    @Schema(description = "Duración total en minutos")
    private Long duracionTotalMinutos;
    
    @Schema(description = "Eventos de vuelo ordenados temporalmente")
    private List<EventoLineaDeTiempoVueloDTO> eventos;
    
    @Schema(description = "Total de eventos en la simulación")
    private Integer totalEventos;
    
    @Schema(description = "Rutas de productos para referencia")
    private List<RutaProductoDTO> rutasProductos;
    
    @Schema(description = "Total de productos en la simulación")
    private Integer totalProductos;
    
    @Schema(description = "Total de vuelos únicos")
    private Integer totalVuelos;
    
    @Schema(description = "Total de aeropuertos involucrados")
    private Integer totalAeropuertos;


}

