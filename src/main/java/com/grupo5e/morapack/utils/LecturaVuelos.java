package com.grupo5e.morapack.utils;

import com.grupo5e.morapack.core.enums.EstadoVuelo;
import com.grupo5e.morapack.core.model.Aeropuerto;
import com.grupo5e.morapack.core.model.Vuelo;
import com.grupo5e.morapack.repository.AeropuertoRepository;
import com.grupo5e.morapack.repository.VueloRepository;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Time;

public class LecturaVuelos {

    private AeropuertoRepository aeropuertoRepository;
    private VueloRepository vueloRepository;

    private static final String RUTA_ARCHIVO = "src/main/java/com/grupo5e/morapack/utils/planes_vuelo.txt";

    public void cargarVuelos() {
        System.out.println("=== Iniciando carga de vuelos ===");
        try (BufferedReader br = new BufferedReader(new FileReader(RUTA_ARCHIVO))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                if (linea.trim().isEmpty() || linea.startsWith("#")) continue; // Ignora líneas vacías o comentarios

                // Ejemplo: SVMI-SBBR-15:00-22:22-0360
                String[] partes = linea.split("-");
                if (partes.length < 5) {
                    System.err.println("Línea inválida: " + linea);
                    continue;
                }

                String codigoOrigen = partes[0].trim();
                String codigoDestino = partes[1].trim();
                Time horaSalida = Time.valueOf(partes[2] + ":00".substring(Math.max(0, 5 - partes[2].length())));
                Time horaLlegada = Time.valueOf(partes[3] + ":00".substring(Math.max(0, 5 - partes[3].length())));
                int capacidad = Integer.parseInt(partes[4].trim());

                Aeropuerto origen = aeropuertoRepository.findByCodigoIATA(codigoOrigen)
                        .orElseThrow(() -> new RuntimeException("Aeropuerto origen no encontrado: " + codigoOrigen));

                Aeropuerto destino = aeropuertoRepository.findByCodigoIATA(codigoDestino)
                        .orElseThrow(() -> new RuntimeException("Aeropuerto destino no encontrado: " + codigoDestino));

                Vuelo vuelo = new Vuelo();
                vuelo.setAeropuertoOrigen(origen);
                vuelo.setAeropuertoDestino(destino);
                vuelo.setHoraSalida(horaSalida.toLocalTime());
                vuelo.setHoraLlegada(horaLlegada.toLocalTime());
                vuelo.setCapacidadMaxima(capacidad);
                vuelo.setCapacidadUsada(0);
                vuelo.setFrecuenciaPorDia(1);
                vuelo.setTiempoTransporte(calcularDuracion(horaSalida, horaLlegada));
                vuelo.setCosto(calcularCosto(capacidad));
                vuelo.setEstado(EstadoVuelo.DISPONIBLE);
                vuelo.setTipoData(0);
                vueloRepository.save(vuelo);
                System.out.println("Vuelo registrado: " + codigoOrigen + " -> " + codigoDestino);
            }
            System.out.println("=== Carga de vuelos completada ===");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private double calcularDuracion(Time salida, Time llegada) {
        long duracionMs = (llegada.getTime() - salida.getTime());
        if (duracionMs < 0) duracionMs += 24 * 60 * 60 * 1000; // Ajuste si pasa de medianoche
        return duracionMs / (1000.0 * 60.0 * 60.0);
    }

    private double calcularCosto(int capacidad) {
        return 100 + (capacidad * 1.2);
    }
}
