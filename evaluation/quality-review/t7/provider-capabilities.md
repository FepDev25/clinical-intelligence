# Verificación documental del proveedor — 28-10-2026

Fuentes oficiales consultadas públicamente, sin claves ni peticiones de inferencia. La fecha identifica la lectura, no una prueba de compatibilidad de la cuenta ni la versión histórica de la documentación.

| Aspecto | Resultado documental y decisión T7 | Fuente |
| --- | --- | --- |
| Modelo | `gemini-3.8-flash`: texto, salida estructurada, thinking LOW/MEDIUM/HIGH; MINIMAL no admitido. Límites publicados: entrada 1 048 576 y salida 65 536 tokens. El perfil T7 restringe el modelo a este ID verificado. | [Ficha del modelo](https://ai.google.dev/gemini-api/docs/models/gemini-3.8-flash) |
| Muestreo | La guía de migración indica retirar candidate_count y parámetros de muestreo obsoletos. Se omiten candidateCount y temperature del cuerpo actual y se rechazan como opciones experimentales. El decoder mantiene la exigencia de exactamente un candidato. | [Guía 3.8](https://ai.google.dev/gemini-api/docs/whats-new-gemini-3.8) |
| Temperatura | La referencia de generateContent declara que temperature se ignora desde 3.6 Flash. No proponer una ablación de temperatura como intervención efectiva con este modelo. | [Referencia REST, GenerationConfig](https://ai.google.dev/api/generate-content#v1beta.GenerationConfig) |
| Endpoint y thinking | Se conserva `v1beta/models/gemini-3.8-flash:generateContent` y su conteo, con thinkingConfig.thinkingLevel explícito e includeThoughts=false. No se migra a Interactions ni se interpreta una recomendación de migración como requisito para esta tarea. | [Referencia REST](https://ai.google.dev/api/generate-content), [thinking](https://ai.google.dev/gemini-api/docs/thinking) |
| Esquema | El modelo declara soporte estructurado. T7 conserva responseMimeType y responseJsonSchema del adaptador, validación JSON local estricta y ausencia de herramientas. No asumir que cualquier JSON Schema aceptado localmente es aceptado por el proveedor. El esquema restringido de detección necesita comprobación remota autorizada en T8. | [Salida estructurada](https://ai.google.dev/gemini-api/docs/structured-output), [referencia REST](https://ai.google.dev/api/generate-content#v1beta.GenerationConfig) |
| Tarifa | La tabla estándar publicada mantiene hasta 31-12-2026: USD 0.75/M entrada, 3.75/M salida incluido thinking y 0.075/M entrada cacheada. Coincide con el estimador existente, que no se modifica. No se verificaron tier real, impuestos, factura ni elegibilidad de caché de la cuenta. El presupuesto se aprobará en T8. | [Precios oficiales](https://ai.google.dev/gemini-api/docs/pricing) |

Las guías son específicas del modelo; una tabla genérica de GenerationConfig no garantiza que cada parámetro funcione con todos los modelos. En particular, que candidateCount aparezca en la referencia general no invalida la restricción de la guía 3.8.

La corrección de compatibilidad actual no prueba por qué las campañas anteriores devolvieron HTTP 200 ni cuánto afectó cada parámetro a sus métricas. Los cuerpos y resultados históricos permanecen inmutables. Todas las futuras ablaciones comparten el transporte corregido para evitar atribuir su efecto al prompt o filtro.

Los aliases de modelo pueden cambiar de implementación. Registrar requestedModel, modelVersion comunicada, prompt/schema SHA-256, thinking, requestVersion, límites y condiciones por brazo; no anunciar un identificador inmutable más granular si el proveedor no lo devuelve. Para comparar otro modelo hace falta un perfil y tarifa verificados por separado, sin reutilizar los límites o precios de Flash a ciegas.
