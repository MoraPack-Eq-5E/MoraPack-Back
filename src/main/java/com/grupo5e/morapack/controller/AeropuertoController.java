package com.grupo5e.morapack.controller;

import com.grupo5e.morapack.api.dto.BulkResponseDTO;
import com.grupo5e.morapack.api.dto.ErrorResponseDTO;
import com.grupo5e.morapack.api.exception.ResourceNotFoundException;
import com.grupo5e.morapack.core.model.Aeropuerto;
import com.grupo5e.morapack.service.AeropuertoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/aeropuertos")
@CrossOrigin(origins = "*")
@Tag(name = "Aeropuertos", description = "API para gestión de aeropuertos")
public class AeropuertoController {
    
    private final AeropuertoService service;
    private final com.grupo5e.morapack.api.mapper.AeropuertoMapper mapper;

    public AeropuertoController(AeropuertoService service, com.grupo5e.morapack.api.mapper.AeropuertoMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Operation(summary = "Listar todos los aeropuertos", description = "Obtiene una lista de todos los aeropuertos registrados")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de aeropuertos obtenida exitosamente")
    })
    @GetMapping
    public ResponseEntity<List<com.grupo5e.morapack.api.dto.AeropuertoDTO>> listar() {
        List<com.grupo5e.morapack.api.dto.AeropuertoDTO> dtos = service.listar().stream()
                .map(mapper::toDTO)
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @Operation(summary = "Obtener aeropuerto por ID", description = "Obtiene un aeropuerto específico por su ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Aeropuerto encontrado"),
            @ApiResponse(responseCode = "404", description = "Aeropuerto no encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<Aeropuerto> obtener(
            @Parameter(description = "ID del aeropuerto", required = true)
            @PathVariable Integer id) {
        Aeropuerto aeropuerto = service.buscarPorId(id);
        if (aeropuerto == null) {
            throw new ResourceNotFoundException("Aeropuerto", "id", id);
        }
        return ResponseEntity.ok(aeropuerto);
    }

    @Operation(summary = "Buscar aeropuerto por código IATA", description = "Busca un aeropuerto por su código IATA")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Aeropuerto encontrado"),
            @ApiResponse(responseCode = "404", description = "Aeropuerto no encontrado")
    })
    @GetMapping("/codigo/{codigoIATA}")
    public ResponseEntity<Aeropuerto> buscarPorCodigo(
            @Parameter(description = "Código IATA del aeropuerto (4 letras)", required = true)
            @PathVariable String codigoIATA) {
        Optional<Aeropuerto> aeropuerto = service.buscarPorCodigoIATA(codigoIATA);
        return aeropuerto.map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Aeropuerto", "codigoIATA", codigoIATA));
    }

    @Operation(summary = "Crear nuevo aeropuerto", description = "Registra un nuevo aeropuerto en el sistema")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Aeropuerto creado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos")
    })
    @PostMapping
    public ResponseEntity<Integer> insertar(@Valid @RequestBody Aeropuerto aeropuerto) {
        Integer id = service.insertar(aeropuerto);
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    @Operation(summary = "Actualizar aeropuerto", description = "Actualiza los datos de un aeropuerto existente")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Aeropuerto actualizado exitosamente"),
            @ApiResponse(responseCode = "404", description = "Aeropuerto no encontrado")
    })
    @PutMapping("/{id}")
    public ResponseEntity<Aeropuerto> actualizar(
            @Parameter(description = "ID del aeropuerto", required = true)
            @PathVariable Integer id,
            @Valid @RequestBody Aeropuerto aeropuerto) {
        Aeropuerto actualizado = service.actualizar(id, aeropuerto);
        return ResponseEntity.ok(actualizado);
    }

    @Operation(summary = "Activar/Desactivar aeropuerto", description = "Cambia el estado de un aeropuerto entre DISPONIBLE y NO_DISPONIBLE")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estado del aeropuerto cambiado exitosamente"),
            @ApiResponse(responseCode = "404", description = "Aeropuerto no encontrado")
    })
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<Aeropuerto> toggleEstado(
            @Parameter(description = "ID del aeropuerto", required = true)
            @PathVariable Integer id) {
        Aeropuerto aeropuerto = service.toggleEstado(id);
        return ResponseEntity.ok(aeropuerto);
    }

    @Operation(summary = "Eliminar aeropuerto", description = "Elimina un aeropuerto del sistema")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Aeropuerto eliminado exitosamente"),
            @ApiResponse(responseCode = "404", description = "Aeropuerto no encontrado")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @Parameter(description = "ID del aeropuerto", required = true)
            @PathVariable Integer id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Crear aeropuertos en bulk", description = "Registra múltiples aeropuertos en una sola operación")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Aeropuertos creados exitosamente"),
            @ApiResponse(responseCode = "400", description = "Error en validación de datos")
    })
    @PostMapping("/bulk")
    public ResponseEntity<BulkResponseDTO<Integer>> crearBulk(@Valid @RequestBody List<Aeropuerto> aeropuertos) {
        List<Aeropuerto> creados = service.insertarBulk(aeropuertos);
        List<Integer> ids = creados.stream().map(Aeropuerto::getId).collect(Collectors.toList());
        
        BulkResponseDTO<Integer> response = BulkResponseDTO.<Integer>builder()
                .totalProcesados(aeropuertos.size())
                .exitosos(ids.size())
                .fallidos(0)
                .idsExitosos(ids)
                .mensaje("Aeropuertos creados exitosamente")
                .build();
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
