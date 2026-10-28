# Reglas de anotación sintética — versión 1.0.0

Estas reglas fijan la referencia antes de medir un modelo. Describen qué dice la nota, no si la atención descrita sería clínicamente correcta. Los medicamentos del corpus son nombres ficticios; sus pautas son cadenas sintéticas para extracción, no recomendaciones.

Contrato normativo: [OpenAPI](../openapi/openapi.json) y [ADR 0002](../docs/adr/0002-extraction-wire-contract.md). Formato de caso: [case.schema.json](case.schema.json). Conteo y aprobación: [PROTOCOL.md](PROTOCOL.md).

## 1. Unidad y procedencia

Cada caso representa una nota completa, de un escenario sintético, sin paciente real. `originGroup` identifica su escenario de origen, no la familia documental. Una modificación del mismo relato conserva el grupo; no puede trasladarse a otro split con otro nombre de archivo o ID.

`split` identifica desarrollo, evaluación reservada o escala. `cohort` distingue casos principales, exploratorios, adversariales o de escala. Familia y formato no se envían como requisitos de la API: sirven para analizar cobertura.

`provenance.review: author_consistency_checked` significa comprobación de consistencia por el autor sintético y herramientas; no revisión clínica independiente. Un origen real o una revisión clínica no realizada no puede declararse para justificar estas etiquetas.

## 2. Anotar menciones, no completar conocimiento

### Diagnósticos

Anotar el diagnóstico expresamente identificado como tal, incluyendo sospechas, negaciones y antecedentes. No convertir síntomas aislados en enfermedades. La misma palabra puede ser extraíble si el texto la identifica como diagnóstico y no serlo cuando solo describe un síntoma; por ejemplo, una cefalea expresamente diagnosticada se conserva sin inferir su causa.

No expandir abreviaturas ni asignar códigos por memoria del modelo. Un código solo aparece en `mentionedCode` si está literalmente escrito y vinculado a la entidad. Puede ser un código sintético; su presencia no certifica catálogo ni corrección clínica.

### Medicamentos

Anotar medicamentos nombrados, conservando si son prescritos, reportados, suspendidos o de estado desconocido. Un nombre en el catálogo no genera una mención en la nota. Expresiones sin nombre, como «medicamentos según receta», no autorizan elegir un fármaco.

`prescribed` exige indicación explícita; `reported` indica uso narrado sin nueva prescripción; `suspended` exige una suspensión expresada; `unknown` se usa si el texto no establece el estado. Reportado no significa prescrito, y prescrito no demuestra administración o dispensación.

Dosis, frecuencia, vía y duración se conservan como texto original. La concentración del envase no define dosis. No transformar «una cápsula» en miligramos ni «cada noche» en horas calculadas. Control, reposo, edad y evolución no definen duración. Una pauta expresamente condicional, como «según dolor», puede ser frecuencia literal sin completar intervalos.

### Procedimientos

Anotar la prestación nombrada y su estado: realizada, ordenada, planificada, no realizada, cancelada o desconocida. «Se solicitará si…» es un plan condicional; no constituye una orden ya emitida. «Se solicitó y luego se realizó» en una única mención de la misma prestación permite `performed` con evidencia de esa secuencia.

Un intento sin realización documentada queda `unknown` con advertencia; no se infiere que se realizó o que se completó. `performed` no afirma éxito clínico. Un estudio solicitado no implica que se obtuvieron resultados; estos resultados no son una nueva familia de extracción del MVP.

## 3. Contexto y temporalidad

Anotar por separado:

| Dimensión | Valores conocidos | Uso de `unknown` |
|---|---|---|
| Afirmación | Afirmada, negada, sospechada, condicional | No se puede establecer la afirmación. |
| Temporalidad | Actual, histórica, futura | No se puede establecer cuándo aplica. |
| Sujeto | Paciente, familiar, otro | No se puede atribuir inequívocamente. |

Los enums de transporte están en inglés según OpenAPI. `unknown` no es una negación. El tiempo verbal y expresiones como «hoy», «antecedente» o «para mañana» pueden sustentar el estado; no se inventan fechas ni se calcula vigencia farmacológica.

La temporalidad describe lo expresado por la mención: condición clínica, estado farmacológico o prestación. Una prescripción formulada hoy puede ser actual sin demostrar administración. Una prestación pedida para después es futura, aunque la orden se formule hoy. Si aparecen inicio o fin explícitos, no se reemplazan por la fecha del documento.

