# Spec — MVP de Clinical Extraction API

Fecha: 2026-10-27

Estado: aprobada en conversación; H1 completo en artefactos (contrato, ejemplos, corpus y reglas), núcleo H2, API HTTP H3 y adaptador Gemini H4 implementados y verificados. H4 incluye una extracción sintética autenticada. H5 completó 234 operaciones sintéticas reales y no aprobó los criterios: [informe](../../h5-validation.md). El MVP permanece pendiente de corrección y reevaluación.

Referencia normativa del proyecto: `AGENTS.md`.

## 1. Intención y resultado comprobable

Clinical Intelligence permite que una aplicación externa transforme una nota clínica suministrada en propuestas estructuradas de diagnósticos mencionados, prescripciones y procedimientos, con evidencia verificable y límites explícitos. El consumidor conserva la responsabilidad de revisar, aceptar, editar y persistir el resultado.

El MVP se demuestra consumiendo una API HTTP desde cURL u otro cliente independiente. Debe funcionar sin SFM, sin un expediente preexistente y sin base de datos para el procesamiento clínico.

El usuario confirmó un MVP con API y un proceso spec → plan estratégico y arquitectónico → desarrollo mediante TDD. Este documento define el diseño; no contiene un inventario exhaustivo de archivos, clases, métodos ni código anticipado.

## 2. Alcance y exclusiones

Incluye una operación síncrona de extracción, contrato OpenAPI versionado, autenticación de consumidores, validación de entrada y salida, evidencia textual, resolución opcional de catálogos suministrados, observabilidad sin contenido clínico, pruebas deterministas y evaluación con datos sintéticos.

El primer idioma evaluado será español. El soporte de otras localidades deberá declararse explícitamente; recibir un identificador de localidad no demuestra soporte multilingüe.

Quedan fuera CIE-10 por búsqueda semántica, consultas de historial, conciliación de cargos, interfaces visuales, almacenamiento de notas, gestión de pacientes, SDKs, portal comercial y provisión de organizaciones. Tampoco se incluyen OCR, documentos adjuntos, procesamiento por lotes, streaming, conversaciones ni escritura en sistemas clínicos externos.

Los códigos existentes en el texto pueden conservarse como menciones. La extracción no genera códigos diagnósticos o de procedimientos a partir de conocimiento del modelo. Buscar candidatos de codificación será una capacidad posterior e independiente.

## 3. Enfoque y alternativas consideradas

| Enfoque | Consecuencia |
|---|---|
| Extraer directamente el módulo P2 de SFM | Acelera el primer prototipo, pero arrastra repositorios, IDs internos y garantías insuficientes de evidencia. |
| Crear una API independiente de extracción | Permite comprobar integración, evidencia y pruebas desde el primer incremento, con un alcance acotado. |
| Construir primero una plataforma de cuatro capacidades | Amplía las decisiones de persistencia, infraestructura y contratos antes de validar una integración. |

La propuesta seleccionada es una API independiente centrada en extracción, consistente con `AGENTS.md` y el acuerdo de MVP con API. SFM aporta aprendizajes y contraejemplos; cualquier reutilización futura requiere revisión específica.

## 4. Semántica de extracción

### Diagnósticos mencionados

Se representan afirmaciones diagnósticas expresadas en la nota, preservando su carácter actual, histórico, sospechado o negado cuando el texto permita distinguirlo. Una mención negada no se presenta como un diagnóstico afirmado; una sospecha no se transforma en una confirmación. Los antecedentes familiares deben distinguirse de los del paciente.

No se convierte un síntoma en una enfermedad inferida ni se deduce un diagnóstico a partir de un medicamento. Cuando el contexto no permite clasificar una mención, se conserva la incertidumbre o se omite con advertencia; nunca se asigna un estado por defecto que afirme una condición actual.

### Prescripciones

Se extraen indicaciones de medicamentos explícitas. Los medicamentos históricos, suspendidos o negados no aparecen como prescripciones activas. Una lista de medicamentos sin contexto de prescripción no autoriza inventar una indicación; debe señalarse la ambigüedad.

Dosis, frecuencia, vía y duración se informan únicamente si están sustentadas por la nota. La concentración de una presentación no equivale automáticamente a la dosis administrada. Los días de reposo, evolución o control no equivalen a duración del tratamiento.

### Procedimientos

