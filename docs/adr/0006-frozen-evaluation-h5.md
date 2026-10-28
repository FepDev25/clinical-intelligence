# ADR 0006 — Evaluación congelada y consumo acotado de H5

Fecha: 2026-10-27. Estado: decisión tomada antes de medir; implementación y evaluación completadas. El resultado es un prototipo evaluado, sin aprobación del MVP; ver el cierre al final.

## Decisión

Se evalúa por HTTP la salida final aceptada, con el mismo JAR del producto, una nota sintética por operación y tres rondas sin seleccionar resultados. Un arnés Python separado conserva resultados sintéticos, reglas y condiciones; la API no incorpora persistencia clínica ni conoce referencias del corpus.

Frente a usar otro LLM como juez, se implementa puntuación determinista contra las referencias versionadas y se mantienen adjudicaciones explícitas para ambigüedades. Frente a igualdad JSON, se alinean nombres y anclajes literales conservando contexto, duplicados y campos. Los criterios 95/90 por tipo y cero tolerancias permanecen exactamente como en el protocolo; no se añade equivalencia de sinónimos, unidades o dosis.

Una variante textual que sea subcadena de la referencia, en su misma evidencia, no recibe crédito automático: conserva FP/FN y exige revisión antes de aprobar. Una asociación ambigua de entidad/atributo o advertencia semántica adicional también queda abierta. La automatización no certifica semántica clínica; el reporte identifica límites y adjudicaciones sin ocultarlas. Las anotaciones conservan su procedencia sintética, sin atribuir revisión médica independiente.

El JAR, código, prompt, esquema, reglas, evaluador y dependencias de validación quedan identificados por hashes antes de llamar al modelo reservado. El repositorio sigue sin commit inicial, por lo que un snapshot de fuente sustituye a una revisión Git recuperable; el artefacto ejecutable se conserva en `target/h5-frozen/`. Reanudar exige los mismos activos, reglas y configuración. Si hay cambios, se crea otra ejecución y se preserva el reporte anterior.

## Consumo y privacidad

Las 78 notas fijas se miden tres veces (234 operaciones, hasta 468 llamadas incluyendo conteo). Se mantienen `maxOutputTokens=65536`, temperatura 1.0, thinking LOW, deadline 120 s y cero reintentos. La ejecución es secuencial para vincular observaciones seguras con cada caso, sin convertirla en un benchmark de capacidad productiva.

El usuario autorizó continuar H5. Se solicitó preferencia de presupuesto; mientras no se reciba otra indicación se adopta un tope operativo conservador de USD10, no una autorización supuesta por silencio. Esta es una elección del implementador para acotar el trabajo ya solicitado. Antes de cada operación se reserva una cota conservadora; datos de uso faltantes no se convierten en coste cero. Si el saldo no permite otra reserva, se detiene y se informa incompletitud. Los resultados faltantes no se eliminan del denominador.

Se añade a la telemetría interna el total de tokens comunicado por Google y la versión efectiva sanitizada. `totalTokenCount` incluye prompt, herramientas, pensamientos y candidatos. Para presupuesto se calcula una cota superior tratando toda la entrada como no cacheada y los demás tokens como salida. Si no hay total/entrada válidos, se mantiene la reserva. No se deduce cero para pensamientos o caché ausentes y la estimación exacta de H4 puede seguir desconocida. [Referencia de uso](https://ai.google.dev/api/generate-content#UsageMetadata).

La reserva previa contempla la entrada máxima del modelo y una provisión separada para salida/pensamientos al precio estándar vigente. Es un control operativo estimado con tarifa identificada, no una factura ni una garantía contractual de facturación externa. El arnés rechaza tarifa vencida y documenta costes desconocidos.

Solo el proceso de la aplicación importa el `.env` del usuario. El arnés no lo abre ni obtiene la clave de Google; genera una clave temporal del consumidor y registra únicamente respuestas sintéticas públicas y telemetría permitida. Los logs privados no se vuelcan y se eliminan al terminar. Las pruebas usan solo desarrollo/casos unitarios y red local.

## Reservado y escala

El arnés carga las peticiones reservadas solo al ejecutar expresamente ese split, tras congelar y completar las rondas de desarrollo. Nunca envía gold, familias, tags o anotaciones al modelo. Las referencias se usan en análisis posterior. Si se usa un fallo reservado para ajustar, se debe mover a regresión y reemplazar su escenario conforme al protocolo antes de otra medición independiente; no se reescribe el resultado original.

Las diez recetas de escala se auditan y materializan localmente; no se ejecuta su grilla remota por defecto. Cualquier sonda remota seleccionada tendrá condiciones/resultados separados y no extenderá el soporte demostrado a tamaños no medidos.

## Resultado y revisión de decisión

H5 completó 234 operaciones con configuración congelada; [informe](../h5-validation.md): prototipo evaluado, MVP no aprobado. Se mantuvieron los conteos originales y disputas explícitas. Una revisión posterior de decisión 1.0.1 exige auditoría y cero tolerancias en ambos splits mediante control separado, sin alterar la fuente/puntuación congeladas o pedir más inferencia. Su código, pruebas y hashes acompañan la ejecución; los criterios siguen siendo los del protocolo previo. Incidencias de comparación/referencia se preservan para una nueva versión, no se corrigen retrospectivamente para aprobar.
