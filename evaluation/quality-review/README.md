# T1 — Línea base reproducible de revisión de calidad

Estado: T1 terminada el 28-10-2026. T2–T10 no ejecutadas. Se implementó exclusivamente análisis offline; el extractor y las campañas históricas no se modificaron. El MVP sigue sin aprobación.

Plan: [revisión integral](../../docs/superpowers/plans/2026-10-28-extraction-quality-review-and-remediation.md).

## Insumos y reproducción

`baseline-manifest.json` registra HEAD, rama, estado inicial del árbol y hashes de campañas/auditorías históricas, fuentes Java, recursos Gemini, corpus utilizados y ensayos. El árbol inicial contenía el plan aprobado aún sin commit y su enlace en el índice; no se presupone un checkout limpio.

`analyze_baseline.py` solo lee esos insumos y escribe JSON por stdout. No importa runner, evaluador, cliente HTTP ni proveedor. Verifica todos los hashes registrados antes de analizar. Si un insumo cambia, falla: no actualizar el manifiesto para ocultar el cambio; preparar una nueva línea base versionada cuando corresponda.

```sh
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s evaluation/quality-review -p 'test_*.py'
PYTHONDONTWRITEBYTECODE=1 python3 evaluation/quality-review/analyze_baseline.py > /tmp/clinical-t1-analysis.json
cmp evaluation/quality-review/baseline-analysis.json /tmp/clinical-t1-analysis.json
```

La función `analyze_baseline(scores, cases, rounds=(1, 2, 3))` exige cobertura exacta de cada caso y ronda por split. Rechaza casos/scores duplicados, registros ajenos, rondas faltantes, conteos inválidos y repetición de una misma discrepancia contextual. El lector rechaza claves JSON repetidas. La prueba con dos dimensiones erróneas verifica una sola entidad incorrecta y dos diferencias de campo.

`baseline-analysis.json` conserva el resultado reproducible por split, cohorte, ronda y tipo. `alignedByAutomaticScorer` es TP más predicciones únicas marcadas `CONTEXT_MISMATCH` por el comparador histórico: no es alineación clínica independiente. `operationalFailureCases` e `invariantViolationCases` son señales del grupo, repetidas en las tres filas de tipo; no sumarlas entre tipos.

## Resultado principal

Casos principales reservados, sin nueva adjudicación ni cambio de referencias:

| Tipo | Alineadas automáticamente, rondas 1/2/3 | TP estrictos, rondas 1/2/3 | Referencias por ronda |
| --- | --- | --- | --- |
| Diagnósticos | 30/30/30 | 29/28/29 | 30 |
| Medicamentos | 22/22/22 | 18/18/19 | 24 |
| Procedimientos | 26/26/26 | 15/17/17 | 26 |

Los procedimientos tienen 11/9/9 entidades con contexto distinto al gold. Dimensiones discrepantes: temporalidad 9/5/6; sujeto 3/4/5; afirmación 0/1/0. Una entidad puede diferir en más de una dimensión. No hay diferencias de `performanceStatus` en este subconjunto automático. Esto no invalida los rechazos semánticos de la auditoría ni prueba corrección de las citas.

Los medicamentos muestran dos referencias no recuperadas por ronda además de 4/4/3 diferencias de contexto. Los diagnósticos muestran 1/2/1 diferencias de contexto. Estos conteos orientan la investigación hacia referencias y asociación contextual, sin rebajar los criterios estrictos.

## Inventario de causas conocidas y desconocidas

| Observación | Etapa demostrada | Límite de atribución |
| --- | --- | --- |
| Negación aceptada como `AFFIRMED`, cero advertencias | Aceptación/saneamiento del servicio ante propuesta sintética deliberadamente incorrecta | No prueba que cada caso remoto nazca del mismo patrón |
| Dosis de Beta aceptada para Alfa, cero advertencias | Asociación por contención del servicio ante cita amplia | No cuantifica frecuencia de este fallo en Gemini |
| Diferencias de sujeto/tiempo/afirmación con gold | Comparación automática de respuesta final | Puede intervenir proveedor, resolución de cita, filtro o referencia |
| Omisiones finales de medicamentos | Resultado del sistema completo | Sin propuesta cruda v3 no se separa omisión original de descarte |
| Advertencias, evidencias válidas y ausencia de errores HTTP | Señales técnicas del resultado | No demuestran respaldo semántico |

