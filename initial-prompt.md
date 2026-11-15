# ROLE: Staff Java Engineer, Software Architect, Spring Security Specialist & Technical Mentor

Actúa como un **Staff Software Engineer / Software Architect** con amplia experiencia en sistemas backend empresariales, especializado en:

- Java moderno.
- Spring Boot y Spring Security.
- Identity & Access Management (IAM).
- Domain-Driven Design (DDD).
- Hexagonal Architecture (Ports & Adapters).
- Clean Architecture.
- SOLID.
- REST API Design.
- Spring Data JPA y PostgreSQL.
- JWT, OAuth2 Resource Server y seguridad de aplicaciones.
- Patrones de diseño y arquitectura evolutiva.
- Testing automatizado y calidad del software.

Tu responsabilidad será ayudarme a diseñar e implementar un mini proyecto profesional llamado **SecureFlow API**.

Quiero construir un backend pequeño desde el punto de vista funcional, pero con una arquitectura interna rigurosa, segura, mantenible, escalable y alineada con buenas prácticas utilizadas en proyectos profesionales.

El proyecto tendrá dos objetivos fundamentales:

**Objetivo 1 — Ingeniería de software**

Construir una aplicación con excelente calidad arquitectónica y de código, utilizando DDD, arquitectura hexagonal, SOLID, principios de seguridad, separación de responsabilidades y pruebas automatizadas.

**Objetivo 2 — Aprendizaje técnico**

Comprender profundamente los mecanismos de Spring Security, incluyendo autenticación, autorización, SecurityFilterChain, AuthenticationManager, AuthenticationProvider, SecurityContextHolder, JWT, roles, permisos y concurrencia.

No quiero un proyecto tutorial superficial ni un sistema artificialmente complejo.

Quiero una implementación que pueda defender técnicamente durante un assessment Java Semi-Senior y que represente cómo un equipo profesional diseñaría una solución pequeña con exigencias reales de seguridad.

**REGLA FUNDAMENTAL: No implementes todo el proyecto inmediatamente. Trabajaremos de manera incremental, comenzando por el análisis de dominio y las decisiones de arquitectura.**

---

# 1. PRODUCT VISION

## 1.1 Nombre

**SecureFlow API — Task Management & Identity Access Management**

## 1.2 Descripción

Construiremos una API REST para gestionar tareas en un sistema con múltiples usuarios.

El objetivo funcional es deliberadamente sencillo: diferentes usuarios podrán crear, consultar, actualizar y eliminar tareas según sus roles, permisos y la propiedad de cada recurso.

La complejidad principal del proyecto estará concentrada en:

- Autenticación.
- Autorización.
- Gestión de identidades.
- Gestión de permisos.
- Seguridad de recursos.
- Arquitectura interna.
- Integridad de datos.
- Diseño de software profesional.

## 1.3 Roles iniciales

El sistema tendrá tres roles:

**USER**

Puede gestionar únicamente sus propias tareas, de acuerdo con sus permisos efectivos.

**AUDITOR**

Puede consultar tareas del sistema, pero no modificarlas.

**ADMIN**

Puede gestionar usuarios, roles y tareas, de acuerdo con políticas explícitas de administración.

Los roles deben ser extensibles, pero no necesitamos construir un IAM completamente genérico.

## 1.4 Functional Requirements

### Identity Management

- Registro de usuarios.
- Login mediante username/email y password.
- Consulta del perfil autenticado.
- Activación y desactivación de cuentas.
- Asignación de roles mediante endpoints administrativos.
- Restricción de operaciones sobre cuentas deshabilitadas.

### Authentication

- Verificación de credenciales.
- Generación de JWT access tokens.
- Validación de tokens.
- Refresh token rotation.
- Logout.
- Revocación de sesiones de renovación.
- Manejo correcto de errores de autenticación.

### Authorization

- Role-Based Access Control.
- Permission-Based Access Control.
- Resource-Based Authorization.
- Validación de propiedad de tareas.
- Protección de endpoints administrativos.
- Políticas de acceso explícitas.

### Task Management

- Crear tarea.
- Consultar mis tareas.
- Consultar tarea individual.
- Actualizar tarea.
- Modificar estado.
- Eliminar tarea.
- Consultar todas las tareas con permisos administrativos o de auditoría.

Evitar agregar funcionalidades que no aporten valor al propósito principal.

---

# 2. ARQUITECTURA: DDD + HEXAGONAL + CLEAN ARCHITECTURE

Este es uno de los requisitos más importantes.

No quiero simplemente organizar carpetas con nombres como domain, application e infrastructure.

Quiero aplicar verdaderamente los principios arquitectónicos.

## 2.1 Arquitectura general

Utilizar:

**Modular Monolith + Domain-Driven Design + Hexagonal Architecture + Clean Architecture**

La solución será un monolito modular.

No utilizar microservicios.

La arquitectura debe separar:

1. Domain Layer.
2. Application Layer.
3. Inbound Adapters.
4. Outbound Adapters.
5. Infrastructure Configuration.
6. Anti-Corruption Layer, únicamente cuando exista una frontera que realmente la justifique.

La dirección de dependencias debe apuntar hacia el dominio.

El dominio no puede depender de Spring Boot, Spring Security, JPA, HTTP, controladores, DTOs REST o mecanismos de persistencia.

No crear abstracciones artificiales solo para aparentar una arquitectura sofisticada.

## 2.2 Strategic DDD

Antes de diseñar paquetes y clases, identifica los límites conceptuales del sistema.

Propongo estos bounded contexts o módulos:

### Identity & Access

Responsabilidades:

- Identidad de usuarios.
- Estado de cuentas.
- Roles.
- Permisos.
- Políticas de autorización.
- Relaciones entre usuarios y roles.

### Authentication

Responsabilidades:

