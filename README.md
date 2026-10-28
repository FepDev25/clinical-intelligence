# Clinical Intelligence

API-first para inteligencia clínica asistida. H1 define contrato/corpus, H2 aporta el núcleo Java 21 y H3 incorpora HTTP con autenticación por consumidor. H4 integra Gemini 3.8 Flash, con transporte controlado y una extracción sintética autenticada verificados. H5 completó 234 operaciones sintéticas reales: el MVP **no aprueba** los criterios de calidad; se entrega como prototipo evaluado. Ver [informe y próximos pasos](docs/h5-validation.md).

La [guía de integración H5](docs/h5-integration.md) reúne configuración, cURL, errores y revisión del resultado. El [arnés de evaluación](evaluation/RUNNING.md) conserva rondas, costes estimados, auditoría y decisión final.

El [índice documental](docs/README.md) organiza decisiones, guías e informes. Para retomar el proyecto, empezar por el [cierre de sesión del 27-10-2026](docs/sesion-27-10-2026/README.md).

## Usar la API local

La [guía de integración H3](docs/h3-integration.md) incluye generación de credenciales fuera del repositorio, arranque del JAR y consumo con cURL. Para esa guía se usa `CI_PROVIDER_MODE=simulated` y `CI_CREDENTIALS_FILE`; no hay credenciales ni modo fake implícitos.

El fake de arranque devuelve listas vacías y metadatos `simulated`: demuestra integración y no hace extracción clínica. Las pruebas inyectan propuestas completas mediante el mismo puerto.

Para el modo real `CI_PROVIDER_MODE=gemini`, la [guía H4](docs/h4-integration.md) explica `GOOGLE_API_KEY`, importación explícita del `.env` por la aplicación, presupuestos y condiciones de datos. Las pruebas ordinarias no cargan ese archivo y bloquean tráfico externo. El [informe H4](docs/h4-validation.md) distingue implementación de prueba remota y de evaluación clínica.

## Ejecutar las pruebas del núcleo

Requisitos: JDK 21 y Maven 3.9 o superior. La primera resolución de dependencias usa Maven Central; las pruebas no necesitan red, claves, base de datos, SFM ni contenedores.

```sh
mvn test
mvn verify
```

El resultado de `verify` es el JAR ejecutable del proyecto en `target/`. Las pruebas HTTP usan un puerto efímero de loopback, credenciales temporales y propuestas controladas; no llaman a proveedores externos.

## Qué hace H2

El [caso de uso](src/main/java/com/clinicalintelligence/extraction/application/ExtractionService.java) recibe una nota y catálogos opcionales, invoca un [puerto de proveedor](src/main/java/com/clinicalintelligence/extraction/application/ExtractionProvider.java), comprueba evidencias, descarta atributos inválidos y resuelve IDs únicamente contra los catálogos de esa petición. Preserva contexto y advertencias con referencias remapeadas; distingue vacío válido, descartes advertidos y fallo del proveedor.

El [proveedor simulado](src/main/java/com/clinicalintelligence/extraction/infrastructure/ScriptedExtractionProvider.java) requiere un script explícito y etiqueta la ejecución como simulada. No realiza inferencia, no consulta gold por ID ni sustituye una integración real. Los resultados son propuestas para revisión humana.

Las reglas técnicas y las responsabilidades que quedan para H3/H4 se detallan en [ADR 0003](docs/adr/0003-extraction-core-h2.md). En particular, la validación literal no certifica interpretación clínica y el chequeo sintáctico de localidad no sustituye la validación completa de subtags de H3.

La [validación de H2](docs/h2-validation.md) registra 86 pruebas Java, la revisión independiente y las comprobaciones del contrato y corpus de desarrollo.

H3 añade filtro de API keys/scopes, límites de bytes reales, decodificación estricta, validación BCP 47 sin red y mapeo explícito al contrato. Decisiones: [ADR 0004](docs/adr/0004-extraction-http-h3.md); verificación: [informe H3](docs/h3-validation.md).

## Contrato, corpus y plan

- [OpenAPI v0.1.0 y ejemplos](openapi/README.md).
- [Corpus sintético y reglas](evaluation/README.md).
- [Spec aprobada](docs/superpowers/specs/2026-10-27-clinical-extraction-mvp-design.md).
- [Plan e hitos](docs/superpowers/plans/2026-10-27-clinical-extraction-mvp-plan.md).

H2 usa únicamente el conjunto de desarrollo como recurso de pruebas. No abrir las anotaciones reservadas para TDD o ajuste. La validación de artefactos existente se puede ejecutar con las dependencias Python indicadas en la guía del corpus; su comando por defecto valida desarrollo y no carga reservado.

El endpoint local funciona con el proveedor simulado y con Gemini explícito cuando se configura. Una extracción sintética real demuestra integración, no precisión clínica. No se modifican SFM ni PreAudit Health, y este repositorio utiliza únicamente datos sintéticos.
