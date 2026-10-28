# ADR 0002 — Formalización técnica de extracción v0.1

> Registro histórico del hito indicado: sus cifras y pendientes describen ese momento. Estado actual: H1–H4 verificados, H5 evaluado y MVP no aprobado. Consultar [H5](../h5-validation.md), [cierre de sesión](../sesion-27-10-2026/README.md) e [índice documental](../README.md).

Fecha: 2026-10-27

Estado: decidido para el contrato v0.1.0, por autorización de formalizar H1 pasos 1–3. No implica implementación ni evaluación ejecutada.

Referencias: [decisiones acordadas](0001-extraction-contract-decisions.md), [OpenAPI](../../openapi/openapi.json), [guía contractual](../../openapi/README.md).

## Contexto y alcance

La ronda D1–D11 dejó acordado el comportamiento del producto. Este ADR concreta los detalles necesarios para OpenAPI y ejemplos. No fija proveedor ni implementa la API. El corpus reservado y sus reglas están ahora en [evaluation](../../evaluation/README.md); los ejemplos contractuales son públicos y reutilizables en desarrollo, nunca evaluación reservada.

## Decisiones

### Transporte y evolución

OpenAPI 3.1.1 en JSON, con esquemas JSON Schema 2020-12 y versión de contrato `0.1.0`. JSON evita un parser YAML adicional para leer los ejemplos y el contrato. Se usan referencias internas para esquemas y referencias a archivos Example Object para ejemplos, validables de forma reproducible. La estructura pública no depende de Spring AI ni de un proveedor.

`POST /api/v1/extractions` recibe JSON con `clinicalNote` obligatorio, `locale` opcional por defecto `es` y `catalogs` opcional por defecto `[]`. `null` no sustituye la omisión de estos campos opcionales. Se rechazan campos desconocidos y claves JSON duplicadas. La nota no puede estar vacía ni contener únicamente espacios Unicode; no se recorta ni normaliza antes de extraer. Se rechazan secuencias Unicode inválidas, incluidos sustitutos aislados.

La respuesta usa `diagnoses`, `medications` y `procedures`. `medications` conserva menciones históricas, suspendidas y ambiguas con `prescriptionStatus`, sin llamarlas todas prescripciones. Cada entidad tiene un ID único local a la respuesta, nombre textual respaldado, código mencionado opcional, contexto y resolución de catálogo. No hay clasificación implícita ni códigos inferidos.

Los campos de respuesta declarados son obligatorios; los desconocidos usan `null` cuando el esquema lo permite, o el estado `unknown`. Las colecciones vacías usan `[]`. Los textos humanos se documentan en español y los nombres/enums/códigos de máquina en inglés. El cliente no debe interpretar mensajes humanos como códigos estables.

### Evidencia y contexto

Cada evidencia contiene `start`, `end` y `quote`. Los índices son puntos de código Unicode, base cero, inicio incluido y fin excluido, sobre `clinicalNote` tras decodificar JSON. No son bytes, grafemas ni unidades UTF-16. Debe cumplirse `0 <= start < end <= longitud` y el segmento exacto debe ser igual a `quote`. No normalizar Unicode ni saltos de línea. Los ejemplos cubren CRLF, emoji, tildes compuestas y descompuestas, y fragmentos repetidos.

Java deberá convertir explícitamente desde puntos de código cuando use offsets UTF-16; JavaScript puede segmentar por puntos de código. La conversión es responsabilidad del adaptador/validador y no del consumidor clínico. El modelo no es autoridad sobre las posiciones: el backend debe comprobarlas y rechazar asociaciones ambiguas, sin asignar automáticamente la primera aparición de una cita repetida.

Los atributos textuales tienen `{value, evidence}`; `value` conserva el texto extraído y debe aparecer literalmente dentro de al menos una evidencia asociada. Dosis, frecuencia, vía y duración se conservan como texto, sin convertir concentraciones, unidades o fechas a valores inferidos. Una evidencia literal no demuestra asociación clínica correcta; eso se evalúa semánticamente.

`context` separa `assertion` (`affirmed`, `negated`, `suspected`, `conditional`, `unknown`), `temporality` (`current`, `historical`, `future`, `unknown`) y `subject` (`patient`, `family`, `other`, `unknown`). Cada valor conocido requiere evidencia propia; `unknown` lleva evidencia vacía. No significa dato negativo ni afirmativo.

Medicamentos agregan `prescriptionStatus`: `prescribed`, `reported`, `suspended` o `unknown`. Procedimientos agregan `performanceStatus`: `performed`, `ordered`, `planned`, `not_performed`, `cancelled` o `unknown`. El estado de procedimiento representa su realización, no el éxito o fracaso clínico de su resultado. Una tentativa no claramente realizada queda `unknown` con advertencia; no se fuerza a `performed`.

