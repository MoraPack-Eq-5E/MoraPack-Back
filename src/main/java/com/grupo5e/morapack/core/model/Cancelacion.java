package com.grupo5e.morapack.core.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "cancelaciones", indexes = {
    @Index(name = "idx_cancelacion_origen_destino", columnList = "codigoIATAOrigen, codigoIATADestino"),
    @Index(name = "idx_cancelacion_fecha", columnList = "fechaHoraCancelacion")
})
public class Cancelacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(nullable = false)
    private int diasCancelado;

    @Column(nullable = false, length = 4)
    private String codigoIATAOrigen;

    @Column(nullable = false, length = 4)
    private String codigoIATADestino;

    @Column(nullable = false)
    private int hora;

    @Column(nullable = false)
    private int minuto;

    // Campos calculados para facilitar consultas
    private LocalTime horaCancelacion;
    
    private LocalDateTime fechaHoraCancelacion;

    // Relación opcional con vuelo (si se identifica el vuelo específico cancelado)
    @ManyToOne
    @JoinColumn(name = "vuelo_id", referencedColumnName = "id")
    private Vuelo vuelo;

    /**
     * Calcula y establece la hora de cancelación basándose en hora y minuto
     */
    @PrePersist
    @PreUpdate
    public void calcularHoraCancelacion() {
        if (hora >= 0 && hora <= 23 && minuto >= 0 && minuto <= 59) {
            this.horaCancelacion = LocalTime.of(hora, minuto);
        }
    }

    /**
     * Genera el identificador del vuelo afectado
     * Formato: "ORIGEN-DESTINO-HH:MM"
     */
    public String getIdentificadorVueloAfectado() {
        if (codigoIATAOrigen == null || codigoIATADestino == null) {
            return null;
        }
        return String.format("%s-%s-%02d:%02d",
            codigoIATAOrigen,
            codigoIATADestino,
            hora,
            minuto
        );
    }
}
