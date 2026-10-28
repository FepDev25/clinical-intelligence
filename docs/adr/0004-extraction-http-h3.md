# ADR 0004 — Transporte HTTP y arranque de H3

> Registro histórico del hito indicado: sus cifras y pendientes describen ese momento. Estado actual: H1–H4 verificados, H5 evaluado y MVP no aprobado. Consultar [H5](../h5-validation.md), [cierre de sesión](../sesion-27-10-2026/README.md) e [índice documental](../README.md).

Fecha: 2026-10-27. Estado: decisión de implementación dentro del alcance aprobado.

## Opciones y decisión

Se incorpora Spring Boot 3.5.16 sobre Java 21. Conserva el núcleo sin dependencia de HTTP y permite integrar Spring AI 1.1.x después. Frente a Boot 4, evita adelantar una migración que H3 no requiere. Compatibilidad: [Boot 3.5](https://docs.spring.io/spring-boot/3.5/system-requirements.html) y [Spring AI](https://github.com/spring-projects/spring-ai). Maven Central contiene la versión seleccionada.

El BOM de Boot mantiene alineados Jackson core/annotations/databind (2.21.4 para core/databind, 2.21 para annotations). JUnit se configura con la propiedad del BOM `junit-jupiter.version=5.13.4`, evitando mezclar el agregador con motores/API de otra versión. NetworkNT 1.5.9 es exclusivo de pruebas y valida respuestas HTTP contra JSON Schema 2020-12 extraído del OpenAPI; no interviene en peticiones productivas. Referencia: [validador](https://github.com/networknt/json-schema-validator/tree/1.5.9).

El adaptador usa un filtro de entrada para identidad, autorización y límites de transporte, un controlador REST para invocar el caso de uso y un mapper explícito del contrato. Frente a serializar directamente los records internos, permite conservar nombres, nulabilidad y enums públicos sin acoplar el dominio a Jackson. No se añade un segundo marco de autenticación para una sola credencial servidor a servidor: el filtro verifica la clave y construye una identidad inmutable derivada del servidor.

Las credenciales se leen de un archivo externo al arrancar: consumidor, scopes y SHA-256 de claves aleatorias de 256 bits. Frente a secretos en claro o una base de identidad, satisface el aprovisionamiento y la rotación acordados sin nueva infraestructura. El token tiene formato `ci_` más 43 caracteres base64url; las comparaciones de digest usan `MessageDigest.isEqual`. La API no acepta identidad autoritativa en el body. Configuración inválida falla con mensajes seguros, sin revelar el archivo ni sus valores. Reiniciar aplica revocaciones; se permiten dos verificadores para rotación.

El modo `simulated` es obligatorio explícitamente en H3. El fake de arranque devuelve una propuesta vacía y metadatos simulados; demuestra integración y no hace extracción. Las pruebas inyectan propuestas sintéticas más completas a través del mismo puerto. Ninguna cabecera o parámetro permite elegir salidas, fallos o escenarios. Un modo real incompleto falla al arrancar.

Para BCP 47 se conserva una copia versionada del registro público IANA, validada sin red por petición. Frente a listas de regiones improvisadas o descargas en runtime, preserva subtags registrados, incluyendo regiones numéricas, y un arranque reproducible. La fecha y hash acompañan el recurso; la actualización requiere pruebas. La sintaxis se valida antes de consultar el registro y antes de comprobar soporte español.

## Consecuencias

El body se lee con cota de bytes reales y UTF-8 estricto; el JSON rechaza duplicados, campos desconocidos, tipos incorrectos, nulos explícitos, trailing tokens y Unicode inválido. Los límites estructurales se verifican antes del proveedor. No se copian notas a logs ni archivos.

El servidor local se enlaza a loopback por defecto. Un despliegue remoto deberá terminar TLS; no se confía en cabeceras forwarded arbitrarias. No se promete exposición pública ni operación productiva con H3. Los errores de HTTP fuera del contenedor/proxy quedan fuera del control del formato del contrato, como establece ADR 0002.

El contrato OpenAPI v0.1.0 no cambia. Los presupuestos del proveedor, rate limiting y saturación reales pertenecen a H4; las pruebas de H3 no los presentan como implementados.
