package com.grupo5e.morapack.core.enums;

public enum EstadoInstanciaVuelo {
    PLANIFICADO, // Vuelo programado pero no ha salido
    EN_VUELO,    // Vuelo que ya partió pero no ha llegado
    COMPLETADO,  // Vuelo que ha llegado a su destino
    CANCELADO    // Vuelo que ha sido cancelado
}
