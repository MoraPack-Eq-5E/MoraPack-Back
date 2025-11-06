package com.grupo5e.morapack.repository;

import com.grupo5e.morapack.core.model.PedidoTemporal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PedidoTemporalRepository extends JpaRepository<PedidoTemporal, Long> {
    void deleteAll(); // para limpiar la tabla antes de una nueva carga
}