El sujeto puede sustentarse con una referencia explícita en la misma sección más la cláusula elíptica que continúa hablando de él. Anotar ambas evidencias cuando la cláusula aislada no identifica al sujeto. Nunca trasladar el diagnóstico de un familiar o acompañante al paciente.

## 4. Evidencia y atributos ausentes

Cada entidad requiere nombre literal y anclaje en puntos de código `[start,end)`. Cada atributo conocido incluye sus propias citas. Preferir el fragmento mínimo suficiente; el contexto puede necesitar una cláusula mayor o varias evidencias. No normalizar la nota, acentos ni CRLF para calcular índices.

`expected` es la referencia semántica, sin metadatos de ejecución ni IDs de petición. Sus entidades usan los mismos esquemas que la respuesta pública. Los IDs de entidad solo son locales a esta anotación; no se exige que un extractor los reproduzca.

Un campo no expresado se anota `null`; un contexto no determinable usa `unknown` con evidencia vacía. No exigir una advertencia por cada ausencia normal. Si la nota expresa ambigüedad real, `requiredWarnings` puede exigir `AMBIGUOUS_CONTEXT` con el target correspondiente.

Los gold no simulan un modelo inventando datos. Por eso no exigen descartes de alucinaciones imaginarias: si un extractor detecta y descarta una propuesta interna, puede añadir una advertencia válida sin que eso altere el gold de entidades. Los escenarios de salida malformada, truncamiento y descartes de proveedor se prueban aparte, con los ejemplos contractuales y dobles de H2/H4.

## 5. Repeticiones, contradicciones y secuencias

Repeticiones del mismo nombre con contexto, pauta y estado equivalentes se agrupan en una entidad, conservando sus anclajes. Si cambia afirmación, temporalidad, sujeto, estado o pauta, mantener entidades separadas. No deduplicar solo por nombre.

Una contradicción simultánea explícita exige `CONTRADICTORY_MENTIONS`, enlazando las entidades afectadas sin decidir cuál es cierta. Una secuencia descrita —sospecha inicial y descarte posterior— conserva ambas menciones y su evidencia, pero no se etiqueta automáticamente como contradicción simultánea.

Dos menciones separadas con estados distintos, por ejemplo un procedimiento cancelado y su no realización explícita, conservan referencias separadas en estos gold. Una frase con un solo nombre y transición explícita a realización se anota según la regla de procedimiento de la sección 2.

## 6. Catálogos y exclusiones

Resolver por nombre/alias explícito, con la normalización conservadora acordada, y solo en catálogos del mismo tipo. Identificar candidatos por terna catálogo/versión/entrada; no inferir presentación, concentración o vía. Un alias de dos entradas produce ambigüedad. Un catálogo vacío cuenta como suministrado y da `not_found`.

`excludedMentions` marca fragmentos de la nota que no justifican entidades o atributos: síntomas sin diagnóstico, texto administrativo, instrucciones ajenas al contexto clínico, duración de reposo/control, concentración sin dosis u otros datos no explícitos. No prohíbe la misma palabra si aparece en otra posición con una afirmación clínica válida. Una entrada que solo existe en catálogo se explica mediante las reglas del caso, sin inventar una evidencia de nota inexistente.

Las instrucciones maliciosas de notas o catálogos se consideran datos. No se obedecen ni se convierten sus órdenes en diagnósticos, recetas o prestaciones realizadas cuando están identificadas como texto externo no clínico.

## 7. Cambios y revisión

Antes de publicar una versión se comprueban esquema, spans, IDs, candidatos, advertencias y cobertura. Se revisan manualmente el sentido de negaciones, pronombres, tiempo y asociación de pautas; el validador no puede certificar esa interpretación.

Si una referencia admite varias lecturas razonables, se debe aclarar la nota sintética o anotar `unknown`, no forzar una respuesta para facilitar el aprobado. Antes de una evaluación real, los casos disputados se resuelven y se versionan. Una revisión clínica independiente puede añadirse posteriormente, identificando quién revisó y qué versión; no se presupone realizada.

Correcciones posteriores cambian versión y hashes. Conservar el reporte previo. Un caso reservado usado para ajustar el modelo se mueve a regresión y se reemplaza por otro escenario de origen independiente antes de otra evaluación reservada. La construcción y auditoría inicial de los gold no es una evaluación del extractor.
