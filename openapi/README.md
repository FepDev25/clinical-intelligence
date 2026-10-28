# Contrato Clinical Extraction v0.1.0

Estado: contrato y ejemplos públicos con backend HTTP H3, proveedor simulado explícito y adaptador Gemini H4. La [guía HTTP](../docs/h3-integration.md) permite arrancarlo con fake; la [guía Gemini](../docs/h4-integration.md), con el proveedor real. El [corpus y reglas](../evaluation/README.md) son los artefactos de H1. H5 completó 234 operaciones sintéticas reales: [informe y métricas](../docs/h5-validation.md). No se aprobaron los criterios de calidad; el MVP sigue pendiente de corrección y reevaluación. El [cierre de sesión](../docs/sesion-27-10-2026/README.md) y el [índice documental](../docs/README.md) describen el estado actual.

Fuentes:

- [OpenAPI 3.1.1 en JSON](openapi.json): contrato de transporte.
- [ADR 0001](../docs/adr/0001-extraction-contract-decisions.md): decisiones D1–D11 acordadas.
- [ADR 0002](../docs/adr/0002-extraction-wire-contract.md): formalización técnica e invariantes.
- [Manifest de escenarios](examples/manifest.json): enlaza peticiones, respuestas, estados y cabeceras.

## Operación y entrada

`POST /api/v1/extractions`, cuerpo `application/json` UTF-8, credencial en `X-API-Key`. El servidor deriva consumidor y scope `extractions:write`; no hay tenant o identidad con autoridad en el body. Acceso remoto por HTTPS; HTTP solo en loopback durante desarrollo.

| Campo | Regla |
|---|---|
| `clinicalNote` | Obligatorio; texto original no vacío ni solo espacios. |
| `locale` | Opcional; `es` por defecto. Acepta BCP 47 válido cuyo idioma primario sea español, conservando el valor recibido. |
| `catalogs` | Opcional; `[]` por defecto. Cada catálogo tiene ID, versión, tipo y entradas con IDs/nombres/alias explícitos. |

Campos desconocidos, claves JSON duplicadas, Unicode inválido y `null` en campos opcionales de entrada se rechazan. La nota no se recorta ni normaliza. Una etiqueta malformada es 400; un idioma válido no soportado es 422. La declaración de localidad no detecta automáticamente el idioma de la nota.

## Respuesta y evidencia

Las colecciones son `diagnoses`, `medications` y `procedures`. `medications` incluye menciones reportadas o suspendidas, diferenciadas mediante `prescriptionStatus`; no debe interpretarse toda la colección como prescripciones activas. `performanceStatus` separa procedimientos realizados, solicitados, planificados, cancelados y no realizados.

Cada entidad conserva contexto de afirmación, temporalidad y sujeto; no sustituir estados `unknown` por valores afirmativos. Considerar una indicación vigente requiere revisar conjuntamente el contexto y el estado específico. La API no adjudica validez clínica.

Los atributos textuales incluyen `value` y `evidence`. Los opcionales de respuesta siempre están presentes, con `null` si no hay dato sustentado. Un dato ausente no genera una advertencia por campo. Un atributo propuesto que se descarta sí genera advertencia. Los estados contextuales desconocidos usan `{ "value": "unknown", "evidence": [] }`.

Cada evidencia contiene `start`, `end`, `quote`: intervalo `[start,end)` en puntos de código Unicode, base cero, sobre la nota original tras decodificar JSON. Debe cumplirse que el segmento es exactamente `quote`. Un emoji fuera del plano básico cuenta como un punto de código; una letra y su acento combinante cuentan como dos. CRLF conserva ambos puntos de código. Java y JavaScript no deben usar índices UTF-16 directamente sin conversión.

El texto de un atributo debe estar literalmente contenido en alguna de sus citas. Esto no prueba que la relación semántica sea correcta: la evaluación del modelo sigue siendo necesaria. En menciones repetidas la posición identifica la ocurrencia específica; no basta buscar siempre la primera coincidencia.

`mentionedCode` solo conserva un código literal de la nota. No genera CIE-10 ni certifica pertenencia al catálogo por aparecer en el texto.

Una respuesta incluye `reviewRequired: true`, identificador de petición, localidad efectiva y versiones de contrato, procesamiento, prompt, validación y proveedor/modelo. Los metadatos `simulated` de esos ejemplos describen propuestas controladas; el proveedor real sí está integrado desde H4 y se identifica como `provider` en sus ejecuciones.

