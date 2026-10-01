# Arquitectura del backend

El código se organiza por responsabilidad técnica global. Los controladores reciben HTTP,
los servicios aplican reglas y transacciones, los repositorios acceden a PostgreSQL, los modelos
representan las tablas y los DTO/mappers definen el contrato de API. Las integraciones externas
dependen de interfaces y no se exponen al cliente.

```text
com.drivique.api/
├── controller/                     endpoints HTTP de todos los dominios
├── service/                        reglas de negocio y transacciones
├── repository/                     consultas JPA
├── model/                          entidades JPA y enumeraciones
├── dto/                            contratos de entrada y salida HTTP
├── config/                         configuración transversal de Spring
├── exception/                      contrato común de errores
├── mapper/                         conversión entre modelo y DTO
├── integration/                    proveedores externos, como Frankfurter
└── security/                       filtro JWT y componentes de seguridad
```

Cada clase conserva un nombre que expresa su dominio (`AuthService`, `UserConsent`,
`CatalogController`, etc.), pero su ubicación corresponde a la responsabilidad que cumple.
Así se encuentran juntos todos los controladores, servicios, repositorios, modelos y DTO.

Las migraciones, SQL y datos semilla son responsabilidad exclusiva del repositorio `database`.
El backend usa `ddl-auto: validate` y no crea ni modifica el esquema de ejecución.
