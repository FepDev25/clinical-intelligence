# T7 — Variantes reproducibles de prompt y configuración

Preparada e implementada para pruebas locales el 28-10-2026, sobre T6 `67b0a78`. Atiende L10/L11/L15 y prepara las ablaciones L01–L14 del [plan aprobado](../../../docs/superpowers/plans/2026-10-28-extraction-quality-review-and-remediation.md). **Cero inferencias remotas**: no se ejecutó T8 ni se aprobó el MVP. T2 mantiene adjudicaciones abiertas.

## Corrección de compatibilidad y telemetría

La [verificación de documentación oficial](provider-capabilities.md) identifica que el modelo 3.8 requiere retirar `candidateCount` y los parámetros de muestreo obsoletos, incluido `temperature`, que se ignora en las generaciones actuales. Se omiten ambos de la petición nueva; esto **no demuestra causalidad histórica** ni explica por sí solo los resultados previos. Se conserva generateContent, sin migrar a otra API.

Producción continúa con **gemini-3.8-flash, LOW y prompt v2**. [historical-request.json](historical-request.json) se exportó utilizando las clases del JAR T6 real, preservado en [baseline.json](baseline.json); es el cuerpo del protocolo antes de que el proveedor añada el campo model, no una captura remota nueva. La comparación exacta con v2/LOW actual confirma que los únicos cambios de ese cuerpo son retirar candidateCount y temperature. El test del servidor local comprueba además que countTokens recibe la misma petición completa que generación.

`GeminiGeneration` permite elegir explícitamente LOW/MEDIUM/HIGH y recursos de prompt empaquetados. El modelo se restringe al único perfil verificado, 3.8 Flash. Rechaza MINIMAL, modelos no verificados, prompts arbitrarios, claves adicionales, tipos incorrectos y opciones obsoletas; no hace fallback. No se añaden propiedades Spring/ENV ni selección desde el body HTTP: los experimentos usan el constructor Java explícito. Los límites de operación existentes permanecen configurables y sin reducción.

`GeminiProtocol.Identity` registra modelo solicitado, versión/hash de prompt, hash de esquema, thinking y versión de petición `gemini-generate-content-t7-1`. Esa identidad describe esos campos y se acompaña de límites/condiciones en el manifiesto; no sustituye la petición o configuración completa. La respuesta del proveedor usa el prompt realmente seleccionado. La telemetría de API toma `ExtractionService.VALIDATION_VERSION` (**evidence-4**) y la identidad real del proveedor, corrigiendo el literal evidence-1. No se modifican métricas históricas ni se registran notas, propuestas, secretos o razonamiento.

La versión de modelo más granular se registra cuando llega y pasa la validación existente. Cuando falta una versión válida, el decoder conserva el alias solicitado como fallback de metadatos: ese alias no demuestra una versión inmutable comunicada por el proveedor. Cada estudio debe conservar también la identidad solicitada y todas las condiciones.

## Variantes y decisiones de referencia

| Variante | Función | Situación |
| --- | --- | --- |
| `extraction-gemini-v2` | Línea base de una pasada | Predeterminada; recurso original intacto. |
| `extraction-gemini-v3-t7` | Asociación por mención, citas, agrupación, negaciones, sujetos, pautas y secuencias | Candidata; no desplegada por defecto ni medida con modelo. |
| `detection-gemini-v1-t7` | Nombres por aparición, con contexto/estado unknown y atributos null | Etapa experimental con esquema restringido propio. |
| `classification-gemini-v1-t7` | Clasificar/asociar las menciones detectadas conservando sus anclas | Etapa experimental; requiere detección explícita. |

La candidata v3 incluye la propuesta R1 de órdenes sin fecha (`ordered/unknown`) y abstención ante vínculo retrospectivo insuficiente. **No adjudica T2 ni adopta ADR 0007.** El estudio debe mantener los denominadores, revelar diferencias con referencias pendientes y cerrar las decisiones que afecten una selección antes de medir una candidata para entrega. No se amplía automáticamente el alcance de control/revisión ni se elige una etiqueta clínica por haber mejorado el score.

[variant-requests.json](variant-requests.json) contiene los **12 cuerpos construidos localmente** (4 prompts × 3 niveles), con hashes verificables y datos sintéticos. Incluye el esquema enviado y los anclajes Unicode de clasificación. No son respuestas del modelo ni pruebas de que los 12 cuerpos funcionen remotamente en esta cuenta.

## Dos etapas: prototipo con dobles

`GeminiTwoStageExperiment` existe **solo en fuentes de test**, sin HTTP, claves, bean o comando de campaña. El adaptador ordinario rechaza los prompts de etapas. `GeminiStageBoundary` y `classificationRequest` comprueban los límites estructurales de la variante:

- Detección: una aparición literal válida por mención; no admite clasificación anticipada, atributos, notices ni duplicados del mismo anclaje.
- Clasificación: recibe nota original y nombres/tipos/anclas bloqueados como datos. No puede añadir, omitir, duplicar o desplazar una aparición. Puede agrupar conservando la unión exacta de anclas; la equivalencia clínica sigue siendo probabilística y se evalúa aparte.
- Detección vacía válida omite la segunda etapa. Respuestas incompletas, malformadas, fallos de transporte o de asociación de etapas no se convierten en éxito vacío o parcial; no hay reintento ni fallback.
- Registra identidad, modelo, tokens y latencia por etapa, también en `StageFailure`, sin guardar el cuerpo en la excepción. Los totales suman entradas y salida+thinking; si falta un contador, el agregado correspondiente es desconocido. Se preservan emoji, CRLF y puntos de código originales.

