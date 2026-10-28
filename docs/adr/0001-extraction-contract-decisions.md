# ADR 0001 — Decisiones del contrato de extracción

> Registro histórico del hito indicado: sus cifras y pendientes describen ese momento. Estado actual: H1–H4 verificados, H5 evaluado y MVP no aprobado. Consultar [H5](../h5-validation.md), [cierre de sesión](../sesion-27-10-2026/README.md) e [índice documental](../README.md).

Fecha: 2026-10-27

Estado: decisiones D1–D11 acordadas y formalizadas en [ADR 0002](0002-extraction-wire-contract.md), [OpenAPI v0.1.0](../../openapi/openapi.json) y [corpus/reglas v1.0.0](../../evaluation/README.md). H1 completado en artefactos.

Referencias: [spec](../superpowers/specs/2026-10-27-clinical-extraction-mvp-design.md) y [plan](../superpowers/plans/2026-10-27-clinical-extraction-mvp-plan.md).

## Contexto

H1 debe establecer un contrato que preserve información explícita, permita revisión humana y distinga evidencia, inferencia y resolución de catálogos. Estas decisiones se formalizan antes del código HTTP.

## D1 — Menciones con contexto: acordada

Conservar menciones negadas, históricas o sospechadas y procedimientos solicitados o planificados, claramente diferenciados de hechos afirmados actuales y procedimientos realizados. Representar afirmación, temporalidad y sujeto por separado cuando corresponda. Un medicamento histórico o suspendido no aparece como prescripción activa.

La conservación corresponde al resultado de la petición y al contexto aportado en ella. No introduce memoria conversacional ni almacenamiento servidor de notas o resultados.

Alternativa considerada: devolver solo hechos afirmados actuales y prestaciones realizadas. Simplifica la respuesta, pero pierde contexto y dificulta distinguir exclusión de omisión.

Consecuencia: el contrato y el corpus deberán probar estados y combinaciones sin promover negaciones, antecedentes o planes a hechos actuales.

## D2 — Evidencia por entidad y atributo: acordada

Cada entidad y atributo extraído requiere respaldo identificable en la nota original. Si la entidad no tiene evidencia válida, se descarta con advertencia. Si solo un atributo carece de respaldo, se conserva la entidad sustentada y se devuelve ese atributo como `null`, con advertencia del descarte.

Un dato simplemente no mencionado se representa como `null` sin una advertencia individual de ausencia. Los días de control o reposo no respaldan duración de tratamiento.

Alternativas consideradas: rechazar toda la extracción por un atributo inválido o conservar atributos por plausibilidad. La primera pierde datos sustentados; la segunda incumple la exigencia de evidencia.

Consecuencia: los descartes se distinguen de ausencias legítimas. La verificación literal no constituye validación semántica clínica. ADR 0002 formaliza la convención de posiciones y Unicode.

## D3 — Catálogos conservadores y opcionales: acordada

Preservar el nombre original extraído y resolver únicamente contra nombres o alias explícitos recibidos del integrador. No asumir equivalencias clínicas mediante el modelo.

Distinguir catálogo no suministrado, coincidencia única, sin coincidencia y coincidencia ambigua. Ante ambigüedad, devolver candidatos pertenecientes al catálogo sin seleccionar uno automáticamente.

La normalización admitirá diferencias de mayúsculas y espacios; no eliminará diferencias relevantes de concentración, presentación o vía. La política exacta de normalización se fijará en el contrato y sus pruebas.

Alternativa considerada: equivalencias implícitas o selección probabilística de un candidato. Introduce decisiones no sustentadas por el catálogo y hace menos verificable la resolución.

Consecuencia: pueden quedar entidades sin resolver aunque sean equivalentes para una persona; los alias explícitos permiten al integrador ampliar cobertura de manera controlada. La cantidad de candidatos y entradas deberá respetar los límites definidos en H1.

## D4 — API keys por consumidor: acordada

Cada aplicación consumidora tendrá credenciales propias para integración servidor a servidor o cURL. El backend derivará la identidad y los permisos de la credencial validada, con scope inicial `extractions:write`; ningún identificador de consumidor o tenant enviado en el body tendrá autoridad.

Las credenciales se configurarán fuera de Git, con rotación y revocación mediante configuración. El acceso remoto requerirá HTTPS y controles de consumo por consumidor. La clave no se expondrá en URLs, logs ni clientes públicos de navegador.

