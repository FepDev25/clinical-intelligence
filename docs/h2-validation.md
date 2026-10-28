# Validación de H2 — núcleo con proveedor simulado

> Registro histórico del hito indicado: sus cifras y pendientes describen ese momento. Estado actual: H1–H4 verificados, H5 evaluado y MVP no aprobado. Consultar [H5](h5-validation.md), [cierre de sesión](sesion-27-10-2026/README.md) e [índice documental](README.md).

Fecha: 2026-10-27. Resultado: H2 implementado y verificado; H3–H5 pendientes.

## Entrega

Proyecto Maven Java 21 con dominio independiente del transporte, puerto de proveedor, fake explícito, caso de uso, validación de evidencia por puntos de código, descarte de atributos inválidos y resolución conservadora de catálogos por petición. Los resultados preservan contexto y referencias de advertencias válidas; diferencian vacío legítimo, descartes y errores de proveedor.

Archivos de entrada: [pom.xml](../pom.xml), [ExtractionService](../src/main/java/com/clinicalintelligence/extraction/application/ExtractionService.java), [puerto](../src/main/java/com/clinicalintelligence/extraction/application/ExtractionProvider.java), [fake](../src/main/java/com/clinicalintelligence/extraction/infrastructure/ScriptedExtractionProvider.java), [pruebas](../src/test/java/com/clinicalintelligence/extraction), [ADR 0003](adr/0003-extraction-core-h2.md).

## Comprobaciones ejecutadas

Entorno: Java 21.0.13 y Maven 3.10.0. La resolución inicial de dependencias se realizó antes de la verificación final sin red.

| Comando o inspección | Resultado |
| --- | --- |
| `mvn -B -ntp -o verify` | BUILD SUCCESS; 86 pruebas, cero fallos, errores u omitidas; JAR generado. |
| `python -m unittest discover -s evaluation/tools -p 'test_*.py'` | 32 pruebas aprobadas. |
| `python openapi/tools/validate_contract.py` | 21 esquemas, 29 escenarios, 58 archivos de ejemplo; 30 mutaciones inválidas rechazadas. |
| `python evaluation/tools/validate_corpus.py` | 39 casos de desarrollo; esquema, evidencias, pertenencia a catálogos, cobertura y hashes correctos. Reservado no cargado. |
| Inspección del JAR productivo | Incluye el caso de uso; excluye corpus, anotaciones reservadas y artefactos de pruebas. |

Los comandos Python usaron un entorno virtual con las dependencias de validación de H1. La suite Java incluye los 39 casos de desarrollo con propuestas controladas, escenarios Unicode/CRLF, colisiones de alias, descartes, errores y 80 peticiones concurrentes con notas/catálogos distintos. Una prueba comprueba que el corpus reservado no está disponible en el classpath de pruebas.

## TDD y revisión

Se observaron fallos antes de implementar las políticas de evidencia, catálogos, entrada y orquestación. Las regresiones adicionales de asociación farmacológica y valores Unicode inválidos también fallaron antes de corregirse. El [registro de ejecución](h2-progress.md) conserva la secuencia y las decisiones.

La revisión independiente final no encontró incidencias críticas. Encontró una incidencia importante: una localidad malformada muy larga desbordaba la pila del validador regex. Se reprodujo en una prueba y se sustituyó esa validación por `Locale.Builder`; ahora se devuelve `INVALID_LOCALE` sin invocar al proveedor. La suite final de 86 pruebas incluye esa corrección.

Pendiente menor aceptado: la validación vuelve a recorrer la nota para cada evidencia. Antes de ajustar capacidad con notas muy largas en H4/H5, medir y considerar un índice de posiciones por petición. No se ha medido capacidad productiva ni se impone un límite bajo para ocultar este coste.

## Decisiones y límites para el siguiente hito

- Java puro en H2; Spring Boot se incorpora con HTTP en H3 y Spring AI con el adaptador real en H4. ICU4J implementa el full case folding; Jackson y JUnit son exclusivos de pruebas.
- El proveedor recibe nota/localidad; los catálogos se resuelven localmente. No hay persistencia ni logs de contenido clínico. Los fallos se convierten en códigos seguros.
- Los atributos farmacológicos requieren evidencia dentro de un ámbito válido que nombre el medicamento; citas amplias o multilínea siguen necesitando evaluación semántica del adaptador. La comprobación literal no certifica interpretación clínica.
- H3 debe implementar decodificación estricta, nulabilidad JSON, claves desconocidas/duplicadas, límites de bytes, API keys/scopes, Problem Details y registro completo de subtags BCP 47. Esos controles no se consideran resueltos por el núcleo.
- H4/H5 deben evaluar interpretación, contradicciones, prompt injection del modelo, presupuestos, observabilidad y capacidad. Las pruebas con fake no miden precisión clínica.
- El corpus reservado no se abrió durante H2. No hay resultados de generalización ni evaluación con un modelo real.
- Se trabajó en `feat/extraction-core-h2`, sin commits, dado que el repositorio no tenía commit inicial y los artefactos existentes estaban sin seguimiento. El registro explica esta adaptación y sus costes.

No se modificaron SFM ni PreAudit Health, no se levantaron servicios y no se enviaron notas a proveedores. El siguiente incremento mínimo es H3: exponer este caso de uso mediante el contrato HTTP aprobado y verificar autenticación y serialización con el fake explícito.
