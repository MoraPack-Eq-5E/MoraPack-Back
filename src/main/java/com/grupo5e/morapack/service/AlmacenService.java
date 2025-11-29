package com.grupo5e.morapack.service;

import com.grupo5e.morapack.core.model.Almacen;
import com.grupo5e.morapack.repository.AlmacenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlmacenService {
    
    private final AlmacenRepository almacenRepository;
    
    public Almacen obtenerPorId(Integer id) {
        return almacenRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Almacén no encontrado: " + id));
    }
    
    public List<Almacen> listar() {
        return almacenRepository.findAll();
    }
    
    public Optional<Almacen> obtenerPorAeropuerto(Integer aeropuertoId) {
        return almacenRepository.findByAeropuertoId(aeropuertoId);
    }
    
    public List<Almacen> listarPrincipales() {
        return almacenRepository.findByEsAlmacenPrincipal(true);
    }
    
    @Transactional
    public Almacen asignar(Integer id, int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("Cantidad debe ser mayor a 0");
        }
        
        Almacen almacen = obtenerPorId(id);
        int nuevaCapacidad = almacen.getCapacidadUsada() + cantidad;
        
        // Solo validar si NO es almacén principal
        if (!Boolean.TRUE.equals(almacen.getEsAlmacenPrincipal())) {
            if (nuevaCapacidad > almacen.getCapacidadMaxima()) {
                log.warn("Almacén {} supera capacidad: {} > {}", 
                    id, nuevaCapacidad, almacen.getCapacidadMaxima());
                throw new IllegalArgumentException("Capacidad excedida en almacén " + id);
            }
        }
        
        almacen.setCapacidadUsada(nuevaCapacidad);
        Almacen actualizado = almacenRepository.save(almacen);
        
        log.info("Almacén {} ({}): Asignado {} → Capacidad usada: {}/{}", 
            id, almacen.getNombre(), cantidad, actualizado.getCapacidadUsada(), 
            actualizado.getCapacidadMaxima());
        
        return actualizado;
    }
    
    @Transactional
    public Almacen liberar(Integer id, int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("Cantidad debe ser mayor a 0");
        }
        
        Almacen almacen = obtenerPorId(id);
        int nuevaCapacidad = Math.max(0, almacen.getCapacidadUsada() - cantidad);
        
        almacen.setCapacidadUsada(nuevaCapacidad);
        Almacen actualizado = almacenRepository.save(almacen);
        
        log.info("Almacén {} ({}): Liberado {} → Capacidad usada: {}/{}", 
            id, almacen.getNombre(), cantidad, actualizado.getCapacidadUsada(), 
            actualizado.getCapacidadMaxima());
        
        return actualizado;
    }
    
    @Transactional
    public Almacen crear(Almacen almacen) {
        validar(almacen);
        return almacenRepository.save(almacen);
    }
    
    @Transactional
    public Almacen actualizar(Integer id, Almacen almacenActualizado) {
        Almacen almacen = obtenerPorId(id);
        
        if (almacenActualizado.getNombre() != null) {
            almacen.setNombre(almacenActualizado.getNombre());
        }
        if (almacenActualizado.getCapacidadMaxima() != null) {
            almacen.setCapacidadMaxima(almacenActualizado.getCapacidadMaxima());
        }
        if (almacenActualizado.getEsAlmacenPrincipal() != null) {
            almacen.setEsAlmacenPrincipal(almacenActualizado.getEsAlmacenPrincipal());
        }
        
        validar(almacen);
        return almacenRepository.save(almacen);
    }
    
    private void validar(Almacen almacen) {
        if (almacen.getNombre() == null || almacen.getNombre().isBlank()) {
            throw new IllegalArgumentException("Nombre es requerido");
        }
        if (almacen.getCapacidadMaxima() == null || almacen.getCapacidadMaxima() < 0) {
            throw new IllegalArgumentException("Capacidad máxima inválida");
        }
        if (almacen.getCapacidadUsada() == null || almacen.getCapacidadUsada() < 0) {
            throw new IllegalArgumentException("Capacidad usada inválida");
        }
        
        // Validar solo si NO es almacén principal
        if (!Boolean.TRUE.equals(almacen.getEsAlmacenPrincipal())) {
            if (almacen.getCapacidadUsada() > almacen.getCapacidadMaxima()) {
                throw new IllegalArgumentException("Capacidad usada no puede exceder máxima");
            }
        }
    }
}

