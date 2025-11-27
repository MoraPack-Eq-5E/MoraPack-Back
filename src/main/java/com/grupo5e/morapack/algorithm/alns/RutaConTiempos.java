package com.grupo5e.morapack.algorithm.alns;

import com.grupo5e.morapack.core.model.Pedido;
import lombok.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsula una ruta completa de vuelos con todos los tiempos absolutos
 * calculados por el algoritmo ALNS.
 * 
 * Esta clase representa la "fuente única de verdad" para los tiempos de
 * entrega,
 * eliminando la necesidad de recalcular fechas en el Controller.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RutaConTiempos {

    /**
     * Pedido al que pertenece esta ruta
     */
    private Pedido pedido;

    /**
     * Lista ordenada de tramos con sus tiempos calculados.
     * El primer elemento es el primer vuelo, el último es el vuelo final.
     */
    @Builder.Default
    private List<TramoConTiempo> tramos = new ArrayList<>();

    /**
     * Hora de inicio de la ruta (salida del primer vuelo)
     */
    private LocalDateTime horaInicioRuta;

    /**
     * Hora de fin de la ruta (llegada del último vuelo al destino final)
     */
    private LocalDateTime horaFinRuta;

    /**
     * Tiempo total de transporte en horas (sin contar esperas)
     */
    private double tiempoTotalHoras;

    /**
     * Verifica si el pedido llegará a tiempo según el deadline.
     * 
     * @return true si la hora de fin es anterior al deadline del pedido
     */
    public boolean llegoATiempo() {
        if (pedido == null || pedido.getFechaLimiteEntrega() == null || horaFinRuta == null) {
            return false;
        }
        return !horaFinRuta.isAfter(pedido.getFechaLimiteEntrega());
    }

    /**
     * Calcula el margen de tiempo entre la entrega y el deadline.
     * 
     * @return horas de margen (positivo = a tiempo, negativo = tarde)
     */
    public double getMargenHoras() {
        if (pedido == null || pedido.getFechaLimiteEntrega() == null || horaFinRuta == null) {
            return 0;
        }
        return ChronoUnit.MINUTES.between(horaFinRuta, pedido.getFechaLimiteEntrega()) / 60.0;
    }

    /**
     * Obtiene la duración total de la ruta en minutos (desde salida hasta llegada
     * final).
     * 
     * @return duración en minutos
     */
    public long getDuracionTotalMinutos() {
        if (horaInicioRuta == null || horaFinRuta == null) {
            return 0;
        }
        return ChronoUnit.MINUTES.between(horaInicioRuta, horaFinRuta);
    }

    /**
     * Obtiene el número de escalas en la ruta.
     * Una ruta directa tiene 0 escalas, una con 2 vuelos tiene 1 escala, etc.
     * 
     * @return número de escalas
     */
    public int getNumeroEscalas() {
        return Math.max(0, tramos.size() - 1);
    }

    /**
     * Verifica si la ruta está vacía (sin vuelos asignados).
     * 
     * @return true si no hay tramos
     */
    public boolean isEmpty() {
        return tramos == null || tramos.isEmpty();
    }

    /**
     * Obtiene el código IATA del aeropuerto de origen de la ruta.
     * 
     * @return código IATA del origen o null si la ruta está vacía
     */
    public String getCodigoOrigenRuta() {
        if (isEmpty()) {
            return null;
        }
        return tramos.get(0).getCodigoOrigen();
    }

    /**
     * Obtiene el código IATA del aeropuerto de destino final de la ruta.
     * 
     * @return código IATA del destino o null si la ruta está vacía
     */
    public String getCodigoDestinoRuta() {
        if (isEmpty()) {
            return null;
        }
        return tramos.get(tramos.size() - 1).getCodigoDestino();
    }

    /**
     * Agrega un tramo a la ruta y actualiza los tiempos.
     * 
     * @param tramo tramo a agregar
     */
    public void agregarTramo(TramoConTiempo tramo) {
        if (tramos == null) {
            tramos = new ArrayList<>();
        }
        tramos.add(tramo);

        // Actualizar hora de inicio si es el primer tramo
        if (tramos.size() == 1 && tramo.getHoraSalidaReal() != null) {
            horaInicioRuta = tramo.getHoraSalidaReal();
        }

        // Actualizar hora de fin con el último tramo
        if (tramo.getHoraLlegadaReal() != null) {
            horaFinRuta = tramo.getHoraLlegadaReal();
        }

        // Recalcular tiempo total
        recalcularTiempoTotal();
    }

    /**
     * Recalcula el tiempo total de transporte sumando todos los tramos.
     */
    private void recalcularTiempoTotal() {
        tiempoTotalHoras = 0;
        if (tramos != null) {
            for (TramoConTiempo tramo : tramos) {
                tiempoTotalHoras += tramo.getDuracionHoras();
            }
        }
    }
}
