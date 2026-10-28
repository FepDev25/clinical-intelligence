# H4 — Gemini: configuración, ejecución y límites

El adaptador usa Gemini Developer API con `gemini-3.8-flash`. La elección está documentada en [ADR 0005](adr/0005-gemini-provider-h4.md). No se usa OpenRouter, no hay fallback al fake y el arranque no llama al modelo.

## Clave y archivo local

La variable se llama **`GOOGLE_API_KEY`**. La aplicación puede recibirla del entorno o de un archivo cargado explícitamente por Spring Config. Spring Boot no carga automáticamente un `.env`; el agente no necesita abrirlo, copiarlo ni mostrar su contenido.

El archivo existente de la raíz está ignorado por Git, junto con sus variantes. Las pruebas ordinarias no lo importan y bloquean tráfico externo: solo permiten loopback. No se incluyen claves reales en ejemplos, argumentos de comandos, logs o artefactos de evaluación.

Si se importa como properties, el formato es `GOOGLE_API_KEY=valor`, sin `export`. Se permiten comillas simples o dobles que rodeen todo el valor. No ejecutar `cat .env`, `printenv`, trazas de shell o comandos que impriman variables para verificar la clave.

## Facturación y alcance

La suscripción de Google de unos $20 y el nivel de facturación de Gemini API son productos distintos: comprobar la columna Billing Tier del proyecto en AI Studio. La API pagada requiere una cuenta de Cloud Billing vinculada y activa; según el plan, saldo prepago disponible. [Documentación oficial](https://ai.google.dev/gemini-api/docs/billing).

El usuario declaró facturación habilitada y autorizó la elección del proveedor y su clave. En H4 se verificó una extracción sintética real, limitada a una operación y 2.048 tokens de salida. Después H5 completó 234 operaciones con el máximo configurado de 65.536; [la evaluación](h5-validation.md) no aprobó calidad. El nivel efectivo de cuenta sigue sin verificarse: ni la declaración ni un HTTP200 demuestran Paid/Tier1. Las pruebas con respuestas controladas tampoco lo establecen.

Los servicios gratuitos y pagados tienen condiciones de datos diferentes; pagar no significa retención cero. La API nueva de Interactions almacena interacciones por defecto; este adaptador usa `generateContent`, plenamente soportada, sin historial ni caché explícita. Tampoco se afirma ausencia de registros de abuso o retención externa.

Los términos actuales de Developer API excluyen práctica clínica y asesoramiento médico. Esta entrega se limita a desarrollo/evaluación con **datos sintéticos**, sin afirmar permiso para despliegue clínico real. [Términos](https://ai.google.dev/gemini-api/terms).

## Arrancar

Primero generar las credenciales de consumidor externas siguiendo la [guía HTTP](h3-integration.md). Son distintas de la clave de Google: `CI_API_KEY` autentica al consumidor de nuestra API; `GOOGLE_API_KEY` autentica a nuestra aplicación ante Google.

```sh
mvn verify

export CI_CREDENTIALS_FILE=/ruta/externa/credentials.json
export CI_PROVIDER_MODE=gemini

java -jar target/clinical-intelligence-0.1.0-SNAPSHOT.jar \
  '--spring.config.import=optional:file:.env[.properties]'
```

El archivo se carga dentro del proceso de la aplicación, sin imprimir su contenido. Si `GOOGLE_API_KEY` ya está en el entorno, se puede omitir `spring.config.import`. Una clave ausente, configuración inválida o modo desconocido impide el arranque; nunca activa el fake silenciosamente.

El backend continúa en loopback por defecto. En otra terminal, con la credencial de consumidor configurada fuera de Git:

```sh
curl --url http://127.0.0.1:8080/api/v1/extractions \
  --header 'Content-Type: application/json' \
  --header 'Accept: application/json, application/problem+json' \
  --header "X-API-Key: ${CI_API_KEY}" \
  --data-binary '{"clinicalNote":"Diagnóstico actual del paciente: asma.","locale":"es-EC"}'
```

Este comando **sí envía la nota a Google** y puede generar coste. Usar solo la nota sintética y un presupuesto autorizado. Una ejecución real debe identificar `processing.executionMode=provider` y `provider=google-gemini`; el resultado seguirá requiriendo revisión humana. No se utiliza el conjunto reservado en H4.

## Presupuestos operativos

| Variable | Inicial |
| --- | --- |
| `CI_GEMINI_DEADLINE_MS` | 120.000 ms para toda la operación. |
| `CI_GEMINI_CONNECT_TIMEOUT_MS` | 10.000 ms, nunca mayor que el deadline. |
| `CI_GEMINI_OUTPUT_TOKENS` | 65.536 tokens reservados de salida, máximo publicado del modelo. |
| `CI_GEMINI_CONCURRENCY` | 2 operaciones de proveedor por instancia, sin espera en cola al saturar. |
| `CI_GEMINI_RESPONSE_BYTES` | 16.777.216 bytes por respuesta de Google, incluidos errores. |

Los enteros deben ser positivos; deadline máximo configurable de 30 minutos y concurrencia hasta 10.000. Son cotas administrativas de configuración, no capacidad productiva medida. Se conservan los límites de entrada altos de H1/H3.

Cada extracción admite **dos llamadas como máximo: conteo y generación; cero reintentos**. El conteo contiene la petición completa, instrucciones y esquema. Entrada más reserva de salida debe caber en 1.048.576 tokens; de lo contrario se devuelve 422 `PROVIDER_CAPACITY_EXCEEDED`. No hay recortes ni fragmentación implícita.

Un timeout cancela las operaciones locales pendientes. La capacidad permanece ocupada hasta que el trabajador salga realmente, incluso durante la limpieza por cancelación; no se crean trabajadores ilimitados. Cancelar localmente no garantiza que Google detenga una generación ya recibida ni que no facture su trabajo.

No se configura una cuota comercial por consumidor en este incremento. Un 429 de Google se traduce a indisponibilidad del proveedor, no a `CONSUMER_RATE_LIMITED`, que corresponde a una cuota propia del consumidor y no se presenta como implementada.

## Finalización y evidencia

Solo una respuesta completa, de un candidato, con `finishReason=STOP`, texto no vacío y estructura localmente válida puede convertirse en propuesta. `MAX_TOKENS` produce truncamiento explícito; bloqueos, falta de contenido, códigos de error, JSON inválido y avisos con referencias inválidas no se convierten en vacío exitoso.

El modelo proporciona citas exactas y ocurrencias explícitas. El adaptador convierte esas posiciones a puntos de código sobre la nota original; H2 verifica citas y atributos, descarta propuestas sin respaldo y resuelve catálogos localmente. Los catálogos no se envían a Google. La validez literal/estructural no garantiza interpretación clínica ni resistencia semántica a prompt injection; H5 medirá esas propiedades.

## Observabilidad y coste

El logger `clinical.provider.metrics` emite únicamente modelo/configuración, versiones, estado técnico, latencia, conteos de tokens disponibles y coste estimado. No incluye nota, prompt, respuesta, pensamientos, clave o mensajes crudos de Google. No se habilitan trazas de transporte.

El coste estimado usa la tarifa estándar publicada el 2026-10-27 para Gemini 3.8 Flash hasta 2026-12-31: USD 0,75/M tokens de entrada no cacheados, USD 0,075/M de entrada cacheada y USD 3,75/M de salida, incluyendo pensamientos. Se calcula únicamente si están presentes todos los conteos necesarios; ausencias quedan desconocidas (`null`), no cero. La tarifa está identificada; después de su vigencia el coste queda desconocido hasta actualizarlo. No es una factura ni estima cuotas gratuitas, descuentos o cargos de otros servicios. [Tarifas](https://ai.google.dev/gemini-api/docs/pricing).

## Verificación reproducible

`mvn verify` ejecuta núcleo, contrato HTTP, arranque y adaptador con un servidor de Google simulado en loopback. Comprueba orden de conteo/generación, capacidad, bytes, timeout compartido, saturación, cancelación, finalización, redacción de errores y versiones. No importa el `.env` ni hace llamadas autenticadas a Google.

Estado y evidencia: [informe de H4](h4-validation.md). H4 tiene una prueba real mínima aprobada; H5 añadió total de tokens y versión efectiva sanitizada a la telemetría interna. La [evaluación H5](h5-validation.md) completó las rondas reales y no aprobó la calidad del MVP. Google devolvió el alias del modelo, sin una versión inmutable más granular.