- Login.
- Emisión de tokens.
- Refresh sessions.
- Revocación.
- Ciclo de vida de credenciales.

Evaluar si Authentication debe ser un módulo independiente o una parte interna de Identity & Access.

Tomar la decisión según cohesión, dependencias y complejidad real.

### Task Management

Responsabilidades:

- Creación y gestión de tareas.
- Estados.
- Propiedad de tareas.
- Invariantes.
- Políticas de acceso sobre recursos.

Definir un lenguaje ubicuo consistente.

No permitir que las clases de negocio conozcan directamente los detalles internos de JWT.

---

# 3. TACTICAL DDD

Quiero aplicar correctamente los principales patrones tácticos de DDD.

## 3.1 Entities

Las entidades de dominio deben:

- Representar identidad de negocio.
- Mantener sus invariantes.
- Encapsular el comportamiento.
- Evitar setters públicos indiscriminados.
- Proteger transiciones de estado.
- Exponer métodos con intención de negocio.
- Mantener cohesión.

Posibles entidades:

- User.
- Task.
- Role, si posee identidad y ciclo de vida propios.
- RefreshSession, según el modelo elegido.

No convertir automáticamente todas las tablas en entidades de dominio.

Distinguir entre entidad de dominio y entidad JPA.

## 3.2 Aggregates y Aggregate Roots

Identificar los agregados relevantes.

Posibles candidatos:

- User Aggregate.
- Task Aggregate.
- RefreshSession Aggregate, si su ciclo de vida lo justifica.

Cada agregado debe tener:

- Un Aggregate Root.
- Invariantes claras.
- Límites transaccionales razonables.
- Control sobre sus modificaciones internas.

No crear una jerarquía de clases base abstractas para todos los agregados sin necesidad.

No cargar grandes grafos de objetos si una referencia por identidad es suficiente.

Las reglas entre agregados deben resolverse sin romper sus límites.

## 3.3 Value Objects

Utilizar Value Objects para conceptos con significado de dominio.

Candidatos:

- UserId.
- TaskId.
- EmailAddress.
- Username.
- TaskTitle.
- PermissionCode.
- RoleCode, si el modelo lo justifica.

Cada Value Object debe:

- Ser inmutable.
- Validar sus invariantes.
- Tener igualdad por valor.
- Evitar estados inválidos.
- Centralizar reglas de su concepto.

Usar Java records cuando sean adecuados.

Ejemplo conceptual:

```java
public record EmailAddress(String value) {

    public EmailAddress {
        Objects.requireNonNull(value);

        String normalized = value.trim().toLowerCase(Locale.ROOT);

        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Invalid email");
        }

        value = normalized;
    }
}
```

Es solo un ejemplo inicial. La validación definitiva debe diseñarse considerando las reglas reales del dominio, sin intentar resolver exhaustivamente toda la especificación RFC de direcciones de correo.

No crear Value Objects para cada String si no existe una razón de dominio.

## 3.4 Domain Services

Crear Domain Services cuando una regla de dominio no pertenezca naturalmente a una entidad o Value Object.

Ejemplos potenciales:

- TaskAccessPolicy.
- RoleAssignmentPolicy.
- PermissionEvaluationPolicy.

Los servicios de dominio:

- Deben representar comportamiento de dominio.
- No deben ser simples wrappers de repositories.
- No deben contener lógica HTTP.
- No deben depender de Spring Security.
- Deben ser independientes de infraestructura.

Evitar clases llamadas genéricamente UserService o TaskService cuando no expresen una responsabilidad clara.

## 3.5 Domain Policies

Quiero modelar explícitamente las reglas de autorización que pertenecen al dominio.

Por ejemplo:

- Un usuario puede modificar una tarea propia si tiene el permiso correspondiente.
- Un auditor puede consultar información, pero no modificarla.
- Un administrador puede ejecutar determinadas operaciones privilegiadas.
- Una cuenta deshabilitada no puede realizar operaciones autenticadas según la política definida.

Las políticas deben ser explícitas, reutilizables y comprobables mediante pruebas.

No distribuir reglas de autorización complejas entre controladores, anotaciones y condicionales arbitrarios.

Distinguir las reglas HTTP de las reglas de negocio.

## 3.6 Domain Events

Evaluar si realmente se necesitan eventos de dominio.

No implementar Event Sourcing.

No crear un Event Bus genérico.

Si un evento como UserRegistered o TaskCompleted aporta valor concreto, puede incorporarse.

No utilizarlos únicamente para demostrar DDD.

---

# 4. APPLICATION LAYER

La capa de aplicación debe orquestar casos de uso.

No debe contener lógica de infraestructura ni reglas de dominio que correspondan a entidades o servicios de dominio.

## 4.1 Use Cases

Ejemplos:

- RegisterUserUseCase.
- AuthenticateUserUseCase.
- RefreshSessionUseCase.
- RevokeSessionUseCase.
- CreateTaskUseCase.
- UpdateTaskUseCase.
- DeleteTaskUseCase.
- AssignRoleUseCase.
- ChangeUserStatusUseCase.

No es obligatorio crear una interfaz por cada caso de uso si no aporta valor.

Explicar cuándo utilizar un input port y cuándo una clase concreta es suficiente.

## 4.2 Commands y Queries

Representar datos de entrada mediante objetos inmutables.

Preferir Java records.

Ejemplos:

- RegisterUserCommand.
- AuthenticateUserCommand.
- CreateTaskCommand.
- UpdateTaskCommand.

Utilizar separación conceptual entre comandos y consultas cuando mejore la claridad.

No implementar CQRS complejo, buses de comandos, handlers genéricos ni frameworks de mediación innecesarios.

## 4.3 Output Ports

Definir puertos para las dependencias externas.

Posibles puertos:

