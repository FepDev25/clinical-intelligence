# Plan estratégico y arquitectónico — Clinical Extraction MVP

Fecha: 2026-10-27

Estado: plan aprobado; H1 completo en artefactos y H2–H4 implementados y verificados. H4 incluye una prueba sintética autenticada con Gemini. H5 ejecutado como evaluación: 234 operaciones sintéticas, criterios incumplidos y MVP no aprobado. Ver [informe H5](../../h5-validation.md). Evidencia: [validación de H2](../../h2-validation.md), [H3](../../h3-validation.md) y [H4](../../h4-validation.md).

Spec de referencia: [MVP de Clinical Extraction API](../specs/2026-10-27-clinical-extraction-mvp-design.md), aprobada en conversación el 2026-10-27.

Normas del proyecto: [AGENTS.md](../../../AGENTS.md).

**Objetivo:** entregar una API de extracción consumible por una aplicación externa, con evidencia, catálogos opcionales y revisión humana, sin depender de SFM ni almacenar notas clínicas.

**Arquitectura:** un monolito con una capacidad implementada, un caso de uso independiente del transporte y del proveedor, validadores deterministas y un adaptador LLM sustituible por un fake de pruebas.

**Stack previsto:** Java 21, Spring Boot, Spring AI, REST/JSON y OpenAPI; versiones compatibles verificadas al iniciar la ejecución.

**Método de ejecución:** el builder leerá spec y plan y desarrollará por hitos mediante TDD. Los checkboxes representan resultados por demostrar, no un guion de archivos ni código anticipado. La preferencia explícita del usuario por un plan estratégico prevalece sobre el formato detallado de la guía `writing-plans`.

## 1. Punto de partida y límites

Al aprobar este plan, el repositorio contenía las instrucciones y la spec, sin aplicación ni contratos. H1 cuenta con [decisiones](../../adr/0001-extraction-contract-decisions.md), [formalización técnica](../../adr/0002-extraction-wire-contract.md), [OpenAPI y ejemplos verificables](../../../openapi/README.md) y [corpus/reglas](../../../evaluation/README.md), con [reporte de cierre](../../../evaluation/VALIDATION.md). Ese fue el punto de partida documental; H2 y H3 añaden ahora núcleo, aplicación HTTP y pruebas del backend. H4 incorporó después el proveedor real; H5 lo evaluó sin aprobar los criterios de calidad.

- Una API síncrona de extracción en español como primer idioma evaluado.
- Un desplegable, sin BD, Redis, pgvector, colas ni servicios auxiliares obligatorios.
- Un proveedor real al completar el MVP; el fake demuestra comportamiento técnico, no calidad del modelo.
- Contrato sin IDs obligatorios de paciente, cita, expediente o tenant.
- Evidencias sobre texto original y catálogos opcionales recibidos en cada operación.
- Sin códigos inferidos, porcentajes de confianza, decisiones terapéuticas ni persistencia automática.
- SFM permanece como referencia de solo lectura; PreAudit Health no interviene en la construcción.
- El plan no autoriza despliegues públicos, contenedores, acceso a datos reales ni cambios en otros proyectos.

## 2. Responsabilidades e interfaces arquitectónicas

| Límite | Responsabilidad | Restricción de dependencia |
|---|---|---|
| HTTP y seguridad | Identificar consumidor, autorizar scope, validar contrato, traducir resultado y errores | No implementar reglas clínicas ni exponer objetos del SDK. |
| Caso de uso | Orquestar procesamiento, aplicar políticas de aceptación y producir un resultado verificable | Depender de contratos internos, no de controladores, JPA ni `ChatClient`. |
| Dominio y validación | Representar menciones, contexto, evidencia, atributos y coincidencias de catálogo | No depender de HTTP ni del proveedor. |
| Adaptador LLM | Construir la petición al proveedor, interpretar salida y comunicar finalización, uso y errores | No resolver IDs de negocio ni dictar la forma pública de la API. |
| Operación | Configuración, límites de recursos y métricas seguras | No capturar contenido de solicitudes o respuestas clínicas. |

El puerto del proveedor deberá entregar propuestas y metadatos técnicos suficientes para distinguir finalización, truncamiento y fallo. El caso de uso deberá devolver éxito válido, éxito con descartes advertidos o fallo explícito. Un resultado sin entidades no puede servir como sustituto de un error.

