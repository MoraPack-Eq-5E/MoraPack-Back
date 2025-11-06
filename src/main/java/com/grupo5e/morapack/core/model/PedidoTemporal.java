package com.grupo5e.morapack.core.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

import com.grupo5e.morapack.core.enums.EstadoPedido;

@Entity
@Table(name = "pedidos_temporales")
@Data
public class PedidoTemporal {

    // @Id
    // @GeneratedValue(strategy = GenerationType.IDENTITY)
    // private Long id; // id autogenerado, no el del pedido real
    @Id
    @Column(name = "id_pedido_archivo")
    private Long id;

    @Column(name = "id_cliente")
    private Long idCliente;

    @Column(name = "aeropuerto_origen")
    private String aeropuertoOrigen;

    @Column(name = "aeropuerto_destino")
    private String aeropuertoDestino;

    @Column(name = "fecha_pedido")
    private LocalDateTime fechaPedido;

    @Column(name = "fecha_limite_entrega")
    private LocalDateTime fechaLimiteEntrega;

    @Column(name = "cantidad_productos")
    private Integer cantidadProductos;

    @Column(name = "prioridad")
    private Double prioridad;

    @Column(name = "estado")
    private EstadoPedido estado;

    // Puedes agregar más campos si los necesitas para simulación
}
