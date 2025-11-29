package com.grupo5e.morapack;

import com.grupo5e.morapack.core.model.Vuelo;
import com.grupo5e.morapack.repository.VueloRepository;
import com.grupo5e.morapack.utils.LecturaVuelos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class LecturaVuelosTest {
    @Autowired
    private VueloRepository vueloRepository;

    @Test
    void testCargaVuelosDesdeArchivo() {
        // Ejecutar la carga
        LecturaVuelos lecturaVuelos = new LecturaVuelos();

        lecturaVuelos.cargarVuelos();

        // Verificar que se insertaron vuelos
        List<Vuelo> vuelos = vueloRepository.findAll();
        Assertions.assertFalse(vuelos.isEmpty(), "No se cargaron vuelos desde el archivo");

        // Verificar algunos campos de un vuelo
        Vuelo vueloEjemplo = vuelos.get(0);
        Assertions.assertNotNull(vueloEjemplo.getAeropuertoOrigen(), "El aeropuerto origen no debe ser nulo");
        Assertions.assertNotNull(vueloEjemplo.getAeropuertoDestino(), "El aeropuerto destino no debe ser nulo");
        Assertions.assertNotNull(vueloEjemplo.getHoraSalida(), "La hora de salida no debe ser nula");
        Assertions.assertNotNull(vueloEjemplo.getHoraLlegada(), "La hora de llegada no debe ser nula");

        // Validar el estado inicial
        Assertions.assertEquals("DISPONIBLE", vueloEjemplo.getEstado().name(), "El estado inicial debe ser DISPONIBLE");

        System.out.println("✅ Test exitoso: vuelos cargados = " + vuelos.size());
    }
}
