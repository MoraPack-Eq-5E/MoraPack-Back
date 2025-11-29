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
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pedido_seq")
    @SequenceGenerator(name = "pedido_seq", sequenceName = "pedidos_id_seq", allocationSize = 50)
    private Integer id;

    @Column(name = "external_id", length = 50)
    private String externalId;

    @Column(name = "nombre", nullable = true)
    private String nombre;

    // Relación: muchos pedidos pertenecen a un cliente
//    @ManyToOne(fetch = FetchType.LAZY, optional = true)
//    @JoinColumn(name = "cliente_id", nullable = true)
//    private Cliente cliente;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumns({
            @JoinColumn(name = "cliente_id", referencedColumnName = "id", nullable = true),
            @JoinColumn(name = "cliente_tipo_data", referencedColumnName = "tipo_data", nullable = true)
    })
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

    private LocalDateTime fechaEntrega;

    // Relación con Ruta (opcional si ya tienes la clase)
    // NOTA: La relación con Ruta ahora se maneja desde Ruta.java
    // con tabla intermedia "ruta_pedidos" (mappedBy en el lado de Ruta)
    @ManyToMany(mappedBy = "pedidos")
    private List<Ruta> rutas;

    private double prioridad;

    // Relación: un paquete puede contener varios productos
    // OPTIMIZACIÓN: LAZY para evitar problema N+1 (cargar productos solo cuando se necesiten)
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Producto> productos;

    // Relación: un pedido puede tener varios planes de viaje (histórico)
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PlanViaje> planesViaje;

    private int cantidadProductos;

    @Column(nullable = true)
    private int tipoData;
    // ===================================================================
    // MÉTODOS DE OPTIMIZACIÓN
    // ===================================================================
    
    /**
     * OPTIMIZACIÓN: Obtiene la cantidad de productos SIN cargar la lista
     * 
     * Usa el campo cantidadProductos directo (si está disponible) para evitar
     * un query adicional a la tabla productos.
     * 
     * Úsalo en el algoritmo en lugar de getProductos().size()
     * 
     * @return Cantidad de productos del pedido
     */
    public int getCantidadProductosRapido() {
        // Prioridad 1: Usar campo directo (más rápido)
        if (this.cantidadProductos > 0) {
            return this.cantidadProductos;
        }
        
        // Prioridad 2: Si el campo está en 0 pero hay productos cargados
        if (this.productos != null && !this.productos.isEmpty()) {
            return this.productos.size();
        }
        
        // Prioridad 3: Default a 1 (para compatibilidad)
        return 1;
    }
    
    /**
     * OPTIMIZACIÓN: Verifica si tiene productos SIN cargarlos
     * 
     * @return true si tiene al menos un producto
     */
    public boolean tieneProductos() {
        return this.cantidadProductos > 0;
    }
    
    /**
     * HELPER: Asegura que cantidadProductos esté sincronizado
     * Llamar esto ANTES de persistir el pedido
     */
    public void sincronizarCantidadProductos() {
        if (this.productos != null && !this.productos.isEmpty()) {
            this.cantidadProductos = this.productos.size();
        } else if (this.cantidadProductos == 0) {
            // Si no hay productos cargados ni cantidad, default a 1
            this.cantidadProductos = 1;
        }
    }
}