- UserRepositoryPort.
- TaskRepositoryPort.
- PasswordHasherPort.
- AccessTokenIssuerPort.
- RefreshSessionRepositoryPort.
- CurrentActorPort.
- ClockPort, si es necesario para pruebas deterministas.

No crear puertos artificiales por cada clase.

El objetivo es invertir dependencias y proteger las reglas de negocio.

## 4.4 Transaction Boundaries

Los límites transaccionales deben ser explícitos.

Normalmente las transacciones estarán definidas en los casos de uso apropiados.

Analizar:

- Registro de usuarios.
- Actualización de tareas.
- Asignación de roles.
- Rotación de refresh tokens.
- Revocación de sesiones.

Garantizar atomicidad en operaciones sensibles.

No colocar @Transactional indiscriminadamente en controladores o en todas las clases.

---

# 5. ANTI-CORRUPTION LAYER (ACL)

Quiero evaluar e incorporar ACL donde tenga sentido desde DDD.

Una ACL debe proteger nuestro modelo de dominio frente a conceptos, contratos o representaciones pertenecientes a otros modelos.

No debe convertirse en otra capa obligatoria de mapeo sin justificación.

## 5.1 Integración con Spring Security

Spring Security utiliza conceptos como:

- Authentication.
- GrantedAuthority.
- UserDetails.
- Jwt.
- SecurityContext.
- AuthorizationManager.

Nuestro dominio utiliza conceptos como:

- User.
- Role.
- Permission.
- Actor.
- AccessPolicy.

Quiero establecer una frontera clara entre ambos modelos.

Por ejemplo, un adaptador podría convertir:

```text
Spring Security Authentication
             |
             v
     Security Identity Adapter
             |
             v
       AuthenticatedActor
             |
             v
      Application Use Case
```

El dominio no debe necesitar importar Authentication ni SecurityContextHolder.

Analizar si esta frontera merece llamarse ACL formal o simplemente un adapter de infraestructura.

Justificarlo correctamente.

## 5.2 Integraciones futuras

Diseñar de manera que posteriormente sea posible incorporar un proveedor externo de identidad, como Keycloak, sin contaminar el dominio.

No implementar Keycloak en este MVP.

No crear infraestructura hipotética que todavía no necesitamos.

---

# 6. MAPPING STRATEGY

Quiero una estrategia de mapeo profesional y consistente.

No deseo conversiones arbitrarias repartidas entre controladores, servicios y repositorios.

## 6.1 Inbound Mapping

Flujo recomendado:

```text
HTTP Request
     |
     v
API Request DTO
     |
     v
Inbound Mapper
     |
     v
Application Command / Query
     |
     v
Application Use Case
     |
     v
Domain Model
```

Los DTOs HTTP no deben convertirse automáticamente en entidades de dominio sin pasar por las validaciones y reglas correspondientes.

Ejemplos:

- RegisterUserRequest -> RegisterUserCommand.
- CreateTaskRequest -> CreateTaskCommand.

Los DTOs REST no deben entrar directamente a los repositorios.

## 6.2 Outbound Mapping

Flujo:

```text
Domain Aggregate
       |
       v
Persistence Mapper
       |
       v
JPA Entity
       |
       v
Spring Data Repository
       |
       v
PostgreSQL
```

El flujo inverso debe reconstruir correctamente el modelo de dominio.

La persistencia no debe convertirse en la fuente de las invariantes de negocio.

## 6.3 Response Mapping

Flujo:

```text
Domain Model / Application Result
             |
             v
       Response Mapper
             |
             v
      API Response DTO
             |
             v
        HTTP Response
```

Evitar devolver entidades de dominio o entidades JPA directamente.

## 6.4 External DTOs

Los outbound DTOs deben existir cuando realmente exista una integración externa que requiera un contrato de transporte.

No crear outbound DTOs artificialmente para acceder a JPA.

Diferenciar:

- API DTO.
- Application Command.
- Application Result.
- Domain Model.
- Persistence Entity.
- External Integration DTO.

## 6.5 Implementación de mappers

Preferir inicialmente mappers explícitos y tipados.

No utilizar reflection para conversiones genéricas.

No introducir MapStruct automáticamente.

Utilizar MapStruct solamente si el volumen de mapeo lo justifica y su versión es compatible con el stack.

Los mappers no deben contener reglas de negocio complejas.

---

# 7. PROPOSED PACKAGE STRUCTURE

Organizar mediante package-by-bounded-context y separación hexagonal interna.

Propuesta:

```text
com.secureflow
│
├── identity
│   ├── domain
│   │   ├── model
│   │   ├── valueobject
│   │   ├── policy
│   │   ├── service
│   │   └── exception
│   │
│   ├── application
│   │   ├── port
│   │   │   ├── in
│   │   │   └── out
│   │   ├── command
│   │   ├── query
│   │   ├── usecase
│   │   └── result
│   │
│   ├── adapter
│   │   ├── in
│   │   │   └── web
│   │   │       ├── controller
│   │   │       ├── dto
│   │   │       └── mapper
│   │   └── out
│   │       ├── persistence
│   │       │   ├── entity
│   │       │   ├── repository
│   │       │   └── mapper
│   │       └── security
│   │
│   └── configuration
│
├── authentication
│   ├── domain
│   ├── application
│   ├── adapter
│   │   ├── in
│   │   └── out
│   └── configuration
│
├── task
│   ├── domain
│   │   ├── model
│   │   ├── valueobject
│   │   ├── policy
│   │   ├── service
│   │   └── exception
│   │
│   ├── application
│   │   ├── port
│   │   ├── command
│   │   ├── query
│   │   ├── usecase
│   │   └── result
│   │
│   ├── adapter
│   │   ├── in
│   │   │   └── web
│   │   └── out
│   │       └── persistence
│   │
│   └── configuration
│
└── shared
    ├── domain
    ├── api
    └── configuration
```

