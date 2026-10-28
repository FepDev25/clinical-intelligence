# H5 — Evaluación sintética y estado del MVP

Resultado: **prototipo evaluado; MVP no aprobado**. Se completaron 234 operaciones reales autenticadas con Gemini, sin modificar prompt, código de extracción, corpus o umbrales entre desarrollo y reservado. Las tres rondas reservadas incumplen los criterios acordados; no se declara calidad clínica ni entrega productiva.

La integración funciona por HTTP y las pruebas deterministas pasan. La calidad semántica todavía necesita trabajo. El resultado no se sustituye por la mejor ronda ni por un promedio.

## Ejecución y condiciones

Intervalo UTC: `2026-10-28T04:00:01.873564+00:00` a `2026-10-28T04:31:43.838014+00:00`. Son 78 notas sintéticas distintas, 39 por split, tres rondas de cada una; 24 principales, 9 exploratorias y 6 adversariales por ronda. Cada principal tiene 30 diagnósticos, 24 medicamentos y 27 procedimientos de referencia.

Configuración: `gemini-3.8-flash`, temperatura 1, thinking LOW, máximo 65.536 tokens de salida, deadline 120 s, concurrencia de evaluación 1 y cero reintentos. Cada operación admite hasta dos llamadas a Google: conteo y generación. Las referencias y los catálogos no se envían al modelo. Se evaluó la respuesta final de la API, después de sus descartes y resolución local de catálogos.

Solo Java importó la clave desde el archivo local en ejecución; el agente y el arnés no lo abrieron. Se generaron credenciales temporales de consumidor, el servidor escuchó en loopback y los procesos/credenciales temporales se retiraron al salir. No se usaron pacientes reales, SFM, PreAudit Health, base de datos, contenedores o despliegue público.

El modelo efectivo comunicado fue `gemini-3.8-flash` en 234/234 observaciones: Google devolvió el alias; no se dispone de un identificador inmutable más granular. El repositorio no tiene commit inicial, por lo que se conservaron hashes enumerados y snapshot de fuente/JAR. Condiciones completas: [freeze.json](../evaluation/runs/2026-10-28-gemini38-v1/freeze.json), [ADR 0006](adr/0006-frozen-evaluation-h5.md) y [protocolo 1.0.0](../evaluation/PROTOCOL.md).

## Entidades principales: conteo automático original

TP/FP/FN se calculan contra las referencias fijas, por nombre/anclaje/contexto y sin sinónimos. Contexto incorrecto cuenta FP+FN; duplicados añaden FP. Estos son conteos de extracción bajo esas reglas, no precisión clínica. Umbrales por tipo y por ronda reservada: precisión ≥95% y cobertura ≥90%, decididos con enteros sin redondear. Denominador cero se informa N/A.

| Split/ronda | Tipo | TP/FP/FN | Precisión | Cobertura |
| --- | --- | --- | --- | --- |
| development/1 | diagnoses | 29/5/1 | 85.29% | 96.67% |
| development/1 | medications | 21/4/3 | 84.00% | 87.50% |
| development/1 | procedures | 25/3/2 | 89.29% | 92.59% |
| development/2 | diagnoses | 29/4/1 | 87.88% | 96.67% |
| development/2 | medications | 20/5/4 | 80.00% | 83.33% |
| development/2 | procedures | 22/6/5 | 78.57% | 81.48% |
| development/3 | diagnoses | 29/4/1 | 87.88% | 96.67% |
| development/3 | medications | 23/2/1 | 92.00% | 95.83% |
| development/3 | procedures | 23/5/4 | 82.14% | 85.19% |
| reserved/1 | diagnoses | 25/8/5 | 75.76% | 83.33% |
| reserved/1 | medications | 20/3/4 | 86.96% | 83.33% |
| reserved/1 | procedures | 18/10/9 | 64.29% | 66.67% |
| reserved/2 | diagnoses | 28/5/2 | 84.85% | 93.33% |
| reserved/2 | medications | 20/3/4 | 86.96% | 83.33% |
| reserved/2 | procedures | 18/10/9 | 64.29% | 66.67% |
| reserved/3 | diagnoses | 28/5/2 | 84.85% | 93.33% |
| reserved/3 | medications | 21/2/3 | 91.30% | 87.50% |
| reserved/3 | procedures | 18/11/9 | 62.07% | 66.67% |

## Comprobaciones en todas las cohortes fijas

Las cifras siguientes son las originales previas a adjudicación. Un error crítico o un atributo sin respaldo bloquea aprobación aunque los porcentajes principales fueran suficientes. `Contexto crítico` cuenta dimensiones; `texto externo` cuenta entidades contaminadas por instrucciones, no ejecución de comandos.

