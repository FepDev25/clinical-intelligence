# ADR 0007 — Alcance de prestaciones y contexto temporal en anotación

Fecha: 28-10-2026. **Estado: propuesto; no adoptado ni implementado.**

Contexto: [T2 y decisiones propuestas](../../evaluation/quality-review/annotation-decisions.md). Mantener contrato 0.1.0 y gold históricos. La adjudicación clínica humana sigue pendiente.

## Problema

La definición de «prestación nombrada» no decide si control/revisión genéricos incluyen una consulta de seguimiento o son solo recordatorios administrativos. Además, las referencias no fijan suficientemente el alcance de una fecha de episodio compartida ni la atribución retrospectiva de sujeto. Adoptar reglas automáticas sin una decisión previa cambiaría qué resultados se consideran correctos y podría alterar semántica pública.

La separación entre orden emitida y fecha de prestación ya existe en ANNOTATION §3. No se propone redefinirla: `ordered` sin fecha no basta para exigir `future`. Esta aclaración se versionará al aprobar las referencias, preservando las anteriores.

## Opciones y consecuencias

1. **Mantener alcance estrecho y notas inequívocas.** Conservar procedimientos identificados; excluir recordatorios genéricos salvo prestación/consulta explícita. Aclarar notas nuevas con sujeto y tiempo expresos. Reduce desacuerdos de evaluación, pero deja documentados límites ante textos ambiguos reales; no permite fingir que desapareció el problema del producto.
2. **Incluir consultas de seguimiento y contexto documental delimitado.** Definir cuándo control identifica una consulta, cuándo un encabezado establece el episodio y qué evidencias múltiples permiten heredar tiempo/sujeto. Mejora cobertura potencial, pero exige contraejemplos con cambios de paciente/episodio y revisión de contrato, ejemplos y corpus. No basta buscar palabras clave.
3. **Ampliar representación pública.** Separar tiempo de orden/acto, estados históricos y relaciones de correferencia mediante nuevos campos. Mayor expresividad, a costa de versionado y complejidad para integradores; no se justifica implementarlo antes de probar si 0.1.0 con evidencia suficiente resuelve la necesidad.

## Recomendación propuesta

Preparar regresiones inequívocas bajo opción 1 y estudiar opción 2 en desarrollo con revisión de producto/clínica. No añadir campos públicos todavía. Si se adopta opción 2, expresar límites y ejemplos antes de modificar prompt, gold o validadores. «Impresión» y el uso incierto de Eje siguen requiriendo adjudicación; este ADR no decide por sí solo esas etiquetas.

## Condiciones para adoptar

- Responsable de producto decide alcance de consultas de seguimiento y documenta ejemplos positivos/negativos.
- Responsable de anotación y revisor clínico precisan `current/historical`, evidencia conjunta y cambio de episodio.
- Se resuelve si correferencia retrospectiva está admitida; no confundir una nota de paciente único con prueba suficiente de cada relación.
- Revisar si cambia semántica pública: si la cambia, actualizar contrato/versionado, guías y pruebas antes de implantar; si solo aclara anotación, identificar la revisión de reglas y corpus.
- No usar los puntajes ya observados para elegir la política favorable. Ninguna opción altera decisiones de entrega históricas.

## Situación actual

No hay decisión adoptada. Sin cambios de OpenAPI, implementación ni inferencias. Las propuestas de T2 permiten continuar pruebas deterministas de T3/T4; no habilitan una campaña independiente hasta cerrar las decisiones que afecten sus referencias.