## Catálogos

La resolución es determinista por igualdad de nombre/alias: NFC, case folding Unicode y colapso/recorte de espacios. No elimina acentos, puntuación, números, concentración o vía; no expande abreviaturas ni usa equivalencias médicas implícitas.

- `not_provided`: no llegó ningún catálogo del tipo de entidad correspondiente.
- `not_found`: hubo catálogo del tipo, pero ninguna entrada coincide; incluye catálogo explícitamente vacío.
- `matched`: exactamente una entrada coincide.
- `ambiguous`: varias entradas coinciden; se devuelven todas sin selección automática ni puntuación.

Los candidatos se referencian por `catalogId`, `version`, `entryId`, deduplicados y ordenados por esa terna. Los catálogos y sus IDs no se comparten entre peticiones. La resolución puede hacerse fuera del prompt para evitar enviar catálogos completos al proveedor.

## Vacío, descartes y errores

| Situación | Comportamiento |
|---|---|
| Proveedor terminó, salida válida, sin advertencias | 200, `completed`. Puede contener listas vacías legítimas. |
| Proveedor terminó, pero se descartaron hallazgos/atributos o hay contradicciones | 200, `completed_with_warnings`. Advertencias obligatorias, incluso si todas las entidades fueron descartadas. |
| Proveedor interrumpido, truncado o salida ilegible | Error explícito; no usar 200 con listas vacías. |

Las advertencias tienen código estable, mensaje seguro y referencias JSON Pointer a elementos devueltos. Si se descarta una entidad completa, no existe un target de respuesta: `targets: []`, `source: /clinicalNote`. Los descartes de atributo señalan el campo `null` o `unknown`; las contradicciones señalan varias entidades.

Los errores usan `application/problem+json` conforme a [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html). `type` es la URN estable del código y `instance` la URN del request ID. Los clientes procesan `code`/`type`, no el texto de `detail`. `errors` contiene ubicaciones y razones seguras, nunca valores de notas o credenciales. El mapa HTTP completo está en ADR 0002 y OpenAPI.

`X-Request-Id` coincide con `requestId`; `Cache-Control: no-store` aplica a éxito y errores. 401 incluye el desafío de API key. `Retry-After` se usa en 429/503 solo cuando la espera es conocida. El contrato no obliga a reintentar ni garantiza idempotencia facturable: repetir una extracción puede volver a consumir al proveedor.

Los errores de otros métodos sobre la ruta usan 405 y `Allow: POST`; el manifest incluye un ejemplo. Los errores emitidos por proxies ajenos al control de la aplicación pueden no tener este formato.

## Límites configurables iniciales

| Recurso | Default técnico provisional |
|---|---|
| Cuerpo HTTP sin comprimir | 64 MiB |
| Nota | 1.000.000 puntos de código |
| Catálogos | 1.000 por petición |
| Entradas de catálogos | 100.000 en total |
| Alias | 1.000.000 en total |

Estos valores figuran en `x-runtime-limits`. No son máximos inmutables de JSON Schema ni garantías de calidad. Los despliegues deben publicar sus valores efectivos y no cambiarlos silenciosamente. Cada exceso produce 413 sin truncamiento. El límite de bytes se mide sobre el body HTTP real, no sobre una reserialización que puede cambiar espacios o escapes.

Solo se admite `Content-Encoding` ausente o `identity` en v0.1. Una entrada puede estar dentro de estos máximos y exceder el contexto del proveedor; en ese caso, 422 `PROVIDER_CAPACITY_EXCEEDED`. No hay fragmentación implícita. Los rangos recomendados se establecerán con la evaluación; los presupuestos operativos del proveedor se formalizaron en [H4](../docs/h4-integration.md). H5 solo midió notas breves y no aprobó calidad; no se recomienda todavía una frontera de tamaño.

## Ejemplos públicos

Cada archivo de ejemplo es un OpenAPI Example Object con `summary` y `value`. El body HTTP es **solo `value`**. El manifest agrupa 29 escenarios con 58 archivos de petición/respuesta:

