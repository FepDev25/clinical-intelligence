# Corpus sintético de Clinical Extraction — v1.0.0

Corpus de referencia para desarrollar y evaluar la API según el contrato aprobado. Los archivos de referencia no contienen pacientes reales ni respuestas de un LLM. H5 conserva respuestas sintéticas reales en un directorio de ejecución separado; no son mediciones de precisión clínica. Sus anotaciones son sintéticas y no tienen revisión clínica independiente.

H5 ya ejecutó las seis rondas fijas (234 operaciones) sin modificar estas referencias. Resultado: prototipo evaluado, MVP no aprobado. [Informe](../docs/h5-validation.md), [ejecución](runs/2026-10-28-gemini38-v1/), [reproducción](RUNNING.md) e [incidencias metodológicas](reports/2026-10-28-gemini38-v1-errata.md). El manifest y el protocolo conservan su estado histórico previo a la medición.

## Qué incluye

- [Reglas de anotación](ANNOTATION.md): entidades, contexto, evidencia, ausencias, repeticiones y exclusiones.
- [Protocolo de evaluación](PROTOCOL.md): alineación, TP/FP/FN, atributos, advertencias, errores críticos y aprobación.
- [Manifest versionado](corpus/v1/manifest.json): composición, objetivos y hashes SHA-256.
- [Esquema de los casos](case.schema.json), que reutiliza los esquemas de [OpenAPI](../openapi/openapi.json).
- [Casos de desarrollo/regresión](corpus/v1/development.json), disponibles para TDD y ajuste.
- Evaluación reservada en `corpus/v1/reserved.json`, destinada a la configuración congelada de H5. Evitar usar sus notas o gold como ejemplos del prompt o pruebas del builder.
- [Diez recetas de escala](corpus/v1/stress-recipes.json), materializadas en memoria sin llamadas externas.
- [Validador](tools/validate_corpus.py), [materializador de escala](tools/stress_cases.py) y pruebas de ambos.

La separación reservada es metodológica y está dentro del mismo repositorio: no es un secreto ni un cegamiento criptográfico. El autor construyó y auditó ambos conjuntos. Los escenarios no se reutilizan entre splits; el control detecta IDs/orígenes compartidos y notas duplicadas tras NFC/case/espacios, pero no puede probar independencia semántica por sí solo.

## Composición inicial

| Cohorte | Desarrollo | Reservado | Cobertura |
|---|---|---|---|
| Principal | 24 | 24 | Seis notas por consulta externa, evolución, interconsulta y procedimientos. |
| Exploratoria | 9 | 9 | Tres notas por ingreso, emergencia y epicrisis. |
| Adversarial | 6 | 6 | Instrucciones incrustadas, catálogos, negación/sujeto y pautas incompletas. |
| Total fijo | 39 | 39 | 78 notas sintéticas. |

Las 78 notas contienen 277 entidades anotadas. Por conjunto principal hay 30 diagnósticos, 24 medicamentos y 27 procedimientos, incluyendo controles negativos. Los otros casos se reportan por cohorte, sin inflar los denominadores principales. Se cubren narrativa, secciones, SOAP, notas telegráficas, CRLF, emoji y Unicode descompuesto.

Los tags documentan la intención del caso; el gold y sus evidencias determinan las expectativas. No se acredita cobertura solo por poner un tag. Los casos de catálogo usan nombres/alias explícitos y medicamentos ficticios, sin asignar terapias por conocimiento médico.

## Formato de caso

Cada caso tiene ID, grupo de origen, split, familia, cohorte, formato, tags, procedencia, petición pública y anotación esperada. `expected` contiene las tres colecciones de entidades y las advertencias mínimas requeridas; no contiene IDs de petición ni versiones de modelo que deban reproducirse.

Las entidades usan los mismos esquemas públicos de OpenAPI. Los IDs y el orden de referencia son locales; el evaluador debe alinear por nombre, ocurrencia y contexto según el protocolo. No comparar respuestas mediante igualdad JSON completa.

`excludedMentions` conserva evidencia de fragmentos que no justifican hallazgos o atributos, por ejemplo instrucciones externas o días de control. El validador rechaza anotaciones que conviertan esos fragmentos excluidos en entidades o duraciones de tratamiento.

## Validación local

Reutilizar las dependencias de herramientas del contrato; no se agregan dependencias al producto Java. Desde la raíz, con un entorno temporal que no exista ya para otro trabajo:

```sh
python3 -m venv /tmp/clinical-intelligence-evaluation-venv
/tmp/clinical-intelligence-evaluation-venv/bin/python -m pip install -r openapi/tools/requirements.txt
```

Uso normal del builder, que no abre las anotaciones reservadas:

```sh
PYTHONDONTWRITEBYTECODE=1 /tmp/clinical-intelligence-evaluation-venv/bin/python evaluation/tools/validate_corpus.py
PYTHONDONTWRITEBYTECODE=1 /tmp/clinical-intelligence-evaluation-venv/bin/python -m unittest discover -s evaluation/tools -p 'test_*.py'
```

Auditoría completa explícita de autoría/release, que comprueba también separación y hashes de reservado:

```sh
PYTHONDONTWRITEBYTECODE=1 /tmp/clinical-intelligence-evaluation-venv/bin/python evaluation/tools/validate_corpus.py --include-reserved --stress
```

`--json` imprime un reporte agregado, sin notas ni gold. Las validaciones no llaman a modelos, no levantan servicios y no escriben sondas grandes en disco. Los fallos de anotación reportan ID y regla/ubicación sin volcar la nota.

También debe seguir pasando la validación contractual:

```sh
PYTHONDONTWRITEBYTECODE=1 /tmp/clinical-intelligence-evaluation-venv/bin/python openapi/tools/validate_contract.py
```

## Escala sin promesas de calidad

Las recetas cubren hasta 1.000.000 puntos de código, 100.000 entradas de catálogo y 100 procedimientos, con hallazgos al inicio, medio y final. El materializador conserva el número exacto de caracteres y los hallazgos, y coloca la coincidencia de catálogo al final de las entradas para evitar depender del primer candidato.

Es relleno administrativo sintético para probar volumen y posiciones, no una simulación de toda la complejidad clínica de una nota extensa. Las notas exploratorias aportan escenarios temporales más complejos, sin sustituir una evaluación representativa futura.

El validador comprueba tamaño y serialización canónica local; no demuestra que un proveedor pueda procesar esas sondas. El arnés debe verificar el presupuesto y tamaño exactos que enviaría, medir los resultados y establecer rangos recomendados sin truncar contenido.

## Versionado y siguiente uso

El manifest congela hashes de datos, esquema y materializador. Una corrección de referencia requiere revisión, nueva versión y preservación de reportes anteriores; no actualizar solo el hash para esconder un cambio. La auditoría inicial de H1 precedió a cualquier medición. H5 conserva los conteos originales y no modifica referencias a partir de resultados.

En H5 se congelaron código, reglas, prompt, parámetros y configuración antes de medir. Si se usan casos reservados para ajustar, moverlos a regresión y reemplazarlos por escenarios independientes. La medición no aprobó los criterios; los cambios posteriores requieren nueva trazabilidad y evaluación.

H1 entregó corpus y reglas verificables. H2–H4 incorporaron núcleo, HTTP y proveedor real con fakes de prueba; H5 añade puntuación, arnés externo y resultados. Las pruebas del builder siguen usando desarrollo, sin una búsqueda por ID para devolver gold. El [índice de regresiones](regressions/README.md) identifica fallos de desarrollo observados, sin afirmar que se corrigieron.
