# T3 — Evaluador candidato 1.1.0 y comparación offline

Estado: T3 implementada y verificada sobre datos sintéticos congelados. Es un evaluador de desarrollo candidato, **no integrado al runner de campañas ni al control de entrega vigente**. No se modificaron referencias, auditorías, respuestas ni decisiones históricas. T2 continúa con adjudicaciones abiertas.

## Archivos y reproducción

- [score_extractions_v3.py](../score_extractions_v3.py): nueva versión separada. Parte del comparador congelado 1.0.1 y conserva sus reglas estrictas, con las correcciones detalladas abajo.
- [test_score_extractions_v3.py](../test_score_extractions_v3.py): 36 pruebas de conteos, evidencia, advertencias, auditoría y remuestreo.
- [compare_evaluators.py](../compare_evaluators.py): comparación offline de las 234 respuestas originales en modo automático y con la auditoría revisión 2.
- [test_compare_evaluators.py](../test_compare_evaluators.py): 7 pruebas de vinculación de auditoría y rechazo de registros ajenos, cambiados o duplicados.
- [input-manifest.json](input-manifest.json): hashes del evaluador anterior y sus dependencias de validación, además del manifiesto de T1.
- [comparison.json](comparison.json): resultados, diferencias por caso, métricas auxiliares, migración de resoluciones e incertidumbre agrupada.

Con un entorno que tenga `openapi/tools/requirements.txt` (en esta sesión se reutilizó `/tmp/clinical-eval-next`):

```sh
PYTHONDONTWRITEBYTECODE=1 /tmp/clinical-eval-next/bin/python -m unittest discover -s evaluation/quality-review -p 'test_*.py'
PYTHONDONTWRITEBYTECODE=1 /tmp/clinical-eval-next/bin/python evaluation/quality-review/compare_evaluators.py --draws 1000 --seed 1729 > /tmp/clinical-t3-comparison.json
cmp evaluation/quality-review/t3/comparison.json /tmp/clinical-t3-comparison.json
```

Para ejecutar únicamente las 14 pruebas originales de T1, usar el filtro `-p test_analyze_baseline.py`; el descubrimiento general incluye ahora las pruebas nuevas y requiere el entorno de validación del proyecto.

La herramienta escribe por stdout, verifica primero los hashes y no importa runners, clientes HTTP ni proveedores. No ejecuta inferencia, no escribe en directorios históricos y no emite una nueva decisión de entrega de esas campañas.

## Métricas: qué mide cada una

| Campo | Definición y denominador |
| --- | --- |
| `entities` | TP/FP/FN estrictos: nombre/aparición, todo el contexto y rechazos explícitos de entidades auditadas. Error de contexto conserva FP y FN. |
| `mentionAlignment` | Coincidencia automática de nombre y anclaje, una vez por referencia; duplicados y anclajes ambiguos no añaden cobertura. No demuestra asociación semántica ni corrección clínica. |
| `contextDimensions` | Igualdad de valores por afirmación, tiempo, sujeto y estado. `correct/incorrect` solo sobre ganadores alineados; `missing` cuenta referencias sin alineación. `accuracyOnAligned` excluye missing y es N/A sin alineaciones. No certifica evidencia: puede coincidir el valor y estar rechazado su respaldo. |
| `attributes` | Conteos textuales estrictos y ausencias. `null/null` sigue siendo ausencia, no TP. No se convierten dosis ni sinónimos. |
| `catalogs` | Estado y conjunto de candidatos exactos; `not_provided` se informa aparte. Un ID ajeno sigue siendo violación de invariantes. |
| `attributeReferenceDiscrepancies` | Marcas conservadoras heredadas antes llamadas `unbackedAttributes`, más atributos de predicciones con anclaje ambiguo. No incluye todo FP textual: las variantes ya tienen adjudicación propia. No es cantidad confirmada de invenciones. |
| `unbackedAttributes` y `attributeSupport.confirmedUnsupported` | Cantidad de campos presentes rechazados explícitamente en revisión de respaldo. «Confirmado» significa decisión explícita de esa revisión sintética, no verdad clínica ni revisión humana implícita. |
| `attributeSupport.requiresReview` | Campos con una marca de discrepancia/alineación pendiente, sin rechazo explícito ni revisión dirigida favorable. Cada campo añade una adjudicación bloqueante. |
| `semanticSupport` | Casos sin auditoría, pendientes y con revisión completada; entidades rechazadas únicas y campos rechazados. Evita interpretar `semanticSupportFailures` heredado —que suma señales de distinta clase— como tasa o número de errores independientes. |
| `discardedAttributes` | Destinos únicos de `ATTRIBUTE_DISCARDED` por operación. Agrupar o separar avisos con la misma cobertura no cambia el conteo. |
| `discardedAttributeWarnings` | Número de avisos de descarte, conservado para comparar con informes anteriores. |
| `discardedEntities` | Avisos de descarte completo; sin propuesta cruda no identifica cuántas menciones originales ni qué valores se eliminaron. T4 sigue pendiente. |

