package com.grupo5e.morapack.integration;

import com.grupo5e.morapack.algorithm.input.FuenteDatosBaseDatos;
import com.grupo5e.morapack.core.enums.EstadoProducto;
import com.grupo5e.morapack.core.enums.EstadoVuelo;
import com.grupo5e.morapack.core.model.InstanciaVuelo;
import com.grupo5e.morapack.core.model.Pedido;
import com.grupo5e.morapack.core.model.Producto;
import com.grupo5e.morapack.core.model.Vuelo;
import com.grupo5e.morapack.repository.PedidoRepository;
import com.grupo5e.morapack.repository.ProductoRepository;
import com.grupo5e.morapack.repository.VueloRepository;
import com.grupo5e.morapack.service.ServicioExpansionVuelos;
import com.grupo5e.morapack.service.VueloService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de integración para el sistema de ventanas temporales.
 * 
 * Casos de prueba:
 * 1. Prefill: Ejecutar dos ventanas consecutivas y verificar que la segunda use capacidades de la primera
 * 2. Cancelación: Intentar cancelar vuelo antes y después del despegue
 * 3. Asignación incremental: Agregar pedido durante simulación activa
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class VentanasTemporalesTest {
    
    @Autowired
    private FuenteDatosBaseDatos fuenteDatosBD;
    
    @Autowired
    private VueloRepository vueloRepository;
    
    @Autowired
    private ProductoRepository productoRepository;
    
    @Autowired
    private PedidoRepository pedidoRepository;
    
    @Autowired
    private ServicioExpansionVuelos servicioExpansion;
    
    @Autowired
    private VueloService vueloService;
    
    /**
     * Test 1: Prefill de capacidades en ventanas consecutivas
     * 
     * ESCENARIO:
     * 1. Crear vuelo con capacidad de 10 productos
     * 2. Asignar 5 productos en ventana 1
     * 3. Cargar asignaciones existentes en ventana 2
     * 4. Verificar que la ventana 2 reconozca que hay 5 productos ya asignados
     */
    @Test
    @DisplayName("Test Prefill: Ventana 2 debe cargar capacidades de ventana 1")
    public void testPrefillDeAsignacionesExistentes() {
        // ARRANGE: Configurar datos de prueba
        LocalDateTime ventana1Inicio = LocalDateTime.now();
        LocalDateTime ventana1Fin = ventana1Inicio.plusHours(2);
        
        // Crear vuelo de prueba
        Vuelo vuelo = new Vuelo();
        vuelo.setCapacidadMaxima(10);
        vuelo.setCapacidadUsada(0);
        vuelo.setHoraSalida(LocalTime.of(10, 0));
        vuelo.setHoraLlegada(LocalTime.of(12, 0));
        vuelo.setEstado(EstadoVuelo.DISPONIBLE); // Estados disponibles: DISPONIBLE, CANCELADO, EN_VUELO, FINALIZADO
        vuelo = vueloRepository.save(vuelo);
        
        // Expandir vuelo a instancia diaria
        List<InstanciaVuelo> instancias = servicioExpansion.expandirVuelosParaSimulacion(
            List.of(vuelo),
            ventana1Inicio,
            ventana1Fin
        );
        
        assertFalse(instancias.isEmpty(), "Debe haber al menos una instancia de vuelo");
        InstanciaVuelo instancia = instancias.get(0);
        
        // Crear pedido y productos
        Pedido pedido = new Pedido();
        pedido.setAeropuertoOrigenCodigo("SPIM");
        pedido.setAeropuertoDestinoCodigo("SKBO");
        pedido.setFechaPedido(ventana1Inicio);
        pedido = pedidoRepository.save(pedido);
        
        // Asignar 5 productos a la instancia (simulando ventana 1)
        for (int i = 0; i < 5; i++) {
            Producto producto = new Producto();
            producto.setNombre("Producto " + i);
            producto.setPeso(1.0);
            producto.setVolumen(1.0);
            producto.setPedido(pedido);
            producto.setEstado(EstadoProducto.EN_ALMACEN); // Estados disponibles: EN_ALMACEN, EN_VUELO, ENTREGADO, PERDIDO
            producto.setInstanciaVueloAsignada(instancia.getIdInstancia());
            productoRepository.save(producto);
        }
        
        // ACT: Cargar asignaciones existentes (simulando ventana 2)
        LocalDateTime ventana2Inicio = ventana1Fin;
        LocalDateTime ventana2Fin = ventana2Inicio.plusHours(2);
        
        Map<String, List<Producto>> asignacionesExistentes = 
            fuenteDatosBD.cargarAsignacionesExistentes(ventana2Inicio, ventana2Fin);
        
        // ASSERT: Verificar que se cargaron las asignaciones
        assertNotNull(asignacionesExistentes, "Debe retornar mapa de asignaciones");
        assertTrue(asignacionesExistentes.containsKey(instancia.getIdInstancia()),
                  "Debe contener la instancia de vuelo");
        
        List<Producto> productosCargados = asignacionesExistentes.get(instancia.getIdInstancia());
        assertEquals(5, productosCargados.size(),
                    "Debe cargar los 5 productos asignados en ventana 1");
        
        // Verificar que todos los productos están correctamente cargados
        for (Producto p : productosCargados) {
            assertEquals(instancia.getIdInstancia(), p.getInstanciaVueloAsignada(),
                        "Producto debe estar asignado a la instancia correcta");
            assertEquals(EstadoProducto.EN_ALMACEN, p.getEstado(),
                        "Producto debe estar en estado EN_ALMACEN");
        }
    }
    
    /**
     * Test 2: Validación de cancelación de vuelo
     * 
     * ESCENARIO:
     * 1. Crear vuelo con productos asignados en estado PENDING
     * 2. Intentar cancelar ANTES del despegue -> debe permitir
     * 3. Cambiar productos a IN_TRANSIT (simular despegue)
     * 4. Intentar cancelar DESPUÉS del despegue -> debe rechazar
     */
    @Test
    @DisplayName("Test Cancelación: Solo permitir antes del despegue")
    public void testValidacionCancelacionVuelo() {
        // ARRANGE: Crear vuelo con productos
        LocalDateTime tiempoActual = LocalDateTime.now();
        
        Vuelo vuelo = new Vuelo();
        vuelo.setCapacidadMaxima(10);
        vuelo.setCapacidadUsada(0);
        vuelo.setHoraSalida(LocalTime.of(14, 0));
        vuelo.setHoraLlegada(LocalTime.of(16, 0));
        vuelo.setEstado(EstadoVuelo.DISPONIBLE);
        vuelo = vueloRepository.save(vuelo);
        
        // Crear productos en PENDING
        Pedido pedido = new Pedido();
        pedido.setAeropuertoOrigenCodigo("SPIM");
        pedido.setAeropuertoDestinoCodigo("SKBO");
        pedido.setFechaPedido(tiempoActual);
        pedido = pedidoRepository.save(pedido);
        
        String idInstancia = "FL-" + vuelo.getId() + "-DAY-0-1400";
        
        Producto producto = new Producto();
        producto.setNombre("Producto Test");
        producto.setPeso(1.0);
        producto.setVolumen(1.0);
        producto.setPedido(pedido);
        producto.setEstado(EstadoProducto.EN_ALMACEN);
        producto.setInstanciaVueloAsignada(idInstancia);
        producto = productoRepository.save(producto);
        
        // ACT & ASSERT 1: Debe permitir cancelación antes del despegue
        boolean puedeSerCancelado1 = vueloService.puedeSerCancelado(vuelo.getId(), tiempoActual);
        assertTrue(puedeSerCancelado1,
                  "Debe permitir cancelación cuando productos están en EN_ALMACEN");
        
        // ACT & ASSERT 2: Simular despegue y verificar rechazo
        producto.setEstado(EstadoProducto.EN_VUELO);
        productoRepository.save(producto);
        
        boolean puedeSerCancelado2 = vueloService.puedeSerCancelado(vuelo.getId(), tiempoActual);
        assertFalse(puedeSerCancelado2,
                   "No debe permitir cancelación cuando hay productos EN_VUELO");
    }
    
    /**
     * Test 3: Expansión de vuelos para ventana de simulación
     * 
     * ESCENARIO:
     * 1. Crear vuelo template con horario específico
     * 2. Expandir para ventana de 7 días
     * 3. Verificar que se crean 7 instancias diarias
     * 4. Verificar que cada instancia tiene ID único
     */
    @Test
    @DisplayName("Test Expansión: Crear instancias diarias de vuelo")
    public void testExpansionVuelosParaSimulacion() {
        // ARRANGE: Crear vuelo template
        Vuelo vueloTemplate = new Vuelo();
        vueloTemplate.setCapacidadMaxima(20);
        vueloTemplate.setCapacidadUsada(0);
        vueloTemplate.setHoraSalida(LocalTime.of(8, 30));
        vueloTemplate.setHoraLlegada(LocalTime.of(11, 45));
        vueloTemplate.setEstado(EstadoVuelo.DISPONIBLE);
        vueloTemplate = vueloRepository.save(vueloTemplate);
        
        // ACT: Expandir para 7 días
        LocalDateTime inicio = LocalDateTime.now().withHour(0).withMinute(0);
        LocalDateTime fin = inicio.plusDays(7);
        
        List<InstanciaVuelo> instancias = servicioExpansion.expandirVuelosParaSimulacion(
            List.of(vueloTemplate),
            inicio,
            fin
        );
        
        // ASSERT: Verificar número de instancias
        assertEquals(7, instancias.size(),
                    "Debe crear 7 instancias para ventana de 7 días");
        
        // Verificar que cada instancia tiene ID único
        long idsUnicos = instancias.stream()
            .map(InstanciaVuelo::getIdInstancia)
            .distinct()
            .count();
        
        assertEquals(7, idsUnicos,
                    "Cada instancia debe tener ID único");
        
        // Verificar que todas las instancias tienen la capacidad correcta
        for (InstanciaVuelo instancia : instancias) {
            assertEquals(20, instancia.getCapacidadMaxima(),
                        "Instancia debe heredar capacidad máxima del template");
            assertEquals(0, instancia.getCapacidadUsada(),
                        "Instancia debe iniciar con capacidad usada en 0");
            assertNotNull(instancia.getIdInstancia(),
                         "Instancia debe tener ID generado");
            assertTrue(instancia.getIdInstancia().startsWith("FL-" + vueloTemplate.getId()),
                      "ID de instancia debe seguir formato correcto");
        }
    }
}

