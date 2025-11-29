package com.grupo5e.morapack.service.impl;

import com.grupo5e.morapack.core.model.ProductAssignment;
import com.grupo5e.morapack.repository.ProductAssignmentRepository;
import com.grupo5e.morapack.service.ProductAssignmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductAssignmentServiceImpl implements ProductAssignmentService {

    private final ProductAssignmentRepository repository;

    @Autowired
    public ProductAssignmentServiceImpl(ProductAssignmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public ProductAssignment save(ProductAssignment assignment) {
        return repository.save(assignment);
    }

    @Override
    public List<ProductAssignment> getAssignmentsByPedido(Integer pedidoId) {
        return repository.findByPedidoId(pedidoId);
    }

    @Override
    public List<ProductAssignment> getAssignmentsByFlightInstance(String flightInstanceId) {
        return repository.findByFlightInstanceId(flightInstanceId);
    }
}

