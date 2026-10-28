# Ejecutar y analizar H5

La evaluación llama a Gemini con notas sintéticas y puede generar consumo. Las reglas de [PROTOCOL.md](PROTOCOL.md) y sus umbrales están fijados antes de medir. El arnés no abre el `.env`; solo la aplicación lo importa explícitamente en ejecución. Nunca se publica su clave.

## Preparación

Desde la raíz, con Java 21, Maven y las dependencias Python de `openapi/tools/requirements.txt` instaladas en un entorno virtual:

```sh
mvn verify
python -m unittest discover -s evaluation/tools -p 'test_*.py'
python -m unittest discover -s evaluation/reports -p 'test_*.py'
python openapi/tools/validate_contract.py
python evaluation/tools/validate_corpus.py
```

Las pruebas Java y Python no necesitan una clave real ni cargar anotaciones reservadas. Las pruebas del cliente utilizan loopback. Los validadores no llaman al modelo.

## Congelar antes de medir

```sh
python evaluation/tools/run_evaluation.py prepare \
  --output evaluation/runs/FECHA-gemini38-v1 --budget-usd 10
```

Elegir un directorio nuevo; no se sobrescribe una ejecución existente. Se guardan configuración, hashes, versiones de herramientas y snapshot de fuente, sin `.env` ni credenciales. El JAR congelado se conserva en `target/h5-frozen/`. Mantener ese artefacto para reanudar; compilar de nuevo no lo sustituye.

Los parámetros son los de H4: `gemini-3.8-flash`, temperatura 1, thinking LOW, máximo 65.536 tokens de salida, 120 s totales, dos llamadas como máximo y cero reintentos. El arnés ejecuta una operación a la vez, aunque el proveedor admite concurrencia 2. No se recortan notas, respuestas ni catálogos para obtener aprobación.

## Medición explícita

```sh
python evaluation/tools/run_evaluation.py run \
  --output evaluation/runs/FECHA-gemini38-v1 --split development

python evaluation/tools/run_evaluation.py analyze \
  --output evaluation/runs/FECHA-gemini38-v1
```

Después de completar las tres rondas de desarrollo, se puede ejecutar reservado con la configuración congelada:

```sh
python evaluation/tools/run_evaluation.py run \
  --output evaluation/runs/FECHA-gemini38-v1 --split reserved

python evaluation/tools/run_evaluation.py analyze \
  --output evaluation/runs/FECHA-gemini38-v1
```

El comando `run` levanta el JAR en loopback, con credenciales temporales de consumidor, y solo envía `case.request`. No se pasan referencias, familia, tags o anotaciones al modelo. El proceso y credenciales temporales se eliminan al salir. No hay reintentos ni selección de la mejor ronda.

El directorio usa un bloqueo exclusivo POSIX (`flock`) durante ejecución y análisis; un segundo proceso se rechaza antes de llamar al proveedor. El archivo `.run.lock` persiste y el sistema libera su bloqueo al salir.

Reanudar el mismo comando omite intentos ya registrados. Una interrupción con resultado incierto se cuenta como fallo operativo y conserva la reserva, en lugar de ejecutar de nuevo para escoger una respuesta mejor. Si cambió un activo congelado, se requiere una ejecución nueva. Preservar los resultados anteriores.

## Artefactos y presupuesto

- `freeze.json`: condiciones, hashes y versiones; sin secretos.
- `source-snapshot.zip`: activos de implementación/puntuación, sin corpus ni dotenv.
- `responses.jsonl`: salida pública sintética, cabeceras, tiempo, bytes y telemetría por caso/ronda.
- `scores.json`: conteos y diferencias por caso.
- `summary.json`: conteos auditados por ronda y grupos; `pass` de este arnés congelado se limita a las rondas reservadas.
- `summary-automatic.json` / `scores-automatic.json`: conteos originales conservados antes de auditoría.
- `semantic-audit.json`: decisiones, rechazos, disputas y hashes de cada intento.
- `release-decision.json`: decisión agregada del protocolo en ambos splits.
- `runtime-metadata.json`: latencias, tamaños, versiones y uso conocido/desconocido.