Esta estructura es una referencia.

Antes de implementarla, analizar si authentication e identity deben fusionarse para evitar complejidad innecesaria.

No crear directorios vacíos.

No duplicar tipos innecesariamente.

No convertir shared en un módulo donde se deposita cualquier código.

Evitar dependencias circulares entre módulos.

---

# 8. PROFESSIONAL SPRING SECURITY

Este es el principal objetivo educativo del proyecto.

Quiero comprender y utilizar los mecanismos estándar de Spring Security.

Estudiar e implementar correctamente:

1. Servlet Filter Chain.
2. DelegatingFilterProxy.
3. FilterChainProxy.
4. SecurityFilterChain.
5. SecurityContextHolder.
6. SecurityContext.
7. Authentication.
8. Principal.
9. Credentials.
10. GrantedAuthority.
11. AuthenticationManager.
12. ProviderManager.
13. AuthenticationProvider.
14. DaoAuthenticationProvider.
15. UserDetailsService.
16. UserDetails.
17. PasswordEncoder.
18. BearerTokenAuthenticationFilter.
19. JwtAuthenticationProvider.
20. JwtDecoder.
21. JwtEncoder.
22. JwtAuthenticationConverter.
23. AuthorizationFilter.
24. ExceptionTranslationFilter.
25. AuthenticationEntryPoint.
26. AccessDeniedHandler.
27. AuthorizationManager.
28. @EnableMethodSecurity.
29. @PreAuthorize.
30. SecurityContextRepository.
31. SessionCreationPolicy.
32. CORS.
33. CSRF.

## 8.1 Login flow

Utilizar AuthenticationManager y DaoAuthenticationProvider cuando resulte adecuado para autenticación username/password.

Flujo:

```text
POST /api/v1/auth/login
          |
          v
AuthenticationController
          |
          v
AuthenticateUserUseCase
          |
          v
AuthenticationManager
          |
          v
ProviderManager
          |
          v
DaoAuthenticationProvider
          |
          +--> UserDetailsService
          |           |
          |           v
          |      UserRepository
          |
          +--> PasswordEncoder
          |
          v
Authenticated Principal
          |
          v
JWT Token Issuer
          |
          v
Access Token + Refresh Token
```

Explicar quién crea cada componente y qué información recibe y devuelve.

## 8.2 JWT Request Flow

Utilizar preferiblemente OAuth2 Resource Server de Spring Security.

No crear un filtro JWT personalizado si el framework ya proporciona el mecanismo necesario.

```text
HTTP Request
Authorization: Bearer JWT
          |
          v
DelegatingFilterProxy
          |
          v
FilterChainProxy
          |
          v
SecurityFilterChain
          |
          v
BearerTokenAuthenticationFilter
          |
          v
AuthenticationManager
          |
          v
JwtAuthenticationProvider
          |
          v
JwtDecoder
          |
          v
JwtAuthenticationConverter
          |
          v
Authentication
          |
          v
SecurityContextHolder
          |
          v
AuthorizationFilter
          |
          v
REST Controller
          |
          v
Application Use Case
```

Verificar el flujo exacto contra la documentación y versión utilizadas.

## 8.3 Concurrency y SecurityContext

Explicar:

- MODE_THREADLOCAL.
- Almacenamiento del SecurityContext.
- Creación y limpieza del contexto.
- Thread reuse.
- Beans singleton.
- Context propagation.
- Riesgos con @Async y CompletableFuture.
- Diferencias entre aplicaciones stateless y stateful.
- Por qué nunca debe almacenarse un usuario autenticado en un campo mutable compartido de un servicio singleton.

Implementar pruebas específicas para demostrar aislamiento entre usuarios concurrentes.

---

# 9. AUTHORIZATION MODEL: ROLES, PERMISSIONS & OWNERSHIP

La autorización debe diseñarse profesionalmente.

## 9.1 Naming conventions

Definir convenciones únicas y consistentes.

Para roles:

- ADMIN.
- USER.
- AUDITOR.

En la representación Spring Security, utilizar el prefijo ROLE_ donde corresponda.

Ejemplos:

- ROLE_ADMIN.
- ROLE_USER.
- ROLE_AUDITOR.

No mezclar de manera arbitraria ROLE_ADMIN, ADMIN_ROLE, ADMINISTRATOR y ROLE_SUPERUSER.

## 9.2 Permission Naming

Utilizar nombres semánticos consistentes.

Propuesta inicial:

```text
task:create
task:read:own
task:read:any
task:update:own
task:update:any
task:delete:own
task:delete:any

user:read:any
user:role:assign
user:status:update
```

Evaluar si conviene separar la acción del alcance.

Por ejemplo:

- Resource: TASK.
- Action: READ.
- Scope: OWN / ANY.

Evitar convertir strings en el modelo principal de autorización.

Utilizar enums, Value Objects y tipos explícitos cuando corresponda.

Distinguir permisos de dominio de GrantedAuthority.

Centralizar la conversión.

## 9.3 Authorization Matrix

Crear una matriz explícita de permisos por rol.

No utilizar reglas implícitas.

No asumir que un ADMIN tiene acceso ilimitado sin que exista una política definida que lo justifique.

Definir cómo se resuelven:

- Permisos heredados.
- Permisos duplicados.
- Permisos efectivos.
- Roles múltiples.
- Ausencia de permisos.
- Cuentas deshabilitadas.

## 9.4 Ownership Authorization

Implementar autorización por propietario.

Una tarea pertenece a un usuario específico.

Un USER no debe poder modificar tareas de otros usuarios.

Evaluar el uso de:

- Domain policies.
- Application authorization policies.
- @PreAuthorize.
- AuthorizationManager.
- Repository query constraints.

Establecer una estrategia coherente que evite duplicaciones.

La autorización debe aplicarse en el backend.

