package com.grupo5e.morapack.repository;

import com.grupo5e.morapack.core.model.Almacen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlmacenRepository extends JpaRepository<Almacen, Integer> {
    Optional<Almacen> findByAeropuertoId(Integer aeropuertoId);
    List<Almacen> findByEsAlmacenPrincipal(Boolean esPrincipal);
}

