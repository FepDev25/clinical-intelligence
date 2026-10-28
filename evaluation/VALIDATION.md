# Verificación del corpus y cierre técnico de H1

> Registro histórico del hito indicado: sus cifras y pendientes describen ese momento. Estado actual: H1–H4 verificados, H5 evaluado y MVP no aprobado. Consultar [H5](../docs/h5-validation.md), [cierre de sesión](../docs/sesion-27-10-2026/README.md) e [índice documental](../docs/README.md).

Fecha: 2026-10-27. Corpus y reglas: 1.0.0. Contrato: 0.1.0.

## Entrega comprobada

78 casos sintéticos (39 desarrollo, 39 reservados), 277 entidades anotadas, reglas de anotación y conteo, separación por escenario, manifest con hashes y 10 recetas de escala. Cada split tiene 24 casos principales, 9 exploratorios y 6 adversariales. La autoría es sintética; no se atribuye revisión clínica independiente.

Artefactos: [guía](README.md), [anotación](ANNOTATION.md), [protocolo](PROTOCOL.md), [manifest](corpus/v1/manifest.json), [validador](tools/validate_corpus.py).

## Comandos y resultados ejecutados

Se reutilizó el entorno temporal de validación contractual (Python 3.14.8 y dependencias de `openapi/tools/requirements.txt`). Desde la raíz:

```sh
PYTHONDONTWRITEBYTECODE=1 /tmp/clinical-contract-validation.wygT3R/bin/python -m unittest discover -s evaluation/tools -p 'test_*.py'
```

Resultado: **32 tests, OK**. Se observaron primero fallos por funcionalidades ausentes del validador/materializador y después ejecuciones verdes. Los tests verifican rechazo de anotaciones inválidas, spans UTF-16 incorrectos, evidencias falsas, IDs de catálogo inventados, contradicción con exclusiones, fugas de origen/nota, hashes modificados, cobertura incompleta y mensajes que volcarían una nota. También verifican materialización completa y determinista de sondas.

```sh
PYTHONDONTWRITEBYTECODE=1 /tmp/clinical-contract-validation.wygT3R/bin/python evaluation/tools/validate_corpus.py
```

Resultado: desarrollo validado; el comando por defecto no abrió las anotaciones reservadas.

```sh
PYTHONDONTWRITEBYTECODE=1 /tmp/clinical-contract-validation.wygT3R/bin/python evaluation/tools/validate_corpus.py --include-reserved --stress --json
```

Resultado agregado de auditoría de autoría:

| Split | Casos | Diagnósticos | Medicamentos | Procedimientos |
|---|---|---|---|---|
| Desarrollo | 39 | 56 | 39 | 44 |
| Reservado | 39 | 55 | 39 | 44 |

En cada cohorte principal: 30 diagnósticos, 24 medicamentos y 27 procedimientos. Cobertura de familias, tags y formatos comprobada. No se detectaron IDs, grupos de origen ni notas normalizadas compartidas entre splits, ni copias exactas normalizadas de ejemplos contractuales en reservado. Este control no prueba independencia semántica por sí solo.

Las diez recetas se materializaron sin red ni archivos grandes de salida. La mayor nota tuvo exactamente 1.000.000 puntos de código; la mayor sonda de catálogo, 100.000 entradas; la mayor densidad, 100 procedimientos. Sus bodies canónicos estuvieron dentro del default técnico de 64 MiB. No se comprobó capacidad de ningún proveedor remoto.

```sh
PYTHONDONTWRITEBYTECODE=1 /tmp/clinical-contract-validation.wygT3R/bin/python openapi/tools/validate_contract.py
```

Resultado: OpenAPI 3.1.1, 21 esquemas, 29 escenarios y 58 archivos de ejemplos válidos; 30 mutaciones inválidas rechazadas. La incorporación del corpus no alteró el contrato público.

## Qué se cierra y qué no

H1 queda completo en artefactos: decisiones técnicas, OpenAPI, ejemplos, corpus sintético y reglas verificables. No se ejecutaron backend, contenedores, migraciones, llamadas a modelos ni pruebas de precisión clínica. No se modificaron SFM, PreAudit Health ni `AGENTS.md`.

Los porcentajes aprobados siguen siendo objetivos; no son resultados de esta validación. El corpus puede contener sesgos o errores de autoría y necesitar una versión corregida tras revisión. El arnés de puntuación de predicciones y la integración con un LLM aún no existen.

El siguiente paso del plan es H2: núcleo independiente y proveedor simulado mediante TDD, utilizando desarrollo/regresión. H2–H5 permanecen sin iniciar en esta entrega.