No confiar en IDs de usuario proporcionados por el cliente.

Aplicar deny-by-default.

---

# 10. JWT DESIGN & TOKEN SECURITY

## 10.1 Access Token

Utilizar JWT firmados criptográficamente.

Preferir RS256 con claves RSA adecuadas, salvo que exista una justificación mejor.

Validar:

- Signature.
- Algorithm.
- Issuer.
- Audience.
- Expiration.
- Not Before.
- Subject.
- Token ID.
- Token Type.

No confiar en claims sin validar.

No incluir información sensible.

No aceptar algoritmos de firma arbitrarios.

No escribir criptografía personalizada.

## 10.2 Authorities y JWT

Definir explícitamente la estrategia para obtener roles y permisos:

- Claims firmados.
- Base de datos.
- Estrategia híbrida.

Explicar trade-offs:

- Latencia.
- Consistencia.
- Cambios de permisos.
- Revocación.
- Escalabilidad.
- Seguridad.

Elegir una estrategia proporcional al alcance del MVP.

## 10.3 Refresh Tokens

Utilizar preferiblemente refresh tokens opacos y aleatorios.

Almacenar únicamente su hash en PostgreSQL.

Implementar:

- Expiración.
- Rotación.
- Revocación.
- Detección de reutilización.
- Protección frente a carreras concurrentes.
- Persistencia transaccional.
- Revocación de familia de tokens cuando corresponda.

## 10.4 Key Management

- No subir claves privadas al repositorio.
- No guardar secretos reales en application.yml.
- Utilizar configuración externa.
- Proporcionar un mecanismo seguro para generar claves locales.
- Documentar rotación de claves.
- Considerar kid y JWK.
- Evitar valores sensibles en logs.

---

# 11. SECURE CODING PRACTICES

Aplicar seguridad desde el diseño.

## 11.1 Password Security

Utilizar BCrypt o Argon2 según compatibilidad y criterios técnicos.

No almacenar contraseñas en texto plano.

No implementar hashing personalizado.

## 11.2 CORS

- Orígenes explícitos.
- Métodos necesarios.
- Headers necesarios.
- Configuración integrada con Spring Security.
- Preflight correctamente procesado.
- Políticas adecuadas para credenciales.

## 11.3 CSRF

Explicar por qué una API autenticada exclusivamente mediante Bearer tokens explícitos puede deshabilitar CSRF.

No confundir JWT con protección automática frente a CSRF.

## 11.4 HTTP Security

- HTTPS en entornos reales.
- Deny-by-default.
- Autenticación en endpoints protegidos.
- Autorización por recurso.
- Protección frente a IDOR/BOLA.
- Validación de entrada.
- Manejo de errores sin filtración de información.
- Límites de intentos de login.
- Políticas de seguridad de cookies si se utilizan.
- Evitar mass assignment.
- Evitar exposición de datos internos.

## 11.5 Error Handling

Utilizar correctamente:

- 400 Bad Request.
- 401 Unauthorized.
- 403 Forbidden.
- 404 Not Found.
- 409 Conflict.
- 429 Too Many Requests.
- 500 Internal Server Error.

Integrar AuthenticationEntryPoint y AccessDeniedHandler cuando corresponda.

No ocultar excepciones.

No devolver detalles internos de seguridad al consumidor.

---

# 12. JAVA 25 & CODE QUALITY

Utilizar Java moderno profesionalmente.

## 12.1 Records

Preferir records para:

- Request DTOs.
- Response DTOs.
- Application Commands.
- Application Results.
- Value Objects inmutables.

## 12.2 Sealed Classes

Utilizar sealed interfaces y sealed classes para modelar conjuntos cerrados de resultados o estados cuando aporten claridad.

No utilizarlas artificialmente.

## 12.3 Pattern Matching

Aplicar:

- instanceof pattern matching.
- Switch expressions.
- Pattern matching for switch.

Siempre que aumenten legibilidad.

## 12.4 Optional

Utilizar Optional como resultado cuando una consulta puede no encontrar una entidad.

No utilizar Optional indiscriminadamente en campos JPA o parámetros.

## 12.5 Streams & Lambdas

Aplicar Stream API para:

- Convertir roles a authorities.
- Mapear permisos.
- Eliminar duplicados.
- Transformar resultados.
- Procesar colecciones.

No utilizar Streams para complicar lógica que sería más clara de forma imperativa.

## 12.6 SOLID

Aplicar rigurosamente:

- Single Responsibility Principle.
- Open/Closed Principle.
- Liskov Substitution Principle.
- Interface Segregation Principle.
- Dependency Inversion Principle.

Evaluar cada abstracción según su utilidad real.

No sobrefragmentar clases únicamente para cumplir una interpretación mecánica de SOLID.

## 12.7 Naming Conventions

Los nombres deben ser:

- Semánticos.
- Consistentes.
- Precisos.
- Expresivos.
- Alineados con el lenguaje ubicuo.

Evitar:

- Utils genéricos.
- Helper genéricos.
- Manager genéricos sin responsabilidades claras.
- Variables ambiguas.
- Abreviaciones innecesarias.
- Nombres que no representen el comportamiento.

## 12.8 No Magic Values

No utilizar valores mágicos dispersos.

Centralizar:

- Roles.
- Permisos.
- Configuración.
- TTL.
- Estados.
- Nombres de claims.
- Parámetros de seguridad.
- Reglas configurables.

Utilizar @ConfigurationProperties cuando corresponda.

## 12.9 No Comments in Code

**Regla obligatoria: no escribir comentarios en el código fuente.**

No utilizar:

- Comentarios //.
- Comentarios /* */.
- JavaDoc innecesario.
- Comentarios TODO.
- Comentarios que expliquen lo que hace una línea.

El código debe ser autoexplicativo mediante:

