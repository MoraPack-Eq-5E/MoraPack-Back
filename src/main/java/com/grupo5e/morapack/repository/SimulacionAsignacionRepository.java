package com.grupo5e.morapack.repository;

import com.grupo5e.morapack.core.model.Pedido;
import com.grupo5e.morapack.core.model.SimulacionAsignacion;
import com.grupo5e.morapack.core.model.SimulacionSemanal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SimulacionAsignacionRepository extends JpaRepository<SimulacionAsignacion, Long> {
    List<SimulacionAsignacion> findByPedido(Pedido pedido);

    List<SimulacionAsignacion> findByMinutoInicioLessThanEqualAndMinutoFinGreaterThanEqual(
        Integer minutoInicio, Integer minutoFin);

    List<SimulacionAsignacion> findByPedidoId(Long pedidoId);

    List<SimulacionAsignacion> findAllByOrderByPedidoIdAscSecuenciaAsc();

}

