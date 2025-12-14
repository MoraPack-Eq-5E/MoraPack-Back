package com.grupo5e.morapack.core.model;

import com.grupo5e.morapack.core.enums.EstadoInstanciaVuelo;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Representa una instancia específica de un vuelo en un día/hora particular.
 * 
 * Ejemplo:
 * - Vuelo LIM-CUZ sale diariamente a las 08:00
 * - InstanciaVuelo de vuelo para la fecha -> fechaHoraSalida = 2025-01-02T08:00
 * - InstanciaVuelo de vuelo para la fecha -> fechaHoraSalida = 2025-01-03T08:00
 * 
 * Esto permite tracking de capacidad por salida individual, no solo por ruta.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "instancias_vuelo")
public class InstanciaVuelo {
    
    @Id
    @Column(name = "id_instancia", length = 50)
    private String idInstancia;  // "FL-{vueloId}-{yyyyMMDD}-{HHmm}"
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vuelo_base_id", nullable = false)
    private Vuelo vueloBase;
    
    @Column(name = "fecha_hora_salida", nullable = false)
    private LocalDateTime fechaHoraSalida;
    
    @Column(name = "fecha_hora_llegada", nullable = false)
    private LocalDateTime fechaHoraLlegada;
    
    @Column(name = "capacidad_maxima", nullable = false)
    private Integer capacidadMaxima;
    
    @Column(name = "capacidad_usada", nullable = false)
    @Builder.Default
    private Integer capacidadUsada = 0;

    @Column(name = "estado_instancia", nullable = false)
    @Enumerated(EnumType.STRING)
    private EstadoInstanciaVuelo estadoInstancia;

    // Relación Inversa (Opcional pero útil):
    // Te permite hacer instancia.getAsignaciones() para ver qué lleva cargado
    @OneToMany(mappedBy = "instanciaVuelo", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<ProductAssignment> asignaciones = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InstanciaVuelo)) return false;
        InstanciaVuelo that = (InstanciaVuelo) o;
        return Objects.equals(idInstancia, that.idInstancia);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idInstancia);
    }
    public InstanciaVuelo(String instanciaId,Vuelo vuelo,LocalDateTime horaSalidaReal,LocalDateTime horaLlegadaReal){
        this.idInstancia = instanciaId;
        this.vueloBase = vuelo;
        this.fechaHoraSalida = horaSalidaReal;
        this.fechaHoraLlegada = horaLlegadaReal;
        this.capacidadMaxima = vuelo.getCapacidadMaxima();
        this.capacidadUsada = 0;
        this.estadoInstancia = EstadoInstanciaVuelo.PLANIFICADO;
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