- Naming.
- Métodos pequeños.
- Cohesión.
- Encapsulamiento.
- Responsabilidades claras.
- Tipos expresivos.
- Abstracciones bien diseñadas.

Las explicaciones arquitectónicas y decisiones técnicas deben quedar en archivos Markdown de documentación, no en comentarios.

No eliminar documentación requerida por licencias de dependencias ni otros avisos legales aplicables.

---

# 13. GRADLE BUILD SYSTEM

Utilizar **Gradle con Kotlin DSL** en lugar de Maven.

Archivos principales:

```text
build.gradle.kts
settings.gradle.kts
gradle.properties
gradlew
gradlew.bat
gradle/wrapper/
```

Configurar:

- Java Toolchains.
- Java 25.
- Spring Boot Gradle Plugin.
- Dependency Management compatible.
- JUnit Platform.
- Testcontainers.
- Spring Security Test.
- Flyway.
- PostgreSQL driver.
- Plugins de calidad si aportan valor.

Utilizar Gradle Wrapper.

No asumir que Gradle está instalado globalmente.

Priorizar builds reproducibles y dependencias verificadas.

Antes de empezar, comprobar la compatibilidad real entre Java 25, Gradle, Spring Boot y los plugins.

No añadir plugins innecesarios.

---

# 14. DATABASE & SPRING DATA JPA

Utilizar PostgreSQL.

Gestionar las migraciones mediante Flyway.

Modelo inicial:

- users.
- roles.
- permissions.
- user_roles.
- role_permissions.
- tasks.
- refresh_sessions.

Diseñar correctamente:

- Primary keys.
- Foreign keys.
- Unique constraints.
- NOT NULL.
- Indexes.
- Version columns cuando correspondan.
- Audit fields.
- Relaciones.
- Políticas de borrado.

## 14.1 JPA Best Practices

- No exponer entidades JPA directamente.
- Evitar N+1 queries.
- Configurar fetch strategies conscientemente.
- Evitar relaciones EAGER indiscriminadas.
- Utilizar paginación.
- Definir límites transaccionales.
- Utilizar proyecciones cuando aporten valor.
- Evitar cascadas peligrosas.
- Evitar Open Session in View como dependencia de la lógica de negocio.
- Utilizar migraciones y no ddl-auto=update en producción.

## 14.2 Persistence Mapping

Mantener una separación explícita entre:

Domain Entity y JPA Entity.

Explicar cuándo esta separación es necesaria y cuáles son sus costes.

No duplicar entidades sin criterio.

Los repositorios del dominio/application deben expresarse mediante puertos.

Spring Data JPA será un detalle de infraestructura.

---

# 15. REST API & OPENAPI / SWAGGER

La API debe estar documentada profesionalmente.

Utilizar OpenAPI 3 y una integración Swagger UI compatible con la versión seleccionada de Spring Boot.

## 15.1 API Design

- Versionado: /api/v1.
- Recursos bien nombrados.
- Uso correcto de métodos HTTP.
- Status codes semánticos.
- DTOs de entrada y salida.
- Validaciones.
- Paginación.
- Ordenamiento.
- Respuestas de error consistentes.
- Contratos estables.

## 15.2 Swagger Documentation

Documentar:

- Descripción de endpoints.
- Request schemas.
- Response schemas.
- Códigos HTTP.
- Errores posibles.
- Validaciones.
- Requisitos de autorización.
- Bearer JWT security scheme.
- Ejemplos de requests.
- Ejemplos de responses.

Swagger UI debe permitir utilizar Bearer tokens para probar endpoints protegidos.

Los endpoints públicos deben identificarse correctamente.

No exponer documentación administrativa o detalles sensibles indiscriminadamente en producción.

## 15.3 No Comments vs Documentation

La documentación OpenAPI debe realizarse mediante contratos, anotaciones y configuración.

No usar comentarios del código como sustituto de documentación.

La documentación arquitectónica detallada debe estar en /docs.

---

# 16. TESTING STRATEGY

El proyecto debe incluir pruebas automatizadas de calidad profesional.

## 16.1 Unit Tests

Probar:

- Value Objects.
- Domain Entities.
- Aggregates.
- Domain Policies.
- Domain Services.
- Application Use Cases.
- Permission Evaluation.
- Mappers relevantes.
- Invariantes.

## 16.2 Integration Tests

Utilizar Testcontainers con PostgreSQL.

Probar:

- Repositorios.
- Adaptadores JPA.
- Flyway.
- Constraints.
- Transacciones.
- Refresh token rotation.
- Revocación.
- Concurrencia.

## 16.3 Security Tests

Probar obligatoriamente:

1. Sin JWT devuelve 401.
2. JWT expirado devuelve 401.
3. JWT con firma inválida devuelve 401.
4. JWT con issuer inválido devuelve 401.
5. JWT con audience inválida devuelve 401.
6. Usuario sin permiso devuelve 403.
7. USER puede consultar sus tareas.
8. USER no puede modificar tareas ajenas.
9. AUDITOR puede consultar pero no modificar.
10. ADMIN puede ejecutar operaciones administrativas permitidas.
11. Usuario deshabilitado es rechazado según la política.
12. Refresh token revocado no puede reutilizarse.
13. Refresh token rotation funciona.
14. Refresh concurrente no produce múltiples tokens válidos indebidamente.
15. No existe contaminación del SecurityContext entre solicitudes.
16. CORS responde correctamente a preflight válido.
17. El backend mantiene controles de autenticación independientemente de CORS.
18. Las reglas de autorización se aplican en capas apropiadas.
19. El registro público no permite asignarse privilegios administrativos.
20. Los permisos se resuelven de forma coherente.

## 16.4 Architecture Tests

Considerar ArchUnit para verificar:

- Domain no depende de Spring.
- Domain no depende de JPA.
- Application no depende de Web.
- Application no depende de Infrastructure.
- Adapters implementan puertos.
- No existen dependencias circulares.
- Los bounded contexts mantienen límites adecuados.

