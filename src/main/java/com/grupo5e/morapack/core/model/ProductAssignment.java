package com.grupo5e.morapack.core.model;

import com.grupo5e.morapack.core.enums.EstadoProducto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "product_assignment")
public class ProductAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer productoId;

    @Column(nullable = false)
    private Integer pedidoId;

    // CAMBIO CLAVE: Relación directa en lugar de solo String.
    // Esto asegura integridad referencial en la BD.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_instance_id", nullable = false)
    private InstanciaVuelo instanciaVuelo;

    private Integer indiceEnRuta; // posición en la secuencia de vuelos

    @Enumerated(EnumType.STRING)
    private EstadoProducto estadoProducto; // EN_ALMACEN, EN_VUELO, ENTREGADO
}