El fake y el adaptador real cumplirán el mismo contrato interno. Las propuestas del modelo se consideran no confiables hasta atravesar validación. Los catálogos se resuelven fuera del modelo; las credenciales de consumidor se resuelven fuera de la entrada clínica.

El builder elegirá paquetes, nombres, tipos y archivos mínimos para preservar estos límites. No se crean módulos vacíos para capacidades futuras. Los artefactos de contrato, evaluación y documentación se mantienen versionados junto al código.

## 3. Decisiones previas a la implementación correspondiente

El builder prepara ADRs breves antes del código afectado. Cada ADR debe incluir opciones, decisión, consecuencias y evidencia; las decisiones dentro del alcance se pueden resolver técnicamente. Solo una ampliación material del alcance o una autorización externa faltante exige volver al usuario.

| Decisión | Recomendación de partida | Cierre requerido |
|---|---|---|
| Contrato público y evidencia | Partir de `POST /api/v1/extractions`; representar contexto y evidencia por entidad/atributo | Ruta, nombres, nulabilidad, estados, errores, posiciones Unicode y política de descartes definidos en OpenAPI y ejemplos. |
| Identidad del integrador | Evaluar primero API keys configuradas fuera de Git con identidad y scopes en backend | Comparación con OAuth2, validación de credenciales, rotación/revocación por configuración y ausencia de tenant autodeclarado como autoridad. |
| Proveedor y salida del modelo | Un proveedor y una modalidad de salida; sin failover | Disponibilidad autorizada de credenciales, compatibilidad del stack, modelo identificado, finalización verificable y condiciones de tratamiento de datos documentadas. |
| Límites operativos | Presupuesto acotado para entrada, salida, llamadas y concurrencia | Valores y errores observables para tamaños, catálogos, timeout total, sobrecarga y reintentos; sin esperas o trabajo ilimitados. |
| Evaluación | Corpus sintético anotado antes de medir | Reglas de anotación, métricas, umbrales por entidad y atributos, política para fallos críticos y procedimiento de repetición. |

La elección comercial del proveedor puede quedar pendiente mientras se construyen contrato y núcleo con fake. Debe quedar cerrada antes del hito de integración real. Los valores operativos se justifican con el escenario de demo y las restricciones del proveedor; no se promete capacidad de producción sin medición.

## 4. Hitos de construcción

### H1 — Contrato y reglas verificables

**Resultado:** una integración y una evaluación pueden describirse sin depender de clases internas.

- [x] Formalizar el ADR del contrato y un OpenAPI validable con ejemplos de éxito, vacío válido, advertencias y errores.
- [x] Definir estados de diagnóstico, prescripción y procedimiento; separar afirmación, temporalidad y sujeto cuando combinarlos permita ambigüedades.
- [x] Definir evidencia por hallazgo y atributo, convención Unicode, resolución de catálogos y campos desconocidos.
- [x] Fijar límites de entrada, política de localidad y errores; cerrar el mecanismo mínimo de autenticación.
- [x] Crear el corpus sintético inicial, las reglas de anotación y los criterios de evaluación antes de medir el modelo.

**Prueba de salida:** contrato validado y ejemplos conformes; cada caso del corpus tiene un resultado esperado o una regla de aceptación explícita. Los ejemplos incluyen negaciones, duración ausente y catálogo ambiguo. Las decisiones públicas están documentadas antes de implementar HTTP.

### H2 — Núcleo de extracción con proveedor simulado

**Depende de:** H1. El scaffolding mínimo y la selección compatible de dependencias forman parte de este hito.

**Resultado:** el caso de uso aplica las garantías del producto sin red, servidor HTTP ni infraestructura externa.

- [x] Establecer el puerto del proveedor, su fake y los resultados de finalización y error mediante TDD.
- [x] Implementar la política de evidencias y atributos: rechazar referencias inválidas y evitar que un campo sin respaldo se exponga como sustentado.
- [x] Resolver nombre/alias contra catálogos con normalización conservadora, conservando ambigüedad y ausencia de coincidencia.
- [x] Preservar el contexto y las contradicciones de las propuestas aceptadas, sin completar información ni fusionar estados incompatibles.
- [x] Distinguir vacío legítimo, descartes con advertencias y respuesta incompleta o no conforme.