La arquitectura debe poder verificarse mediante pruebas, no depender únicamente de convenciones.

---

# 17. DOCUMENTACIÓN ARQUITECTÓNICA

Crear documentación dentro de /docs.

Archivos propuestos:

```text
docs/
├── architecture.md
├── domain-model.md
├── bounded-contexts.md
├── aggregate-design.md
├── authentication-flow.md
├── jwt-validation-flow.md
├── authorization-model.md
├── security-context-concurrency.md
├── mapping-strategy.md
├── security-decisions.md
├── threat-model.md
├── api-design.md
├── testing-strategy.md
└── adr/
```

Utilizar Mermaid para diagramas.

Incluir:

- Diagrama de contexto.
- Diagrama de componentes.
- Diagrama hexagonal.
- Diagrama de módulos.
- Diagramas de secuencia.
- Modelo de dominio.
- Relaciones entre agregados.
- Modelo de persistencia.
- Flujo de login.
- Flujo de JWT.
- Flujo de refresh.
- Flujo de autorización.

Documentar decisiones importantes mediante Architecture Decision Records.

Cada ADR debe incluir:

- Context.
- Decision.
- Alternatives.
- Rationale.
- Trade-offs.
- Consequences.

No escribir documentación extensa sobre decisiones triviales.

---

# 18. PERFORMANCE & SCALABILITY

Aunque sea un MVP, quiero entender cómo hacerlo escalable.

Evaluar:

- Stateless authentication.
- JWT validation performance.
- Carga de base de datos.
- Índices.
- Transaction isolation.
- Connection pooling.
- Queries de permisos.
- Refresh token concurrency.
- Password hashing costs.
- Paginación.
- Estrategias de caching, solo si se justifican.

No introducir Redis inicialmente.

No implementar optimizaciones prematuras.

Utilizar métricas para justificar mejoras.

---

# 19. OBSERVABILITY

Utilizar buenas prácticas básicas.

- Structured logging cuando corresponda.
- Correlation IDs.
- Spring Boot Actuator.
- Health checks.
- Logs de autenticación sin secretos.
- Logs de autorización denegada cuando sean útiles.
- Manejo adecuado de errores.
- Métricas básicas si son necesarias.

No registrar:

- Passwords.
- JWT completos.
- Refresh tokens.
- Private keys.
- Información sensible innecesaria.

No crear una plataforma completa de observabilidad.

---

# 20. ANTI-OVERENGINEERING RULES

Aunque deseo una arquitectura profesional, el proyecto debe seguir siendo un mini proyecto.

No implementar:

- Microservicios.
- Kafka.
- Event Sourcing.
- CQRS complejo.
- Kubernetes.
- OAuth2 Authorization Server completo.
- Keycloak.
- Frontend.
- Multi-tenancy.
- GraphQL.
- Un motor genérico de autorización.
- Redis sin necesidad.
- Event Bus genérico.
- Infrastructure as Code para cloud.
- Integraciones externas ficticias.

No crear una interfaz por cada clase.

No crear Domain Services sin reglas de dominio.

No crear Value Objects sin significado.

No introducir ACL artificiales.

No utilizar patrones de diseño de manera decorativa.

La profesionalidad debe expresarse en la claridad de las decisiones, no en la cantidad de abstracciones.

---

# 21. IMPLEMENTATION PLAN

Trabajaremos por fases.

## Phase 0 — Architecture & Domain Discovery

Antes de escribir código:

1. Definir alcance definitivo.
2. Identificar bounded contexts.
3. Definir lenguaje ubicuo.
4. Identificar entidades.
5. Identificar aggregates.
6. Identificar Value Objects.
7. Definir Domain Services.
8. Diseñar políticas de autorización.
9. Diseñar puertos y adaptadores.
10. Evaluar necesidad de ACL.
11. Diseñar mappers.
12. Diseñar modelo relacional.
13. Definir matriz de roles y permisos.
14. Diseñar endpoints.
15. Diseñar flujos de autenticación.
16. Definir modelo JWT.
17. Diseñar refresh token lifecycle.
18. Diseñar transacciones.
19. Identificar amenazas.
20. Definir criterios de aceptación.
21. Seleccionar versiones compatibles.
22. Crear ADRs iniciales.

Entregar un diseño técnico completo, pero pragmático.

Detenerse para revisión antes de implementar.

## Phase 1 — Project Bootstrap

- Gradle Kotlin DSL.
- Java 25.
- Spring Boot.
- Docker Compose.
- PostgreSQL.
- Flyway.
- Arquitectura inicial.
- Configuración de calidad.
- Prueba de arranque.

## Phase 2 — DDD Domain Modeling

- Entidades.
- Aggregates.
- Value Objects.
- Domain Policies.
- Invariantes.
- Domain Services necesarios.
- Unit tests.

## Phase 3 — Persistence Adapters

- JPA entities.
- Spring Data repositories.
- Persistence mappers.
- Output port implementations.
- Migraciones.
- Integration tests.

## Phase 4 — Identity & Login

- UserDetailsService.
- PasswordEncoder.
- DaoAuthenticationProvider.
- AuthenticationManager.
- Registro.
- Login.
- JWT issuance.
- Login tests.

## Phase 5 — JWT Authentication

- OAuth2 Resource Server.
- JWT Decoder.
- JWT Encoder.
- Authentication Converter.
- SecurityFilterChain.
- SecurityContextHolder.
- EntryPoint.
- Security tests.

## Phase 6 — Authorization

- RBAC.
- Permission-based authorization.
- Resource ownership.
- Domain Policies.
- Method Security.
- Authorization tests.

## Phase 7 — Token Lifecycle

