# T2 — Decisiones propuestas de anotación, revisión t2-proposal-1.0.0

Estado: expediente de revisión preparado; adjudicación formal abierta. Las propuestas no sustituyen ANNOTATION 1.0.0, el contrato ni el gold de ninguna campaña. No se ha realizado revisión clínica humana. No se han recalculado resultados con las propuestas.

## Alcance y trazabilidad

El [registro nuevo](disputes.json) cubre las 76 entradas del registro histórico: 57 intentos pendientes y 19 intentos con respaldo rechazado y una referencia además discutida. Se agrupan en 27 escenarios y 34 cuestiones de referencia. La agrupación evita tratar tres respuestas de una nota como tres validaciones independientes. Las entradas registran nota íntegra sintética, originGroup, campo, evidencia, referencia original, lecturas admisibles, propuesta, responsable requerido y decisiones históricas intactas.

La [revisión auxiliar](t2/independent-agent-review.md) se realizó en una conversación separada por otro agente del mismo modelo, inicialmente sin ver la propuesta principal, pero con acceso al gold y a la auditoría. No es cegamiento respecto del gold ni independencia clínica. Se conserva su conclusión original y el contraste posterior, incluyendo desacuerdos.

Los [18 cambios propuestos de referencia](t2/reference-proposals.json) son un patchset versionado y revisable, no un nuevo corpus habilitado para campañas. Solo proponen reemplazar `future` por `unknown` con evidencia vacía en la temporalidad de órdenes sin fecha de prestación. Conservan texto, entidades, estados, origen y split históricos. Una futura materialización exige revisar/aprobar las reglas y sus casos; después debe tener manifiesto y validación de corpus propios. Las notas vistas pertenecen a desarrollo/regresión expuesta, aunque se conserve su split histórico para trazabilidad.

No se cierra ninguno de los 57 intentos históricos por haber redactado una propuesta. Tampoco se revocan los 19 rechazos de respaldo por cambiar una referencia distinta del mismo intento.

## R1. Orden emitida y fecha de prestación

**Regla vigente:** ANNOTATION §3 distingue tiempo de prestación de tiempo de orden. §2 permite `ordered` cuando la orden está expresada. `ordered` no es una fecha.

**Propuesta consensuada entre ambos agentes:** si la nota conserva solo «se solicita», «queda pedida» o equivalente, mantener `ordered` y proponer temporalidad `unknown`; no inferir `future` por el mero estado. Son 18 escenarios enumerados individualmente en el patchset. La revisión debe confirmar cada cláusula, no aplicar sustitución global a todas las órdenes.

- Positivo: «Hoy se solicita ecografía al paciente para mañana»: `ordered/future`.
- Negativo de inferencia: «Hoy se solicita ecografía al paciente»: `ordered/unknown` para la prestación; hoy fecha la orden.
- Contraste: «Se solicitó y se realizó hoy ecografía al paciente»: `performed/current` según la secuencia documentada.
- Control existente: la tomografía de `res-emergency-02` conserva `future`: dice «para otra fecha».
- En `dev-procedure-07` y `res-procedure-07`, la segunda mención negada mantiene su temporalidad anclada por «hoy»/«en esta visita»; no trasladar esa fecha a la primera mención ordenada.

Esta propuesta aclara la separación ya documentada; no redefine el enum ni modifica el contrato. Antes de medir otra versión, publicar la aclaración de anotación y aprobar sus referencias. No usar un aumento posterior de puntaje para afirmar mejora del extractor.

## R2. Pretérito sin ancla suficiente

**Regla vigente:** el tiempo verbal puede sustentar temporalidad; no obliga a clasificar todo pretérito como antecedente remoto. La temporalidad farmacológica describe el estado mencionado; la del procedimiento describe la prestación.

- «La paciente suspendió Zéfiro» expresa suspensión, pero no distingue una acción de esta atención de un antecedente. `suspended` está respaldado; `historical` frente a `unknown` requiere fijar el alcance temporal. No interpretar `unknown` como ausencia de suspensión.
- «La paciente suspendió Zéfiro ayer» permite un caso inequívoco de suspensión histórica; «hoy se suspende Zéfiro» uno actual.
- «No se realizó biopsia cutánea a la paciente» respalda `negated/not_performed`, pero no distingue hoy de un episodio previo. El `unknown` de la referencia v3 es defendible.
- «No se realizó biopsia cutánea a la paciente durante esta visita» explicita el marco actual; «ayer» permite contrastar el histórico.