| Split/ronda | Fallos operación | Invariantes evidencia/IDs | Contexto crítico | Atributos sin respaldo | Texto externo | Marcas de adjudicación |
| --- | --- | --- | --- | --- | --- | --- |
| development/1 | 0 | 0 | 2 | 3 | 2 | 23 |
| development/2 | 0 | 0 | 7 | 3 | 0 | 28 |
| development/3 | 0 | 0 | 5 | 2 | 0 | 23 |
| reserved/1 | 0 | 0 | 8 | 2 | 0 | 23 |
| reserved/2 | 0 | 0 | 5 | 1 | 0 | 25 |
| reserved/3 | 0 | 0 | 5 | 1 | 0 | 25 |

Cero evidencias/IDs inválidos expuestos no implica semántica correcta: una cita real puede atribuirse mal. El filtro descartó propuestas inválidas; las omisiones resultantes permanecen en los denominadores. No se observaron fallos operativos en estas notas, pero esto no mide disponibilidad productiva.

## Atributos, catálogos y advertencias

Conteos TP/FP/FN originales, todas las cohortes. Ausencia null/null se registra aparte en JSON y no suma TP. Variantes textuales legítimas conservan FP/FN léxico; las resoluciones no fabrican equivalencias de dosis ni unidades.

| Split/ronda | Código textual | Dosis | Frecuencia | Vía | Duración |
| --- | --- | --- | --- | --- | --- |
| development/1 | 2/0/0 | 9/2/1 | 5/0/0 | 1/4/3 | 4/3/3 |
| development/2 | 2/0/0 | 9/2/1 | 5/1/0 | 1/3/3 | 1/6/6 |
| development/3 | 2/0/0 | 9/2/1 | 5/0/0 | 1/3/3 | 1/6/6 |
| reserved/1 | 2/0/0 | 7/1/1 | 3/0/1 | 0/4/3 | 2/3/4 |
| reserved/2 | 2/0/0 | 8/1/0 | 4/0/0 | 0/3/3 | 4/2/2 |
| reserved/3 | 2/0/0 | 7/1/1 | 3/0/1 | 0/3/3 | 2/4/4 |

Catálogos: aciertos/evaluados por estado y ternas completas; `sin` indica entidades alineadas sin catálogo suministrado y no infla la exactitud. Las entidades no alineadas se reflejan en FP/FN, no se inventa una resolución para ellas.

| Split/ronda | Diagnósticos | Medicamentos | Procedimientos |
| --- | --- | --- | --- |
| development/1 | 1/1; sin 55 | 7/7; sin 32 | 0/0; sin 44 |
| development/2 | 1/1; sin 55 | 7/7; sin 32 | 0/0; sin 44 |
| development/3 | 1/1; sin 55 | 7/7; sin 32 | 0/0; sin 44 |
| reserved/1 | 1/1; sin 54 | 5/5; sin 32 | 0/0; sin 44 |
| reserved/2 | 1/1; sin 54 | 5/5; sin 32 | 0/0; sin 43 |
| reserved/3 | 1/1; sin 54 | 5/5; sin 32 | 0/0; sin 44 |

Advertencias y vacíos: `diferencias` es coincidencia exacta de grupos requeridos, no siempre falta semántica; ver la limitación de agrupación documentada abajo. Los descartes adicionales válidos se admiten, pero no compensan omisiones.

| Split/ronda | Diferencias requeridas | Adicionales | Entidades descartadas | Atributos descartados | Vacío limpio | Vacío con avisos |
| --- | --- | --- | --- | --- | --- | --- |
| development/1 | 3 | 27 | 0 | 25 | 0 | 0 |
| development/2 | 3 | 24 | 0 | 23 | 0 | 0 |
| development/3 | 3 | 17 | 0 | 16 | 0 | 0 |
| reserved/1 | 5 | 24 | 0 | 23 | 1 | 0 |
| reserved/2 | 4 | 23 | 0 | 22 | 1 | 0 |
| reserved/3 | 4 | 23 | 0 | 22 | 1 | 0 |

## Revisión textual y disputas

Se examinó cada nota y respuesta, o su diferencia respecto de una respuesta ya examinada con el mismo caso. Revisor: agente constructor, consistencia textual sintética; **sin revisión clínica independiente ni cegamiento**. La auditoría enlaza hashes de freeze, respuestas y cada registro. Reconoce variantes respaldadas, conserva duplicados y no reasigna menciones ambiguas por un contexto favorable.

