package com.grupo5e.morapack.algorithm.alns;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.ArrayList;
import com.grupo5e.morapack.core.model.Pedido;
import com.grupo5e.morapack.core.model.Vuelo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Clase para capturar el estado de la simulación en modo colapso
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EventoSimulacion {
    private String fase;
    private int iteracion;
    private LocalDateTime timestamp;
    private int pedidosAsignados;
    private int pedidosTotales;
    private int almacenesLlenos;
    private int vuelosSaturados;
    private int pedidosCriticosNoAsignados;
    private HashMap<Pedido, ArrayList<Vuelo>> rutasMuestreadas;
}