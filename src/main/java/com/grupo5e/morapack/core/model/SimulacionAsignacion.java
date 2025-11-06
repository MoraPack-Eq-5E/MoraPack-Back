package com.grupo5e.morapack.core.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidad para almacenar las asignaciones de la simulación (solución del ALNS)
 * Cada registro representa un vuelo asignado a un pedido
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "simulacion_asignacion")
public class SimulacionAsignacion {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "simulacion_id", nullable = false)
    private SimulacionSemanal simulacion;

    @ManyToOne
    @JoinColumn(name = "pedido_id", nullable = false)
    private PedidoTemporal pedido;

    @Column(name = "secuencia")
    private Integer secuencia; // Orden en la ruta (1, 2, 3...)

    @Column(name = "indice_Producto")
    private Integer indiceProducto; // Índice del producto en el pedido 

    @ManyToOne
    @JoinColumn(name = "vuelo_id", nullable = false)
    private Vuelo vuelo;

    @Column(name = "minuto_inicio")
    private Integer minutoInicio; // Minuto desde T0 donde inicia este vuelo para este paquete

    @Column(name = "minuto_fin")
    private Integer minutoFin; // Minuto donde el paquete llega al destino de este vuelo

    @Column(name = "latitud_inicio")
    private Double latitudInicio;

    @Column(name = "longitud_inicio")
    private Double longitudInicio;

    @Column(name = "latitud_fin")
    private Double latitudFin;

    @Column(name = "longitud_fin")
    private Double longitudFin;
}

