package com.grupo5e.morapack.core.model;

import com.grupo5e.morapack.core.enums.EstadoInstanciaVuelo;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Representa una instancia específica de un vuelo en un día/hora particular.
 * 
 * Ejemplo:
 * - Vuelo LIM-CUZ sale diariamente a las 08:00
 * - InstanciaVuelo para Día 1: fechaHoraSalida = 2025-01-02T08:00
 * - InstanciaVuelo para Día 2: fechaHoraSalida = 2025-01-03T08:00
 * 
 * Esto permite tracking de capacidad por salida individual, no solo por ruta.
 */
@Entity
@Table(name = "instancias_vuelo")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstanciaVuelo {
    
    @Id
    @Column(name = "id_instancia", length = 50)
    private String idInstancia;  // "FL-{vueloId}-DAY-{day}-{HHmm}"
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vuelo_base_id", nullable = false)
    private Vuelo vueloBase;
    
    @Column(name = "fecha_hora_salida", nullable = false)
    private LocalDateTime fechaHoraSalida;
    
    @Column(name = "fecha_hora_llegada", nullable = false)
    private LocalDateTime fechaHoraLlegada;
    
    @Column(name = "dia_instancia", nullable = false)
    private Integer diaInstancia; //???
    
    @Column(name = "capacidad_maxima", nullable = false)
    private Integer capacidadMaxima;
    
    @Column(name = "capacidad_usada", nullable = false)
    @Builder.Default
    private Integer capacidadUsada = 0;

    @Column(name = "estado_instancia", nullable = false)
    @Enumerated(EnumType.STRING)
    private EstadoInstanciaVuelo estadoInstancia;

    /**
     * Genera un ID único para esta instancia de vuelo.
     * Formato: "FL-{vueloId}-DAY-{day}-{HHmm}"
     * 
     * Ejemplo: "FL-45-DAY-0-0800" = Vuelo 45, Día 0, Salida 08:00
     * 
     * @return ID único de la instancia
     */
    public String generarIdInstancia() {
        if (vueloBase == null || fechaHoraSalida == null || diaInstancia == null) {
            throw new IllegalStateException("Cannot generate instance ID without vueloBase, fechaHoraSalida, and diaInstancia");
        }
        
        this.idInstancia = String.format("FL-%d-DAY-%d-%02d%02d",
            vueloBase.getId(),
            diaInstancia,
            fechaHoraSalida.getHour(),
            fechaHoraSalida.getMinute()
        );
        return this.idInstancia;
    }
    
    /**
     * Verifica si esta instancia tiene capacidad disponible para la cantidad solicitada.
     * 
     * @param cantidad Cantidad de productos a verificar
     * @return true si hay capacidad suficiente, false en caso contrario
     */
    public boolean tieneCapacidad(int cantidad) {
        return (capacidadUsada + cantidad) <= capacidadMaxima;
    }
    
    /**
     * Reserva capacidad en esta instancia de vuelo.
     * 
     * @param cantidad Cantidad de productos a reservar
     * @throws IllegalStateException si no hay capacidad suficiente
     */
    public void reservarCapacidad(int cantidad) {
        if (!tieneCapacidad(cantidad)) {
            throw new IllegalStateException(
                String.format("Insufficient capacity in flight instance %s: requested %d, available %d",
                    idInstancia, cantidad, capacidadMaxima - capacidadUsada)
            );
        }
        this.capacidadUsada += cantidad;
    }
    
    /**
     * Libera capacidad en esta instancia de vuelo.
     * 
     * @param cantidad Cantidad de productos a liberar
     */
    public void liberarCapacidad(int cantidad) {
        this.capacidadUsada = Math.max(0, this.capacidadUsada - cantidad);
    }
    
    /**
     * Obtiene la capacidad disponible actual.
     * 
     * @return Capacidad disponible (capacidad máxima - capacidad usada)
     */
    public int getCapacidadDisponible() {
        return capacidadMaxima - capacidadUsada;
    }
    
    @Override
    public String toString() {
        return String.format("InstanciaVuelo[id=%s, vuelo=%s, salida=%s, capacidad=%d/%d]",
            idInstancia,
            vueloBase != null ? vueloBase.getIdentificadorVuelo() : "null",
            fechaHoraSalida,
            capacidadUsada,
            capacidadMaxima
        );
    }
}