Se distingue una prestación documentada como realizada de una solicitada, planificada, histórica, negada o de estado desconocido. La extracción no implica realización, facturabilidad ni recomendación de ejecutar el procedimiento.

### Información faltante y contradicciones

Los campos desconocidos se representan de una única forma definida en OpenAPI, preferentemente con ausencia explícita o `null`, sin textos de relleno como «según indicación médica». No se completan valores habituales por conocimiento clínico general.

Las afirmaciones contradictorias se conservan con sus evidencias y una advertencia. No se elige silenciosamente una como verdadera. Repeticiones equivalentes pueden agruparse conservando sus evidencias; menciones con diferencias de estado, dosis o temporalidad no se fusionan automáticamente.

## 5. Evidencia y garantías

Cada hallazgo debe enlazarse con uno o más fragmentos literales de la nota original. Los atributos relevantes —por ejemplo dosis, duración, negación o realización— también requieren respaldo identificable. Una cita del nombre de un medicamento no respalda por sí sola su frecuencia.

El contrato deberá fijar fragmentos y posiciones sobre el texto original sin normalizar, con una convención inequívoca de índices y Unicode. Esa convención se decidirá al formalizar OpenAPI y tendrá ejemplos con tildes, saltos de línea y caracteres fuera del plano básico.

La aplicación comprobará de forma determinista que los fragmentos corresponden al texto y que los identificadores resueltos pertenecen al catálogo recibido. Estas comprobaciones detectan referencias inválidas; no demuestran por sí solas que la interpretación clínica sea correcta. Negación, temporalidad y asociación entre atributos también necesitan evaluación semántica.

Si un hallazgo carece de evidencia válida, se excluye y se devuelve una advertencia sin reproducir el contenido descartado en logs. No se devuelven porcentajes de confianza. Un resultado vacío válido se distingue de una respuesta del modelo incompleta o fallida.

## 6. Catálogos opcionales

El consumidor puede aportar catálogos acotados para resolver menciones. Cada entrada identifica su catálogo, versión e identificador opaco, junto con un nombre y, si corresponde, alias explícitos. Los identificadores son datos del consumidor, no UUIDs obligatorios de SFM ni credenciales de autorización.

La extracción debe funcionar sin catálogos. Primero preserva lo que dice la nota y después intenta resolverlo; no sustituye una entidad desconocida por una disponible en el catálogo.

Para v0.1 se propone coincidencia determinista por nombre o alias con normalización documentada y conservadora. No se incorporan búsqueda vectorial ni equivalencias clínicas implícitas. Una coincidencia ambigua queda sin resolver; una inexistente se informa sin inventar IDs. El resultado diferencia no suministrado, resuelto, sin coincidencia y ambiguo.

Pertenecer a un catálogo no certifica equivalencia clínica. Los códigos textuales se distinguen de los códigos cuya pertenencia a un catálogo explícito haya sido comprobada.

## 7. Contrato público

La operación `POST /api/v1/extractions` está formalizada mediante ADR 0002 y OpenAPI v0.1.0, e implementada en H3 con proveedor simulado explícito.

La entrada contendrá una nota, localidad y catálogos opcionales. No exigirá IDs de paciente, cita, expediente, factura o tenant. Se definirán límites de tamaño y cardinalidad, tratamiento de espacios y campos desconocidos, y localidades admitidas.

La respuesta contendrá entidades y atributos sustentados, contexto de las menciones, evidencia, estado de resolución de catálogos, advertencias estructuradas y versiones de procesamiento. Incluirá un identificador de petición generado por el servidor y una indicación inequívoca de revisión humana requerida.

La finalización del procesamiento será explícita: un éxito indica que la respuesta del proveedor terminó y fue validada. Puede contener hallazgos descartados con advertencias, pero no ocultar truncamiento o interrupción del proveedor.

Los errores serán estables y legibles por máquina. Se distinguirán entrada inválida, falta de autenticación, scope insuficiente, límites de servicio, indisponibilidad o timeout del proveedor y salida no conforme. OpenAPI fijará códigos HTTP, estructura y ejemplos; los errores no expondrán prompts, notas, credenciales ni respuestas crudas del proveedor.

Se versionará el contrato público independientemente del prompt, la política de validación y la configuración del modelo. OpenAPI será la fuente del contrato de transporte; esta spec será la fuente de intención y comportamiento. Un cambio semántico exigirá revisar ambos y sus pruebas.

## 8. Arquitectura y dependencias

