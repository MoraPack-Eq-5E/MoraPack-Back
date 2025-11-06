package com.grupo5e.morapack.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.grupo5e.morapack.core.model.Pedido;
import com.grupo5e.morapack.core.model.PedidoTemporal;
import com.grupo5e.morapack.repository.PedidoTemporalRepository;
import com.grupo5e.morapack.service.PedidoTemporalService;

import jakarta.transaction.Transactional;

@Service

public class PedidoTemporalServiceImpl implements PedidoTemporalService {

    private final PedidoTemporalRepository pedidoTemporalRepository;

    public PedidoTemporalServiceImpl(PedidoTemporalRepository pedidoTemporalRepository) {
        this.pedidoTemporalRepository = pedidoTemporalRepository;
    }

    @Override
    @Transactional
    public void guardarPedidosTemporales(List<Pedido> pedidos) {
        // Limpiar tabla antes de insertar nueva carga
        pedidoTemporalRepository.deleteAll();

        for (Pedido pedido : pedidos) {
            PedidoTemporal temporal = new PedidoTemporal();

            temporal.setId(pedido.getId());
            temporal.setIdCliente(pedido.getCliente().getId());
            temporal.setAeropuertoOrigen(pedido.getAeropuertoOrigenCodigo());
            temporal.setAeropuertoDestino(pedido.getAeropuertoDestinoCodigo());
            temporal.setFechaPedido(pedido.getFechaPedido());
            temporal.setFechaLimiteEntrega(pedido.getFechaLimiteEntrega());
            temporal.setCantidadProductos(pedido.getProductos().size());
            temporal.setPrioridad(pedido.getPrioridad());
            temporal.setEstado(pedido.getEstado());

            pedidoTemporalRepository.save(temporal);
        }
    }
    
}
