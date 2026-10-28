# Cierre de sesión — 27 de octubre de 2026

**Estado para retomar: prototipo de Clinical Extraction implementado y evaluado; MVP no aprobado.** Esta sesión termina por tamaño de contexto, a petición del usuario. El trabajo siguiente es corregir y reevaluar Extraction, no empezar otra capacidad.

Fecha local: America/Guayaquil. La evaluación está archivada como `2026-10-28-gemini38-v1` porque utiliza UTC; no corresponde a una segunda sesión.

## Qué se hizo hoy

Se definió un producto independiente de SFM/PreAudit Health, API-first y stateless por defecto. Se aprobaron una [spec](../superpowers/specs/2026-10-27-clinical-extraction-mvp-design.md), un [plan estratégico por hitos](../superpowers/plans/2026-10-27-clinical-extraction-mvp-plan.md), contratos y criterios antes de implementar mediante TDD. El plan conserva decisiones de arquitectura y resultados esperados, sin convertirse en una transcripción anticipada de archivos/código.

| Hito | Resultado y evidencia |
| --- | --- |
| H1 | [OpenAPI 0.1.0 y ejemplos](../../openapi/README.md): 21 esquemas, 29 escenarios, 58 archivos. [Corpus](../../evaluation/README.md): 78 notas sintéticas, 277 entidades y 10 recetas de escala. [Decisiones](../adr/0001-extraction-contract-decisions.md), [contrato técnico](../adr/0002-extraction-wire-contract.md), [verificación contractual](../../openapi/VALIDATION.md) y [verificación del corpus](../../evaluation/VALIDATION.md). |
| H2 | Núcleo Java 21: puerto del proveedor, fake explícito, evidencia Unicode, estados, descartes y catálogos por petición. [ADR](../adr/0003-extraction-core-h2.md), [informe](../h2-validation.md), [ledger](../h2-progress.md). |
| H3 | Spring Boot, endpoint HTTP, API keys por consumidor, scopes, validación estricta, errores, límites y ejemplo externo. [ADR](../adr/0004-extraction-http-h3.md), [guía](../h3-integration.md), [informe](../h3-validation.md), [ledger](../h3-progress.md). |
| H4 | Gemini Developer API con REST/cliente HTTP del JDK, conteo previo, plazo total, concurrencia acotada, sin reintentos/fallback y observabilidad segura. Se verificó una extracción sintética mínima. [ADR](../adr/0005-gemini-provider-h4.md), [guía](../h4-integration.md), [informe](../h4-validation.md), [ledger](../h4-progress.md). |
| H5 | Puntuación, congelación/reanudación, presupuesto, cliente HTTP externo, seis rondas reales, revisión textual y control agregado de entrega. [ADR](../adr/0006-frozen-evaluation-h5.md), [informe](../h5-validation.md), [guía de integración](../h5-integration.md), [reproducción](../../evaluation/RUNNING.md), [ledger](../h5-progress.md). |

El cierre actualiza documentación y crea los primeros commits locales por bloques grandes, por instrucción expresa del usuario. No se realiza push, publicación o despliegue.

## Decisiones vigentes: no volver a abrir por falta de contexto

- Primera capacidad: Extraction; Coding, History Intelligence y Charge Reconciliation no están implementadas. Producto independiente, revisión humana y ninguna persistencia clínica automática.
- Un monolito modular, Java 21/Spring Boot 3.5.16, sin base de datos obligatoria. Spring AI se consideró; H4 eligió REST directo para controlar presupuestos/cancelación. El caso de uso depende de un puerto interno y admite dobles deterministas.
- Contrato público: `POST /api/v1/extractions`, `clinicalNote`, localidad española BCP 47 y catálogos opcionales. Estados y evidencia por campo; índices en puntos de código Unicode `[start,end)` sobre el original. Los catálogos se resuelven localmente y no se envían a Google.
- API keys por consumidor, scope `extractions:write`, identidad derivada del servidor; archivo externo de verificadores y reinicio para rotación/revocación. No hay identidad/tenant autoritativo en el body ni OAuth2/portal comercial implementados.
- Proveedor elegido por el usuario: `gemini-3.8-flash` en Google AI Studio/Developer API. La variable es `GOOGLE_API_KEY`; **no abrir, mostrar, buscar ni copiar el `.env` del usuario**. Solo la aplicación lo importa explícitamente en ejecución. Las pruebas ordinarias no requieren esa clave.
- El usuario pidió límites inicialmente muy altos para no recortar información por anticipado: nota de un millón de puntos de código, body 64 MiB, catálogos hasta 100.000 entradas y máximo de salida de Gemini 65.536 tokens. Son protecciones técnicas, no tamaños con calidad demostrada. Plazo de proveedor 120 s, concurrencia 2; evaluación secuencial y cero reintentos.
- Criterios previos: precisión ≥95% y cobertura ≥90% por tipo principal en cada ronda reservada; cero tolerancias de evidencia/IDs, contexto crítico y atributos sin respaldo, además de revisión semántica y adjudicaciones resueltas. No bajar umbrales para aprobar.
- Solo datos sintéticos. SFM permanece de solo lectura y no es dependencia; PreAudit Health no se modificó. No se usaron contenedores ni datos reales.

