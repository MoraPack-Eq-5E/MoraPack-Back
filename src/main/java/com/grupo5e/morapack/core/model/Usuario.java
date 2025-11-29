package com.grupo5e.morapack.core.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.grupo5e.morapack.core.enums.Rol;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "usuarios")
@Inheritance(strategy = InheritanceType.JOINED)  // Herencia en BD
public class Usuario {
    @EmbeddedId
    private UsuarioId usuarioId;
    // Exponer id y tipoData como columnas individuales para FK
    @Column(name = "id", insertable = false, updatable = false)
    private Long id;

    @Column(name = "tipo_data", insertable = false, updatable = false)
    private Integer tipoData;
    // Credenciales
    @Column(nullable = false, unique = false, length = 100)
    private String usernameOrEmail; // Para clientes puede ser correo, para empleados un username

    @Column(nullable = false)
    private String password; // Encriptada con BCrypt

    @Enumerated(EnumType.STRING)
    private Rol rol; // CLIENTE, EMPLEADO, ADMIN

    private boolean activo = true;
}
