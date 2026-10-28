# Notas metodológicas de la ejecución congelada v1

Las dos primeras incidencias se registraron durante desarrollo, antes de medir reservado. La corrección de decisión agregada se añadió después de completar las mediciones. Se preservan corpus, código congelado, respuestas y conteos originales. No se cambia el extractor ni se rebajan umbrales. Estas notas no autorizan aprobación ni sustituyen una nueva versión de los artefactos afectados.

## Advertencias: agrupación exacta frente a cobertura de targets

En `dev-consultation-06`, el gold requiere `AMBIGUOUS_CONTEXT` para `/diagnoses/2/context/assertion`. Una respuesta incluye esa misma advertencia con assertion, temporality y subject en un único grupo; las tres incertidumbres están expresas en la nota. El comparador v1 usa igualdad del conjunto completo del grupo y marca un faltante y una advertencia semántica adicional. La advertencia sí cubre el target requerido.

Por tanto, `missingWarnings` de v1 se interpreta como diferencias de coincidencia exacta, **no exclusivamente como ausencia semántica**. Una próxima versión debe comparar cobertura por código/target y probar agrupaciones equivalentes, conservando el reporte original. La revisión puede reconocer que la advertencia adicional es válida; no altera silenciosamente los conteos congelados.

## Anclaje de una mención de desarrollo

En `dev-outpatient-05`, Brisa aparece primero en la concentración del envase y después en la prescripción de media tableta. El único anclaje gold del nombre apunta a la primera aparición, aunque su estado y dosis corresponden a la prescripción. Una respuesta que separa ambas menciones puede dejar la prescripción correcta sin alinear. El conteo literal puede subestimar recuperación en este caso; no se debe atribuir toda su penalización al extractor.

La concentración del envase presentada como dosis sigue siendo un error real distinto de ese problema de anclaje. Corregir la referencia exige una revisión y versión del corpus; no editarla después de medir para elevar la puntuación. Este escenario ya pertenece a desarrollo y puede convertirse en una regresión explícita de anclajes en una iteración posterior.

## Auditoría del respaldo

Los porcentajes automáticos describen coincidencia con referencias sintéticas bajo reglas determinadas. No certifican que una cita mínima se atribuya a la entidad correcta. La auditoría asociada registra rechazos de entidades/atributos/contextos y resoluciones justificadas de variantes textuales; conserva las métricas léxicas de esas variantes y no reasigna entidades ambiguas por contexto favorable.

La auditoría del agente es consistencia textual frente a notas/reglas sintéticas, sin revisión clínica independiente. Las incidencias anteriores limitan la interpretación fina de algunas cifras, pero no eliminan los fallos críticos observados en instrucciones no clínicas, dosis o promociones de contexto.

## Alcance de la decisión agregada — revisión 1.0.1

Después de medir se identificó que `summary.pass` del runner congelado comprueba las rondas reservadas, pero no exige que las revisiones/cero tolerancias de desarrollo estén resueltas. Su propia `semanticAuditComplete` global podía ser falsa sin formar parte de esa expresión. No se usa ese indicador como decisión final de entrega.

El control separado [release.py](release.py), con cinco pruebas de conteos ficticios y hash propio, recompone los puntajes mediante el runner congelado y aplica las condiciones originales de auditoría y cero tolerancias a ambos splits. No altera métricas, referencias, umbrales ni proveedor y no dispara inferencia. La decisión final y sus hashes se conservan como revisión 1.0.1, junto a fuente/pruebas de ese control. Tanto las métricas originales como la decisión corregida rechazan esta entrega.