Un solo desplegable y una sola capacidad implementada. Java 21, Spring Boot y Spring AI son la base preferida; las versiones compatibles se verificarán con documentación oficial al preparar el build.

Los límites necesarios son:

- Transporte HTTP: autenticación, validación contractual y traducción de errores.
- Aplicación: orquestación de la extracción y política de resultados.
- Dominio: evidencias, menciones y resolución de catálogos, sin dependencia del proveedor LLM.
- Infraestructura: adaptador del modelo, configuración y métricas.

El caso de uso dependerá de un puerto interno de extracción, sustituible por un fake determinista. El SDK, las respuestas del proveedor y las anotaciones HTTP no definirán el dominio ni el contrato público. El builder elegirá la organización mínima de paquetes que preserve esos límites, sin módulos Maven adicionales por anticipación.

El flujo previsto es validar → obtener propuestas del modelo → comprobar finalización y estructura → validar evidencia y atributos → resolver catálogos → responder con advertencias y metadatos. El modelo no recibirá herramientas capaces de persistir datos, consultar expedientes o ejecutar acciones externas.

Se integrará un proveedor real para demostrar el MVP. Su selección, modelo y modalidad de salida quedan pendientes de una decisión técnica documentada. Tool Calling y salida estructurada son opciones del adaptador; la cantidad variable de entidades no basta para imponer una de ellas.

No habrá failover automático entre proveedores en v0.1. Se definirán timeout total, presupuesto de llamadas, concurrencia y política acotada de reintentos. Una salida truncada, ilegible o interrumpida producirá un error explícito, sin presentarse como extracción vacía exitosa.

## 9. Identidad, privacidad y operación

La autenticación mínima usa API keys de consumidor configuradas fuera del repositorio, con identidad y scope de extracción derivados en backend. ADR 0001 compara API keys y OAuth2; ADRs 0002/0004 formalizan la implementación de H3. Esta decisión no cierra el modelo comercial ni un esquema multi-tenant.

No se confiará en un tenant declarado en el body ni se creará un repositorio compartido de contenido clínico. El scope y la identidad se comprobarán antes de invocar al proveedor.

Las notas y los catálogos se tratarán como datos no confiables, separados de las instrucciones de procesamiento. La protección combinará instrucciones, ausencia de herramientas con efectos externos, límites de entrada y validación posterior. El corpus incluirá instrucciones maliciosas insertadas en ambos tipos de entrada.

No se persistirán notas, prompts ni resultados clínicos en BD, archivos, trazas, cachés o logs. La configuración de observabilidad y del SDK deberá comprobarlo también en rutas de error. La política de retención del proveedor remoto deberá documentarse por separado; stateless en nuestra API no implica ausencia de retención externa.

Se medirán latencia, estado técnico, modelo, versiones de prompt y validación, tokens y coste estimado cuando estén disponibles. El coste usará una tarifa identificable y sus supuestos; un dato no disponible no se registrará como cero. Identificadores de petición y consumidor no se usarán como etiquetas de métricas de cardinalidad ilimitada.

El arranque y la operación requerirán únicamente la aplicación y su configuración; no habrá indexaciones automáticas, migraciones ni llamadas al modelo al iniciar. Las pruebas ordinarias funcionarán sin red y sin claves reales. El acceso remoto al proveedor será una operación explícita de evaluación o procesamiento.

## 10. Estrategia de verificación y TDD

El builder desarrollará cada incremento mediante ciclos de prueba fallida → implementación mínima → refactorización. El plan posterior describirá objetivos, dependencias y evidencia de salida; el builder decidirá archivos y detalles de implementación en función de las pruebas.

Se requieren pruebas de comportamiento del caso de uso con proveedor simulado, invariantes de evidencia y catálogos, contrato HTTP, autenticación/scopes, errores y ausencia de filtración de contenido en logs. La prueba de contrato debe detectar discrepancias de nombres, tipos y nulabilidad, como `procedureCode` frente a `code` en SFM.

El corpus sintético versionado cubrirá al menos:

- Extracción simple, múltiples entidades y ausencia válida de hallazgos.
- Negaciones, sospechas, antecedentes personales y familiares.
- Medicación actual frente a histórica, suspendida o meramente mencionada.
- Concentración frente a dosis y duración de tratamiento frente a reposo/control.
- Procedimientos realizados, solicitados, planificados y negados.
- Entidades desconocidas, alias, colisiones y ausencia de catálogos.
- Repeticiones, contradicciones y atributos sin evidencia.
- Evidencias inexistentes, posiciones incorrectas y texto Unicode.
- Salidas malformadas, truncadas, timeout y fallos del proveedor.
- Prompt injection en notas y catálogos.

