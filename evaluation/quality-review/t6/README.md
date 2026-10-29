# T6 — Contexto, negación y contradicciones

Implementada y verificada offline el 28-10-2026, sobre T5 `241eda6`. Atiende patrones de L01/L03/L04/L06/L13 del [plan aprobado](../../../docs/superpowers/plans/2026-10-28-extraction-quality-review-and-remediation.md). **MVP no aprobado**; T2 continúa con adjudicación formal abierta. No se hizo inferencia remota ni se modificaron golds, campañas o decisiones históricas.

## Qué cambió

El núcleo añade `ContextEvidencePolicy`, independiente del proveedor. Tras validar literalidad, comprueba asociación de las citas de contexto y rechaza etiquetas incompatibles con patrones españoles explícitos y delimitados. Abarca afirmación, sujeto, temporalidad, estado de prescripción y estado de prestación. Un campo rechazado pasa a `unknown` con evidencia vacía y `ATTRIBUTE_DISCARDED`; nunca se sustituye por una etiqueta inferida ni se promueve un desconocido.

`ExtractionService` comunica `validationVersion=evidence-4`. La traza optativa distingue `CONTEXT_ASSOCIATION_REJECTED` y `CONTEXT_CONFLICT_REJECTED` de los fallos de evidencia literal. La ruta ordinaria sigue sin captura. Los campos públicos, enums, prompt, proveedor/modelo, catálogos y límites no cambian; no hay almacenamiento clínico ni dependencias nuevas. La telemetría legada que imprime evidence-1 se mantiene como pendiente explícito de T7 y no representa la versión efectiva de la respuesta.

La [política](policy.md) define el alcance: unidades de mención, negación directa y coordinada, sospecha, negación no concluyente, sujeto explícito y continuidad hacia delante, fechas expresas, suspensión frente a prescripción y estado de procedimientos. Se preservan los offsets originales; NFC/espacios se aplican solo a copias para predicados. CRLF simple no es un párrafo vacío. El inciso explícito «sin parentesco con el paciente» no convierte al acompañante en paciente.

Las menciones incompatibles fusionadas por el proveedor se abstienen en el campo afectado sin elegir una aparición ni borrar anclajes. Se conservan tanto las repeticiones equivalentes ya agrupadas como las menciones separadas; **no hay deduplicación automática por nombre**. Si el proveedor divide repeticiones equivalentes, esa división permanece: fusionar episodios sin relación explícita puede ocultar diferencias. La agrupación semántica sigue siendo un objetivo de proveedor/prompt para T7–T8.

El núcleo añade `CONTRADICTORY_MENTIONS` solo bajo un encabezado explícito «simultáneo sin resolución:» previo a ambas menciones, en el mismo bloque, con temporalidad actual, identidad textual de sujeto compatible y afirmaciones opuestas. Madre/padre y paciente A/B no se equiparan por compartir enum. Los targets se agrupan una vez; una secuencia resuelta no genera por sí sola este aviso. Se conservan los avisos del proveedor bajo sus validaciones estructurales previas; este cambio no certifica su interpretación semántica.

## Comparación real contra T5

[baseline.json](baseline.json) identifica el JAR T5 copiado antes de implementar y su commit. Las clases de ese JAR procesaron exactamente las mismas propuestas que T6: **sin modelo, red ni emulación de la versión anterior**. [study-inputs.json](study-inputs.json) fija las fuentes iniciales; las regresiones adicionales se reportan separadamente.

| Referencias de campo | T5 | T6 |
| --- | ---: | ---: |
| Positivas retenidas | 35/35 | 35/35 |
| Falsos descartes en positivos | 0/35 | 0/35 |
| Etiquetas incorrectas previstas bloqueadas | 0/24 | 24/24 |
| Fuga residual declarada | 1/1 | 1/1 |

Son **55 escenarios y 60 referencias de campo**: 43 escenarios iniciales y 12 suplementarios. Los positivos incluyen abstenciones `unknown` correctas; no todos son etiquetas conocidas. Incluyendo la residual, T6 deja pasar **1 de 25 etiquetas incorrectas** en este estudio. El caso es «La presencia de asma queda lejos de estar demostrada», propuesto como `affirmed`: esta negación indirecta queda fuera de la gramática actual.

La contradicción explícita del estudio pasó de no advertirse a tener un aviso con targets resolubles; no hubo avisos falsos en los otros escenarios de ese estudio. Los tests adicionales cubren encabezados ajenos/posteriores, sujetos distintos y agrupación de evidencia. No extrapolar esas cifras a la semántica general.

[summary.json](summary.json) conserva cada referencia y resultado, separado por fase y categoría. Los exportes definitivos son `after.json` y `review-after.json`; `after-initial.json` preserva el estado intermedio. Los archivos `before` no se reescriben. Estas cifras miden controles deterministas sobre propuestas fijas, **no precisión clínica, mejora del modelo ni aprobación de entrega**. No se modificaron umbrales.

## Falsos descartes y desarrollo completo

