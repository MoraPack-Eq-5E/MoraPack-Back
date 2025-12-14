package com.grupo5e.morapack.integration;

import com.grupo5e.morapack.algorithm.alns.ALNSSolver;
import com.grupo5e.morapack.algorithm.alns2.ALNSSolver2;
import com.grupo5e.morapack.core.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SpringBootTest
public class AlnsSolverIntegrationTest {

    //private ALNSSolver2 solver;

//    @Autowired
//    private VueloRepository vueloRepo;
//
//    @Autowired
//    private PedidoRepository pedidoRepo;
//
//    @Autowired
//    private InstanciaVueloRepository instanciaRepo;
//
//    @Autowired
//    private ProductAssignmentRepository paRepo;

    @Test
    void probarALNS() {

        // =============================
        // 1. Preparar datos simples
        // =============================
        int iteraciones = 800;
        LocalDateTime horaInicioSimulacion = LocalDateTime.of(2025, 11, 25, 10, 0);
        LocalDateTime horaFinSimulacion = LocalDateTime.of(2025, 11, 25, 11, 0);

        int tipoData = 1;
        System.out.println("===== INICIANDO TEST ALNS =====");
        ALNSSolver2 solver = new ALNSSolver2(iteraciones, horaInicioSimulacion, horaFinSimulacion,tipoData);
        solver.resolver();

//        AlnsRequestDTO dto = new AlnsRequestDTO();
//        dto.setPedidos(pedidos);
//        dto.setVuelos(vuelos);
//        dto.setHoraInicio(LocalDateTime.now());
//        dto.setEscenario("TEST");

        // =============================
        // 2. Ejecutar algoritmo
        // =============================

//        ResultadoAlgoritmoDTO resultado = solver.ejecutar(dto);
//
//        System.out.println("===== RESULTADO DEL ALNS =====");
//        System.out.println("Costo: " + resultado.getCostoTotal());
//        System.out.println("Factible: " + resultado.isFactible());
//        System.out.println("Pedidos asignados: " + resultado.getPedidosAsignados());
//
//        System.out.println("\n===== RUTAS DE LA SOLUCIÓN =====");
//        for (Map.Entry<Long, List<Long>> entry : resultado.getRutas().entrySet()) {
//            Long pedidoId = entry.getKey();
//            List<Long> tramos = entry.getValue();
//            System.out.println("Pedido " + pedidoId + " → vuelos: " + tramos);
//        }

        // =============================
        // 3. Imprimir InstanciaVuelo reales
        // =============================
        System.out.println("\n===== INSTANCIAS DE VUELO CREADAS =====");
        Set<InstanciaVuelo> instancias = solver.getInstancias();

        for (InstanciaVuelo inst : instancias) {
            System.out.println("\nInstancia: " + inst.getIdInstancia());
            System.out.println("  Vuelo base: " + inst.getVueloBase().getId());
            System.out.println("  Origen: " + inst.getVueloBase().getAeropuertoOrigen().getCodigoIATA());
            System.out.println("  Destino: " + inst.getVueloBase().getAeropuertoDestino().getCodigoIATA());
            System.out.println("  Salida Real: " + inst.getFechaHoraSalida());
            System.out.println("  Llegada Real: " + inst.getFechaHoraLlegada());
            System.out.println("  Capacidad Usada: " + inst.getCapacidadUsada());
            System.out.println("  Capacidad Maxima: " + inst.getVueloBase().getCapacidadMaxima());

            // Productos asignados a esta instancia
            List<ProductAssignment> asignaciones = solver.getAssignmentsNuevos();

            if (asignaciones.isEmpty()) {
                System.out.println("  (sin productos asignados)");
            } else {
                System.out.println("  Productos asignados:");
                for (ProductAssignment pa : asignaciones) {
                    System.out.println("    Pedido " + pa.getId() + "  |  Producto=" + pa.getProductoId());
                }
            }

        }

        System.out.println("\n===== FIN DEL TEST =====");
    }
}

