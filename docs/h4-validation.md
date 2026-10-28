# H4 — evidencia de implementación y verificación

> Registro histórico del hito indicado: sus cifras y pendientes describen ese momento. Estado actual: H1–H4 verificados, H5 evaluado y MVP no aprobado. Consultar [H5](h5-validation.md), [cierre de sesión](sesion-27-10-2026/README.md) e [índice documental](README.md).

Fecha: 2026-10-27. Estado: H4 implementado y verificado con transporte controlado y una extracción sintética autenticada. Revisión independiente completada y dos incidencias importantes corregidas mediante TDD. No constituye evaluación de calidad clínica de H5.

## Cambios

- [GeminiProtocol](../src/main/java/com/clinicalintelligence/extraction/infrastructure/gemini/GeminiProtocol.java): prompt/esquema versionados, salida estructurada, finalización y citas con ocurrencia explícita.
- [GeminiProvider](../src/main/java/com/clinicalintelligence/extraction/infrastructure/gemini/GeminiProvider.java): conteo completo antes de generar, deadline compartido, respuesta acotada, cancelación, concurrencia y observaciones seguras.
- [Configuración](../src/main/java/com/clinicalintelligence/http/ApiConfiguration.java): modo `gemini` explícito, `GOOGLE_API_KEY`, límites configurables y cierre de recursos. Modo simulado preservado y nunca activado como fallback.
- [Puertos/casos de uso](../src/main/java/com/clinicalintelligence/extraction/application): traducción tipada de capacidad, sobrecarga y fallos sin filtrar mensajes del proveedor. El contrato HTTP v0.1.0 se mantiene.
- [Pruebas](../src/test/java/com/clinicalintelligence/extraction/infrastructure/gemini), [aislamiento de red](../src/test/java/com/clinicalintelligence/testing/NetworkIsolation.java), recursos del prompt/esquema y exclusión de `.env`/variantes de Git.

## Evidencia ejecutada

`mvn -B -ntp -o verify`: BUILD SUCCESS, 133 pruebas Java sin fallos/errores/omitidas; JAR ejecutable construido. La suite aísla la configuración antes de bootstrap y restringe las rutas de HttpClient a loopback; no importa el `.env` del usuario. Este guard no es un sandbox universal para cualquier socket de la JVM.

Se verificaron separación de instrucciones/datos, esquema nativo/local, datos ausentes, JSON duplicado/malformado, finalización, truncamiento, bloqueo, pensamientos excluidos, varios candidatos, Unicode/CRLF, citas repetidas, índices excesivos y un ejemplo público con las tres familias atravesando adaptador y núcleo.

Herramientas Python: 32 pruebas aprobadas; OpenAPI válido (21 esquemas, 29 escenarios, 58 ejemplos; 30 mutaciones rechazadas); 39 casos de desarrollo válidos con hashes conservados. El JAR contiene prompt/esquema y el validador runtime, excluyendo corpus, tests y archivos dotenv. El arranque del JAR con un archivo de configuración sintético externo verificó importación explícita y rechazo de entrada inválida antes del proveedor; proceso y credenciales ficticias se eliminaron después.

El servidor local de pruebas comprueba conteo antes de generación, máximo dos llamadas, errores HTTP sin reintentos, límite real de respuesta, timeout con cuerpo lento, deadline compartido, saturación sin cola, liberación tras cancelación y rechazo de redirecciones que podrían reenviar la clave. El arranque con clave ficticia no llama a Google; la ausencia de clave o cierre del contexto falla antes de trabajar. Las observaciones distinguen uso desconocido de cero e incluyen pensamientos en el coste cuando el uso es conocido.

TDD y ajustes de pruebas están registrados en [h4-progress](h4-progress.md). Se reprodujeron y corrigieron, entre otros, pérdida de códigos operativos, falta de soporte de arranque, cierre de recursos, wrapping de índices y sustitución silenciosa de Unicode inválido. Una sonda inicial de ciclo de vida pudo alcanzar el endpoint real con una clave ficticia y sin resultado autenticado; se reemplazó y se añadió el guard de red para impedirlo en las rutas de la suite ordinaria.

## Revisión independiente

Sin hallazgos críticos. Dos hallazgos importantes se reprodujeron antes de corregirse: una ocurrencia mayor que long podía convertirse en cero, y las propiedades por defecto de las pruebas permitían importar configuración heredada del shell. Se comprueba representabilidad y se preserva precisión decimal; se aíslan las fuentes de propiedades antes de ConfigData. La suite de 133 pruebas incluye esas regresiones, evidencia de nombres/atributos e importación canario exclusivamente sintética.

Pendiente menor: las observaciones registran el alias configurado, mientras que la versión efectiva sanitizada se conserva en `processing.model` de la respuesta pública. Añadir ambas versiones a observabilidad facilitará correlacionar consumo con cambios del proveedor. No se introdujo este ajuste en el pase de correcciones de incidencias importantes.

## Prueba sintética autenticada

El usuario declaró facturación habilitada, eligió el proveedor y provisionó su clave. Se ejecutó una sola extracción con conteo + generación, cero reintentos, deadline 60 s y salida máxima de 2.048 tokens. La aplicación cargó el `.env` explícitamente; ni el agente ni el script abrieron o mostraron su contenido. Los logs completos no se volcaron; proceso y credenciales temporales del consumidor se eliminaron al terminar.

| Dato | Resultado |
| --- | --- |
| Entrada | `Diagnóstico actual del paciente: asma.`; `es-EC`. |
| HTTP / estado | 200 / `completed`. |
| Procesamiento | `provider`, `google-gemini`, `gemini-3.8-flash`; prompt `extraction-gemini-v1`, validación `evidence-1`. |
| Entidades | Un diagnóstico `asma`, cero medicamentos/procedimientos; sin advertencias; `reviewRequired: true`. |
| Latencia del adaptador | 4.785 ms. |
| Uso comunicado | 530 tokens de entrada, 254 de salida. Pensamientos y caché desconocidos. |
| Coste estimado | Desconocido por falta de conteos necesarios, no cero. |
| Privacidad | Nota sintética ausente del log; clave no mostrada al agente ni incluida en artefactos. |
| Nivel de cuenta | No verificado. Un HTTP200 no demuestra Paid/Tier1 ni conformidad para uso clínico. |

Esta ejecución demuestra acceso y aceptación remota del esquema en la configuración probada; no mide precisión, cobertura ni robustez del modelo. No se ejecutó corpus remoto ni se abrió reservado.

## Pendientes

- Comprobar por separado el nivel de facturación, retención y condiciones de despliegue; la suscripción personal y la extracción exitosa no los demuestran.
- Resuelto después en H5: se registra versión efectiva sanitizada y total de tokens. Google devolvió el alias, sin versión inmutable más granular.
- Ejecutado después en H5: 234 operaciones con configuración congelada, sin ajuste con reservado. Calidad no aprobada; ver [resultados](h5-validation.md).

La [guía H4](h4-integration.md) documenta arranque, importación explícita del archivo por la aplicación, presupuestos, coste, privacidad y restricciones del servicio. El [ADR 0005](adr/0005-gemini-provider-h4.md) explica elección de REST frente a SDK/Interactions, datos y consecuencias. No se añadieron servicios, BD ni acceso a SFM/PreAudit Health.
