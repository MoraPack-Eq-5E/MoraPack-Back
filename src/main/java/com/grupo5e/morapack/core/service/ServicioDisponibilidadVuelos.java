package com.grupo5e.morapack.core.service;

import com.grupo5e.morapack.core.model.Cancelacion;
import com.grupo5e.morapack.core.model.Vuelo;
//import com.grupo5e.morapack.utils.LectorCancelaciones;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio que gestiona la disponibilidad de vuelos considerando cancelaciones.
 *
 * Responsabilidad: determinar si un vuelo está disponible en un día específico.
 *
 * Adaptado para usar la clase Cancelacion en lugar de Map<String, Set<Integer>>.
 */
public class ServicioDisponibilidadVuelos {

    // Lista total de cancelaciones registradas (día × vuelo)
    private final List<Cancelacion> cancelaciones;

    public ServicioDisponibilidadVuelos() {
        this.cancelaciones = new ArrayList<>();
    }

    /**
     * Registra una cancelación de vuelo.
     *
     * @param cancelacion Objeto con información del vuelo cancelado.
     */
    public void registrarCancelacion(Cancelacion cancelacion) {
        if (cancelacion == null) return;
        this.cancelaciones.add(cancelacion);
    }

    /**
     * Carga cancelaciones desde un lector de archivo.
     *
     * @param lector Lector de cancelaciones configurado.
     */
//    public void cargarCancelaciones(LectorCancelaciones lector) {
//        List<Cancelacion> leidas = lector.leerCancelaciones();
//        this.cancelaciones.addAll(leidas);
//        System.out.println("✅ Cancelaciones cargadas: " + leidas.size());
//    }

    public void cargarCancelaciones(List<Cancelacion> cancelacionesDesdeBd) {
        if (cancelacionesDesdeBd == null || cancelacionesDesdeBd.isEmpty()) {
            System.out.println("⚠️ No se recibieron cancelaciones desde BD. Continuando sin cancelaciones.");
            return;
        }

        this.cancelaciones.clear();              // opcional: limpiar anteriores
        this.cancelaciones.addAll(cancelacionesDesdeBd);

        System.out.println("✅ Cancelaciones cargadas desde BD: " + cancelacionesDesdeBd.size());

        // Log chiquito de ejemplo
        cancelacionesDesdeBd.stream().limit(5).forEach(c -> {
            String vueloInfo = (c.getVuelo() != null)
                    ? "VueloID=" + c.getVuelo().getId()
                    : "Vuelo=NULL";
            System.out.println("   - Cancelación: día=" + c.getDiasCancelado() +
                    " " + c.getCodigoIATAOrigen() + "→" + c.getCodigoIATADestino() +
                    " " + String.format("%02d:%02d", c.getHora(), c.getMinuto()) +
                    " | " + vueloInfo);
        });
    }
    /**
     * Verifica si un vuelo está disponible en un día específico.
     *
     * @param vuelo Vuelo a verificar.
     * @param dia   Día a consultar (1-based).
     * @return true si el vuelo está disponible, false si está cancelado.
     */
    public boolean estaDisponible(Vuelo vuelo, int dia) {
//        if (vuelo == null) return false;
//
//        // Buscar cancelaciones que coincidan con el vuelo y día
//        return cancelaciones.stream()
//                .noneMatch(c ->
//                        c.getCodigoIATAOrigen().equalsIgnoreCase(vuelo.getAeropuertoOrigen().getCodigoIATA())
//                                && c.getCodigoIATADestino().equalsIgnoreCase(vuelo.getAeropuertoDestino().getCodigoIATA())
//                                && c.getHora() ==  vuelo.getHoraSalida().getHour()
//                                && c.getMinuto() == vuelo.getHoraSalida().getMinute()
//                                && c.getDiasCancelado() == dia
//                );
        if (vuelo == null) return false;

        String keyVuelo = String.format(
                "%s-%s %02d:%02d (id=%d)",
                vuelo.getAeropuertoOrigen().getCodigoIATA(),
                vuelo.getAeropuertoDestino().getCodigoIATA(),
                vuelo.getHoraSalida().getHour(),
                vuelo.getHoraSalida().getMinute(),
                vuelo.getId()
        );

        // Buscar si EXISTE una cancelación que matchee
        Optional<Cancelacion> match = cancelaciones.stream()
                .filter(c ->
                        c.getCodigoIATAOrigen().equalsIgnoreCase(vuelo.getAeropuertoOrigen().getCodigoIATA()) &&
                                c.getCodigoIATADestino().equalsIgnoreCase(vuelo.getAeropuertoDestino().getCodigoIATA()) &&
                                c.getHora() == vuelo.getHoraSalida().getHour() &&
                                c.getMinuto() == vuelo.getHoraSalida().getMinute() &&
                                c.getDiasCancelado() == dia
                )
                .findFirst();

        if (match.isPresent()) {
            Cancelacion c = match.get();
            System.out.printf(
                    "⛔ Vuelo CANCELADO para día=%d → %s | cancelación: diaCancelado=%d, origen=%s, dest=%s, hora=%02d:%02d%n",
                    dia,
                    keyVuelo,
                    c.getDiasCancelado(),
                    c.getCodigoIATAOrigen(),
                    c.getCodigoIATADestino(),
                    c.getHora(),
                    c.getMinuto()
            );
            return false;
        } else {
            // Si quieres ver también cuándo está disponible, descomenta este log:
            // System.out.printf("✔ Vuelo DISPONIBLE para día=%d → %s (sin cancelación matching)%n", dia, keyVuelo);
            return true;
        }
    }

    /**
     * Obtiene los días en que un vuelo está cancelado.
     *
     * @param vuelo Vuelo a consultar.
     * @return Set de días cancelados (vacío si no hay cancelaciones).
     */
    public Set<Integer> obtenerDiasCancelados(Vuelo vuelo) {
        if (vuelo == null) return Set.of();

        return cancelaciones.stream()
                .filter(c ->
                        c.getCodigoIATAOrigen().equalsIgnoreCase(vuelo.getAeropuertoOrigen().getCodigoIATA())
                                && c.getCodigoIATADestino().equalsIgnoreCase(vuelo.getAeropuertoDestino().getCodigoIATA())
                                && c.getHora() == vuelo.getHoraSalida().getHour()
                                && c.getMinuto() == vuelo.getHoraSalida().getMinute()
                )
                .map(Cancelacion::getDiasCancelado)
                .collect(Collectors.toSet());
    }

    /**
     * Verifica si un vuelo tiene al menos una cancelación registrada.
     *
     * @param vuelo Vuelo a consultar.
     * @return true si tiene cancelaciones, false en caso contrario.
     */
    public boolean tieneCancelaciones(Vuelo vuelo) {
        return !obtenerDiasCancelados(vuelo).isEmpty();
    }

    /**
     * Obtiene el número total de instancias de cancelación (día × vuelo).
     */
    public int getTotalCancelaciones() {
        return cancelaciones.size();
    }

    /**
     * Obtiene el número de vuelos únicos con al menos una cancelación.
     */
    public int getVuelosAfectados() {
        return (int) cancelaciones.stream()
                .map(c -> c.getCodigoIATAOrigen() + "-" + c.getCodigoIATADestino() + "-" + c.getHora() + ":" + c.getMinuto())
                .distinct()
                .count();
    }

    /**
     * Obtiene todas las cancelaciones cargadas.
     */
    public List<Cancelacion> getCancelaciones() {
        return Collections.unmodifiableList(cancelaciones);
    }
}
