# Arquitectura del backend

El código se organiza por módulo funcional y, dentro de cada módulo, por responsabilidad.
Los controladores reciben HTTP, los servicios aplican reglas y transacciones, los repositorios
acceden a PostgreSQL, las entidades representan las tablas y los DTO/mappers definen el contrato
de API. Las integraciones externas dependen de interfaces del módulo y no se exponen al cliente.

```text
com.drivique.api/
├── config/                         configuración transversal de Spring
├── common/exception/               contrato común de errores
├── system/
│   ├── controller/                 idiomas, monedas, marca y parámetros públicos
│   ├── service/                    reglas de catálogo, marca y configuración
│   ├── repository/                 consultas JPA del módulo system
│   ├── entity/                     tablas core e iam ya disponibles
│   ├── dto/                        contratos HTTP del módulo system
│   ├── mapper/                     conversión entidad/DTO
│   └── integration/                Frankfurter y su abstracción
└── auth/
    ├── entity/                     políticas y, cuando exista BD, usuarios/OTP
    ├── repository/                 acceso a políticas y autenticación
    ├── service/                    reglas de contraseña y registro
    ├── dto/                        contratos de registro y autenticación
    ├── mapper/                     conversión autenticación/DTO
    └── controller/                 endpoints de autenticación
```

Las carpetas `catalog` y `branding` fueron reemplazadas por `system`: idiomas, monedas,
tasas, marca, tema y parámetros globales pertenecen al módulo de configuración del sistema.
La autenticación queda aislada en `auth`.

Las migraciones, SQL y datos semilla son responsabilidad exclusiva del repositorio `database`.
El backend usa `ddl-auto: validate` y no crea ni modifica el esquema de ejecución.
