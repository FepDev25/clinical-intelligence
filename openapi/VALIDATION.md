# Verificación de H1 — Contrato y ejemplos

> Registro histórico del hito indicado: sus cifras y pendientes describen ese momento. Estado actual: H1–H4 verificados, H5 evaluado y MVP no aprobado. Consultar [H5](../docs/h5-validation.md), [cierre de sesión](../docs/sesion-27-10-2026/README.md) e [índice documental](../docs/README.md).

Fecha: 2026-10-27.

Alcance de este reporte: formalización técnica, OpenAPI y ejemplos (pasos 1–3). El cierre posterior de H1 con corpus está documentado en [evaluation/VALIDATION.md](../evaluation/VALIDATION.md). La implementación no forma parte de estas verificaciones.

## Artefactos

- [OpenAPI v0.1.0](openapi.json), una operación: `POST /api/v1/extractions`.
- [ADR 0002](../docs/adr/0002-extraction-wire-contract.md), con esquemas, estados, Unicode, autenticación, límites configurables y códigos HTTP.
- [Manifest](examples/manifest.json), 29 escenarios públicos y 58 archivos de ejemplos.
- [Validador](tools/validate_contract.py) y [dependencias de herramientas](tools/requirements.txt).
- [Guía de integración y validación](README.md).

## Comprobaciones ejecutadas

Entorno: Python 3.14.8, `openapi-spec-validator` 0.7.2, `jsonschema` 4.23.0 y `langcodes` 3.5.0, instalados en un entorno temporal fuera del repositorio.

Comando ejecutado desde la raíz:

```sh
/tmp/clinical-contract-validation.wygT3R/bin/python openapi/tools/validate_contract.py
```

Resultado:

```text
PASS: OpenAPI 3.1.1, 21 schemas, 29 scenarios, 58 example files.
PASS: evidence/Unicode/catalog/header invariants; 30 invalid mutations rejected; normalization boundaries preserved.
Contract artifacts only: no backend, provider calls or clinical quality evaluation executed.
```

La ruta temporal identifica el entorno usado en esta sesión; la guía proporciona pasos reproducibles para crear otro.

También se comprobaron enlaces Markdown locales y ausencia de espacios finales en los artefactos documentales. Las verificaciones no modificaron `AGENTS.md`, SFM ni otros proyectos.

Las mutaciones rechazadas incluyen campos desconocidos, valores nulos no admitidos, nota vacía, localidad inválida/no soportada, sustitutos Unicode aislados, catálogos/IDs duplicados, citas inexistentes, rangos incorrectos, atributos sin respaldo literal, candidatos inventados, cardinalidad incorrecta de coincidencias, IDs de entidad duplicados, estados de advertencia inconsistentes, falta de revisión humana, nombres de campos incompatibles, claves JSON duplicadas y discrepancias de estado/cabeceras. Se verificaron límites configurables con valores pequeños de prueba, sin adjudicar calidad a los defaults altos.

## Límites de esta verificación

No se levantó un backend ni se ejecutó el ejemplo cURL. Los estados HTTP/cabeceras del manifest son expectativas contractuales, no respuestas observadas de un servidor. Los escenarios de proveedor no son fallos provocados de un servicio real.

No se midieron calidad semántica, rendimiento, consumo real, autenticación efectiva, configuración del SDK, filtración en logs del backend ni límites de transporte en vivo. Los porcentajes y reglas de aceptación aprobados siguen siendo objetivos para la evaluación futura.

Los ejemplos públicos pueden usarse para desarrollo/regresión; no pertenecen a la evaluación reservada. El corpus, sus reglas y el cierre conjunto de H1 están documentados en el reporte de evaluación enlazado al inicio; no modifican los resultados históricos de esta comprobación del contrato.
