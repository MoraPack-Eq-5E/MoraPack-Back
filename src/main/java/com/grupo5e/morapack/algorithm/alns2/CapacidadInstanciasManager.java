package com.grupo5e.morapack.algorithm.alns2; // o el paquete donde esté ALNSDestruction2

import com.grupo5e.morapack.core.model.Pedido;
import com.grupo5e.morapack.core.model.Vuelo;

import java.util.ArrayList;

public interface CapacidadInstanciasManager {
    /**
     * Libera capacidad y asignaciones (ProductAssignment) asociadas
     * al pedido y su ruta. Ruta es la lista de vuelos base correspondiente.
     */
    void liberarCapacidadYAssignments(Pedido pedido, ArrayList<Vuelo> ruta);
}
