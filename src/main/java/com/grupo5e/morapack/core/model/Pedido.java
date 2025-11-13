package com.grupo5e.morapack.core.model;

import com.grupo5e.morapack.core.enums.EstadoPedido;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "pedidos")
public class Pedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "external_id", unique = true, length = 50)
    private String externalId;

    @Column(name = "nombre", nullable = true)
    private String nombre;

    // Relación: muchos pedidos pertenecen a un cliente
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "cliente_id", nullable = true)
    private Cliente cliente;

    // Aeropuerto destino
    private String aeropuertoDestinoCodigo;

    private LocalDateTime fechaPedido;
    
    @Column(nullable = false)
    private LocalDateTime fechaLimiteEntrega;

    @Enumerated(EnumType.STRING)
    private EstadoPedido estado;

    // Aeropuerto donde se encuentra actualmente
    private String aeropuertoOrigenCodigo;

    private Double horasRecogida;

    @CreationTimestamp
    private LocalDateTime fechaCreacion;

    @UpdateTimestamp
    private LocalDateTime fechaActualizacion;

    // Relación con Ruta (opcional si ya tienes la clase)
    // NOTA: La relación con Ruta ahora se maneja desde Ruta.java
    // con tabla intermedia "ruta_pedidos" (mappedBy en el lado de Ruta)
    @ManyToMany(mappedBy = "pedidos")
    private List<Ruta> rutas;

    private double prioridad;

    // Relación: un paquete puede contener varios productos
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Producto> productos;

    // Relación: un pedido puede tener varios planes de viaje (histórico)
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PlanViaje> planesViaje;

    private int cantidadProductos;
}