Un estado conocido cuyo respaldo se descarta se reemplaza por `unknown` con advertencia, sin eliminar necesariamente la entidad. La ausencia legítima de información no genera advertencias por campo. La entidad sin nombre/evidencia válidos se descarta por completo. Duplicados equivalentes pueden agruparse con todas sus evidencias; contradicciones se mantienen como entidades distintas y se enlazan mediante advertencia.

### Catálogos

Cada catálogo tiene `catalogId`, `version`, `kind` y `entries`. `kind` es `diagnosis`, `medication` o `procedure`. Cada entrada tiene `entryId`, `name` y `aliases` opcional por defecto `[]`. Los IDs son cadenas opacas, no UUIDs obligatorios. No hay IDs de tenant ni búsquedas externas.

`catalogId` es único por petición; `entryId` es único dentro de un catálogo. Un catálogo vacío explícito cuenta como suministrado, mientras que ausencia de catálogos del tipo correspondiente produce `not_provided`. La respuesta referencia candidatos por la terna `catalogId`, `version`, `entryId`.

La comparación aplica NFC, Unicode case folding independiente de localidad, recorte de espacios Unicode y colapso de secuencias de espacios a un espacio. Conserva tildes, puntuación, números y unidades; no aplica NFKC, eliminación de acentos, expansión de abreviaturas ni equivalencias médicas. Se compara igualdad completa del nombre extraído con nombre o alias, solo en catálogos del mismo tipo.

Se deduplican coincidencias a una misma entrada y se ordenan las ternas lexicográficamente por puntos de código. `matched` exige un candidato, `ambiguous` al menos dos y `not_found`/`not_provided` ninguno. Nunca seleccionar, ordenar por confianza ni truncar candidatos. `mentionedCode` es texto literal no validado por sí mismo; la resolución por nombre/alias no certifica que ese código pertenezca al catálogo ni que sea clínicamente correcto.

Los catálogos pueden resolverse después de la extracción sin enviarlos completos al LLM; su tamaño no exige ampliar el prompt del modelo. Si el adaptador decide transmitir algún dato del catálogo, deberá mantenerlo como datos no confiables y respetar límites de contexto.

### Estado, advertencias y versiones

`processingStatus` es `completed` si no hay advertencias o `completed_with_warnings` si las hay. Ambas formas requieren finalización del proveedor y estructura válidas. Un truncamiento o respuesta ilegible produce error global.

Una respuesta sin entidades y sin descartes es un vacío válido. Una sin entidades pero con descartes mantiene `completed_with_warnings` y las advertencias; no se representa como vacío limpio. Esto no promete que el modelo haya encontrado todas las entidades de la nota.

Las advertencias usan códigos estables, un mensaje seguro, referencias JSON Pointer a elementos devueltos (`targets`) y una ubicación de entrada opcional (`source`). Un descarte completo usa `targets: []` y `source: /clinicalNote`, sin inventar un ID de entidad eliminada. Las contradicciones pueden señalar múltiples entidades. No se expone salida cruda descartada.

Se incluyen `requestId` generado en servidor, `reviewRequired: true`, localidad efectiva, versión de contrato, versión de procesamiento, prompt, validación y proveedor/modelo. Los ejemplos usan un proveedor simulado declarado; sus versiones no son una selección de proveedor real. El cuerpo y `X-Request-Id` coinciden. `Cache-Control: no-store` se aplica a éxito y error.

### Autenticación

Cabecera única `X-API-Key`, representada como esquema OpenAPI `apiKey`. El scope `extractions:write` se documenta como autorización del servidor, no como scope OAuth2 en el Security Requirement Object de una API key.

Las credenciales se aprovisionan fuera de la API, con entropía criptográfica; no se incluyen claves reales en ejemplos. La configuración mapea verificadores de claves a identidad y scopes; no almacenar secretos en claro cuando sea posible usar verificadores. Se permiten claves antigua/nueva simultáneas para rotación. Una instantánea de configuración se valida al arrancar; una clave revocada deja de aceptarse cuando cada instancia carga la nueva configuración mediante reinicio. Una rotación con reinicio progresivo debe documentar ese intervalo; no se promete revocación global instantánea.

Una instancia no arranca con credenciales ambiguas/duplicadas, sin configuración requerida o con validadores inseguros por defecto. Autenticación y autorización preceden al procesamiento clínico y a la llamada al modelo; un límite de transporte puede rechazar antes por tamaño sin procesar la nota.

### Localidad

Validar primero sintaxis y subtags BCP 47; después aceptar etiquetas cuyo idioma primario sea `es`, sin distinguir mayúsculas para la aceptación. Conservar el valor explícito exacto en `locale` de respuesta. `es`, `es-EC`, `es-419` y `es-MX` son ejemplos; una regex de esquema es solo filtro sintáctico inicial, no reemplaza la validación BCP 47. No convertir `es_EC` silenciosamente ni asumir región si solo se envía `es`.