Se repitieron las propuestas de **los 39 casos de desarrollo 1.0.1** sin editar sus referencias. De **657 campos de valor conocidos** (incluidos nombres, códigos y atributos), **656 permanecen idénticos**. No se perdieron entidades ni se añadieron valores conocidos. El único cambio final es:

- `dev-consultation-02`, `/diagnoses/0/context/subject`: `patient` → `unknown`, con `ATTRIBUTE_DISCARDED`. El sujeto de vértigo estaba respaldado mediante su cláusula y la oración posterior de laberintitis. Esta correferencia retrospectiva ya estaba discutida en **T2 R7**. Se registra como diferencia frente a referencia pendiente, sin declararla error del gold ni mejora clínica confirmada. Si después se aprueba ese alcance, habrá que revisar el guard y volver a medir.

`DevelopmentCorpusTest` expresa esa única expectativa de saneamiento y exige igualdad exacta de los otros campos y advertencias. Es una prueba del comportamiento de T6, **no** una modificación de la referencia; el exportador sigue inyectando el gold original sin esa transformación.

Los estados intermedios descartaron dos continuaciones válidas con antecedente explícito y, después, la frase del acompañante con inciso. La investigación y los rojos quedan en `verification/`; se corrigieron en código, no eliminando casos. La política admite antecedentes hacia delante con su cláusula local citada, sin cambio de sujeto o párrafo vacío. No se adopta la correferencia retrospectiva ni el ADR 0007.

## Revisión y límites

La [revisión técnica fresca](review.md) encontró préstamos mediante citas amplias y avisos falsos entre familiares distintos. Se corrigieron con pruebas rojas y verdes. Se añadieron controles de toda la nota citada, pacientes identificados, CRLF y negación de sospecha. Los **74 tests Java añadidos** incluyen 54 escenarios dirigidos y 20 controles auxiliares de relaciones/continuidad; no todos forman parte de los denominadores del estudio comparativo.

La coordinación se apoya en las cabezas propuestas; no descubre todas las entidades omitidas y esas omisiones pueden romper el alcance.

Persisten límites concretos: negaciones indirectas, sintaxis no reconocida, encabezados/episodios y correferencia complejos, alcances pendientes de T2, agrupación dividida por el proveedor y posibles avisos semánticos incorrectos del proveedor. La identidad textual se usa solo para la advertencia acotada; no es resolución general de personas. Un patrón aceptado no certifica el contexto. La densidad extrema y la complejidad global no han sido validadas en T6; T9 conserva las pruebas de escala. La fuga de asociación sin separadores de T5 tampoco se da por resuelta.

## Verificación

Ejecutada sobre las correcciones finales:

- `mvn -B -ntp -o verify`: **269 pruebas Java, cero fallos**, empaquetado correcto.
- Python: quality-review 57, T2 9, T5 6, T6 7, tools 81 y reports 5: **165 pruebas, cero fallos**.
- OpenAPI: 21 esquemas, 29 escenarios, 58 ejemplos y rechazo de 30 mutaciones inválidas.
- [Integridad](verification/integrity.json): manifiestos históricos preservados, diferencias de fuentes vivas/continuidad identificadas, inputs iniciales T5/T6 intactos y JAR congelados T4/T5/H5 preservados. Sin fixtures de estudio en el JAR de aplicación.

Registros completos en [verification/](verification/); hashes propios en `artifact-checksums.json`. Para reproducir después de compilar, usar salidas nuevas porque los exportadores rechazan sobrescritura:

```sh
java -cp 'target/t6-baseline/classes:target/test-classes:target/t6-baseline/lib/*' com.clinicalintelligence.extraction.testing.ContextStudy /tmp/t6-before-new.json
java -cp 'target/classes:target/test-classes:target/t6-baseline/lib/*' com.clinicalintelligence.extraction.testing.ContextStudy /tmp/t6-after-new.json
java -cp 'target/t6-baseline/classes:target/test-classes:target/t6-baseline/lib/*' com.clinicalintelligence.extraction.testing.ContextReviewStudy /tmp/t6-review-before-new.json
java -cp 'target/classes:target/test-classes:target/t6-baseline/lib/*' com.clinicalintelligence.extraction.testing.ContextReviewStudy /tmp/t6-review-after-new.json
/tmp/clinical-eval-next/bin/python evaluation/quality-review/t6/summarize_context.py
```

El resumen usa los JSON versionados. Valida contrato, igualdad exacta de propuestas/referencias, cobertura de escenarios y todas las entradas de desarrollo. El JAR T5 ignorado por Git puede reconstruirse desde el commit completo de baseline.json en un checkout aislado; identificar esa reconstrucción, cuyos bytes pueden diferir por empaquetado. Extraer `BOOT-INF/classes` y `BOOT-INF/lib` para usar ese classpath. No ejecutar `mvn clean` sobre este workspace con JAR congelados.

Siguiente paso mínimo: **T7**, preparar variantes de prompt/configuración reproducibles y alinear telemetría. No ejecutar T8 ni inferencia remota sin sus condiciones y autorización específicas.
