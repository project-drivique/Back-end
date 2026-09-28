## 🟢 HU-BE-01 | Configuración de datasource PostgreSQL 17, perfiles y Actuator Health

**Etiqueta:** 🔴 `Must have`  

### Descripción

Tipo: Backend / Configuración

Contrato actualizado con la base de datos: usar exclusivamente contratos, tablas y columnas en inglés definidos por las HU-BD relacionadas.

Como equipo de desarrollo, necesitamos configurar el pool de conexiones HikariCP, datasource dinámico por variables de entorno y los endpoints de observabilidad de Spring Boot Actuator, para verificar la salud y conectividad de la API con PostgreSQL 17 en dev, qa y main.

Criterios de aceptación:
• Configuración de perfiles `application-dev.yml`, `application-qa.yml` y `application-main.yml`.
• Inyección segura de credenciales (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` o `DB_URL`) sin valores en código.
• Endpoint `GET /api/actuator/health` responde status "UP" con el componente "db" activo.
• Endpoint `GET /api/actuator/info` expone metadatos de versión de la API de forma segura.
• Las migraciones Liquibase se gestionan y validan exclusivamente desde el repositorio database antes del despliegue. El backend no incluye ni ejecuta migraciones; conecta a PostgreSQL y usa Hibernate ddl-auto=validate para las entidades implementadas.

Story Points: 3

Depende de: HU-BE-00

Flujo obligatorio de ramas:
• dev: crear la rama hija desde dev con sufijo -dev y PR solo hija -> dev.
• qa: crear una rama hija desde qa con sufijo -qa; fusionar en ella la rama hija -dev y PR solo hija -> qa.
• main: crear una rama hija desde main con sufijo -main; fusionar en ella la rama hija -qa y PR solo hija -> main.
Prohibido hacer PR o merge entre las ramas padre dev, qa y main.

Ramas por repositorio:
HU-BE-01-dev, HU-BE-01-qa y HU-BE-01-main

### DoR (Definition of Ready)
- [ ] Proyecto Spring Boot inicializado con dependencias de PostgreSQL y Actuator; migraciones a cargo del repositorio database
- [ ] Variables de entorno documentadas en .env.example

### DoD (Definition of Done)
- [ ] Endpoint `/api/actuator/health` retorna status "UP" en dev
- [ ] Separación de responsabilidades verificada: backend sin SQL ni motores de migraciones; aplicación de migraciones a cargo de database
- [ ] Pruebas unitarias de carga de contexto de Spring en verde
- [ ] Pipeline de GitHub Actions ejecuta validaciones en verde
- [ ] PR abierto desde la rama hija HU-BE-01-dev y aprobado hacia dev

### Checklist de ejecución y cierre
- [ ] Configurar application.yml y perfiles dev/qa/main
- [ ] Configurar DataSource y HikariCP en Spring Boot
- [ ] Validar arranque y conexión a PostgreSQL 17
- [ ] Validar que el workflow de GitHub Actions (CI) ejecute y pase en verde
- [ ] Abrir PR desde hija -dev a dev y promover con -qa y -main

---
