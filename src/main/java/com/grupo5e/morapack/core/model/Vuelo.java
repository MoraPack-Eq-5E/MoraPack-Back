package com.grupo5e.morapack.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.grupo5e.morapack.core.enums.EstadoVuelo;

//import java.sql.Time;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import java.time.LocalTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "vuelos")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "rutas"})
public class Vuelo {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "vuelo_seq")
    @SequenceGenerator(name = "vuelo_seq", sequenceName = "vuelos_id_seq", allocationSize = 50)
    private Integer id;

    // Número de frecuencias por día (ejemplo: 2 vuelos diarios)
    private double frecuenciaPorDia;
//    private Time horaSalida;
//    private Time horaLlegada;
// Campos temporales para identificación y cancelaciones
    private LocalTime horaSalida;
    private LocalTime horaLlegada;
    // Relación: un vuelo tiene un aeropuerto origen
    @ManyToOne
    @JoinColumn(name = "aeropuerto_origen_id", referencedColumnName = "id", nullable = false)
    private Aeropuerto aeropuertoOrigen;

    // Relación: un vuelo tiene un aeropuerto destino
    @ManyToOne
    @JoinColumn(name = "aeropuerto_destino_id", referencedColumnName = "id", nullable = false)
    private Aeropuerto aeropuertoDestino;

    private int capacidadMaxima;
    private int capacidadUsada;

    // Tiempo de transporte (ejemplo: 2.5 horas)
    private double tiempoTransporte;
    private double costo;
    private String latitudActual;
    private String longitudActual;

    @Enumerated(EnumType.STRING)
    private EstadoVuelo estado;

    // NOTA: La relación con Ruta ahora es @ManyToMany en Ruta.java
    // Un vuelo puede estar en múltiples rutas
    @ManyToMany(mappedBy = "vuelos")
    private List<Ruta> rutas;

    @Column(nullable = true)
    private int tipoData;
    /**
     * Genera el identificador único del vuelo basado en ruta y horario.
     * Formato: "ORIGEN-DESTINO-HH:MM"
     * Ejemplo: "SKBO-SEQM-03:34"
     * 
     * @return Identificador único del vuelo, o null si faltan datos
     */
    public String getIdentificadorVuelo() {
        if (aeropuertoOrigen == null || aeropuertoDestino == null || horaSalida == null) {
            return null;
        }
        return String.format("%s-%s-%02d:%02d",
            aeropuertoOrigen.getCodigoIATA(),
            aeropuertoDestino.getCodigoIATA(),
            horaSalida.getHour(),
            horaSalida.getMinute()
        );
    }

    public LocalDateTime calcularSiguienteSalidaDesde(LocalDateTime referencia) {
        LocalTime horaSalida = this.getHoraSalida(); // hora programada diaria

        // Candidato: hoy a la hora del vuelo
        LocalDateTime candidato = referencia
                .withHour(horaSalida.getHour())
                .withMinute(horaSalida.getMinute())
                .withSecond(0)
                .withNano(0);

        // Si el vuelo de hoy ya pasó → usar mañana
        if (!candidato.isAfter(referencia)) {
            candidato = candidato.plusDays(1);
        }

        return candidato;
    }

}
