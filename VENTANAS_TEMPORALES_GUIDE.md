# 📚 Guía de Ventanas Temporales y Eventos en Tiempo Real

## ¿Qué son las Ventanas Temporales?

Las **ventanas temporales** son períodos de tiempo en los que el algoritmo planifica rutas de pedidos. En lugar de planificar TODO de una vez (7 días completos), el sistema puede trabajar en **ventanas incrementales** (por ejemplo, 2 horas cada vez).

### Ventajas:
- ✅ **Incremental**: Construye sobre planes previos sin empezar desde cero
- ✅ **Realista**: Refleja operaciones día a día de empresas reales
- ✅ **Flexible**: Permite eventos en tiempo real (nuevos pedidos, cancelaciones)

---

## 🎯 3 Funcionalidades Principales

### 1️⃣ **Sistema de Ventanas con PREFILL**

#### ¿Cómo funciona?
Cuando ejecutas una ventana de simulación (por ejemplo, las próximas 2 horas), el sistema:
1. **Carga** productos ya asignados de ventanas anteriores
2. **Actualiza** capacidades de vuelos (cuántos espacios quedan)
3. **Planifica** nuevos pedidos usando el espacio disponible

#### Desde el Frontend:
```javascript
// Ejecutar ventana diaria (30 min - 2 horas)
POST /api/algoritmo/ejecutar-diario
{
  "horaInicio": "2025-01-15T08:00:00",
  "horaFin": "2025-01-15T10:00:00"
}

// Respuesta:
{
  "exitoso": true,
  "pesoTotal": 1500,
  "rutasCreadas": 25,
  "tiempoEjecucion": 3200
}
```

**Resultado**: Los pedidos que lleguen entre 8:00-10:00 se asignan a vuelos, y cuando ejecutes la siguiente ventana (10:00-12:00), el sistema **recordará** qué vuelos ya tienen productos asignados.

---

### 2️⃣ **Cancelación de Vuelo con Validación**

#### ¿Qué hace?
Permite cancelar un vuelo **SOLO si no ha despegado aún**. El sistema:
1. **Valida** que no hay productos en estado `EN_VUELO` (en tránsito)
2. **Libera** los productos asignados (vuelven a `EN_ALMACEN`)
3. **Marca** el vuelo como `FINALIZADO` (cancelado)

#### Desde el Frontend:
```javascript
// Cancelar vuelo
POST /api/vuelos/45/cancelar-y-reasignar?tiempoSimulacionActual=2025-01-15T07:00:00

// Respuesta exitosa:
{
  "exitoso": true,
  "vueloId": 45,
  "productosAfectados": 12,
  "productosReasignados": 0,
  "productosSinAsignar": 12,
  "requiereReoptimizacion": true,
  "mensaje": "Vuelo cancelado exitosamente. 12 productos requieren re-optimización"
}

// Respuesta si ya despegó:
{
  "exitoso": false,
  "mensaje": "El vuelo no puede cancelarse - ya despegó o tiene productos en tránsito",
  "vueloId": 45
}
```

#### ¿Qué hace el Frontend?
```javascript
// 1. Usuario hace click en "Cancelar Vuelo 45"
const response = await fetch('/api/vuelos/45/cancelar-y-reasignar?tiempoSimulacionActual=' + tiempoActual, {
  method: 'POST'
});

const result = await response.json();

if (result.exitoso) {
  if (result.requiereReoptimizacion) {
    // Mostrar modal: "12 productos sin asignar. ¿Re-ejecutar algoritmo?"
    showReoptimizationModal(result.productosSinAsignar);
  } else {
    // Mostrar: "Vuelo cancelado, productos re-asignados automáticamente"
    showSuccessMessage();
  }
} else {
  // Mostrar error: "El vuelo ya despegó, no se puede cancelar"
  showError(result.mensaje);
}
```

---

### 3️⃣ **Asignación Incremental de Pedidos**

#### ¿Qué hace?
Permite agregar un pedido **durante la simulación** sin pausarla. El sistema:
1. **Busca** espacio disponible en vuelos existentes
2. **Asigna** el pedido si hay capacidad
3. **Retorna error** si no hay espacio (requiere re-optimización)