**Prueba de salida:** casos de uso e invariantes ejecutados con fake; cero evidencias textuales inválidas y cero IDs ajenos al catálogo en los resultados aceptados. Pruebas con varias peticiones demuestran que no comparten notas, catálogos ni acumuladores. Esto prueba las políticas del núcleo, no la capacidad semántica de un LLM.

### H3 — API independiente consumible

**Depende de:** H2 y decisión de identidad cerrada.

**Resultado:** un consumidor externo utiliza el flujo por HTTP con el proveedor simulado seleccionado explícitamente para pruebas.

- [x] Exponer la operación y los errores del contrato, con autenticación y scope antes de cualquier llamada al proveedor.
- [x] Verificar serialización, nulabilidad, nombres y ejemplos mediante pruebas contractuales contra OpenAPI.
- [x] Aplicar límites de entrada y generar identificadores de petición sin incorporar contenido clínico.
- [x] Preparar arranque local y ejemplo cURL sintético, sin BD ni acceso a SFM.
- [x] Asegurar que el modo fake sea explícito y reconocible; una configuración real incompleta no debe cambiar silenciosamente al fake.

**Prueba de salida:** consumo HTTP reproducible; solicitudes inválidas, sin credencial o sin scope no invocan al proveedor. OpenAPI y respuesta real coinciden. El arranque no llama al modelo ni necesita servicios adicionales.

### H4 — Proveedor real y controles operativos

**Depende de:** H3, ADR del proveedor y límites operativos cerrados. [ADR 0005](../../adr/0005-gemini-provider-h4.md) selecciona Gemini 3.8 Flash y documenta los presupuestos. Prueba sintética remota verificada; nivel de cuenta y condiciones para un futuro despliegue se comprueban por separado.

**Resultado:** la misma API y el mismo núcleo procesan notas sintéticas mediante un modelo real.

- [x] Integrar el adaptador con salida validable, prompt versionado y separación de notas/catálogos respecto de instrucciones.
- [x] Probar el adaptador con dobles de transporte o respuestas controladas antes de ejecutar la evaluación remota.
- [x] Aplicar presupuesto total de tiempo y llamadas, concurrencia acotada y reintentos que no excedan ese presupuesto.
- [x] Traducir errores, truncamiento, falta de contenido, estructura ilegible y cancelación a los resultados técnicos definidos.
- [x] Registrar latencia, versiones, uso y coste disponible, comprobando que el SDK y sus rutas de error no filtran contenido ni secretos.

**Prueba de salida:** contrato interno del adaptador verificado sin red y una ejecución sintética explícita con proveedor real. Un fallo de formato no produce un éxito vacío. Las pruebas de timeout y sobrecarga confirman liberación de recursos y presupuesto acotado. Uso o coste desconocido se distingue de cero.

### H5 — Evaluación y entrega del MVP

**Depende de:** H4 y criterios de evaluación fijados en H1.

**Resultado:** una integración reproducible acompañada de evidencia sobre su comportamiento y límites.

- [x] Ejecutar el corpus con el modelo real y reportar precisión, omisiones, atributos sin respaldo, errores de contexto y coincidencias de catálogo.
- [x] Registrar modelo, parámetros, prompt, validación, corpus, fecha, latencia y consumo; conservar únicamente datos sintéticos en artefactos de evaluación.
- [ ] Convertir fallos encontrados en casos de regresión; si se modifica prompt o código, repetir las comprobaciones afectadas y la evaluación necesaria sin relajar los umbrales para obtener aprobación.
- [x] Completar documentación de configuración, cURL, errores, revisión humana, retención externa y límites conocidos.
- [x] Ejecutar la suite de verificación del proyecto y la integración externa final, indicando comandos y resultados reales.

**Evidencia de ejecución:** [H5](../../h5-validation.md), [arnés](../../../evaluation/RUNNING.md) e [índice de regresiones de desarrollo](../../../evaluation/regressions/README.md). Se preservan fallos y seis intentos con decisión de revisión pendiente; no se ajustó el extractor durante la medición. Corregir y volver a evaluar sigue pendiente.

