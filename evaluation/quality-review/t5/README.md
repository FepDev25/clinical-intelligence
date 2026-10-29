# T5 — Asociación de atributos, códigos y menciones

Implementada y verificada offline el 28-10-2026, sobre T4 (`02841c9`). Atiende L02/L04/L05 y parte de L13 del [plan aprobado](../../../docs/superpowers/plans/2026-10-28-extraction-quality-review-and-remediation.md). El MVP sigue sin aprobación; T2 conserva adjudicación abierta y T6–T10 siguen pendientes.

## Cambio y alcance

`MentionAssociationPolicy` comprueba la relación estructural entre las apariciones citadas del nombre y del atributo: unidades, continuidad explícita, cabezas interpuestas y relaciones compartidas restringidas. La literalidad y el scope de afirmación/prescripción anteriores se mantienen. Los códigos mencionados reciben su propia comprobación, independiente del catálogo. Se elimina únicamente el atributo rechazado, conservando entidad y otros campos válidos; no se completa información.

`ExtractionService` comunica `validationVersion=evidence-3`. Los descartes usan `ATTRIBUTE_DISCARDED`; la traza distingue `TEXT_ASSOCIATION_REJECTED` para códigos y conserva `MEDICATION_ASSOCIATION_REJECTED` para pautas. No cambian campos públicos, prompt, modelo, límites ni resolución de catálogos. No se incorpora almacenamiento, retención de notas ni llamadas remotas. El log legado de `ApiConfiguration` todavía dice evidence-1: su corrección está registrada en T7 y no representa la versión efectiva de la respuesta.

La [política](policy.md) delimita reglas y excepciones. Los índices externos siguen siendo puntos de código; NFC y normalización de espacios se usan solo en copias para predicados, conservando nota, valores y evidencia originales. Anclas superpuestas, ocurrencias repetidas, emoji, NFD, CRLF y NBSP tienen regresiones.

## Comparación antes/después

Se ejecutaron las mismas propuestas sintéticas contra **el núcleo T4 compilado real**, preservado antes de modificar código, y contra T5. [baseline.json](baseline.json) identifica commit/JAR/hashes; [study-inputs.json](study-inputs.json) fija los 30 escenarios iniciales y exportador. Los seis casos suplementarios provienen de revisión posterior y se reportan por separado, sin presentarlos como referencias previas al diseño.

| Referencias de campo | T4 | T5 |
| --- | ---: | ---: |
| Positivos retenidos | 28/28 | 28/28 |
| Falsos descartes positivos | 0/28 | 0/28 |
| Asociaciones incorrectas previstas bloqueadas | 0/21 | 21/21 |
| Fuga residual declarada | 1/1 | 1/1 |

Son **36 escenarios y 50 referencias de campo**, incluyendo la residual. En total permanecen 1 de 22 asociaciones incorrectas. [summary.json](summary.json) conserva resultados por campo, denominadores y separación inicial/revisión. No son precisión de extracción ni mejora del modelo: el proveedor es un doble con propuestas fijas, y no se agregó un umbral de aprobación después de observar resultados.

Controles positivos: recetas en líneas separadas, etiquetas y pronombres, dosis iguales, frecuencia compartida explícita, envase frente a dosis, nombres compuestos, códigos antepuestos, catálogo independiente y formatos Unicode. Los negativos incluyen dosis/códigos de otra entidad, citas amplias/repetidas, continuaciones contaminadas y tercer nombre interpuesto.

**Límite observado:** «Alfa 5 mg Beta 9 mg», con Beta omitido de la propuesta y sin separadores, todavía permite tomar 9 mg para Alfa. El guard no descubre todos los nombres ni resuelve semántica clínica general. Otras construcciones fuera de su gramática pueden causar falsos descartes; cero en estos controles no garantiza cero en uso real. Negación, sujeto, tiempo, estados y contradicciones corresponden a T6; no se implementa el ADR 0007 ni se certifica una asociación por pasar esta política.

