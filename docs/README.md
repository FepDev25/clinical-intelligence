# Documentación de Clinical Intelligence

Estado al cierre del 28-10-2026 (America/Guayaquil): **prototipo de Clinical Extraction implementado y evaluado; MVP no aprobado por calidad**. La nueva campaña completó 234 operaciones sintéticas; 57 intentos siguen pendientes de adjudicación. Clinical Coding, History Intelligence y Charge Reconciliation no están implementadas.

Para retomar, leer primero el [resumen de la sesión del 28-10](sesion-28-10-2026/README.md), [AGENTS.md](../AGENTS.md) y el [análisis de la evaluación](sesion-28-10-2026-evaluacion.md). La [sesión del 27-10](sesion-27-10-2026/README.md) y el [informe H5](h5-validation.md) conservan la línea base. Para consumir la API, usar la [guía H5](h5-integration.md), considerando que describe la configuración histórica.

El [plan integral de revisión, corrección y evaluación](superpowers/plans/2026-10-28-extraction-quality-review-and-remediation.md) reúne los hallazgos posteriores y propone diez tareas con trazabilidad, pruebas y criterios de aceptación. Fue aprobado por el usuario; [T1 está completada y verificada](../evaluation/quality-review/README.md). [T2 tiene expediente de revisión preparado](../evaluation/quality-review/t2/README.md), con 18 cambios de referencia propuestos y adjudicación formal abierta. T3–T10 siguen pendientes y no se ha autorizado otra campaña remota.

Actualización posterior: las correcciones de desarrollo del evaluador y la revisión de las seis decisiones H5 están en [evaluación 1.0.1](../evaluation/next/README.md). La evaluación histórica y su decisión negativa no cambian; la próxima campaña requiere integrar estos activos y sustituir los dos escenarios reservados examinados.

La [nueva medición de extracción v2](evaluacion-28-10-2026-v2.md) completó 234 operaciones sintéticas y no aprueba automáticamente el MVP; la auditoría semántica y la revisión de dos nuevas referencias siguen pendientes.

La [revisión de resultados del 28-10](sesion-28-10-2026-evaluacion.md) documenta el contraste con H5, fallos reproducidos en código, límites del modelo y próximos pasos. Se examinaron los 234 intentos; 57 siguen pendientes de adjudicación, por lo que la auditoría formal no está cerrada.

## Diseño, contratos y decisiones

| Documento | Función y estado |
| --- | --- |
| [Spec aprobada](superpowers/specs/2026-10-27-clinical-extraction-mvp-design.md) | Requisitos del MVP; sus criterios todavía no se cumplen completamente. |
| [Plan estratégico](superpowers/plans/2026-10-27-clinical-extraction-mvp-plan.md) | Hitos, dependencias y evidencias; las correcciones/reevaluación siguen pendientes. |
| [OpenAPI y ejemplos](../openapi/README.md) | Contrato 0.1.0, evidencia Unicode, estados, errores y escenarios públicos. |
| [ADR 0001](adr/0001-extraction-contract-decisions.md) | Decisiones D1–D11: alcance, contexto, catálogos, identidad, límites y evaluación. |
| [ADR 0002](adr/0002-extraction-wire-contract.md) | Esquemas y reglas normativas del transporte. |
| [ADR 0003](adr/0003-extraction-core-h2.md) | Núcleo independiente, puerto del proveedor, fake y validación determinista. |
| [ADR 0004](adr/0004-extraction-http-h3.md) | HTTP, API keys/scopes, configuración y validación de localidad. |
| [ADR 0005](adr/0005-gemini-provider-h4.md) | Gemini por REST, presupuestos y condiciones externas documentadas en la sesión. |
| [ADR 0006](adr/0006-frozen-evaluation-h5.md) | Evaluación congelada, consumo acotado y decisión agregada posterior. |
| [ADR 0007 propuesto](adr/0007-annotation-scope-and-temporal-context.md) | Alcance de seguimiento y contexto temporal; no adoptado ni implementado. |

