package com.grupo5e.morapack.utils;

import com.grupo5e.morapack.core.model.Vuelo;
import com.grupo5e.morapack.core.model.Aeropuerto;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LectorVuelos {

    private ArrayList<Vuelo> vuelos;
    private final String rutaArchivo;
    private ArrayList<Aeropuerto> aeropuertos;

    public LectorVuelos(String rutaArchivo, ArrayList<Aeropuerto> aeropuertos) {
        this.rutaArchivo = rutaArchivo;
        this.vuelos = new ArrayList<>();
        this.aeropuertos = aeropuertos;
    }

    /**
     * OPTIMIZADO: Lee vuelos desde un InputStream sin crear archivo temporal
     * Esto mejora el rendimiento evitando I/O de disco innecesario
     * 
     * @param inputStream Stream del archivo de vuelos
     * @param aeropuertos Lista de aeropuertos para resolver referencias
     * @return Lista de vuelos parseados
     * @throws IOException si hay error leyendo el stream
     */
    public static List<Vuelo> leerVuelosDesdeStream(InputStream inputStream, List<Aeropuerto> aeropuertos) throws IOException {
        List<Vuelo> vuelos = new ArrayList<>();
        Map<String, Aeropuerto> mapaAeropuertos = crearMapaAeropuertosStatic(aeropuertos);
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String linea;
            boolean primerLinea = true;
            int lineNumber = 0;
            
            while ((linea = reader.readLine()) != null) {
                lineNumber++;
                
                // Saltar líneas vacías
                if (linea.trim().isEmpty()) {
                    continue;
                }
                
                // Saltar cabecera si existe
                if (primerLinea && linea.contains("Codigo")) {
                    primerLinea = false;
                    continue;
                }
                primerLinea = false;
                
                try {
                    Vuelo vuelo = parsearLineaVuelo(linea, mapaAeropuertos);
                    if (vuelo != null) {
                        vuelos.add(vuelo);
                    }
                } catch (Exception e) {
                    System.err.println("⚠️ Error parseando línea " + lineNumber + ": " + e.getMessage());
                    // Continuar con la siguiente línea en vez de fallar todo
                }
            }
        }
        
        return vuelos;
    }

    public ArrayList<Vuelo> leerVuelos() {
        try (BufferedReader reader = new BufferedReader(new FileReader(rutaArchivo))) {
            String linea;
            Map<String, Aeropuerto> mapaAeropuertos = crearMapaAeropuertos();
            boolean primerLinea = true;
            
            while ((linea = reader.readLine()) != null) {
                // Saltar líneas vacías
                if (linea.trim().isEmpty()) {
                    continue;
                }
                
                // Saltar cabecera si existe
                if (primerLinea && linea.contains("Codigo")) {
                    primerLinea = false;
                    continue;
                }
                primerLinea = false;
                
                // Intentar ambos formatos
                // Formato 1 (CSV): Codigo,Ciudad-Origen,Ciudad-Destino,Hora-Salida,Hora-Llegada,Dias-Transporte,Cap-Max,Frecuencia-Diaria
                // Formato 2 (antiguo): ORIGEN-DESTINO-SALIDA-LLEGADA-CAPACIDAD
                
                Vuelo vuelo = null;
                
                // Intentar formato CSV primero (comas)
                if (linea.contains(",")) {
                    String[] partes = linea.split(",");
                    if (partes.length >= 7) {
                        // partes[0] = codigo del vuelo (no usado, se genera automáticamente)
                        String codigoOrigen = partes[1].trim();
                        String codigoDestino = partes[2].trim();
                        String horaSalida = partes[3].trim();
                        String horaLlegada = partes[4].trim();
                        double diasTransporte = Double.parseDouble(partes[5].trim());
                        int capacidadMaxima = Integer.parseInt(partes[6].trim());
                        double frecuencia = partes.length > 7 ? Double.parseDouble(partes[7].trim()) : 1.0;
                        
                        // Buscar aeropuertos por código IATA
                        Aeropuerto aeropuertoOrigen = mapaAeropuertos.get(codigoOrigen);
                        Aeropuerto aeropuertoDestino = mapaAeropuertos.get(codigoDestino);
                        
                        if (aeropuertoOrigen != null && aeropuertoDestino != null) {
                            // Parsear horas
                            LocalTime horaSalidaParsed = parsearHora(horaSalida);
                            LocalTime horaLlegadaParsed = parsearHora(horaLlegada);
                            
                            // Calcular tiempo de transporte en horas
                            double tiempoTransporte = diasTransporte * 24.0;
                            
                            // Calcular costo
                            double costo = calcularCostoVuelo(aeropuertoOrigen, aeropuertoDestino, capacidadMaxima);
                            
                            // Crear objeto Vuelo
                            vuelo = new Vuelo();
                            // El identificador se genera automáticamente via getIdentificadorVuelo()
                            vuelo.setFrecuenciaPorDia(frecuencia);
                            vuelo.setAeropuertoOrigen(aeropuertoOrigen);
                            vuelo.setAeropuertoDestino(aeropuertoDestino);
                            vuelo.setCapacidadMaxima(capacidadMaxima);
                            vuelo.setCapacidadUsada(0);
                            vuelo.setTiempoTransporte(tiempoTransporte);
                            vuelo.setCosto(costo);
                            vuelo.setHoraSalida(horaSalidaParsed);
                            vuelo.setHoraLlegada(horaLlegadaParsed);
                        } else {
                            System.err.println("⚠️ Aeropuertos no encontrados para vuelo: " + codigoOrigen + " -> " + codigoDestino);
                        }
                    }
                } else {
                    // Formato antiguo con guiones
                    String[] partes = linea.split("-");
                    if (partes.length == 5) {
                        String codigoOrigen = partes[0];
                        String codigoDestino = partes[1];
                        String horaSalida = partes[2];
                        String horaLlegada = partes[3];
                        int capacidadMaxima = Integer.parseInt(partes[4]);
                        
                        // Buscar aeropuertos por código IATA
                        Aeropuerto aeropuertoOrigen = mapaAeropuertos.get(codigoOrigen);
                        Aeropuerto aeropuertoDestino = mapaAeropuertos.get(codigoDestino);
                        
                        if (aeropuertoOrigen != null && aeropuertoDestino != null) {
                            // Parsear horas
                            LocalTime horaSalidaParsed = parsearHora(horaSalida);
                            LocalTime horaLlegadaParsed = parsearHora(horaLlegada);
                            
                            // Calcular tiempo de transporte en horas
                            double tiempoTransporte = calcularTiempoTransporte(horaSalida, horaLlegada);
                            
                            // Calcular costo
                            double costo = calcularCostoVuelo(aeropuertoOrigen, aeropuertoDestino, capacidadMaxima);
                            
                            // Crear objeto Vuelo
                            vuelo = new Vuelo();
                            // El identificador se genera automáticamente via getIdentificadorVuelo()
                            vuelo.setFrecuenciaPorDia(1.0);
                            vuelo.setAeropuertoOrigen(aeropuertoOrigen);
                            vuelo.setAeropuertoDestino(aeropuertoDestino);
                            vuelo.setCapacidadMaxima(capacidadMaxima);
                            vuelo.setCapacidadUsada(0);
                            vuelo.setTiempoTransporte(tiempoTransporte);
                            vuelo.setCosto(costo);
                            vuelo.setHoraSalida(horaSalidaParsed);
                            vuelo.setHoraLlegada(horaLlegadaParsed);
                        }
                    }
                }
                
                if (vuelo != null) {
                    vuelos.add(vuelo);
                }
            }
            
        } catch (IOException e) {
            System.err.println("Error leyendo datos de vuelos: " + e.getMessage());
            e.printStackTrace();
        }
        
        return vuelos;
    }

    private Map<String, Aeropuerto> crearMapaAeropuertos() {
        return crearMapaAeropuertosStatic(aeropuertos);
    }
    
    /**
     * Crea un mapa de aeropuertos por código IATA para búsquedas rápidas O(1)
     */
    private static Map<String, Aeropuerto> crearMapaAeropuertosStatic(List<Aeropuerto> aeropuertos) {
        Map<String, Aeropuerto> mapa = new HashMap<>();
        for (Aeropuerto aeropuerto : aeropuertos) {
            mapa.put(aeropuerto.getCodigoIATA(), aeropuerto);
        }
        return mapa;
    }
    
    /**
     * REFACTORIZADO: Parsea una línea de vuelo y retorna un objeto Vuelo
     * Soporta ambos formatos: CSV y antiguo con guiones
     */
    private static Vuelo parsearLineaVuelo(String linea, Map<String, Aeropuerto> mapaAeropuertos) {
        Vuelo vuelo = null;
        
        // Intentar formato CSV primero (comas)
        if (linea.contains(",")) {
            String[] partes = linea.split(",");
            if (partes.length >= 7) {
                String codigoOrigen = partes[1].trim();
                String codigoDestino = partes[2].trim();
                String horaSalida = partes[3].trim();
                String horaLlegada = partes[4].trim();
                double diasTransporte = Double.parseDouble(partes[5].trim());
                int capacidadMaxima = Integer.parseInt(partes[6].trim());
                double frecuencia = partes.length > 7 ? Double.parseDouble(partes[7].trim()) : 1.0;
                
                Aeropuerto aeropuertoOrigen = mapaAeropuertos.get(codigoOrigen);
                Aeropuerto aeropuertoDestino = mapaAeropuertos.get(codigoDestino);
                
                if (aeropuertoOrigen != null && aeropuertoDestino != null) {
                    LocalTime horaSalidaParsed = parsearHoraStatic(horaSalida);
                    LocalTime horaLlegadaParsed = parsearHoraStatic(horaLlegada);
                    double tiempoTransporte = diasTransporte * 24.0;
                    double costo = calcularCostoVueloStatic(aeropuertoOrigen, aeropuertoDestino, capacidadMaxima);
                    
                    vuelo = new Vuelo();
                    vuelo.setFrecuenciaPorDia(frecuencia);
                    vuelo.setAeropuertoOrigen(aeropuertoOrigen);
                    vuelo.setAeropuertoDestino(aeropuertoDestino);
                    vuelo.setCapacidadMaxima(capacidadMaxima);
                    vuelo.setCapacidadUsada(0);
                    vuelo.setTiempoTransporte(tiempoTransporte);
                    vuelo.setCosto(costo);
                    vuelo.setHoraSalida(horaSalidaParsed);
                    vuelo.setHoraLlegada(horaLlegadaParsed);
                } else {
                    System.err.println("⚠️ Aeropuertos no encontrados: " + codigoOrigen + " -> " + codigoDestino);
                }
            }
        } else {
            // Formato antiguo con guiones
            String[] partes = linea.split("-");
            if (partes.length == 5) {
                String codigoOrigen = partes[0].trim();
                String codigoDestino = partes[1].trim();
                String horaSalida = partes[2].trim();
                String horaLlegada = partes[3].trim();
                int capacidadMaxima = Integer.parseInt(partes[4].trim());
                
                Aeropuerto aeropuertoOrigen = mapaAeropuertos.get(codigoOrigen);
                Aeropuerto aeropuertoDestino = mapaAeropuertos.get(codigoDestino);
                
                if (aeropuertoOrigen != null && aeropuertoDestino != null) {
                    LocalTime horaSalidaParsed = parsearHoraStatic(horaSalida);
                    LocalTime horaLlegadaParsed = parsearHoraStatic(horaLlegada);
                    double tiempoTransporte = calcularTiempoTransporteStatic(horaSalida, horaLlegada);
                    double costo = calcularCostoVueloStatic(aeropuertoOrigen, aeropuertoDestino, capacidadMaxima);
                    
                    vuelo = new Vuelo();
                    vuelo.setFrecuenciaPorDia(1.0);
                    vuelo.setAeropuertoOrigen(aeropuertoOrigen);
                    vuelo.setAeropuertoDestino(aeropuertoDestino);
                    vuelo.setCapacidadMaxima(capacidadMaxima);
                    vuelo.setCapacidadUsada(0);
                    vuelo.setTiempoTransporte(tiempoTransporte);
                    vuelo.setCosto(costo);
                    vuelo.setHoraSalida(horaSalidaParsed);
                    vuelo.setHoraLlegada(horaLlegadaParsed);
                }
            }
        }
        
        return vuelo;
    }
    
    private double calcularTiempoTransporte(String horaSalida, String horaLlegada) {
        return calcularTiempoTransporteStatic(horaSalida, horaLlegada);
    }
    
    private static double calcularTiempoTransporteStatic(String horaSalida, String horaLlegada) {
        LocalTime salida = parsearHoraStatic(horaSalida);
        LocalTime llegada = parsearHoraStatic(horaLlegada);
        
        // Calcular duración entre salida y llegada
        long minutos;
        if (llegada.isBefore(salida)) {
            // Vuelo cruza medianoche
            minutos = Duration.between(salida, LocalTime.of(23, 59, 59)).toMinutes() + 
                     Duration.between(LocalTime.of(0, 0), llegada).toMinutes() + 1;
        } else {
            minutos = Duration.between(salida, llegada).toMinutes();
        }
        
        // Convertir minutos a horas
        return minutos / 60.0;
    }
    
    private LocalTime parsearHora(String horaStr) {
        return parsearHoraStatic(horaStr);
    }
    
    private static LocalTime parsearHoraStatic(String horaStr) {
        int horas = Integer.parseInt(horaStr.substring(0, 2));
        int minutos = Integer.parseInt(horaStr.substring(3, 5));
        return LocalTime.of(horas, minutos);
    }
    
    private double calcularCostoVuelo(Aeropuerto origen, Aeropuerto destino, int capacidad) {
        return calcularCostoVueloStatic(origen, destino, capacidad);
    }
    
    private static double calcularCostoVueloStatic(Aeropuerto origen, Aeropuerto destino, int capacidad) {
        // Modelo de costo simple basado en si los aeropuertos están en el mismo continente y capacidad
        boolean vueloMismoContinente = origen.getCiudad().getContinente() == destino.getCiudad().getContinente();
        
        double costoBase;
        if (vueloMismoContinente) {
            // 2 días mismo continente = 48 horas
            costoBase = 48 * 100;
        } else {
            // 3 días diferente continente = 72 horas
            costoBase = 72 * 150;
        }
        
        // Ajustar costo basado en capacidad
        double factorCapacidad = capacidad / 300.0;
        
        return costoBase * factorCapacidad;
    }
}
