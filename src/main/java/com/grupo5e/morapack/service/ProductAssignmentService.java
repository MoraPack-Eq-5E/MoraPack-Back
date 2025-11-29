package com.grupo5e.morapack.service;

import com.grupo5e.morapack.core.model.ProductAssignment;

import java.util.List;

public interface ProductAssignmentService {
    ProductAssignment save(ProductAssignment assignment);
    List<ProductAssignment> getAssignmentsByPedido(Integer pedidoId);
    List<ProductAssignment> getAssignmentsByFlightInstance(String flightInstanceId);
}