**Prueba de salida:** los diez criterios de aceptación de la spec cuentan con evidencia y los umbrales acordados se satisfacen. Si la calidad no alcanza los criterios, la entrega se informa como prototipo evaluado con pendientes, sin declarar el MVP terminado.

## 5. Focos de revisión y pruebas responsables

| Condición | Comportamiento esperado | Hito responsable |
|---|---|---|
| Fragmentos repetidos, tildes, saltos de línea y emojis | Posiciones inequívocas sobre la nota original; referencias inválidas rechazadas | H1 fija la convención; H2 la verifica. |
| Alias que colisionan o cambian concentración/presentación | Sin elección arbitraria ni equivalencia clínica inventada | H1 fija normalización; H2 prueba colisiones. |
| Dos peticiones simultáneas con catálogos distintos | Sin mezcla de estado ni resultados entre consumidores | H2 prueba aislamiento del núcleo; H3 verifica flujo HTTP. |
| Timeout o reintento durante saturación | Trabajo y duración acotados, recursos liberados y error estable | H4 verifica presupuesto y concurrencia. |
| Excepción del SDK con contenido en el mensaje | Error público y observabilidad sin nota, prompt, salida cruda o credenciales | H4 prueba rutas de error y logging. |

Además, H2 ejercita campos ausentes, contradicciones y evidencias falsas con fake; H3 verifica restricciones contractuales y seguridad; H5 mide negación, antecedentes, atributos y prompt injection con el modelo real. Las pruebas semánticas del modelo y las invariantes deterministas se reportan por separado.

## 6. Protocolo de trabajo del builder

Para cada comportamiento, el builder escribe y ejecuta primero una prueba que falle por la ausencia del comportamiento, implementa lo mínimo, ejecuta las pruebas pertinentes y refactoriza manteniéndolas verdes. Contratos y documentos se verifican con validadores y revisión de coherencia, sin fabricar pruebas que repitan texto.

Al cerrar cada hito debe registrar qué resultado logró, pruebas ejecutadas, decisiones, limitaciones y siguiente dependencia. Un checkbox se marca únicamente con evidencia disponible. El alcance de las pruebas crece en función de los cambios y riesgos; las pruebas ordinarias no consumen APIs remotas.

Si aparece una ambigüedad rutinaria, el builder la resuelve y documenta. Si afecta el contrato, actualiza ADR, OpenAPI, ejemplos y pruebas juntos. Si exige almacenamiento clínico, otra capacidad, un proveedor adicional obligatorio o cambios en SFM/PreAudit Health, detiene esa ampliación y solicita dirección.

## 7. Trazabilidad de aceptación

| Criterios de la spec | Evidencia principal |
|---|---|
| 1–2: integración externa e independencia de almacenamiento | H3: arranque y cURL; H5: integración con modelo real. |
| 3–5: evidencia, contexto, campos y catálogos | H2: invariantes; H5: evaluación semántica sobre corpus. |
| 6: salidas no conformes y fallos | H2: políticas; H4: comportamiento del adaptador. |
| 7: contrato y autorización | H1: OpenAPI; H3: pruebas HTTP y rechazo antes del proveedor. |
| 8: pruebas sin red y evaluación real | H2–H4: suite determinista; H5: reporte contra umbrales previos. |
| 9: observabilidad segura | H4: métricas y pruebas de no filtración. |
| 10: integración, revisión y límites documentados | H5: documentación y demostración reproducible. |

## 8. Handoff

H1–H4 están implementados y verificados. H5 completó su medición y documentación, pero **el MVP no está terminado**: faltan correcciones y una evaluación aprobada. El siguiente incremento debe partir de [los hallazgos H5](../../h5-validation.md), revisar incidencias de referencias/comparación y usar las regresiones de desarrollo para mejorar extracción/evidencia. No usar reservado como oráculo de TDD. Si sus resultados orientan ajustes, trasladar los escenarios afectados a regresión y reemplazarlos antes de una nueva evaluación independiente. Conservar esta línea base; no relajar los umbrales.

La spec define el comportamiento aprobado; OpenAPI fijará el transporte; los ADRs explicarán decisiones; las pruebas y la evaluación aportarán evidencia. El plan coordina esos artefactos y su secuencia. Ningún documento, fake o prueba aislada sustituye la demostración completa del MVP.