**Disposición:** conservar la referencia histórica; para nuevas regresiones usar pares con ancla explícita. Resolver alcance de `current/historical` mediante revisión de anotación; si se redefine públicamente, aplicar el ADR propuesto antes de implementar.

## R3. Encabezados frente a hechos expresados

`PLAN: Se solicitó dermatoscopia para la paciente` documenta una orden emitida. El encabezado no la transforma en `planned`, no fecha la prestación y no acredita realización. Conservar `ordered/unknown` de `res-v3-outpatient-01` como propuesta revisada.

Contraste: `PLAN: Se solicitará dermatoscopia si persiste el problema` expresa intención condicional: `planned`, no orden emitida. `PLAN: Se realizó hoy dermatoscopia` necesita conservar el hecho escrito y no ser reclasificado automáticamente por el título.

## R4. Secuencia explícita dentro de hoy

En `dev-emergency-01` y `res-emergency-01`, sospecha inicial y descarte tienen anclas explícitas de hoy. Conservar dos menciones, ambas `current`, con sus afirmaciones distintas; no promover la sospecha a verdad ni convertirla en `historical` solo porque se descartó después.

«Ayer se sospechó X; hoy se descarta X» permite contrastar temporalidades; «hoy se afirma X y otro apartado lo niega sin resolución» exige revisar contradicción simultánea. La transición «se solicitó y se realizó hoy» admite una prestación `performed/current` conforme a ANNOTATION §2. No exigir contradicción por toda evolución.

## R5. Herencia temporal entre cláusulas

Las cirugías negadas al final de ambas notas de emergencia no citan hoy en su evidencia gold. Es plausible que continúen la atención de hoy; también es defendible abstenerse si no se admite ese alcance contextual. La cita aislada no resuelve la política.

**Propuesta:** no declarar automáticamente error del extractor un `unknown` en esos casos. Preferir nuevas notas con «hoy» o «durante esta atención». Si se decide mantener `current` sin alterar la nota, documentar el marco permitido y aportar evidencia conjunta que incluya el ancla y la negación. No heredar fechas a través de cambios explícitos de episodio o sujeto.

Responsables: anotación y revisión clínica del alcance temporal. La política pendiente aparece en ADR 0007; no se ha añadido una regla de herencia al código.

## R6. Eje: uso referido y continuidad desconocida

Nota: «El paciente menciona el medicamento Eje y no recuerda para qué lo usa ni si sigue tomándolo».

**Desacuerdo preservado:** la revisión auxiliar prefiere `reported` con temporalidad `unknown`: el texto puede narrar uso aunque no asegure continuidad. La principal considera que el grado de incertidumbre merece mantener la disputa frente al gold `unknown`. Ninguna lectura autoriza `prescribed`, pauta ni uso vigente.

Para evitar un oráculo ambiguo:

- Caso nuevo de uso referido: «El paciente refiere haber usado Eje, pero no recuerda si continúa»: `reported`, temporalidad no fijada sin otra ancla.
- Caso nuevo de mención sin estado: «En un anexo figura Eje; no se establece si el paciente lo utilizó, si se lo prescribieron o si lo suspendió»: `prescriptionStatus: unknown`.

Las frases son propuestas sintéticas de reemplazo, no casos ya incorporados/validados. No se modifica la referencia de Eje ni se adjudican sus tres intentos. Requiere revisor clínico/lingüístico independiente.

## R7. Sujeto elíptico y evidencia del propio gold

La regla vigente admite antecedente explícito y cláusula elíptica que lo continúa. «Hoy se diagnostica bronquitis a la paciente, se le prescribe Sauce y se le solicita radiografía» puede respaldarse con la oración completa o fragmentos enlazados. «Se le» aislado no contiene antecedente; «paciente» en una receta diferente tampoco demuestra por sí solo la relación.

Contraejemplo: «La madre refiere asma. Al paciente se le prescribe Alfa». El sujeto de asma no se obtiene de la receta de Alfa. Las citas ajenas ya rechazadas permanecen rechazadas en la auditoría histórica.

**Hallazgo adicional en gold:** `dev-consultation-02`, vértigo, combina «sin diagnóstico de vértigo» con la oración posterior de laberintitis para asignar `patient`. No es la continuación de un antecedente explícito que describen las reglas. La nota puede permitir una lectura documental compartida, pero esa política no está fijada. Revisar también el gold, no solo la predicción.