Todas las filas del JSON indican `semanticAttribution=not_determined_by_scores`. No se reemplaza auditoría con una clasificación inventada de causas. Los 57 pendientes históricos permanecen abiertos; adjudicarlos corresponde a T2.

## Verificaciones ejecutadas

Resultados completos y entorno en [verification](verification/). No hubo llamadas de inferencia ni datos reales.

| Verificación | Comando efectivo | Resultado |
| --- | --- | --- |
| Analizador nuevo | `python3 -m unittest discover -s evaluation/quality-review -p 'test_*.py'` | 14 pruebas, todas pasan |
| Java | `mvn -B -ntp -o verify` | 137 pruebas, cero fallos/errores/omitidas; BUILD SUCCESS |
| Arnés de evaluación | `/tmp/clinical-eval-next/bin/python -m unittest discover -s evaluation/tools -p 'test_*.py'` | 81 pruebas, todas pasan |
| Control de entrega histórico | `/tmp/clinical-eval-next/bin/python -m unittest discover -s evaluation/reports -p 'test_*.py'` | 5 pruebas, todas pasan |
| Contrato | `/tmp/clinical-eval-next/bin/python openapi/tools/validate_contract.py` | 21 esquemas, 29 escenarios, 58 ejemplos y 30 mutaciones inválidas verificadas |
| Corpus desarrollo | `/tmp/clinical-eval-next/bin/python evaluation/tools/validate_corpus.py` | 39 casos; estructura, evidencia, cobertura y hashes válidos |
| Análisis repetido | Comando de reproducción y `cmp` anteriores | Resultado idéntico byte a byte |
| Integridad histórica | SHA-256 declarados en `artifact-checksums.json` de runs/audits | 51 entradas verificadas; además todos los insumos del manifiesto T1 sin cambios |

Las pruebas nuevas se observaron primero en rojo por ausencia del analizador. Después dos pruebas adicionales fallaron por aceptar casos vacíos y rondas configuradas repetidas; tras añadir esas validaciones, las 14 pasan. Los logs rojos y verdes se conservan por separado. Al verificar la primera versión se corrigió una selección equivocada de familia en el test, sin cambiar el significado esperado.

Ensayos, ejecutados tras `mvn verify` con las clases del proyecto, sin `clean`:

```sh
jshell --class-path target/classes evaluation/investigation/negated-as-affirmed.jsh
# NEGATED_AS_ASSERTION=AFFIRMED; WARNINGS=0
jshell --class-path target/classes evaluation/investigation/other-medication-dose.jsh
# WRONG_OTHER_CLAUSE_DOSE=9 mg; WARNINGS=0
```

### Ajustes de ejecución registrados

- El repositorio no contiene `./mvnw`; se usó Maven instalado, tal como indica `evaluation/RUNNING.md`. El plan se corrige para futuras tareas.
- Python del sistema no tiene `langcodes`; se reutilizó el entorno existente `/tmp/clinical-eval-next`, sin instalar dependencias. Para reproducir en otra máquina, preparar un entorno con `openapi/tools/requirements.txt`; la ruta temporal no es requisito del producto.
- Maven offline falló inicialmente por locks de `~/.m2` bloqueados por el sandbox. El arnés Python tuvo un error de socket local y JShell requiere socket local. Se repitieron con escalación autorizada; sus verificaciones finales pasaron. Esos fallos fueron de entorno, no de lógica del producto.
- `git diff --cached --check` detectó espacios finales en doce líneas `[INFO]` del log Maven. Se preserva la salida cruda como evidencia; la comprobación de formato del resto de los archivos excluye únicamente ese log. No es un fallo de pruebas ni se altera el log para hacerlo pasar.
- Se trabajó en la rama de desarrollo existente `feat/extraction-core-h2`, conservando el plan aprobado sin commit y los binarios históricos locales. No se creó otro worktree ni se modificaron fuentes de producción. El alcance autorizado de este turno es únicamente T1; no se continúa automáticamente a T2.

## Continuación

T2 debe resolver reglas de referencia y adjudicación con revisión independiente. T3 usará este desglose como control, sin confundir sus métricas auxiliares con aprobación. T4 sigue siendo necesaria para atribuir a etapas los fallos de próximas inferencias. L01 y L02 están reproducidos, no corregidos; L07 tiene diagnóstico cuantificado y L12 mantiene sus límites de independencia.