## Evidencia y revisión

[Revisión](review.md) registra hallazgos adicionales y correcciones. [verification/](verification/) conserva rojos, verdes, un fallo de integración conocido y la ejecución final. Los exportes intermedios se conservan con sus nombres; los resultados finales son `after-reviewed.json` y `review-after.json`. Los `before` siguen intactos.

La suite completa detectó que `DevelopmentCorpusTest` cargaba el gold histórico v1 de Brisa: la evidencia de duración apuntaba al envase. T5 rechaza esa asociación. La prueba de desarrollo ahora carga **la revisión 1.0.1 ya existente** de `evaluation/next/development.json`, documentada antes de T5. No se alteraron ninguno de los golds ni sus hashes; el fallo queda en `verification/java-final.log`. El recurso Maven nuevo incluye únicamente development.json, sin reservado ni recursos en el JAR de producción.

Se investigó coste cuadrático del primer diseño: `investigation/` preserva código anterior, scripts y mediciones locales. Índices por unidad/grupo y caché de anclas/continuaciones redujeron una sonda de 4000 referencias desde aproximadamente 1399 ms hasta 3.57 ms en `supports`; otra forma con 800 continuaciones pasó de 584 ms a 39 ms. Son microsondas con JIT y ruido, **no** capacidad de producción ni SLA. No se redujeron límites técnicos. Robustez y escala sistemáticas siguen en T9.

## Verificación y reproducción

Ejecutados sobre la versión final:

- `mvn -B -ntp -o verify`: **195 pruebas Java**, sin fallos, compilación y empaquetado correctos.
- Python: quality-review 57, T2 9, tools 81, reports 5, T5 6: **158**, sin fallos.
- Validación OpenAPI: 21 esquemas, 29 escenarios, 58 ejemplos; 30 mutaciones inválidas rechazadas.
- [Integridad](verification/integrity.json): artefactos históricos y propuestas iniciales preservados; diferencias de fuentes vivas registradas sin actualizar hashes históricos. Los dos JAR H5 y el JAR T4 siguen intactos.

Para reproducir después de compilar, usar rutas de salida **nuevas** (los exportadores rechazan sobrescritura):

```sh
java -cp 'target/t5-baseline/classes:target/test-classes:target/t5-baseline/lib/*' com.clinicalintelligence.extraction.testing.AssociationStudy /tmp/t5-before-new.json
java -cp 'target/classes:target/test-classes:target/t5-baseline/lib/*' com.clinicalintelligence.extraction.testing.AssociationStudy /tmp/t5-after-new.json
java -cp 'target/t5-baseline/classes:target/test-classes:target/t5-baseline/lib/*' com.clinicalintelligence.extraction.testing.AssociationReviewStudy /tmp/t5-review-before-new.json
java -cp 'target/classes:target/test-classes:target/t5-baseline/lib/*' com.clinicalintelligence.extraction.testing.AssociationReviewStudy /tmp/t5-review-after-new.json
/tmp/clinical-eval-next/bin/python evaluation/quality-review/t5/summarize.py
```

El JAR T4 ignorado por Git puede reconstruirse desde el commit completo de baseline.json en un checkout aislado. Extraer `BOOT-INF/classes` y `BOOT-INF/lib` al directorio baseline; la reconstrucción puede diferir en bytes por empaquetado y debe identificarse como tal. Los JSON de resultados contienen entradas, propuestas y salidas para inspección sin ese JAR. La comparación ignora solo metadatos no usados para puntuar; valida igualdad de notas/propuestas/catálogos/referencias y contrato de las respuestas. No ejecutar `mvn clean` sobre el workspace con JAR congelados.

Siguiente paso mínimo: T6 con pares contrastantes y medición explícita de falsos rechazos; la campaña remota requiere condiciones y autorización propias, posteriores.