Las métricas auxiliares se agregan también por cohorte, familia documental, localidad y tamaño. Los TP/FP/FN estrictos siguen separados por tipo. No mezclar porcentajes auxiliares con aprobación.

## Correcciones verificadas

### Discrepancia frente a respaldo rechazado

Una dosis que aparece literalmente en una predicción no alineada al gold puede ser correcta para la mención y aun así fallar el comparador por nombre/aparición o referencia. El evaluador anterior incrementaba directamente `unbackedAttributes` en ese caso.

Ahora se conserva el FP/FN y se abre `ATTRIBUTE_SUPPORT_REVIEW`. La alerta solo se convierte en campo rechazado mediante auditoría explícita. Un rechazo dirigido elimina el TP del atributo cuando corresponde. Una revisión favorable dirigida no recupera por sí sola la entidad no alineada ni inventa una referencia nueva.

Las antiguas incidencias `UNBACKED_ATTRIBUTE`/`UNBACKED_DUPLICATE_ATTRIBUTE` se muestran como `ATTRIBUTE_REFERENCE_DISCREPANCY` y conservan `legacyCode` para trazabilidad. Una cita literal sigue sin demostrar asociación.

### Duplicados y orden

El motor anterior elegía el primer duplicado con contexto elegible. Si sus atributos diferían, invertir el orden podía cambiar las métricas textuales.

La nueva selección conserva la prioridad de contexto correcto/no rechazado y desempata por contenido canónico sin ID ni orden de las listas de evidencia. No compara atributos con el gold para elegir el que gane más puntos. Cuando el contenido empata, usa los campos rechazados por auditoría como desempate estable; los punteros deben moverse junto a las entidades cuando se permuta una respuesta auditada.

Los duplicados siguen generando FP y no amplían cobertura. Las predicciones con anclaje ambiguo siguen sin alinearse; ahora sus atributos expuestos también generan FP y revisión, en lugar de desaparecer de los conteos de atributos.

### Auditoría y resoluciones

Se rechazan decisiones sin motivo, índices fuera de rango/repetidos, punteros inexistentes o duplicados y contradicciones entre `support_verified` y rechazos. Una respuesta HTTP fallida no se convierte en vacío correcto; conserva todas las omisiones.

Las resoluciones por índice requieren `evaluatorVersion` y `automaticAdjudicationsSha256`, calculado sobre la lista exacta de adjudicaciones automáticas de esta versión después de aplicar rechazos explícitos. Un índice válido de otra versión no basta. Un anclaje ambiguo no se resuelve eligiendo retrospectivamente el gold favorable. Las nuevas revisiones de atributos se dirigen por campo con `attributeSupportReviews`, razón y decisión, no mediante índices prestados de otra lista.

La comparación importa la auditoría histórica comprobando hashes de freeze, archivo de respuestas y registro individual. Traduce una resolución histórica únicamente cuando su adjudicación es idéntica y única en la versión nueva. Lo que no pueda traducirse queda abierto y se informa; no se convierte en aprobación ni se cambia el archivo de auditoría original. La firma `score_case` es una función local para entradas ya seleccionadas; la vinculación a archivos se realiza en `compare_evaluators.py`, no debe omitirse en un futuro runner.