| Escenario | Petición | Respuesta |
|---|---|---|
| Éxito y coincidencia única | [success-request](examples/success-request.json) | [success-response](examples/success-response.json) |
| Vacío válido | [empty-request](examples/empty-request.json) | [empty-response](examples/empty-response.json) |
| Atributo descartado | [warnings-request](examples/warnings-request.json) | [warnings-response](examples/warnings-response.json) |
| Catálogo ambiguo | [ambiguous-catalog-request](examples/ambiguous-catalog-request.json) | [ambiguous-catalog-response](examples/ambiguous-catalog-response.json) |
| Negaciones, antecedentes y suspensión | [context-request](examples/context-request.json) | [context-response](examples/context-response.json) |
| Unicode, CRLF y ocurrencias repetidas | [unicode-request](examples/unicode-request.json) | [unicode-response](examples/unicode-response.json) |
| Todos los hallazgos descartados | [all-discarded-request](examples/all-discarded-request.json) | [all-discarded-response](examples/all-discarded-response.json) |
| Contradicción | [contradiction-request](examples/contradiction-request.json) | [contradiction-response](examples/contradiction-response.json) |
| Código literal y catálogo vacío | [unmatched-request](examples/unmatched-request.json) | [unmatched-response](examples/unmatched-response.json) |
| Procedimiento realizado, sospecha y medicación reportada | [performed-and-reported-request](examples/performed-and-reported-request.json) | [performed-and-reported-response](examples/performed-and-reported-response.json) |
| Validación inválida | [error-invalid-request-request](examples/error-invalid-request-request.json) | [error-invalid-request](examples/error-invalid-request.json) |
| Autenticación | [error-invalid-api-key-request](examples/error-invalid-api-key-request.json) | [error-invalid-api-key](examples/error-invalid-api-key.json) |
| Proveedor malformado | [error-provider-invalid-response-request](examples/error-provider-invalid-response-request.json) | [error-provider-invalid-response](examples/error-provider-invalid-response.json) |
| Timeout | [error-provider-timeout-request](examples/error-provider-timeout-request.json) | [error-provider-timeout](examples/error-provider-timeout.json) |

Los restantes códigos de error tienen ejemplos enlazados desde OpenAPI y el manifest. Los escenarios de transporte/proveedor son ilustrativos; no se provocaron fallos reales. El caso de JSON malformado incluye `rawRequest` en el manifest porque no puede representarse como un objeto JSON válido.

Ejemplo de consumo con el backend local H3; requiere `jq`, cURL y una credencial configurada fuera de Git según la guía de integración:

```sh
jq '.value' openapi/examples/success-request.json | curl \
  --url http://localhost:8080/api/v1/extractions \
  --header 'Content-Type: application/json' \
  --header 'Accept: application/json, application/problem+json' \
  --header "X-API-Key: ${CI_API_KEY}" \
  --data-binary @-
```

Usar solo datos sintéticos. En H3 el fake de arranque devuelve vacío explícito; los ejemplos con entidades se reproducen mediante propuestas controladas en pruebas. El JAR se verificó además con cURL externo en loopback; evidencia en el [informe H3](../docs/h3-validation.md).

## Validación reproducible de artefactos

Desde la raíz del proyecto, usando un entorno Python temporal:

```sh
python3 -m venv /tmp/clinical-intelligence-contract-venv
/tmp/clinical-intelligence-contract-venv/bin/python -m pip install -r openapi/tools/requirements.txt
/tmp/clinical-intelligence-contract-venv/bin/python openapi/tools/validate_contract.py
```

Usar otra ruta temporal si esa ya pertenece a otro trabajo. La instalación requiere red; la validación, una vez instaladas las dependencias, no llama al proveedor ni descarga esquemas remotos. Las dependencias son herramientas de validación, no dependencias del producto Java.

El comando verifica OpenAPI, esquemas, ejemplos, referencias, localización, evidencia Unicode, candidatos, IDs, advertencias y cabeceras; además exige rechazo de 30 mutaciones inválidas y comprueba fronteras de normalización y límites de estructura configurables. Detecta también la regresión de nombres de campos como `procedureCode` frente al esquema acordado.

La validación no demuestra extracción semántica, autenticación efectiva, ausencia de logs en un backend, comportamiento HTTP, límite real de bytes de transporte ni rendimiento. La implementación y evidencia de esas comprobaciones están en los informes [H2](../docs/h2-validation.md), [H3](../docs/h3-validation.md), [H4](../docs/h4-validation.md) y [H5](../docs/h5-validation.md). Estos ejemplos son datos de desarrollo públicos y nunca deben presentarse como evaluación reservada.