#### Desde el Frontend:
```javascript
// Agregar pedido nuevo durante simulación
POST /api/asignacion-incremental
{
  "pedidoId": 789,
  "tiempoSimulacionActual": "2025-01-15T09:30:00",
  "forzarReoptimizacionSiNoHayEspacio": false,
  "ventanaMaximaHoras": 24
}

// Respuesta exitosa:
{
  "exitoso": true,
  "pedidoId": 789,
  "productosAsignados": 5,
  "codigoVuelo": "SPIM-SKBO-14:00",
  "rutaAsignada": ["SPIM-SKBO-14:00"],
  "capacidadDisponibleRestante": 15,
  "mensaje": "Pedido asignado exitosamente sin re-optimización",
  "tiempoEjecucionMs": 120
}

// Respuesta si no hay espacio:
{
  "exitoso": false,
  "pedidoId": 789,
  "mensaje": "No hay capacidad disponible - se requiere re-optimización completa"
}
```

#### ¿Qué hace el Frontend?
```javascript
// 1. Usuario agrega pedido durante simulación activa
const response = await fetch('/api/asignacion-incremental', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    pedidoId: nuevoPedidoId,
    tiempoSimulacionActual: simulationTime
  })
});

const result = await response.json();

if (result.exitoso) {
  // ✅ Éxito: Actualizar UI sin pausar simulación
  updateMapWithNewAssignment(result.rutaAsignada);
  showToast(`Pedido ${result.pedidoId} asignado a vuelo ${result.codigoVuelo}`);
  
  // Simulación continúa sin interrupción
  
} else {
  // ❌ No hay espacio: Mostrar modal
  showModal({
    title: "No hay capacidad disponible",
    message: "¿Deseas pausar la simulación y re-optimizar todo el plan?",
    actions: [
      { label: "Re-optimizar", onClick: () => reejecutarAlgoritmo() },
      { label: "Cancelar", onClick: () => {} }
    ]
  });
}
```

#### Versión Simplificada:
```javascript
// Si solo tienes el ID del pedido
POST /api/asignacion-incremental/simple/789

// Usa tiempo actual del sistema automáticamente
```

---

## 🎬 Flujos de Usuario en el Frontend

### Flujo 1: Simulación Día a Día

```javascript
// PASO 1: Usuario inicia simulación
const tiempoInicio = "2025-01-15T08:00:00";
const ventanaDuracion = 2; // 2 horas

// PASO 2: Ejecutar primera ventana (8:00 - 10:00)
let tiempoActual = new Date(tiempoInicio);
await ejecutarVentana(tiempoActual, ventanaDuracion);

// PASO 3: Avanzar el reloj (cada 5 minutos)
setInterval(async () => {
  tiempoActual.setMinutes(tiempoActual.getMinutes() + 5);
  
  // Actualizar estados de productos (EN_VUELO -> ENTREGADO)
  await actualizarEstadosProductos(tiempoActual);
  
  // Actualizar visualización
  updateMapAnimation(tiempoActual);
  
}, 5000); // Cada 5 segundos en tiempo real = 5 minutos simulados

// PASO 4: Al llegar a 10:00, ejecutar siguiente ventana
if (tiempoActual >= "10:00") {
  await ejecutarVentana(tiempoActual, ventanaDuracion);
}
```

### Flujo 2: Usuario Cancela Vuelo

```javascript
// Usuario ve el mapa y hace click en un vuelo
onFlightClick(vueloId) {
  showContextMenu([
    { 
      label: "Cancelar Vuelo",
      onClick: async () => {
        // Confirmar
        if (confirm("¿Seguro que deseas cancelar este vuelo?")) {
          const result = await cancelarVuelo(vueloId, tiempoActualSimulacion);
          
          if (!result.exitoso) {
            // Ya despegó
            alert(result.mensaje);
          } else {
            // Cancelado exitosamente
            removeFlightFromMap(vueloId);
            
            if (result.requiereReoptimizacion) {
              // Mostrar overlay semi-transparente
              showOverlay("Re-optimizando plan...");
              pauseSimulation();
              
              // Re-ejecutar algoritmo
              await ejecutarVentana(tiempoActualSimulacion, 24);
              
              hideOverlay();
              resumeSimulation();
            }
          }
        }
      }
    }
  ]);
}
```

### Flujo 3: Usuario Agrega Pedido Durante Simulación

