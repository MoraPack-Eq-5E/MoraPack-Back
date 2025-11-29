package com.grupo5e.morapack.repository;

import com.grupo5e.morapack.core.enums.EstadoVuelo;
import com.grupo5e.morapack.core.model.Aeropuerto;
import com.grupo5e.morapack.core.model.Vuelo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface VueloRepository extends JpaRepository<Vuelo, Integer> {
    List<Vuelo> findByAeropuertoOrigenIdAndAeropuertoDestinoId(Integer origenId, Integer destinoId);
    List<Vuelo> findByEstado(EstadoVuelo estado);
    List<Vuelo> findByCapacidadMaximaGreaterThanEqual(Integer capacidad);
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM vuelos WHERE tipo_data = 0", nativeQuery = true)
    void eliminarTipoDataCero();

    @Query(value = "select count(id) from vuelos where tipo_data = 0",nativeQuery = true)
    int contarTipoDataCero();

    @Query(
            value = "SELECT * FROM vuelos WHERE tipo_data = :tipoData AND estado = 'DISPONIBLE'",
            nativeQuery = true
    )
    List<Vuelo> listarPorTipoData(@Param("tipoData") int tipoData);
}