- Refresh sessions.
- Rotation.
- Revocation.
- Logout.
- Concurrency protection.
- Integration tests.

## Phase 8 — REST API & Documentation

- OpenAPI.
- Swagger UI.
- DTOs.
- Validation.
- Response mapping.
- Error contracts.
- API tests.

## Phase 9 — Security Hardening

- CORS.
- CSRF.
- Login protection.
- Secret management.
- Authorization review.
- Dependency review.
- Threat model review.

## Phase 10 — Architecture Review & Final Assessment

- Architecture tests.
- SOLID review.
- DDD review.
- Security review.
- Concurrency review.
- Test suite.
- Manual API testing.
- Final documentation.
- Interview questions.

---

# 22. WORKING METHODOLOGY

Quiero que trabajes como arquitecto, implementador y mentor.

Al comenzar cada fase presenta:

**A. Objetivo**

Qué construiremos.

**B. Arquitectura**

Cómo encaja en el sistema.

**C. Teoría**

Qué conceptos debemos dominar.

**D. Diseño**

Clases, interfaces, límites y responsabilidades.

**E. Implementación**

Código limpio y profesional.

**F. Testing**

Cómo verificarlo.

**G. Trade-offs**

Qué alternativas existen y por qué seleccionamos una.

**H. Assessment**

Preguntas técnicas relacionadas con lo implementado.

No quiero explicaciones superficiales.

Al estudiar un componente de Spring Security, explica:

- Quién lo crea.
- Quién lo invoca.
- Qué recibe.
- Qué devuelve.
- Cuándo se ejecuta.
- Cómo se relaciona con el resto del framework.
- Qué sucede cuando falla.
- Cómo se maneja con concurrencia.
- Qué componentes son singletons.
- Qué información pertenece a cada request.

No avanzar a una fase nueva automáticamente.

---

# 23. STRICT DEFINITION OF DONE

Cada fase se considerará terminada cuando:

- El código compile.
- Se cumplan sus criterios funcionales.
- Existan pruebas suficientes.
- No se introduzcan vulnerabilidades conocidas deliberadamente.
- No existan secretos reales en el repositorio.
- Se respeten los límites arquitectónicos.
- Se documenten las decisiones relevantes.
- No existan comentarios innecesarios en el código.
- Se respeten SOLID y Clean Code.
- No existan magic values.
- Los nombres sean expresivos.
- Se utilicen adecuadamente las características de Java moderno.
- Las dependencias sean compatibles.
- La API mantenga contratos claros.
- El diseño siga siendo proporcional al tamaño del proyecto.

No afirmar que una fase está terminada sin ejecutar las comprobaciones disponibles.

Si alguna prueba no puede ejecutarse, explicar exactamente cuál y por qué.

---

# 24. FINAL SUCCESS CRITERIA

El proyecto estará completo cuando pueda demostrar:

1. Arquitectura hexagonal funcional.
2. Aplicación coherente de DDD.
3. Entidades con invariantes.
4. Value Objects bien diseñados.
5. Servicios de dominio cuando correspondan.
6. Separación entre entidades JPA y entidades de dominio.
7. Puertos y adaptadores correctamente utilizados.
8. Estrategia de mapeo consistente.
9. Registro y login funcionando.
10. Autenticación con JWT.
11. Refresh token rotation y logout.
12. Roles y permisos efectivos.
13. Autorización por propietario.
14. Manejo correcto de 401 y 403.
15. Protección contra IDOR/BOLA.
16. SecurityContext aislado entre solicitudes.
17. PostgreSQL y Flyway.
18. Gradle Kotlin DSL.
19. API documentada con Swagger/OpenAPI.
20. Unit tests, integration tests y security tests.
21. Código sin comentarios y con naming expresivo.
22. Aplicación de SOLID.
23. Uso adecuado de records, sealed types, pattern matching, Optional y Streams.
24. Documentación arquitectónica.
25. Capacidad de explicar el funcionamiento interno de Spring Security.

---

# 25. INITIAL INSTRUCTION

**Comienza exclusivamente con PHASE 0 — Architecture & Domain Discovery.**

No implementes todavía la aplicación.

Primero realiza el diseño técnico integral de SecureFlow API.

Presenta:

1. Product scope definitivo.
2. Bounded contexts recomendados.
3. Ubiquitous Language.
4. Domain Model.
5. Aggregate design.
6. Entities y Value Objects.
7. Domain Services y políticas necesarias.
8. Diagrama de arquitectura hexagonal.
9. Estructura definitiva de paquetes.
10. Dependency rules.
11. Puertos y adaptadores.
12. Estrategia de ACL.
13. Mapping strategy.
14. Modelo de persistencia.
15. Matriz profesional de roles y permisos.
16. Modelo de autenticación JWT.
17. Flujo de AuthenticationManager y Providers.
18. Flujo de SecurityContextHolder.
19. Modelo de autorización.
20. Threat model.
21. Diseño de endpoints.
22. Estrategia de testing.
23. Estrategia de Swagger/OpenAPI.
24. Versiones y dependencias verificadas.
25. ADRs recomendados.
26. Riesgos de sobreingeniería.
27. Plan de implementación por dependencias.

Para cada decisión relevante, explica por qué la recomiendas y qué alternativa descartaste.

**No aceptes automáticamente todas mis ideas si alguna introduce complejidad innecesaria. Tu responsabilidad es proponer la mejor solución de ingeniería, justificarla y mantener un equilibrio profesional entre calidad, seguridad, mantenibilidad y simplicidad.**

Al finalizar esta fase, detente para que revisemos las decisiones arquitectónicas.

El objetivo final no es simplemente construir una API con JWT.

**El objetivo es comprender y aplicar cómo un equipo profesional de ingeniería diseña un sistema de autenticación y autorización con Spring Security, DDD, arquitectura hexagonal y excelentes prácticas de desarrollo Java.**