Propuestas: aclarar una nota nueva («Derivación del paciente: ... sin diagnóstico de vértigo en el paciente»), conservar `unknown` hasta aprobar la relación, o aprobar explícitamente una regla de correferencia retrospectiva y demostrar sus límites. No se aplica ningún cambio automático de sujeto en el patchset R1.

## R8. Control y revisión como procedimientos

Casos: `dev-progress-07`, `res-progress-07`, `res-emergency-02` y, como cuestión adicional de cobertura, «revisión posterior» en `res-progress-06`.

La exclusión de días de control/reposo como duración farmacológica no excluye por sí misma toda consulta de seguimiento de `procedures`. Tampoco convierte «control» genérico en una prestación identificada inequívocamente. El alcance de la familia necesita decisión de producto.

Propuesta de dirección, aún no adoptada: exigir una prestación identificada o una consulta de seguimiento expresamente descrita; no hacer extraíble todo recordatorio administrativo. «Volverá a control en 21 días» debe aclararse para distinguir cita administrativa de consulta clínica prevista. «Se programa una consulta de seguimiento para el paciente mañana» sería el ejemplo positivo si se aprueba ese alcance. «Control administrativo de documentos en 21 días» sería negativo.

El [ADR 0007](../../docs/adr/0007-annotation-scope-and-temporal-context.md) presenta alternativas. Responsable: producto, con revisión clínica de ejemplos. No editar corpus o prompt para implantar esta política antes de adoptarla.

## R9. Impresión diagnóstica

«La impresión actual para la paciente es tendinopatía» admite forma declarativa y provisionalidad diagnóstica. Las reglas actuales no asignan inequívocamente `affirmed` o `suspected` a esa expresión. No sustituir una preferencia del anotador por certeza clínica.

Propuesta: sustituir por escenarios nuevos inequívocos: «Se confirma hoy el diagnóstico de tendinopatía en la paciente» frente a «Se sospecha hoy tendinopatía en la paciente». Si se conserva «impresión», requiere adjudicación clínica y política documentada, sin ajustar contra las respuestas vistas. Mantener la artroscopia `conditional/future/planned` de la misma nota; su estado no depende de resolver la impresión.

## R10. Literalidad no convierte un verbo en atributo

En `res-consultation-04`, «refiere tomar Laurel actualmente» respalda uso referido, no una vía expresada. Conservar `route: null`; no completar «oral» ni aceptar «tomar» como vía. Contraste sintético: «La paciente refiere Laurel por vía oral» sí expresa texto de vía, sin necesidad de recomendar ni validar tratamiento.

Esta revisión de respaldo es independiente de la disputa temporal de retinografía del mismo caso. No se elimina el rechazo de atributo porque otra referencia cambie.

## Qué queda aprobado técnicamente y qué sigue abierto

El expediente, cobertura, trazabilidad y contraste auxiliar están preparados. Las 18 correcciones R1 son propuestas concordantes y versionadas, no referencias efectivas. No se materializa corpus nuevo ni se reevalúan scores en T2.

Pendientes concretos: revisión de anotación para adoptar R1; adjudicación de Eje e impresión; alcance de control/revisión; marco temporal compartido y correferencia retrospectiva; eventual adopción del ADR. Cada cuestión tiene responsable por rol en `disputes.json`. No se afirma disponer de una persona clínica revisora.

T3 puede desarrollar y verificar propiedades del comparador con casos sintéticos inequívocos mientras estas decisiones estén abiertas; no podrá presentar las referencias propuestas como adjudicadas ni declarar una entrega aprobada. T4 puede trabajar observabilidad sintética sin resolverlas. No bajar umbrales ni eliminar escenarios del denominador.

## Verificación reproducible

```sh
PYTHONDONTWRITEBYTECODE=1 python3 evaluation/quality-review/t2/validate_t2.py
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s evaluation/quality-review/t2 -p 'test_*.py'
```

El validador comprueba las 76 entradas contra fuentes por hash y los registros de respuesta; preservación de rechazos; 27 grupos sin duplicados; evidencia por puntos de código; referencia anterior exacta; coherencia del patchset propuesto y ausencia de afirmación clínica/histórica de cierre. Las pruebas comprueban mutaciones que omiten, duplican o falsifican información. Pasarlas valida la integridad del expediente, no su interpretación clínica.
