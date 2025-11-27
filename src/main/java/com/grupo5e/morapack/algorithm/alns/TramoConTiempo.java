package com.grupo5e.morapack.algorithm.alns;

import com.grupo5e.morapack.core.model.Vuelo;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Representa un tramo de vuelo con sus tiempos absolutos calculados por el ALNS.
 * Encapsula un vuelo junto con las fechas exactas de salida y llegada
 * calculadas durante la ejecución del algoritmo.
 * 
 * Esta clase elimina la necesidad de recalcular tiempos en el Controller,
 * ya que el ALNS es la fuente única de verdad temporal.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TramoConTiempo {

    /**
     * Vuelo asignado a este tramo
     */
    private Vuelo vuelo;

    /**
     * Fecha y hora exacta de salida del vuelo, calculada por ALNS.
     * Considera la fecha del pedido, hora programada del vuelo,
     * y tiempos de conexión previos.
     */
    private LocalDateTime horaSalidaReal;

    /**
     * Fecha y hora exacta de llegada del vuelo, calculada por ALNS.
     * horaSalidaReal + tiempoTransporte del vuelo
     */
    private LocalDateTime horaLlegadaReal;

    /**
     * Posición de este tramo en la ruta completa (0-indexed).
     * 0 = primer vuelo, 1 = segundo vuelo (primera escala), etc.
     */
    private int indiceEnRuta;

    /**
     * Calcula la duración del tramo en minutos.
     * 
     * @return duración en minutos, o 0 si las horas no están definidas
     */
    public long getDuracionMinutos() {
        if (horaSalidaReal == null || horaLlegadaReal == null) {
            return 0;
        }
        return java.time.temporal.ChronoUnit.MINUTES.between(horaSalidaReal, horaLlegadaReal);
    }

    /**
     * Calcula la duración del tramo en horas.
     * 
     * @return duración en horas como double
     */
    public double getDuracionHoras() {
        return getDuracionMinutos() / 60.0;
    }

    /**
     * Obtiene el código IATA del aeropuerto de origen.
     * 
     * @return código IATA o null si no hay vuelo
     */
    public String getCodigoOrigen() {
        if (vuelo == null || vuelo.getAeropuertoOrigen() == null) {
            return null;
        }
        return vuelo.getAeropuertoOrigen().getCodigoIATA();
    }

    /**
     * Obtiene el código IATA del aeropuerto de destino.
     * 
     * @return código IATA o null si no hay vuelo
     */
    public String getCodigoDestino() {
        if (vuelo == null || vuelo.getAeropuertoDestino() == null) {
            return null;
        }
        return vuelo.getAeropuertoDestino().getCodigoIATA();
    }
}