Cada llamada requiere saldo para una reserva conservadora. La cota puede reconciliarse con el total y entrada comunicados por Google, sin inventar contadores ausentes. Si no caben más operaciones, se informa `BUDGET_STOP`. Una ronda incompleta no aprueba; sus referencias no ejecutadas siguen contando como omitidas. La cota de consumo es una estimación operativa estándar en USD, no una factura ni verificación de Paid/Tier1.

La herramienta analiza las respuestas finales con validación OpenAPI y evidencia literal. Alinea por nombre/anclaje, conserva duplicados como FP y cuenta contexto erróneo como FP+FN. Las asociaciones ambiguas o variantes textuales quedan pendientes de adjudicación; no se asignan favorablemente por contexto ni se aceptan sin revisión. Un denominador cero es N/A.

Antes de puntuar se vuelven a verificar los hashes del corpus real, sus poblaciones y el SHA-256 de cada solicitud registrada. Duplicados, identidades ajenas o referencias modificadas detienen el análisis y preservan el informe anterior.

Sin `semantic-audit.json`, los resultados son provisionales: `automaticCriteriaPass` refleja los conteos y `pass` permanece falso. La auditoría versionada debe identificar revisor, alcance `synthetic-reference-support`, `clinicalReview: false`, hashes de `freeze.json` y `responses.jsonl`, y una revisión por intento con su hash canónico. Cada revisión documenta decisión (`support_verified`, `support_failed`, `operation_failure` o `pending`), notas, punteros de entidades/contextos/atributos rechazados y resoluciones justificadas de adjudicaciones. Una respuesta aceptada no puede marcarse como fallo para omitir su revisión. Una auditoría de consistencia sintética hecha por un agente no es revisión clínica independiente.

Los rechazos semánticos conservan FP/FN y bloquean aprobación; una asociación ambigua entre entidades no se reasigna favorablemente. Las resoluciones de variantes justificadas no cambian silenciosamente los conteos léxicos. Cambiar resultados invalida la auditoría por hash.

La decisión final de entrega exige las tres rondas reservadas completas, 95 % de precisión y 90 % de cobertura en cada tipo principal, y las cero tolerancias del protocolo, sin adjudicaciones abiertas. Una aprobación del corpus sintético no demuestra aptitud clínica, generalización ni autorización para usar pacientes reales.

## Decisión agregada y reproducción de metadatos

El comparador congelado conserva su comportamiento original y su `summary.pass` no es la aprobación final. Se detectó después de medir que esa agregación no exigía todas las revisiones/cero tolerancias de desarrollo. La revisión de decisión **1.0.1** aplica el protocolo original a ambos splits, sin cambiar puntuación, gold, inferencia o umbrales; su código/hash se conserva por separado. Sus cinco pruebas usan conteos ficticios, sin notas reservadas.

Después de completar la auditoría:

```sh
python evaluation/reports/release.py evaluation/runs/FECHA-gemini38-v1
python evaluation/reports/summarize.py evaluation/runs/FECHA-gemini38-v1
```

`release.py` recompone el análisis bajo bloqueo con el código congelado y verifica integridad, no confía en un summary editado. Requiere seis rondas completas, toda la revisión semántica decidida, cero tolerancias/advertencias/adjudicaciones en ambos splits y cero fallos operativos principales; exige 95/90 por tipo en cada principal reservado. No añade umbral de precisión a desarrollo. No hace llamadas al proveedor. `release-decision.json` incluye hashes de código, freeze, respuestas, auditoría y resumen.

El segundo comando imprime solo metadatos descriptivos; no decide aprobación. Ambos resultados quedan vinculados a las respuestas originales. Cambiar un activo congelado exige una ejecución nueva; no editar hashes para continuar una anterior. La ejecución H5 v1 conserva también el snapshot `decision-sources-v1.0.1.zip` y sus checksums.

## Escala separada

```sh
python evaluation/tools/validate_corpus.py --stress
```

Este comando materializa recetas y valida cuerpos localmente, sin inferencia remota. No se dispara una grilla de escala al medir las notas fijas. No anunciar soporte de millón de caracteres o 100.000 entradas con calidad solo porque una protección técnica los admita.

Decisiones y límites: [ADR 0006](../docs/adr/0006-frozen-evaluation-h5.md). Los resultados fechados se enlazarán desde la guía del corpus y el informe H5, conservando rondas y fallos originales.
