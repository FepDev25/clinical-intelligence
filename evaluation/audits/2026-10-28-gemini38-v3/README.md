# Auditoría textual parcial — campaña v3

Se examinaron las 234 notas/respuestas mediante resúmenes de entidades, estados y posiciones de citas, y se contrastaron los casos discutidos con las reglas sintéticas y referencias. La revisión es del agente que construyó la iteración, sin cegamiento ni revisión clínica independiente. Una decisión `support_verified` indica respaldo textual de lo expuesto; no significa que no haya omisiones, que se hayan satisfecho todos los criterios o que exista validación clínica. Las omisiones y advertencias continúan en los conteos automáticos.

Estado: **171 intentos con decisión**: 111 `support_verified` y 60 `support_failed`. Otros **63 están `pending`** y la auditoría no está completa. En desarrollo hay 70 verificados, 24 con respaldo rechazado y 23 pendientes; en reservado, 41, 36 y 40 respectivamente. `support_failed` es una decisión por intento, no el número de pacientes o errores clínicos.

Los 63 pendientes incluyen solicitudes sin fecha explícita de la prestación cuya referencia fija `future`, una secuencia temporal de atención, referencias que pueden admitir más de una interpretación, y el alcance de control como prestación. En los dos casos nuevos persiste la disputa de temporalidad basada en verbos pasados y el encabezado PLAN; en uno de ellos la cita del sujeto del diagnóstico apunta además a otra cláusula. Una decisión negativa sobre una cita en un intento no resuelve automáticamente cualquier otra disputa anotada en sus notas. No se ha cambiado el gold ni se han seleccionado rondas favorables.

Hallazgos textuales revisados:

- Citas breves como «paciente», «hoy» o «actual» se tomaron de cláusulas de otras entidades. Se rechaza el respaldo de ese campo; no se afirma por ello que el valor se atribuya a otra persona.
- En `dev-procedure-03` ronda 1 y `res-procedure-06` ronda 2, `assertion=affirmed` contradice una negación, aunque `performanceStatus=not_performed` se conserva. No se afirma que la respuesta declarara realizada la prestación.
- En `res-consultation-04` rondas 1 y 3, se devuelve «tomar» como vía de administración; la nota no expresa una vía.
- Hay sujetos y entidades ausentes, por ejemplo Violeta en `res-procedure-03`. Las ausencias se cuentan como omisiones; la auditoría de respaldo no las convierte en aciertos.
- En las notas adversariales examinadas la salida excluyó los nombres presentes en instrucciones ajenas. Esto describe esas observaciones, no demuestra inmunidad general a instrucciones maliciosas.

`semantic-audit.json` registra una decisión por intento y vincula cada una al hash canónico de la respuesta, además de los hashes de `freeze.json` y `responses.jsonl`. `pending` no cuenta como revisión terminada. El análisis y `release-decision.json` en esta carpeta se recalcularon sobre una copia de los activos originales, sin llamadas al proveedor ni alteración de la campaña. El resultado es **no aprobado**, con auditoría incompleta. Los conteos auditados son provisionales porque faltan decisiones; no deben compararse directamente como métricas finales de calidad clínica.

El resultado original automático sigue en `evaluation/runs/2026-10-28-gemini38-v3/`. Para completar la auditoría hay que adjudicar los 63 pendientes y revisar las notas que registran dudas de referencia incluso en intentos con otro respaldo rechazado, sin modificar el corpus histórico. Si el reservado orienta ajustes del extractor, sus escenarios deben trasladarse a regresión y sustituirse antes de una nueva evaluación independiente.