Las omisiones de detección **se propagan**: la clasificación no puede repararlas. Dos generaciones acumulan coste, latencia y probabilidad de fallo. T8 debe implementar el transporte del estudio, deadline y presupuesto agregados y captura reproducible de propuestas/trazas. Este prototipo no se anuncia como un runner remoto terminado ni como una variante clínica aprobada.

## Diseño causal preparado

[experiment-plan.json](../experiment-plan.json) fija hashes de fuentes, prompts, esquema, evaluador, datos y JAR de filtros, además del orden completo. [experiment_plan.py](experiment_plan.py) construye y valida el blueprint sin cliente de red ni lectura de credenciales.

| Brazo evaluado | Generación | Filtro local |
| --- | --- | --- |
| Base | v2 LOW | evidence-2 congelado de T4 |
| Solo prompt | v3 LOW | evidence-2 |
| Solo filtro | v2 LOW | evidence-4 de T6 |
| Combinación | v3 LOW | evidence-4 |
| Thinking MEDIUM | v3 MEDIUM | evidence-4 |
| Thinking HIGH | v3 HIGH | evidence-4 |
| Dos etapas | Detección + clasificación LOW | evidence-4 |

Cada propuesta cruda se reutiliza para los filtros correspondientes: no se vuelve a pedir una respuesta para atribuir el efecto al filtro. El factorial compara ambas intervenciones y su interacción; thinking mantiene prompt/filtro. La variante de dos etapas se analiza por separado. Todas usan el transporte corregido, sin confundirlo con el cambio de prompt.

Condiciones fijadas: **39 notas de desarrollo expuesto × 3 rondas**, orden por hash con semilla 1729 y rotación de las cinco configuraciones/unidades por bloque. Hay **585 unidades planificadas**, hasta **702 generaciones + 702 conteos** y **819 evaluaciones locales previstas**; **ninguna se ejecutó** como campaña. Los filtros añaden evaluaciones, no llamadas remotas. Si la detección es vacía o falla, disminuyen llamadas posteriores sin ocultar el intento. No hay reintentos ni mejor-ronda; los errores y rondas incompletas permanecen en el reporte.

Se mantienen 65 536 tokens de salida, los límites técnicos anteriores y 120 s por generación, con hasta 240 s acumulados para dos etapas. Concurrencia planificada 1 para el estudio; la configuración de producción no se reduce. Métricas: estrictas 95/90 por tipo/ronda y cero tolerancias sin cambios, diagnóstico por dimensión/etapa, omisiones, falsos descartes, unknown, advertencias, latencia, uso/coste y estabilidad. La incertidumbre se agrupa por originGroup, sin tratar rondas repetidas como notas independientes.

**Estado preparado, no autorizado ni ejecutable.** Presupuesto USD ausente, disputas pendientes, prevalidación del transporte y cuenta, freeze del runner y perfil de otro modelo siguen identificados. T8 necesitará condiciones y autorización específicas, con cota de gasto e interrupción antes de cada llamada. No basta cambiar un booleano en este blueprint: el validador lo rechaza. Antes de cualquier replay/ejecución T8, deben existir los JAR correctos y sus checksums; T7 tolera su ausencia al validar un diseño no ejecutable en otro checkout, pero no sus bytes cambiados.

## Verificación y reproducción offline

- `mvn -B -ntp -o verify`: **285 pruebas Java**, cero fallos; compilación y empaquetado correctos.
- Python: quality-review 57, T2 9, T5 6, T6 7, T7 7, tools 81, reports 5: **172**, cero fallos.
- OpenAPI: 21 esquemas, 29 escenarios y 58 ejemplos; 30 mutaciones inválidas rechazadas.
- Doce peticiones verificadas: identidades únicas, hashes de recursos/esquemas, campos generados, datos no confiables separados del sistema y anclajes Unicode; diferencia exacta T6→T7 de la petición predeterminada.
- Revisión técnica fresca sin bloqueos. Observación menor registrada: T8 debe exigir presencia de binarios antes de ejecutar, además de su checksum.
- Integridad de históricos, originales v2/esquema, corpus, estudios anteriores y JAR congelados verificada sin actualizar hashes históricos. El prototipo y fixtures de test se excluyen del JAR de aplicación.

La primera suite completa detectó dependencia de la prueba de captura de logs respecto del nivel global que dejaban las pruebas de arranque. Se corrigió aislando y restaurando el nivel del logger en el test; el fallo y la ejecución completa posterior se conservan. No se cambió el comportamiento del producto para aprobar esa prueba.

```sh
mvn -B -ntp -o verify
PYTHONDONTWRITEBYTECODE=1 /tmp/clinical-eval-next/bin/python -m unittest discover -s evaluation/quality-review/t7 -p 'test_*.py'
/tmp/clinical-eval-next/bin/python evaluation/quality-review/t7/experiment_plan.py --check
java -cp 'target/classes:target/test-classes:target/t7-baseline/lib/*' com.clinicalintelligence.extraction.infrastructure.gemini.VariantRequestSnapshot /tmp/t7-requests-new.json
```

Usar salida nueva: los exportadores rechazan sobrescritura. El JAR T6 ignorado por Git se puede reconstruir desde baseline.json en un checkout aislado; identificar la reconstrucción si cambia el empaquetado. No usar `mvn clean` en este workspace con JAR congelados. Los resultados y logs de cierre están en [verification/](verification/), con manifiesto propio.

Siguiente paso: preparar en **T8** el presupuesto, referencias aplicables y ejecución controlada concreta. Ninguna variante se presenta como mejora de calidad antes de medirse; la aprobación del MVP y la evaluación independiente siguen pendientes.