## Resultado de la comparación congelada

- Las **234 puntuaciones automáticas históricas se reprodujeron** con el evaluador viejo. Se comparó su representación JSON, porque las tuplas Python de destinos se serializan como listas; no se alteraron valores para obtener igualdad.
- Los **TP/FP/FN de entidades son iguales en las 234 operaciones**, tanto en modo automático como auditado.
- Hay **dos cambios de conteo**, ambos automáticos: `res-consultation-04`, rondas 1 y 3. La vía «tomar» pasa de una marca automática presentada como atributo sin respaldo a una revisión de atributo pendiente: `unbackedAttributes` 1→0 y adjudicaciones 0→1. La auditoría ya rechazaba esa vía; en modo auditado sigue siendo un campo rechazado y no cambian los conteos.
- **Cero resoluciones históricas sin traducción**. Los 57 intentos pendientes de la auditoría continúan pendientes; no se aplicaron las 18 referencias propuestas de T2.
- Las seis combinaciones split/ronda siguen con resultado negativo. No se ganó precisión del extractor al cambiar la etiqueta de una alerta.
- Las nuevas correcciones de orden/descarte/anclajes de atributos se prueban en regresiones sintéticas. No se atribuye una mejora a la campaña si no ejercita esos patrones.

`caseCountDifferences` registra cada ruta numérica cambiada, su antes/después, caso, ronda y modo. La comparación incluye conteos de entidades, atributos, catálogos, descartes, advertencias, violaciones y adjudicaciones; no solo el promedio final.

## Incertidumbre descriptiva por origen

Se añadió bootstrap percentil descriptivo por `originGroup`, con semilla 1729, 1000 remuestreos e intervalo de percentiles 2,5–97,5. Se muestrea el origen completo con reemplazo; las rondas de esa nota viajan juntas. Se informa número de observaciones, grupos y remuestreos con denominador válido; si falta denominador, el intervalo es N/A, no 100 %.

La comparación lo calcula por split y cohorte con puntuaciones auditadas provisionales. En principal reservado hay **72 observaciones y 24 orígenes**, no 72 escenarios independientes. Los intervalos no estiman validación clínica externa: hay referencias disputadas, corpus pequeño, exposición de reservado y posibles similitudes semánticas entre orígenes. Las pruebas con dos orígenes, cada uno repetido tres veces, comprueban que no se separan las rondas al remuestrear.

La aprobación sigue usando los conteos enteros 95/90 por tipo y ronda, cero tolerancias, advertencias requeridas y auditoría completa. Ningún intervalo crea una excepción.

## Verificación y revisión

Se observaron pruebas rojas antes de implementar; después se añadieron regresiones para hallazgos de revisión. El revisor auxiliar encontró y volvió a comprobar corregidos tres fallos: empate auditado que dependía del orden, eliminación de duplicados de `invalidAttributes` antes de validarlos y conteo de descartes dependiente de agrupación de avisos. El resumen queda en [review.md](review.md). No es adjudicación clínica.

Verificaciones finales:

- 57 pruebas de `quality-review`: 36 del evaluador, 7 de vinculación y 14 de T1.
- 9 pruebas de integridad T2.
- 81 pruebas del arnés existente y 5 de control de entrega.
- **152 pruebas Python en total, todas pasan.**
- Comparación reproducida offline con semilla/parámetros fijos y hashes de insumos.
- Manifiestos T1/T2 y fuentes históricas sin cambios; diff sin errores de formato.

No se ejecutó Java de nuevo: no cambió código productivo, contrato ni dependencias Java. Las 137 pruebas de T1 son una verificación histórica, no una ejecución de T3. No se llamó al proveedor ni se accedió a datos reales.

## Continuación

T3 deja un candidato comprobable. Antes de usarlo en una campaña nueva, congelar código, dependencias, reglas, corpus y control conforme a T10. T2 continúa con decisiones de producto/clínica pendientes y el ADR 0007 sigue propuesto. El siguiente trabajo técnico es T4: observar el recorrido de propuestas y descartes con captura sintética explícita; las métricas actuales no permiten reconstruir la propuesta cruda de v3.