Alternativa considerada: OAuth2 con credenciales de aplicación y un proveedor de identidad. Permite tokens de corta duración y gestión centralizada, con infraestructura adicional para este MVP.

Consecuencia: el MVP no requiere un servicio de identidad ni un portal de claves. Deben documentarse los tiempos de aplicación de rotación/revocación, verificar scopes antes de llamar al modelo y probar credenciales inválidas, revocadas y de consumidores diferentes. El formato de clave y cabecera se formalizará en OpenAPI.

Esta decisión se limita al MVP con datos sintéticos; la protección para uso con datos clínicos reales requiere evaluación específica. Referencia consultada: [OWASP REST Security — API Keys](https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html#api-keys), que desaconseja depender exclusivamente de API keys para recursos sensibles.

## D5 — Límites configurables y calibrados por evaluación: acordada

Los límites de tamaño de nota, cardinalidad de catálogos y cuerpo HTTP serán configurables, con valores iniciales deliberadamente muy altos para explorar el comportamiento. Serán protecciones técnicas provisionales, no umbrales de calidad ni una promesa de capacidad soportada.

La propuesta anterior de 20.000 caracteres, 200 entradas y 512 KiB no fue aceptada y no se adopta. Los valores iniciales concretos se documentarán con su configuración y se contrastarán con los recursos del entorno y las restricciones del proveedor; no se dejan restricciones ilimitadas ni se elimina el presupuesto de tiempo, concurrencia o coste.

Los límites de uso recomendado se establecerán a partir de una evaluación sintética por escalones de extensión y complejidad de la nota y tamaño de catálogo. Se medirán omisiones, atributos sin respaldo, errores de contexto, resolución, truncamiento, latencia y consumo. Se conservarán las reglas de anotación y los criterios de calidad fijados antes de medir.

Aceptar un cuerpo HTTP grande no garantiza que el proveedor pueda procesarlo. Sus restricciones de contexto y salida deben comprobarse explícitamente. No recortar notas o catálogos ni omitir contenido silenciosamente para hacerlos caber; devolver un error identificable cuando la operación no pueda procesarse completa. El procesamiento por fragmentos no se introduce implícitamente como solución: requeriría una decisión de diseño y evaluación propia.

Alternativas consideradas: fijar límites bajos como contrato inicial o permitir entradas sin ninguna protección. La primera restringe la exploración sin evidencia de calidad; la segunda deja sin control recursos y costes.

Consecuencia: documentar por separado máximos técnicos configurados y rangos de calidad evaluados. La reducción de valores recomendados deberá justificarse con resultados; cualquier cambio de límites públicos deberá reflejarse en contrato y documentación. Los tamaños explorados no autorizan uso de datos reales.

## D6 — Familias de notas y evaluación equilibrada: acordada

El corpus principal incluirá consulta externa, evolución/seguimiento, interconsulta y notas de procedimientos. Ingreso/anamnesis y examen físico, emergencia y epicrisis/resumen de alta se incluirán desde el inicio como grupos adicionales de complejidad, con resultados separados.

Se variarán extensión, densidad de entidades y formato de escritura, incluyendo narrativa libre, secciones, abreviaturas y SOAP. El tipo documental y el formato son dimensiones diferentes. Las familias definen cobertura de evaluación, no una plantilla obligatoria ni una restricción de entrada basada únicamente en el tipo de nota.

Alternativas consideradas: evaluar solo consulta externa/evolución o exigir cobertura equivalente en todas las familias desde el inicio. Se eligió el enfoque equilibrado para validar el recorrido principal y explorar notas extensas sin ocultar su comportamiento en una media global.

Consecuencia: fijar y publicar métricas por familia, tipo de entidad y niveles de extensión/complejidad; distinguir cobertura evaluada del corpus principal y resultados exploratorios. Los grupos complejos no se excluyen de la medición ni se declaran soportados con calidad demostrada solo porque la API acepte su texto. D7 fija los objetivos de calidad.

Laboratorio, imagenología y anatomía patológica como familias específicas se reservan para una evaluación posterior; la extracción de resultados/observaciones no se añade al alcance de diagnósticos mencionados, prescripciones y procedimientos.

Referencias de clasificación consultadas (no implican conformidad ni dependencia de FHIR o de formularios locales):

- [HL7 US Core — Clinical Notes](https://hl7.org/fhir/us/core/clinical-notes.html).
- [HL7 C-CDA — Discharge Summary](https://hl7.org/cda/us/ccda/StructureDefinition-DischargeSummary.html).
- [MSP — Formularios HCU, anexo de 2021](https://enlace.17d07.mspz9.gob.ec/biblioteca/estad/AC-00115-2021%20ENE%2010.pdf).
- [Aultman — Documentación SOAP](https://aultman.org/education/aultman-family-medicine-clerkship/resources/write-a-soap-note/).
- [HL7 FHIR — DiagnosticReport](https://hl7.org/fhir/diagnosticreport.html).

## D7 — Criterios de calidad de la evaluación: acordada

Se fijan antes de medir los siguientes objetivos técnicos sobre el corpus sintético:

| Criterio | Objetivo |
|---|---|
| Precisión de entidades devueltas | Al menos 95 %. |
| Cobertura de entidades esperadas | Al menos 90 %. |
| Referencias de evidencia o identificadores de catálogo inválidos | Cero expuestos en resultados aceptados. |
| Errores críticos de contexto | Cero en los casos evaluados: negaciones promovidas a afirmaciones, planes promovidos a prestaciones realizadas o antecedentes familiares atribuidos al paciente. |
| Atributos inventados | Cero dosis, frecuencias o duraciones sin respaldo en los casos evaluados. |

Precisión y cobertura se aplicarán por tipo de entidad en el corpus principal; se publicará también el desglose por familia. Ingreso, emergencia y epicrisis tendrán resultados exploratorios separados. Sus fallos serán visibles y delimitarán las afirmaciones de soporte; aceptar entradas de estas familias no demuestra calidad equivalente.

Las reglas de anotación, correspondencia entre resultado y referencia, denominadores, tratamiento de casos sin entidades y composición suficiente de positivos/negativos se formalizarán antes de la primera medición. Estos criterios no son resultados alcanzados ni porcentajes de precisión clínica demostrada.

Alternativas consideradas: priorizar cobertura aun tolerando invenciones o usar solo una media global. Se prioriza evitar información sin respaldo y errores de contexto, manteniendo un objetivo explícito de cobertura.

Consecuencia: los resultados reales se contrastarán contra estos objetivos sin reducirlos después de medir para declarar éxito. Las invariantes deterministas y la evaluación semántica del modelo se reportarán por separado; los rangos recomendados de tamaño se calibrarán conservando estos criterios.

## D8 — Operación pública y entrada básica: acordada

La operación será `POST /api/v1/extractions`, síncrona, con una nota completa por petición.

Entrada acordada:

- `clinicalNote`: texto original obligatorio.
- `locale`: opcional, con español (`es`) por defecto.
- `catalogs`: opcionales, conforme a D3.

La respuesta contendrá las tres familias de entidades, evidencia y contexto, resolución de catálogos, advertencias y versiones de procesamiento. Los nombres y esquemas exactos de esos campos de respuesta se formalizarán en OpenAPI.

No se exigirá tipo documental ni IDs de paciente, cita o expediente. El identificador de petición sirve para seguimiento técnico y no representa una extracción almacenada recuperable mediante otra operación.

Alternativas consideradas: exigir metadatos propios de un EHR o procesamiento asíncrono con consulta posterior. Añaden acoplamiento o persistencia que no necesita el primer incremento.

Consecuencia: OpenAPI y los ejemplos reflejan el valor por defecto de localidad, la independencia de entidades externas y la respuesta síncrona. D11 y ADR 0002 formalizan las localidades explícitas.

## D9 — Errores con Problem Details: acordada

Los errores HTTP usarán Problem Details conforme a [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html), con media type `application/problem+json`. El contrato fijará tipos de problema, estado HTTP, descripción segura e identificador de petición, junto con códigos propios estables para tratamiento programático.

Se distinguirán entrada inválida, límites excedidos, credenciales inválidas, scope insuficiente, saturación, indisponibilidad del proveedor, timeout y respuesta no conforme. Los estados HTTP y las extensiones exactas se formalizarán en OpenAPI; el estado del cuerpo deberá coincidir con el HTTP real.

Los mensajes no incluirán notas, prompts, salidas crudas, credenciales ni detalles internos del proveedor. Los errores de validación identificarán campos sin repetir sus valores sensibles.

Una extracción terminada y estructuralmente válida con entidades o atributos descartados devuelve éxito con advertencias. Las advertencias tendrán códigos y referencias estructuradas al elemento afectado; no usan el formato de error como sustituto de la respuesta exitosa. El truncamiento o interrupción devuelve error explícito y nunca éxito vacío.

Alternativa considerada: formato de errores enteramente propio o tratar todos los descartes como fallos de la operación. Se adopta el estándar para facilitar integración y se preserva la separación entre descartes locales y fallos globales de procesamiento.

Consecuencia: seguridad, validación y adaptador deberán converger al mismo contrato de errores públicos. Las pruebas verificarán formato, códigos, ausencia de filtración y distinción entre advertencias y fallos.

## D10 — Desarrollo y evaluación reservada: acordada

El corpus sintético se separará en dos conjuntos versionados:

- Desarrollo/regresión: utilizable para TDD, ajustes del prompt, validadores y comportamiento.
- Evaluación reservada: casos distintos, fijados antes de medir y no utilizados para ajustar el extractor; verifica los criterios de D7.

Ambos cubrirán las familias de D6 y casos adversariales de negación, contradicciones, atributos ausentes y catálogos ambiguos. No se repartirán variantes de una misma nota o escenario base entre los conjuntos. La separación se hará por escenario de origen, no solo por texto o identificador de archivo.

Las pruebas de escalado de longitud, densidad y catálogos tendrán resultados separados para calibrar límites conforme a D5. Una prueba de escalado utilizada para ajustar el sistema no se contabilizará como evaluación reservada independiente.

Si un fallo reservado se usa para mejorar el extractor, el caso pasará a regresión y deberá reemplazarse para una futura evaluación independiente. Se conservarán el resultado original y las versiones del corpus y del extractor, sin reescribir retrospectivamente la medición.

Alternativa considerada: ajustar y medir siempre sobre los mismos casos. Facilita iterar, pero no aporta una comprobación independiente de los ajustes.

Consecuencia: documentar separación, origen sintético, reglas de anotación, versiones y uso de cada caso. Los resultados reservados se reportarán junto con su tamaño y composición; no se presentarán como evidencia clínica generalizable.

## D11 — Español y variantes regionales: acordada

El alcance lingüístico inicial responde al mercado hispanohablante. Si `locale` se omite, se usa `es`. Se aceptarán variantes regionales válidas de español, como `es-EC`, `es-CO` y `es-MX`, preservando el valor recibido.

Una localidad explícita de otro idioma se rechazará mientras no esté soportada. `locale` es contexto lingüístico declarado por el consumidor; no demuestra el idioma de todo el contenido ni activa una garantía de detección automática de idioma.

Aceptar una variante regional no implica haber demostrado calidad individual para ella. El corpus y los reportes identificarán las variantes y usos lingüísticos efectivamente evaluados. La API no aplicará reglas clínicas o catálogos nacionales implícitos por recibir una región.

Alternativas consideradas: aceptar solo `es` o aceptar cualquier idioma sin evaluación diferenciada. La primera restringe metadatos útiles del mercado actual; la segunda confunde entrada aceptada con soporte demostrado.

Consecuencia: OpenAPI, validación y ejemplos documentarán valor por defecto, variantes admitidas y rechazo de idiomas no soportados. Los tags malformados deberán diferenciarse de localidades bien formadas no soportadas.

## Cierre histórico de la ronda y formalización

Las decisiones de producto D1–D11 fueron acordadas. Los pasos 1–3 autorizados de H1 tienen artefactos: ADR 0002 cierra esquemas y estados de respuesta, evidencia y Unicode, defaults configurables de límites, localidad y códigos HTTP; OpenAPI y ejemplos representan el contrato y tienen validación reproducible.

La composición y reglas de anotación/conteo del corpus se formalizaron en `evaluation/`, con [protocolo](../../evaluation/PROTOCOL.md) y [verificación](../../evaluation/VALIDATION.md). Si aparece una contradicción o un cambio de alcance en la implementación, se expondrá para decisión en lugar de modificar silenciosamente lo aprobado.

El contrato, ejemplos, corpus y reglas tienen comprobación conjunta para cerrar H1 en artefactos. No hay implementación de la API ni resultados de evaluación clínica por estos artefactos.
