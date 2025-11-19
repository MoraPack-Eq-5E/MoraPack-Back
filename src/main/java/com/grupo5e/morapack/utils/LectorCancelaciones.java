package com.grupo5e.morapack.utils;

import com.grupo5e.morapack.core.model.Aeropuerto;
import com.grupo5e.morapack.core.model.Cancelacion;
import com.grupo5e.morapack.core.model.Vuelo;
import com.grupo5e.morapack.repository.VueloRepository;
import com.grupo5e.morapack.service.CancelacionService;
import com.grupo5e.morapack.service.VueloService;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Lector de cancelaciones de vuelos desde archivo.
 *
 * Formato esperado del archivo:
 * dd.ORIGEN-DESTINO-HH:MM
 *
 * Ejemplo: 01.SKBO-SEQM-03:34
 */
public class LectorCancelaciones {

    private final String rutaArchivo;
    //private final VueloService vueloService;
    private ArrayList<Vuelo> vuelos;
    private ArrayList<Cancelacion> cancelaciones;
//    public LectorCancelaciones(String rutaArchivo) {
//        this.rutaArchivo = rutaArchivo;
//    }
    public LectorCancelaciones(String rutaArchivo, ArrayList<Vuelo> vuelos) {
        this.rutaArchivo = rutaArchivo;
        this.vuelos = vuelos;
        this.cancelaciones = new ArrayList<Cancelacion>();
    }
    /**
     * Lee el archivo de cancelaciones y retorna una lista de objetos Cancelacion.
     *
     * @return Lista de cancelaciones cargadas desde el archivo.
     */
    public List<Cancelacion> leerCancelaciones() {
        List<Cancelacion> cancelaciones = new ArrayList<>();
        int lineasLeidas = 0;
        int lineasValidas = 0;
        int lineasInvalidas = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(rutaArchivo))) {
            String linea;

            while ((linea = reader.readLine()) != null) {
                lineasLeidas++;

                // Saltar líneas vacías o comentarios
                if (linea.trim().isEmpty() || linea.trim().startsWith("#")) {
                    continue;
                }

                try {
                    // Parsear línea: dd.ORIGEN-DESTINO-HH:MM
                    String[] partes = linea.trim().split("\\.", 2);
                    if (partes.length != 2) {
                        System.err.println("Formato inválido en línea " + lineasLeidas + ": " + linea);
                        lineasInvalidas++;
                        continue;
                    }

                    int dia = Integer.parseInt(partes[0].trim());
                    if (dia < 1 || dia > 365) {
                        System.err.println("Día fuera de rango en línea " + lineasLeidas + ": " + dia);
                        lineasInvalidas++;
                        continue;
                    }

                    String identificador = partes[1].trim(); // ORIGEN-DESTINO-HH:MM
                    if (!validarFormatoIdentificador(identificador)) {
                        System.err.println("Identificador de vuelo inválido en línea " + lineasLeidas + ": " + identificador);
                        lineasInvalidas++;
                        continue;
                    }

                    // Extraer partes
                    String[] vueloPartes = identificador.split("-");
                    String codigoIATAOrigen = vueloPartes[0];
                    String codigoIATADestino = vueloPartes[1];

                    // Hora está al final: HH:MM
                    String horaCompleta = vueloPartes[2];
                    String[] horaPartes = horaCompleta.split(":");
                    int hora = Integer.parseInt(horaPartes[0]);
                    int minuto = Integer.parseInt(horaPartes[1]);

                    // Crear cancelación usando constructor vacío y setters
                    Cancelacion cancelacion = new Cancelacion();
                    cancelacion.setDiasCancelado(dia);
                    cancelacion.setCodigoIATAOrigen(codigoIATAOrigen);
                    cancelacion.setCodigoIATADestino(codigoIATADestino);
                    cancelacion.setHora(hora);
                    cancelacion.setMinuto(minuto);
                    cancelacion.setFechaHoraCancelacion(LocalDateTime.now());
                    // Intentar asociar el Vuelo correspondiente desde la lista proporcionada
                    Vuelo vueloEncontrado = encontrarVueloCoincidente(codigoIATAOrigen, codigoIATADestino, hora, minuto);
                    if (vueloEncontrado != null) {
                        cancelacion.setVuelo(vueloEncontrado);
                    } else {
                        // Si no se encuentra por hora exacta, intentar emparejar solo por origen/destino
                        vueloEncontrado = encontrarVueloPorRutas(codigoIATAOrigen, codigoIATADestino);
                        if (vueloEncontrado != null) {
                            cancelacion.setVuelo(vueloEncontrado);
                            System.err.println("Advertencia: línea " + lineasLeidas + " - vuelo emparejado por ruta sin coincidencia exacta de hora: " + identificador);
                        } else {
                            System.err.println("Advertencia: vuelo no encontrado para cancelación en línea " + lineasLeidas + ": " + identificador);
                            // No abortar: dejar vuelo nulo y permitir que la validación posterior lo detecte o manejarlo según necesidad
                        }
                    }

                    cancelaciones.add(cancelacion);
                    System.out.println("📝 Cancelacion: Día= "+ cancelacion.getDiasCancelado() + "|" + cancelacion.getCodigoIATAOrigen()+
                            "-"+ cancelacion.getCodigoIATADestino()+ "|" +cancelacion.getHora()+":"+cancelacion.getMinuto()+"| VueloID="+cancelacion.getVuelo() != null ? cancelacion.getVuelo().getId() : "NULL");
                    lineasValidas++;

                } catch (NumberFormatException e) {
                    System.err.println("Error numérico en línea " + lineasLeidas + ": " + linea);
                    lineasInvalidas++;
                } catch (Exception e) {
                    System.err.println("Error procesando línea " + lineasLeidas + ": " + linea);
                    e.printStackTrace();
                    lineasInvalidas++;
                }
            }

        } catch (IOException e) {
            System.err.println("Error leyendo archivo de cancelaciones: " + e.getMessage());
        }

        System.out.println("=== Lectura de Cancelaciones ===");
        System.out.println("Archivo: " + rutaArchivo);
        System.out.println("Líneas procesadas: " + lineasLeidas);
        System.out.println("Líneas válidas: " + lineasValidas);
        System.out.println("Líneas inválidas: " + lineasInvalidas);
        System.out.println("Cancelaciones totales: " + cancelaciones.size());
        return cancelaciones;
    }

    /**
     * Valida el formato del identificador: CODIGO-CODIGO-HH:MM
     */
    private boolean validarFormatoIdentificador(String identificador) {
        if (identificador == null || identificador.isEmpty()) {
            return false;
        }
        String[] partes = identificador.split("-");
        if (partes.length != 3) {
            return false;
        }
        String ultimaParte = partes[2];
        return ultimaParte.matches("\\d{2}:\\d{2}");
    }
    private Vuelo encontrarVueloCoincidente(String origen, String destino, int hora, int minuto) {
        if (vuelos == null) return null;
        for (Vuelo v : vuelos) {
            if (v == null) continue;
            if (v.getAeropuertoOrigen() == null || v.getAeropuertoDestino() == null) continue;
            String ori = v.getAeropuertoOrigen().getCodigoIATA();
            String dest = v.getAeropuertoDestino().getCodigoIATA();
            if (ori == null || dest == null) continue;
            if (origen.equalsIgnoreCase(ori.trim()) && destino.equalsIgnoreCase(dest.trim())) {
                LocalTime salida = v.getHoraSalida();
                if (salida != null && salida.getHour() == hora && salida.getMinute() == minuto) {
                    return v;
                }
            }
        }
        return null;
    }

    private Vuelo encontrarVueloPorRutas(String origen, String destino) {
        if (vuelos == null) return null;
        for (Vuelo v : vuelos) {
            if (v == null) continue;
            if (v.getAeropuertoOrigen() == null || v.getAeropuertoDestino() == null) continue;
            String ori = v.getAeropuertoOrigen().getCodigoIATA();
            String dest = v.getAeropuertoDestino().getCodigoIATA();
            if (ori == null || dest == null) continue;
            if (origen.equalsIgnoreCase(ori.trim()) && destino.equalsIgnoreCase(dest.trim())) {
                return v;
            }
        }
        return null;
    }
}