```javascript
// Usuario completa formulario de nuevo pedido
onNuevoPedidoSubmit(pedidoData) {
  // 1. Crear pedido en BD
  const pedido = await crearPedido(pedidoData);
  
  // 2. Intentar asignación incremental
  const result = await asignarPedidoIncremental(pedido.id, tiempoActualSimulacion);
  
  if (result.exitoso) {
    // ✅ Asignado sin pausar simulación
    showToast("✓ Pedido asignado exitosamente", "success");
    
    // Animar nuevo pedido en el mapa
    animateNewOrderOnMap(result.rutaAsignada);
    
  } else {
    // ❌ No hay espacio
    showDialog({
      icon: "warning",
      title: "Capacidad insuficiente",
      message: result.mensaje,
      buttons: [
        {
          text: "Pausar y Re-optimizar",
          style: "primary",
          onClick: async () => {
            // Pausar simulación
            pauseSimulation();
            showLoader("Re-optimizando plan con nuevo pedido...");
            
            // Re-ejecutar algoritmo
            await ejecutarVentana(tiempoActualSimulacion, 24);
            
            hideLoader();
            resumeSimulation();
          }
        },
        {
          text: "Dejar Pendiente",
          style: "secondary",
          onClick: () => {
            // Marcar pedido como pendiente para siguiente ventana
            marcarPedidoPendiente(pedido.id);
          }
        }
      ]
    });
  }
}
```

---

## 🗂️ Estructura de Datos

### Estado del Frontend

```javascript
const simulationState = {
  // Tiempo actual de simulación
  currentTime: "2025-01-15T09:45:00",
  
  // Velocidad de simulación (1x, 2x, 5x)
  speed: 1,
  
  // Estado (running, paused, stopped)
  status: "running",
  
  // Ventana actual
  currentWindow: {
    inicio: "2025-01-15T08:00:00",
    fin: "2025-01-15T10:00:00"
  },
  
  // Vuelos activos
  flights: [
    {
      id: 45,
      codigo: "SPIM-SKBO-14:00",
      estado: "EN_VUELO",
      capacidadUsada: 8,
      capacidadMaxima: 20,
      productos: [/* ... */]
    }
  ],
  
  // Pedidos pendientes
  pendingOrders: [/* ... */],
  
  // Eventos recientes
  recentEvents: [
    { type: "ORDER_ASSIGNED", orderId: 123, time: "09:30:00" },
    { type: "FLIGHT_DEPARTED", flightId: 45, time: "09:40:00" }
  ]
};
```

---

## 💡 Consejos de UX

### Visual Feedback para el Usuario

1. **Asignación Incremental Exitosa**:
   - 🟢 Toast verde: "Pedido asignado exitosamente"
   - ✨ Animación: Pedido aparece en el mapa con efecto de "fade in"
   - 📊 Actualizar contador: "Capacidad disponible: 15/20"

2. **No Hay Capacidad**:
   - 🟡 Modal amarillo con ícono de advertencia
   - 📝 Mensaje claro: "No hay espacio en vuelos actuales"
   - 🔘 Dos opciones: "Re-optimizar" o "Dejar Pendiente"

3. **Vuelo Cancelado**:
   - ⚠️ Si tiene productos: Modal confirmación + explicación
   - 🔴 Si ya despegó: Toast rojo con mensaje de error
   - 🔄 Si requiere re-optimización: Overlay semi-transparente con spinner

4. **Simulación Pausada para Re-optimización**:
   - ⏸️ Overlay semi-transparente sobre el mapa
   - ⚙️ Spinner + mensaje: "Re-optimizando plan (esto puede tardar 30s)"
   - 📊 Progress bar si es posible

---

## 🔗 Endpoints Disponibles

| Endpoint | Método | Descripción |
|----------|--------|-------------|
| `/api/algoritmo/ejecutar-diario` | POST | Ejecutar ventana diaria (30min-2h) |
| `/api/algoritmo/ejecutar-semanal` | POST | Ejecutar ventana semanal (7 días) |
| `/api/vuelos/{id}/cancelar-y-reasignar` | POST | Cancelar vuelo (solo antes despegue) |
| `/api/asignacion-incremental` | POST | Asignar pedido sin re-optimizar |
| `/api/asignacion-incremental/simple/{pedidoId}` | POST | Versión simplificada de asignación |

---

## 📖 Resumen: ¿Qué logramos?

✅ **Simulaciones realistas**: Operaciones día a día en lugar de "todo de golpe"  
✅ **Eficiencia**: No re-calcula todo, construye sobre lo existente (PREFILL)  
✅ **Eventos en tiempo real**: Cancelaciones y nuevos pedidos durante simulación  
✅ **Validación inteligente**: No permite cancelar vuelos en tránsito  
✅ **UX fluida**: Asignaciones rápidas sin pausar la simulación (cuando hay espacio)  

ejemplo para poder verlo fluido en front
Lo que el usuario ve: Una simulación fluida y continua de 8:00 AM a 6:00 PM
✅ Lo que realmente pasa: Ventanas de 30 min calculándose en background
✅ La clave: Pre-calcular con anticipación + animaciones suaves
✅ Resultado: Usuario nunca nota que hay ventanas separadas 🎭
¡Es como un video continuo hecho de frames individuales! 🎬