Decisiones: 119 con respaldo verificado, 109 con hallazgos/respaldo rechazados y 6 pendientes. `support_failed` identifica hallazgos o respaldo rechazados; no equivale a contar pacientes, errores clínicos ni dimensiones críticas. `pending` conserva una decisión abierta: seis intentos correspondientes a tres temas (interconsulta como documento/prestación, impresión diagnóstica y suficiencia de cita elíptica). Las citas cortas desplazadas a otra cláusula se rechazaron de forma conservadora por suficiencia de la justificación, sin afirmar por ello que el valor corresponda a otra persona.

Los rechazos de entidades/contextos retiran crédito en el análisis auditado; las variantes de atributos conservan sus conteos textuales. Marcas automáticas que corresponden a respaldo rechazado siguen bloqueantes, aunque ya tengan nota de revisión. Las disputas pendientes impiden aprobación. Ver [auditoría](../evaluation/runs/2026-10-28-gemini38-v1/semantic-audit.json) y [conteos auditados completos](../evaluation/runs/2026-10-28-gemini38-v1/summary.json).

| Split/ronda | Diagnósticos TP/FP/FN auditado | Medicamentos TP/FP/FN auditado | Procedimientos TP/FP/FN auditado | Todas con decisión | Aprueba |
| --- | --- | --- | --- | --- | --- |
| development/1 | 29/5/1 | 20/5/4 | 25/3/2 | Sí | No |
| development/2 | 29/4/1 | 19/6/5 | 21/7/6 | No | No |
| development/3 | 29/4/1 | 20/5/4 | 23/5/4 | No | No |
| reserved/1 | 25/8/5 | 17/6/7 | 17/11/10 | No | No |
| reserved/2 | 27/6/3 | 15/8/9 | 18/10/9 | No | No |
| reserved/3 | 27/6/3 | 17/6/7 | 17/12/10 | No | No |

La [nota metodológica versionada](../evaluation/reports/2026-10-28-gemini38-v1-errata.md) conserva dos problemas identificados durante desarrollo: el comparador exacto de grupos de advertencias puede marcar faltantes ya cubiertos por una advertencia más amplia; el anclaje gold de Brisa puede omitir la aparición real de la prescripción. También quedan discutidas algunas formulaciones como impresión diagnóstica, elipsis y tipo documental frente a prestación. No se modificó el gold después de medir ni se presentan esas diferencias como errores clínicos confirmados. Los fallos críticos claros y el incumplimiento del umbral permanecen aun sin usar la auditoría suplementaria para agravarlos.

La agregación `summary.pass` del arnés congelado solo comprueba reservado. Después de medir se detectó que faltaba exigir revisión y cero tolerancias en desarrollo al decidir la entrega global. Se añadió un control separado de decisión **1.0.1**, con cinco pruebas; el fallo inicial fue la ausencia del control y las cinco pasan tras implementarlo, que aplica esas condiciones originales a ambos splits. No cambia fuente congelada, anclajes, gold, TP/FP/FN ni umbrales; no necesita nueva inferencia. Código/hash y [decisión final negativa](../evaluation/runs/2026-10-28-gemini38-v1/release-decision.json) quedan preservados. El control recompone el análisis validado y no acepta un resumen editado. Las cifras originales y la decisión reservada también eran negativas.

## Hallazgos que orientan la siguiente iteración

- Concentración del envase presentada como dosis de una medicación adicional, pese a existir una pauta explícita distinta.
- Síntomas/motivos de consulta convertidos en diagnósticos y repeticiones equivalentes devueltas como entidades duplicadas.
- Fechas de la orden trasladadas al tiempo de la prestación, estados desconocidos promovidos y negaciones mal clasificadas. En algunos casos `assertion=affirmed` contradice la negación aunque `performanceStatus=not_performed` se conserve; no se afirma que el modelo declarara realización.
- Entidades de un bloque explícitamente externo aparecieron en una ronda de desarrollo; sus éxitos posteriores no borran ese fallo.
- Citas repetidas de paciente/hoy/actual producen descartes o respaldos desplazados; una hipótesis para desarrollo es preferir cláusulas inequívocas y revisar selección de ocurrencias, sin darla por corregida.

El [índice de regresiones de desarrollo](../evaluation/regressions/h5-development-v1.json) referencia casos/rondas originales; no incorpora notas reservadas al TDD ni presenta fallos como corregidos. Ningún porcentaje se usa para autorizar una decisión clínica autónoma.

## Latencia, consumo y tamaños

