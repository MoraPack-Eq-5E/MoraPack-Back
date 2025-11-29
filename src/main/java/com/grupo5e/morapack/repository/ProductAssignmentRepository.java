package com.grupo5e.morapack.repository;

import com.grupo5e.morapack.core.model.ProductAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductAssignmentRepository extends JpaRepository<ProductAssignment, Long> {
    List<ProductAssignment> findByPedidoId(Integer pedidoId);
    List<ProductAssignment> findByFlightInstanceId(String flightInstanceId);
    @Query (
            value = "SELECT * FROM product_assignment WHERE pedido_id = ?1 AND flight_instance_id = ?2 " +
                    "AND estado_producto = 'EN_ALMACEN'",
            nativeQuery = true
    )
    List<ProductAssignment> findByPedidoIdAndFlightInstanceId(Integer pedidoId, String flightInstanceId);

    @Query(
            value = "SELECT * FROM product_assignment WHERE estado_producto = 'EN_ALMACEN'",
            nativeQuery = true
    )
    List<ProductAssignment> pedidosEnAlmacen();
}