Las pruebas deterministas exigirán cero evidencias inválidas expuestas y cero identificadores ajenos al catálogo resuelto. Estas invariantes no se presentarán como precisión clínica.

La evaluación con modelo real medirá precisión y omisiones por tipo de entidad, atributos sin respaldo, errores de contexto y resolución de catálogos. Se documentarán corpus, modelo, prompt, parámetros, fecha, latencia y consumo. Antes de ejecutarla se fijarán umbrales de aceptación y reglas de anotación; no se elegirán después de observar los resultados. La aprobación clínica o el uso con datos reales quedan fuera de esta evaluación técnica.

## 11. Secuencia estratégica de entrega

| Incremento | Resultado verificable |
|---|---|
| Contrato y decisiones mínimas | ADRs acotados, OpenAPI validable, ejemplos y reglas de anotación sintética coherentes con esta spec. |
| Núcleo determinista | Caso de uso independiente de HTTP y proveedor, probado con fake; evidencia y catálogos controlados. |
| API consumible | Operación HTTP con autenticación, errores y pruebas contractuales; ejecución local sin SFM ni BD. |
| Adaptador real | Extracción con un proveedor y límites operativos; validación de salida y observabilidad verificadas. |
| Evaluación y entrega | Corpus ejecutado, resultados y límites documentados, integración externa por cURL reproducible. |

Cada incremento deberá poder comprobarse antes de avanzar. El fake permite construir y verificar el contrato, pero no constituye por sí solo la entrega final del MVP.

## 12. Criterios de aceptación del MVP

1. Un consumidor externo autenticado procesa una nota sintética mediante el contrato público sin recursos ni infraestructura de SFM.
2. La extracción funciona con y sin catálogos; no necesita almacenamiento clínico.
3. Cada hallazgo devuelto tiene evidencia literal válida y sus atributos relevantes conservan respaldo identificable.
4. Los estados de las menciones evitan presentar negaciones, antecedentes o planes como hechos actuales confirmados o realizados.
5. Los campos no sustentados no se completan por plausibilidad; los IDs y códigos resueltos pertenecen al catálogo declarado.
6. Las salidas incompletas o no conformes y los fallos del proveedor tienen comportamiento explícito y probado.
7. OpenAPI, ejemplos y contrato HTTP coinciden; autenticación y scopes se verifican en backend.
8. Las pruebas deterministas pasan sin red; una evaluación sintética con el proveedor real informa resultados frente a criterios fijados previamente.
9. La observabilidad informa operación y consumo disponible sin registrar contenido clínico ni secretos.
10. La documentación permite ejecutar e integrar la API e identifica revisión humana, límites conocidos y decisiones pendientes.

## 13. Decisiones pendientes y libertad del builder

Contrato exacto, evidencia y autenticación quedaron formalizados antes de implementarse. ADR 0005 formaliza Gemini 3.8 Flash y sus controles para H4. Los ADRs documentan alternativas y consecuencias sin expandir la plataforma. Los umbrales de evaluación están fijados antes de medir calidad con el modelo real.

Español, operación síncrona, clasificación explícita del contexto, coincidencia determinista de catálogo, error ante truncamiento y la ruta quedaron formalizados en H1 antes de implementarse. H5 no modifica esos requisitos ni los umbrales.

El builder podrá decidir estructura de archivos, tipos internos, librerías justificadas, técnicas de pruebas y organización del adaptador. No podrá cambiar silenciosamente el alcance, persistir contenido clínico, ligar la API a SFM, introducir inferencias terapéuticas ni alterar contratos públicos para acomodar al proveedor.

## 14. Riesgos que acompañan la entrega

La evidencia literal no garantiza interpretación correcta; las ambigüedades y asociaciones clínicas requieren evaluación y revisión humana. El catálogo puede estar incompleto o contener alias ambiguos. La calidad, latencia y consumo pueden variar con el modelo y el tamaño de las entradas. Un proveedor remoto añade condiciones de privacidad independientes del despliegue de la API.

La spec y el plan fueron aprobados y H1–H5 ejecutados con la autorización del usuario. La evaluación H5 exige corregir y reevaluar antes de declarar el MVP terminado.
