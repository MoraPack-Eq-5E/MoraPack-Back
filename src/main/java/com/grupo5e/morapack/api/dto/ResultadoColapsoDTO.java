package com.grupo5e.morapack.api.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ResultadoColapsoDTO {
    private Boolean exitoso;
    private String mensaje;
    private LocalDateTime horaInicio;
    private LocalDateTime horaFin;
    private Long duracionSegundos;
    private Integer iteracionesTotales;
    private String tipoColapso;
    private List<String> condicionesColapso;
    private List<String> bottlenecks;
    private Integer pedidosAsignados;
    private Integer pedidosTotales;
    private Integer almacenesLlenos;
    private Integer vuelosSaturados;
    private List<RutaProductoDTO> rutasProductos;
    private LineaDeTiempoSimulacionDTO lineaDeTiempo;
    private Map<String, Object> metricasDetalladas;
}