Stack implementado: Java 21, Spring Boot 3.5.16 y cliente HTTP del JDK para Gemini. Spring AI fue considerado y no se incorporó. El dominio/caso de uso dependen de un puerto interno; la API no necesita base de datos ni SFM.

## Guías operativas

| Documento | Uso |
| --- | --- |
| [Integración H5](h5-integration.md) | Entrada recomendada: configuración, cURL, errores, revisión humana y límites. |
| [Integración H3](h3-integration.md) | Aprovisionamiento/rotación de credenciales y consumo con fake explícito. |
| [Integración H4](h4-integration.md) | Clave de Google, arranque real, presupuestos, telemetría y condiciones de datos. |
| [Corpus y evaluación](../evaluation/README.md) | Referencias sintéticas, composición, validación y separación del reservado. |
| [Ejecutar la evaluación](../evaluation/RUNNING.md) | Congelación, reanudación, presupuesto, auditoría y decisión final de entrega. |
| [Regresiones H5](../evaluation/regressions/README.md) | Fallos de desarrollo observados; no se presentan como corregidos. |
| [Registro BCP 47](../src/main/resources/bcp47/SOURCE.md) | Procedencia del snapshot IANA incluido; sin consulta remota por petición. |

## Evidencia e historia

| Hito | Informe | Ledger |
| --- | --- | --- |
| H1: contrato | [Verificación OpenAPI](../openapi/VALIDATION.md) | Decisiones en ADR 0001/0002. |
| H1: corpus | [Verificación del corpus](../evaluation/VALIDATION.md) | Reglas de anotación/protocolo. |
| H2: núcleo | [Validación](h2-validation.md) | [Registro](h2-progress.md). |
| H3: HTTP | [Validación](h3-validation.md) | [Registro](h3-progress.md). |
| H4: proveedor | [Validación](h4-validation.md) | [Registro](h4-progress.md). |
| H5: evaluación | [Resultados y límites](h5-validation.md) | [Registro](h5-progress.md). |
| Cierre de sesión | [Estado y próximos pasos](sesion-27-10-2026/README.md) | [Verificación de cierre](sesion-27-10-2026/verificacion.md). |

Los informes H1–H4 y ledgers describen el momento de cada hito. Sus cifras de pruebas no sustituyen el total actual: 134 Java y 71 Python. Las antiguas decisiones de no crear commits correspondían a un repositorio sin commit inicial; el usuario autorizó commits locales al cerrar la sesión.

## Artefactos congelados: cómo interpretar sus estados históricos

La [ejecución H5 v1](../evaluation/runs/2026-10-28-gemini38-v1/) utiliza fecha UTC 28-10; se ejecutó el 27-10 en Guayaquil. Se conservan originales [protocolo](../evaluation/PROTOCOL.md), [anotación](../evaluation/ANNOTATION.md), corpus, fuente/JAR y sus hashes. La frase del protocolo «no hay resultados» describe su congelación anterior a medir; los resultados posteriores están en H5. Del mismo modo, la ausencia de commit que registra `freeze.json` fue real al congelarlo y no se reescribe ahora.

Los [resultados H5](h5-validation.md), [guía H5](h5-integration.md), [RUNNING](../evaluation/RUNNING.md) y [erratas](../evaluation/reports/2026-10-28-gemini38-v1-errata.md) también están vinculados en los checksums de entrega. Se preservan sus bytes; las actualizaciones de continuidad se documentan en este índice y la sesión. No actualizar solo hashes para ocultar cambios.

La autoridad de la decisión agregada es [release-decision.json](../evaluation/runs/2026-10-28-gemini38-v1/release-decision.json), revisión 1.0.1: resultado negativo. `summary.pass` del runner congelado tiene alcance reservado; el control separado exige además auditoría y cero tolerancias en ambos splits. Su fuente/pruebas están archivadas aparte. Ni los informes ni los resultados sintéticos constituyen revisión clínica independiente.