### Límites técnicos iniciales

Son defaults configurables por despliegue, no límites de calidad demostrados ni máximos inmutables del esquema:

| Configuración conceptual | Default inicial |
|---|---|
| Cuerpo HTTP sin comprimir | 67.108.864 bytes (64 MiB) |
| Nota original | 1.000.000 puntos de código |
| Catálogos por petición | 1.000 |
| Entradas sumadas de todos los catálogos | 100.000 |
| Alias sumados de todas las entradas | 1.000.000 |

Los límites de cuerpo aplican a los bytes UTF-8 transferidos sin comprimir; en v0.1 no se acepta `Content-Encoding` distinto de ausente o `identity`. Las cotas de estructura y nota se aplican después de decodificar. El tamaño del body incluye escapes y metadatos JSON. Si se excede cualquiera de estas cotas configuradas se responde 413, sin recortes.

El presupuesto de contexto del proveedor se comprueba aparte antes de invocarlo; si no cabe la entrada completa, 422 `PROVIDER_CAPACITY_EXCEEDED`. No se introduce fragmentación implícita. Presupuesto de tiempo, concurrencia y reintentos pertenecen a los controles operativos del adaptador en H4 y deben cerrarse antes de activarlo. 429 y 503 tienen contrato desde H1 aunque su configuración operativa se implemente después.

### Errores

Problem Details se devuelve con `type` absoluto URN estable derivado de `code`, `title`, `status`, `detail`, `instance` URN de la petición, `requestId`, `code` y `errors` (detalles seguros por campo). `errors` no contiene valores rechazados. Las URN evitan inventar un dominio comercial; no requieren endpoints consultables.

| HTTP | Códigos de problema |
|---|---|
| 400 | `INVALID_REQUEST`, `MALFORMED_JSON`, `INVALID_LOCALE` |
| 401 | `INVALID_API_KEY` (ausente, inválida o revocada sin revelar cuál) |
| 403 | `INSUFFICIENT_SCOPE` |
| 406 | `NOT_ACCEPTABLE` |
| 413 | `INPUT_LIMIT_EXCEEDED` |
| 415 | `UNSUPPORTED_MEDIA_TYPE`, `UNSUPPORTED_CONTENT_ENCODING` |
| 422 | `UNSUPPORTED_LOCALE`, `PROVIDER_CAPACITY_EXCEEDED` |
| 429 | `CONSUMER_RATE_LIMITED` |
| 500 | `INTERNAL_ERROR` |
| 502 | `PROVIDER_INVALID_RESPONSE`, `PROVIDER_TRUNCATED_RESPONSE` |
| 503 | `SERVICE_OVERLOADED`, `PROVIDER_UNAVAILABLE` |
| 504 | `PROVIDER_TIMEOUT` |

401 incluye `WWW-Authenticate: ApiKey realm="clinical-intelligence"`. `Retry-After` en segundos solo se emite en 429/503 cuando hay una espera conocida; su ausencia no autoriza reintentos ilimitados. 405 puede ser generado por el router con `Allow: POST`; se mantiene el mismo formato de error en rutas de API controladas por la aplicación. Errores de proxies fuera de nuestro control se documentan como límite del contrato.

## Consecuencias y alternativas

Los objetos con evidencia por atributo aumentan el tamaño, pero evitan atribuir respaldo a campos no sustentados. Las tres dimensiones de contexto permiten combinaciones que un único enum perdería; las pruebas semánticas deberán comprobar coherencia entre ellas y los estados específicos.

Puntos de código son independientes de la codificación y precisan adaptación explícita en Java/JavaScript. Usar UTF-16 favorecería esos entornos, pero haría menos natural la interoperabilidad con consumidores que cuentan puntos de código.

Los máximos iniciales altos permiten explorar el rango solicitado sin confundir aceptación con calidad. El presupuesto real de un proveedor puede ser menor y debe producir error visible.

## Referencias técnicas

- [OpenAPI 3.1.1](https://spec.openapis.org/oas/v3.1.1.html).
- [RFC 9457 — Problem Details](https://www.rfc-editor.org/rfc/rfc9457.html).
- [RFC 5646 — BCP 47](https://www.rfc-editor.org/rfc/rfc5646.html).

## Verificación y pendientes

El contrato y los ejemplos deben validarse estructuralmente y mediante invariantes cruzadas que JSON Schema no expresa (spans, IDs, catálogos, estados y cabeceras). Los controles de esta etapa verifican artefactos contractuales; no equivalen a pruebas HTTP de un backend existente.

H1 cuenta con corpus sintético, reglas de anotación y [verificación conjunta](../../evaluation/VALIDATION.md). La selección del proveedor y sus condiciones, el rendimiento real y la evaluación de calidad siguen pendientes de las etapas correspondientes.
