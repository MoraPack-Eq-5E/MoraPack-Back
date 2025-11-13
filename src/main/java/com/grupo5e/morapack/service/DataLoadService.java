package com.grupo5e.morapack.service;

import com.grupo5e.morapack.core.model.Aeropuerto;
import com.grupo5e.morapack.utils.LectorPedidosV2;
import com.grupo5e.morapack.utils.ModoSimulacion;
import lombok.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Servicio para cargar datos desde archivos hacia la base de datos.
 * Separación de responsabilidades: carga de datos vs ejecución de algoritmo.
 * 
 * Sigue el patrón del Backend (DataLoadService) para arquitectura Option A:
 * 1. POST /api/datos/cargar-pedidos (carga archivos a BD)
 * 2. POST /api/algoritmo/ejecutar (algoritmo lee desde BD)
 * 
 * Flujo típico:
 * - Cargar pedidos desde archivos _pedidos_{AEROPUERTO}_ a BD
 * - Filtrar por ventana de tiempo (escenario diario/semanal)
 * - Estadísticas detalladas de carga
 */
@Service
@RequiredArgsConstructor
public class DataLoadService {

    private final PedidoService pedidoService;
    private final ClienteService clienteService;
    private final AeropuertoService aeropuertoService;
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DataLoadService.class);

    /**
     * Carga pedidos desde archivos con patrón _pedidos_{AEROPUERTO}_ hacia la base de datos.
     * 
     * Formato de archivo:
     * - id_pedido-aaaammdd-hh-mm-dest-###-IdClien
     * - Ejemplo: 000000001-20250102-01-38-EBCI-006-0007729
     * 
     * @param directorioArchivos Ruta al directorio que contiene los archivos de pedidos
     * @param horaInicioSimulacion Opcional: solo cargar pedidos después de esta hora
     * @param horaFinSimulacion Opcional: solo cargar pedidos antes de esta hora
     * @return Resultado con estadísticas de la carga
     */
    public ResultadoCargaDatos cargarPedidosDesdeArchivos(
            String directorioArchivos,
            LocalDateTime horaInicioSimulacion,
            LocalDateTime horaFinSimulacion,
            ModoSimulacion modoSimulacion) {

        log.info("========================================");
        log.info("CARGANDO PEDIDOS DESDE ARCHIVOS A BASE DE DATOS");
        log.info("Directorio de datos: {}", directorioArchivos);
        if (horaInicioSimulacion != null) {
            log.info("Ventana de tiempo: {} a {}", horaInicioSimulacion, horaFinSimulacion);
        } else {
            log.info("Cargando TODOS los pedidos (sin filtrado de tiempo)");
        }
        log.info("========================================");

        ResultadoCargaDatos resultado = new ResultadoCargaDatos();
        resultado.tiempoInicio = LocalDateTime.now();

        try {
            // Cargar aeropuertos desde BD (necesarios para validar archivos)
            ArrayList<Aeropuerto> aeropuertos = new ArrayList<>(aeropuertoService.listar());
            
            if (aeropuertos.isEmpty()) {
                resultado.exito = false;
                resultado.mensajeError = "No hay aeropuertos en la base de datos. " +
                        "Debe cargar aeropuertos primero.";
                log.error(resultado.mensajeError);
                return resultado;
            }

            log.info("Aeropuertos cargados: {}", aeropuertos.size());

            // Crear lector V2 y ejecutar carga
            LectorPedidosV2 lector = new LectorPedidosV2(
                    directorioArchivos,
                    aeropuertos,
                    pedidoService,
                    clienteService
            );

            LectorPedidosV2.ResultadoCargaPedidos resultadoLector = 
                    lector.leerYGuardarPedidos(horaInicioSimulacion, horaFinSimulacion,modoSimulacion);

            // Mapear resultado del lector al resultado del servicio
            resultado.exito = resultadoLector.exito;
            resultado.mensajeError = resultadoLector.mensajeError;
            resultado.pedidosCargados = resultadoLector.pedidosCargados;
            resultado.pedidosCreados = resultadoLector.pedidosCreados;
            resultado.pedidosFiltrados = resultadoLector.pedidosFiltrados;
            resultado.erroresParseo = resultadoLector.erroresParseo;
            resultado.erroresArchivos = resultadoLector.erroresArchivos;

            log.info("========================================");
            log.info("CARGA DE DATOS COMPLETADA");
            log.info("Pedidos cargados: {}", resultado.pedidosCargados);
            log.info("Pedidos creados: {}", resultado.pedidosCreados);
            log.info("Pedidos filtrados: {}", resultado.pedidosFiltrados);
            log.info("Errores de parseo: {}", resultado.erroresParseo);
            log.info("========================================");

        } catch (Exception e) {
            resultado.exito = false;
            resultado.mensajeError = "Error al cargar pedidos: " + e.getMessage();
            log.error("ERROR en DataLoadService: {}", e.getMessage(), e);
        } finally {
            resultado.tiempoFin = LocalDateTime.now();
            resultado.calcularDuracion();
        }

        return resultado;
    }

    /**
     * Obtiene el estado actual de los datos en la base de datos
     * 
     * @return Estadísticas de datos disponibles
     */
    public EstadoDatos obtenerEstadoDatos() {
        EstadoDatos estado = new EstadoDatos();
        
        try {
            estado.totalAeropuertos = aeropuertoService.listar().size();
            estado.totalPedidos = pedidoService.listar().size();
            estado.pedidosPendientes = (int) pedidoService.listar().stream()
                    .filter(p -> "PENDIENTE".equals(p.getEstado().toString()))
                    .count();
            estado.exito = true;
        } catch (Exception e) {
            estado.exito = false;
            estado.mensajeError = "Error obteniendo estado: " + e.getMessage();
        }
        
        return estado;
    }

    /**
     * DTO para resultado de carga de datos
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResultadoCargaDatos {
        private Boolean exito;
        private String mensajeError;
        private LocalDateTime tiempoInicio;
        private LocalDateTime tiempoFin;
        private Long duracionSegundos;
        
        private Integer pedidosCargados;
        private Integer pedidosCreados;
        private Integer pedidosFiltrados;
        private Integer erroresParseo;
        private Integer erroresArchivos;
        
        public void calcularDuracion() {
            if (tiempoInicio != null && tiempoFin != null) {
                this.duracionSegundos = java.time.Duration.between(tiempoInicio, tiempoFin).getSeconds();
            }
        }
    }

    /**
     * DTO para estado de datos en la base de datos
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EstadoDatos {
        private Boolean exito;
        private String mensajeError;
        private Integer totalAeropuertos;
        private Integer totalPedidos;
        private Integer pedidosPendientes;
    }
}