## Qué mostró H5

234 operaciones autenticadas reales, 39 notas por split × tres rondas. Todas devolvieron HTTP200/COMPLETE; sin timeout o truncamiento. **Ninguna ronda reservada cumplió los criterios de calidad.** La integración técnica y las pruebas deterministas no certifican interpretación correcta.

Precisión principal original contra las referencias, antes de rechazos suplementarios de auditoría:

| Tipo | Ronda reservada 1 | Ronda 2 | Ronda 3 | Objetivo por ronda |
| --- | --- | --- | --- | --- |
| Diagnósticos | 75,76% | 84,85% | 84,85% | ≥95% |
| Medicamentos | 86,96% | 86,96% | 91,30% | ≥95% |
| Procedimientos | 64,29% | 64,29% | 62,07% | ≥95% |

La cobertura y los TP/FP/FN completos, cohortes, familias y atributos están en [el informe](../h5-validation.md) y [summary-automatic.json](../../evaluation/runs/2026-10-28-gemini38-v1/summary-automatic.json). No tratar estas cifras como precisión clínica validada.

Fallos observados: concentraciones de envase convertidas en dosis, síntomas tratados como diagnósticos, repeticiones duplicadas, citas desplazadas, pérdidas de contexto y temporalidad de la orden confundida con la prestación. Hubo entidades procedentes de instrucciones externas en una ronda de desarrollo; éxitos posteriores no borran ese fallo. En algunas negaciones el estado sigue `not_performed` pero `assertion` cambia a `affirmed`; no afirmar por ello que el modelo declaró la prestación realizada.

La revisión textual del constructor dejó 119 intentos con respaldo verificado, 109 con hallazgos/respaldo rechazados y seis con decisión pendiente, correspondientes a tres temas: interconsulta como documento/prestación, impresión diagnóstica y suficiencia de evidencia elíptica. Es una revisión conservadora de consistencia sintética, **sin revisión clínica independiente ni cegamiento**. Las citas insuficientes no se presentan automáticamente como valores atribuidos a otra persona.

Cota de consumo estimada: USD1.01496000 dentro del tope operativo USD10 adoptado y comunicado; no es factura ni verificación del tier. Tokens conocidos: entrada 133.095, candidatos 244.037, total 377.132; caché/pensamientos faltantes siguen desconocidos. Mediana 7,824 s, p95 12,470 s, máximo 18,651 s desde el cliente local. Las notas medidas tenían 92–424 puntos de código y 0–3 entradas de catálogo. La grilla de escala se validó solo localmente; no hay frontera de tamaño remoto con calidad aprobada.

## Incidencias metodológicas y artefactos que deben conservarse

Leer las [erratas de v1](../../evaluation/reports/2026-10-28-gemini38-v1-errata.md) antes de tocar el evaluador:

1. La coincidencia exacta de grupos de advertencias puede contar como faltante una advertencia cuyo target ya está cubierto por un grupo mayor.
2. El anclaje gold de Brisa en desarrollo puede dejar sin alinear la aparición de la prescripción correcta. No cambiar el gold solo para elevar el resultado.
3. El `summary.pass` del runner congelado solo comprueba reservado. El [control separado 1.0.1](../../evaluation/reports/release.py), añadido después de medir con cinco pruebas de conteos ficticios, aplica auditoría y cero tolerancias a ambos splits. No modifica puntajes, referencias, umbrales o inferencia.

La decisión final está en [release-decision.json](../../evaluation/runs/2026-10-28-gemini38-v1/release-decision.json): **false**. La decisión original también era negativa. Preservar [freeze](../../evaluation/runs/2026-10-28-gemini38-v1/freeze.json), [respuestas](../../evaluation/runs/2026-10-28-gemini38-v1/responses.jsonl), [auditoría](../../evaluation/runs/2026-10-28-gemini38-v1/semantic-audit.json), resúmenes originales/auditados, archivos de fuente y [checksums](../../evaluation/runs/2026-10-28-gemini38-v1/artifact-checksums.json). Los artefactos congelados registran correctamente que aún no había commits al medir; no reescribir ese dato por los commits posteriores.

