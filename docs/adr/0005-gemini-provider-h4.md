# ADR 0005 — Gemini Developer API para H4

> Registro histórico del hito indicado: sus cifras y pendientes describen ese momento. Estado actual: H1–H4 verificados, H5 evaluado y MVP no aprobado. Consultar [H5](../h5-validation.md), [cierre de sesión](../sesion-27-10-2026/README.md) e [índice documental](../README.md).

Fecha: 2026-10-27. Estado: implementación y prueba sintética mínima autorizadas con Gemini 3.8 Flash; el usuario declara facturación habilitada. Solo datos sintéticos. El nivel efectivo de la cuenta no se considera verificado por esa declaración.

## Elección y alternativas

El usuario eligió Gemini frente a OpenRouter/DeepSeek y configuró `GOOGLE_API_KEY` en un `.env` que el agente no debe abrir ni mostrar. Se ignoran `.env` y variantes en Git. Las pruebas ordinarias no importan ese archivo ni requieren claves; únicamente el arranque explícito de la aplicación puede importarlo mediante Spring Config, sin exponer su contenido al agente.

Modelo inicial fijo: `gemini-3.8-flash`. Sin failover, grounding, herramientas, archivos remotos, sesiones ni fragmentación. Se usa JSON Schema nativo, además de validación local de la respuesta y políticas de H2.

Se fija `thinkingLevel=LOW`, sin devolver pensamientos, y temperatura 1.0. Modelo/prompt/esquema/parámetros deben registrarse al evaluar; un alias estable no garantiza que el proveedor nunca actualice sus pesos. Si se comunica una versión concreta válida en la respuesta, se conserva como modelo efectivo del resultado.

La documentación actual recomienda Interactions para proyectos nuevos y conserva `generateContent` plenamente soportada. Interactions almacena solicitudes por defecto y requiere `store=false` para evitar ese almacenamiento. Elegimos `generateContent` para esta operación independiente de una sola nota: su respuesta expone finalización y uso, permite `countTokens` y no necesita objetos de interacción persistentes. Es una elección deliberada de una API soportada; no implica que la retención externa sea cero.

Se usa un adaptador REST con HttpClient de Java 21, en lugar de incorporar ahora Spring AI y un SDK con reintentos propios. Esto permite acotar deadline, bytes de respuesta, cancelación, número de llamadas y traducción de errores explícitamente. El puerto interno mantiene intercambiable esta decisión; el coste es mantener el mapeo de transporte de Google. No se alteran DTOs públicos para acomodar al modelo. NetworkNT pasa de test-only a runtime para verificar el esquema interno, no para sustituir las políticas clínicas.

## Evidencia y prompt

Prompt y esquema internos versionados. La nota original y localidad se envían como un objeto JSON de datos en el mensaje de usuario, separado de instrucciones de sistema. Los catálogos permanecen locales. El modelo devuelve citas exactas y una ocurrencia explícita base cero; el adaptador convierte esa referencia a puntos de código sobre la nota original, sin elegir automáticamente la primera coincidencia. Una cita/ocurrencia inválida llega a H2 como evidencia inválida y se descarta con advertencia. Los contextos deben citar la cláusula o bloque del medicamento que respalde sus atributos; no se infiere por proximidad.

Se valida toda la estructura antes de aceptar propuestas. Campos inesperados, JSON duplicado/malformado, salida vacía, bloqueo, rechazo, varios candidatos o ausencia de finalización no se convierten en un éxito vacío. `MAX_TOKENS` se traduce en truncamiento explícito. La protección frente a prompt injection combina separación de datos, ausencia de herramientas y validación; su eficacia semántica se medirá en H5, no se presume por el prompt.

## Presupuestos iniciales

Se mantienen los límites de entrada altos de H1/H3. Antes de generar se cuenta la petición completa con `countTokens`. La entrada debe caber junto con la reserva de salida en 1.048.576 tokens. Reserva inicial configurable de 65.536 tokens de salida (máximo publicado), sin recorte. Incompatibilidad de capacidad: 422 `PROVIDER_CAPACITY_EXCEEDED`.

Deadline total inicial 120 s, conexión 10 s, dos llamadas máximas (conteo + generación), cero reintentos y dos operaciones de modelo concurrentes por instancia, todo configurable dentro de los máximos publicados. Se rechaza saturación con 503; no hay cola ilimitada. La concurrencia se liberará también en timeout, cancelación y fallos. Configuración requiere al menos una llamada de conteo y una de generación; no se repara JSON mediante una llamada adicional oculta. Las cotas son exploratorias, no capacidad de producción demostrada.

Se observan estado técnico, latencia, versiones, modelo y tokens/coste estimado cuando existan. Datos de uso ausentes permanecen desconocidos, nunca cero. No se registran notas, prompts, respuestas, razonamiento, claves ni mensajes crudos del proveedor. La tarifa usada debe quedar identificada y tener vigencia; no constituye garantía del cargo real.

## Datos y facturación

La suscripción de Google no confirma por sí sola el nivel de Gemini API. El usuario debe comprobar el proyecto en AI Studio. El uso de servicios pagados y gratuitos tiene condiciones de datos diferentes. Pagar no implica retención cero. Los términos actuales de Developer API prohíben uso en práctica clínica/asesoramiento médico: esta integración se limita al desarrollo y evaluación sintética; no afirma autorización para despliegue clínico real.

## Fuentes oficiales revisadas

- [Entrada solicitada por el usuario](https://aistudio.google.com/docs).
- [Gemini 3.8 Flash: límites, thinking y estructura](https://ai.google.dev/gemini-api/docs/models/gemini-3.8-flash).
- [Salida estructurada de generateContent](https://ai.google.dev/gemini-api/docs/generate-content/structured-output).
- [Generación y finalización](https://ai.google.dev/api/generate-content).
- [Conteo de tokens](https://ai.google.dev/api/tokens).
- [Interactions y almacenamiento](https://ai.google.dev/gemini-api/docs/interactions-overview).
- [Facturación](https://ai.google.dev/gemini-api/docs/billing), [tarifas](https://ai.google.dev/gemini-api/docs/pricing) y [condiciones de datos/uso](https://ai.google.dev/gemini-api/terms).