Las 234 operaciones devolvieron HTTP200; estados del proveedor: COMPLETE en 234/234. Latencia del cliente local, incluyendo conteo, generación, validación y HTTP: mediana 7.824 s, p95 12.470 s, máximo 18.651 s. p95 usa rango más cercano; no es una prueba de carga o SLO.

Cota de consumo estimada: **USD 1.01496000**, dentro del tope administrativo USD10. Se trataron todos los tokens de entrada como no cacheados y el resto del total como salida. No es factura, confirmación del tier de cuenta ni coste exacto. Tokens: entrada conocida 133095, candidatos de salida conocidos 244037, total conocido 377132. Pensamientos/caché ausentes permanecen desconocidos, no cero; la estimación exacta de producción puede seguir null.

Rango de notas ejecutadas: 92–424 puntos de código; cuerpos HTTP 151–493 bytes UTF-8; 0–3 entradas de catálogo por operación. No se recortaron notas, catálogos o respuestas. No hay un rango de tamaño con calidad aprobada para recomendar todavía.

Las diez recetas de escala se materializaron y validaron localmente, hasta un millón de puntos de código/100.000 entradas; **no se enviaron al modelo**. Esta comprobación no demuestra calidad, latencia o capacidad remota a esos tamaños. [Metadatos por ronda y uso faltante](../evaluation/runs/2026-10-28-gemini38-v1/runtime-metadata.json) y [script descriptivo reproducible](../evaluation/reports/summarize.py).

## Verificación y revisión de código

Antes de congelar se ejecutaron `mvn -B -ntp -o verify`: **134 pruebas Java, cero fallos**; suite Python: **66 pruebas, cero fallos**. Después se verificaron las cinco pruebas adicionales de decisión agregada: **71 Python en total**, todas correctas. OpenAPI: 21 esquemas, 29 escenarios, 58 archivos de ejemplo y 30 mutaciones inválidas rechazadas. Validación local de desarrollo/escala y auditoría final de ambos splits/escala ejecutadas sin inferencia adicional. Fuente de procesamiento y análisis conservada por hashes; el contrato público no cambió.

La revisión independiente del arnés encontró cinco problemas importantes, corregidos con regresiones antes de medir: requisito de revisión semántica, promociones unknown, anclajes de instrucciones, integridad de corpus/solicitudes y concurrencia del directorio. Se reforzó además el bind explícito a loopback. No hubo segunda revisión de código. La robustez ante `processing: null` de un backend ajeno queda como menor pendiente; el JAR fijo de esta ejecución emitió metadatos válidos. El [ledger](h5-progress.md) conserva decisiones y verificaciones.

## Evidencia de aceptación y entrega

| Criterios de la spec | Estado/evidencia |
| --- | --- |
| 1–2: consumidor externo, independencia, catálogos y stateless | Demostrado por API autenticada desde proceso Python separado y pruebas H2/H3; sin SFM/BD. |
| 3–5: evidencia, contexto, atributos e IDs | Parte literal/catálogos verificada; respaldo semántico y calidad no aprobados. Bloquea MVP. |
| 6–7: fallos explícitos, contrato y autorización | Pruebas H2–H4 y OpenAPI conformes; sin fallos remotos en estas notas. |
| 8–9: pruebas, evaluación y observabilidad | 134 Java/71 Python; 234 operaciones reales y metadatos seguros/versionados. Campos de uso ausentes explícitos. |
| 10: ejecución, revisión humana y límites | Guías de integración H3/H4/H5 y este informe; sin autorización clínica o regulatoria implícita. |

Configuración, cURL, errores y revisión: [guía de integración H5](h5-integration.md). Reproducción del arnés: [RUNNING.md](../evaluation/RUNNING.md). Los informes originales `summary-automatic.json`/`scores-automatic.json`, el análisis auditado, las respuestas sintéticas y el snapshot se conservan en [la ejecución](../evaluation/runs/2026-10-28-gemini38-v1/). El JSON publica además desglose por cohorte, familia, localidad y tamaño con sus denominadores, sin promediar rondas.

El siguiente paso mínimo es corregir las incidencias de referencia/comparación con nueva versión, trabajar con las regresiones de desarrollo en exclusión no clínica, asociación de evidencia y contexto, y medir de nuevo. Si resultados reservados orientan los ajustes, trasladarlos a regresión y reemplazarlos por escenarios independientes antes de otra evaluación. No avanzar a otra capacidad como si este MVP hubiera aprobado. Las condiciones de datos y tier de Google siguen pendientes para cualquier futuro uso real; este trabajo utiliza solo sintéticos.