El JAR exacto de la ejecución está referenciado por hash en `freeze.json` y permanece en `target/h5-frozen/`, ignorado por Git. `mvn clean` lo elimina; Git conserva la fuente congelada y su hash, no el binario. No asumir que recompilar reproduce los mismos bytes ni repetir una evaluación de pago solo para reconstruir evidencia. Cualquier nueva configuración/corrección tendrá su propia ejecución/versionado; no editar los hashes de v1 para reanudar con otros activos.

## Próximos pasos concretos, en orden

1. Leer este cierre, [AGENTS.md](../../AGENTS.md), [H5](../h5-validation.md), [erratas](../../evaluation/reports/2026-10-28-gemini38-v1-errata.md) y [regresiones de desarrollo](../../evaluation/regressions/README.md). Confirmar la rama y el árbol de trabajo; usar [el índice](../README.md) para profundizar, sin repetir todas las decisiones H1.
2. Versionar la corrección del comparador de advertencias y de las referencias disputadas. Añadir pruebas específicas con conteos/casos de desarrollo. Conservar la línea base v1 y sus resultados, sin corregirlos retrospectivamente.
3. Mejorar extracción usando desarrollo: excluir instrucciones/texto no clínico; separar síntomas de diagnósticos; agrupar repeticiones equivalentes; robustecer selección de ocurrencias/citas inequívocas y asociación de atributos; preservar negación, estados y `unknown`. Una hipótesis es citar cláusulas menos ambiguas, no reducir salida/entrada ni introducir heurísticas clínicas para fingir garantías.
4. Aplicar TDD a cambios deterministas/contratos y volver a verificar el proyecto. La calidad del prompt requiere medición real posterior; una prueba con fake no demuestra esa mejora. La observación menor `processing: null` del arnés sigue pendiente y no apareció con el JAR probado.
5. Si los resultados reservados orientan ajustes, mover los escenarios afectados a regresión y reemplazarlos por otros de origen independiente antes de otra evaluación. No usar el reservado actual como oráculo de TDD o elegir la mejor ronda.
6. Congelar nueva versión de código/prompt/evaluador/corpus y condiciones. Registrar un nuevo presupuesto para cualquier siguiente campaña; el cierre no programa llamadas adicionales. Ejecutar tres rondas por split, auditar respaldo/disputas y aplicar el control agregado sin relajar 95/90 o cero tolerancias.
7. Solo si la evidencia aprueba, declarar el MVP y valorar el siguiente incremento. Para uso con datos reales siguen faltando revisión clínica independiente y comprobaciones de autorización, privacidad/retención, tier y condiciones externas; ni facturación habilitada ni un servidor privado resuelven eso.

No se pide al usuario que arbitre medicina ni se añaden otras capacidades durante este cierre. El camino de corrección está identificado; la próxima sesión debe partir de esa evidencia.

## Verificación y Git al cerrar

[Verificación reproducible de cierre](verificacion.md): 134 pruebas Java, 66 del arnés/corpus y cinco del control de decisión; contrato y hashes verificados sin nueva inferencia. La [guía H5](../h5-integration.md) describe cómo consumir la API; no ejecutar sus llamadas remotas como comprobación automática.

Los commits se agrupan en contrato/corpus, implementación Java y evaluación/documentación. Se mantienen en la rama local `feat/extraction-core-h2`; el nombre histórico de la rama no limita el alcance actual. Consultar `git log --oneline` para sus IDs. Los ledgers que dicen «sin commits» son históricos y quedan supersedidos por esta autorización de cierre. No se incluyen `.env`, claves, credenciales temporales, `target/`, cachés o locks de proceso.

### Bloques de commits locales

| Commit | Contenido |
| --- | --- |
| `e860ad0` | Contrato OpenAPI, ejemplos, corpus/reglas sintéticos, validadores base, instrucciones y exclusiones de Git. |
| `865efba` | Aplicación Java, núcleo, HTTP/API keys, adaptador Gemini, recursos y pruebas. |
| Commit de cierre que contiene este documento | Arnés/resultados H5, auditoría/control agregado, regresiones y documentación completa de continuidad. Su ID se obtiene con `git log -1 --oneline`. |

Los bloques agrupan el trabajo acumulado al cerrar; no pretenden reconstruir commits que existieran durante la evaluación. Los hashes de la congelación siguen siendo la evidencia de aquella versión.
