# T2 — Revisión textual auxiliar por otro agente

Fecha: 2026-10-28. Alcance: 27 escenarios asociados a las 57 decisiones pendientes del registro `evaluation/audits/2026-10-28-gemini38-v3-revision2/dispute-register.json`, leídos en `evaluation/next/development.json` y `evaluation/corpus/v3/reserved.json`, junto con `AGENTS.md`, `evaluation/ANNOTATION.md` y ADR 0002.

Esta revisión fue realizada por otro agente del mismo modelo. No es revisión clínica humana ni evaluación ciega: el revisor vio los gold y los motivos del registro. Las conclusiones de las secciones 1–7 se enviaron al agente principal antes de recibir su propuesta preliminar. El contraste final se añadió después de conocerla. No se examinó la corrección clínica real de la atención ficticia ni se ejecutó inferencia remota. No se abrieron secretos ni `.env`. No se modificaron gold, respuestas, puntuaciones ni auditorías históricas. Este documento no adjudica ni cierra las 57 decisiones.

## 1. Orden sin fecha: estado de orden y tiempo de prestación

ANNOTATION §3 distingue el tiempo de la prestación del momento de emitir la orden. «Pedida para después» sustenta `future`; «se solicita» sustenta `ordered`, pero no expresa necesariamente cuándo se realizará.

Para los siguientes 18 escenarios propongo conservar entidad, afirmación, sujeto y `ordered`; en una nueva referencia, usar temporalidad `unknown` si se conserva la nota, o añadir una indicación inequívoca —«para mañana», «para otra fecha»— si se quiere evaluar `future`:

| Escenarios | Prestación |
|---|---|
| `dev-outpatient-02`, `dev-outpatient-05` | fondo de ojo; rinoscopia |
| `dev-progress-04`, `dev-progress-06` | gastroscopia; radiografía lumbar |
| `dev-consultation-02`, `dev-consultation-04`, `dev-consultation-06` | audiometría; espirometría; ecografía renal |
| `dev-procedure-07` | primera mención de ecografía |
| `res-outpatient-02`, `res-outpatient-05` | radiografía torácica; limpieza dental |
| `res-progress-02`, `res-progress-04`, `res-progress-06` | doppler venoso; biopsia de piel; resonancia de rodilla |
| `res-consultation-02`, `res-consultation-03`, `res-consultation-04`, `res-consultation-06` | electromiografía; laringoscopia; retinografía; ecografía abdominal |
| `res-procedure-07` | primera mención de resonancia |

En `dev-procedure-07` y `res-procedure-07`, conservar la segunda mención como `negated/current/not_performed`: «hoy» y «en esta visita» sí anclan la no realización. La segunda mención no fecha por sí sola la realización de la orden.

En `res-emergency-02`, conservar `future` para tomografía: «para otra fecha» sí expresa posterioridad.

No hace falta modificar el contrato para aplicar la separación ya descrita. Conviene una aclaración versionada que prohíba equiparar automáticamente `ordered` con `future`. La revisión histórica no se altera.

## 2. Pasado sin ancla y encabezado PLAN

En `res-v3-outpatient-01`, conservar dermatitis `historical`, suspensión de Zéfiro y dermatoscopia `ordered`. El encabezado `PLAN` no deshace «se solicitó» ni convierte una orden emitida en simple `planned`. Tampoco fecha su ejecución. La temporalidad `unknown` de dermatoscopia es defendible y coherente con la sección 1.

Para Zéfiro, `suspendió` expresa una transición anterior al momento de narración, pero no establece si ocurrió durante la atención actual o en un antecedente. `unknown` es una opción conservadora defendible. No consideraría `historical` inequívocamente incorrecto sin fijar mejor qué representa la temporalidad del estado farmacológico.

En `res-v3-procedure-05`, conservar biopsia `negated/not_performed`. «No se realizó» no distingue antecedente remoto de esta atención; conservar `unknown` o aclarar «hoy/en esta visita» en una nota nueva.

La regla dice que el tiempo verbal puede sustentar temporalidad; no ordena convertir todo pretérito en `historical`. El umbral entre un evento anterior dentro de la atención actual y un antecedente necesita aclaración normativa. Si cambia el significado público de `temporality`, corresponde ADR. No todo `unknown` es un error de gold.

## 3. Sospecha inicial, descarte hoy y procedimiento negado en emergencia

En `dev-emergency-01` y `res-emergency-01`:

- Conservar sospecha inicial y descarte como entidades separadas.
- Conservar ambas temporalidades `current`: «inicialmente hoy» y «a las primeras horas de hoy» son anclas explícitas. El descarte posterior no convierte automáticamente la sospecha en antecedente histórico.
- No exigir `CONTRADICTORY_MENTIONS` por esa secuencia.
- Conservar ecografía y angiotomografía `performed/current`, conforme a la transición documentada en una misma mención.
- La temporalidad de cirugía/toracotomía negada al final admite dos lecturas: continuidad de la atención de hoy o falta de ancla local. El gold `current` es contextual y plausible, pero su evidencia aislada no incluye «hoy». No lo cerraría como error inequívoco del extractor si responde `unknown`.

Para nuevas notas, añadir «hoy» o «durante esta atención» a la negación. Para notas sin cambios, documentar primero el alcance permitido de la anáfora temporal y respaldarlo con evidencia conjunta. No convertir la plausibilidad contextual en adjudicación histórica automática.

## 4. Eje: unknown frente a reported

`dev-admission-02` dice que el paciente no recuerda «para qué lo usa ni si sigue tomándolo». Eso admite uso narrado con continuidad desconocida. `reported` no exige uso actual: esa dimensión pertenece a `temporality`.

La propuesta preferida de este revisor para una nueva referencia que conserve la frase es `reported` con temporalidad `unknown`. No hay respaldo para `prescribed`. Conservar nombre, sujeto y ausencia de pauta.

Sin embargo, no trataría la revisión histórica como cerrada: «menciona el medicamento» aislado sería insuficiente y existe tensión con la continuación «lo usa». Para un caso inequívoco de `unknown`, redactar «menciona Eje, pero no puede establecerse si lo utilizó, si se lo prescribieron o si lo suspendió». Para `reported`, «refiere haber usado Eje; no recuerda si continúa». Esta preferencia del revisor no es una resolución acordada.

## 5. Impresión de tendinopatía

En `res-consultation-05`, conservar entidad, sujeto y temporalidad actual. «La impresión actual … es tendinopatía» tiene forma afirmativa, pero “impresión” puede comunicar provisionalidad diagnóstica. Las reglas actuales no fijan esa correspondencia.

No adjudicaría `affirmed` contra `suspected` mediante preferencia del agente. Hace falta una decisión de anotación revisada por una persona con competencia clínica. Para nuevas notas, usar «diagnóstico confirmado» o «se sospecha» según el objetivo. Conservar artroscopia `conditional/future/planned`.

## 6. Control o revisión como procedimiento

Afecta a `dev-progress-07`, `res-progress-07`, `res-emergency-02` y adicionalmente «revisión posterior» en `res-progress-06`.

La regla existente establece que control/reposo no son duración farmacológica. No equivale a excluir universalmente toda consulta de seguimiento de `procedures`. La definición «prestación nombrada» tampoco precisa si control genérico constituye una prestación suficientemente identificada.

Conservar esas expresiones como exclusiones de duración donde están así anotadas. No promoverlas automáticamente a procedimientos ni declarar definitivamente que cualquier extracción es espuria. El alcance necesita una decisión de producto documentada, idealmente ADR: qué prestaciones incluye la API y qué nivel de especificidad exige. Después, ejemplos positivos y negativos versionados. El término adicional de `res-progress-06` debe entrar en la revisión de alcance aunque la disputa registrada de ese escenario se concentre en temporalidad.

## 7. Sujeto elíptico y asociación de evidencias

La regla vigente permite un antecedente explícito en la misma sección más la cláusula elíptica vinculada. Un token «paciente» de otra cláusula, sin la relación, no respalda por sí solo el atributo.

- En `res-outpatient-02`, conservar sujetos de Sauce y radiografía como `patient`: la oración completa contiene antecedente y «se le». Una cita aislada «se le» es insuficiente.
- Los errores registrados de citas tomadas de otras cláusulas en `dev-emergency-01`, `dev-admission-02`, `res-outpatient-05`, `res-consultation-05`, `res-progress-04`, `res-emergency-02`, `dev-progress-07` y `res-v3-procedure-05` no se justifican porque el valor final sea plausible.
- Excepción del propio gold: en `dev-consultation-02`, vértigo obtiene `patient` combinando «sin diagnóstico de vértigo» con la oración posterior sobre laberintitis. No es el patrón de continuación de un antecedente explícito descrito en ANNOTATION. Para versión nueva, explicitar «derivación del paciente … sin diagnóstico de vértigo en el paciente», o conservar sujeto `unknown` hasta aprobar una regla de correferencia retrospectiva.
- En `dev-consultation-02`, conservar temporalidad `unknown` para vértigo: la derivación y la negación no están fechadas.

En `res-consultation-04`, «tomar» no respalda vía de administración. Su literalidad no lo convierte en una vía; conservar `route: null`.

## Contraste posterior con la propuesta preliminar del agente principal

Este apartado se redactó después de recibir la propuesta principal; las conclusiones precedentes ya se habían entregado. El contraste no constituye una segunda revisión ciega.

Hay acuerdo en separar `ordered` sin fecha de `planned`, no trasladar automáticamente el tiempo de la orden a la prestación, conservar por separado sospecha y descarte explícitos de hoy como `current`, mantener pendiente la certeza de “impresión” y proponer una política de alcance antes de adoptar control/revisión como procedimiento.

La diferencia de preferencia está en Eje: este revisor prefiere `reported/unknown` para una versión nueva que conserve la frase completa; el agente principal prefiere conservar la disputa y `unknown` hasta aclarar. Ambas propuestas mantienen la auditoría histórica abierta. La salida operativa compatible es crear notas nuevas inequívocas y registrar ambas lecturas, sin declarar consenso sobre la frase original.

Hay un matiz en las negaciones finales de emergencia: este revisor considera plausible la lectura `current` por continuidad narrativa, pero insuficiente para cerrar la disputa con la evidencia local aislada. El principal propone aclaración o `unknown`, sin heredar un «hoy» distante. No existe desacuerdo sobre la necesidad de explicitar el ancla en notas nuevas ni sobre evitar el cierre automático.

No debe llamarse error de gold a todo `unknown`: las fechas ausentes de suspensión, solicitud o no realización pueden estar genuinamente indeterminadas. Tampoco debe usarse esa prudencia para aceptar cualquier evidencia mal asociada del extractor.

Se incorporan dos hallazgos adicionales: «revisión posterior» de `res-progress-06` requiere entrar en la decisión de alcance; el sujeto gold de vértigo en `dev-consultation-02` requiere revisión por usar una referencia posterior, sin modificar la referencia histórica.

## Pendiente y siguiente paso mínimo

Contrastar estas propuestas, preservar desacuerdos y obtener adjudicación humana sobre impresión diagnóstica y una decisión explícita de producto sobre alcance de procedimientos. Versionar aclaraciones y variantes sintéticas antes de otra evaluación. Los reservados utilizados para orientar cambios deben pasar a regresión y sustituirse por escenarios independientes. Esta revisión no recalcula un aprobado ni cierra las 57 decisiones.

Verificación realizada: lectura de reglas, contrato, registro y notas/gold de los 27 escenarios. No se ejecutaron pruebas de software porque la tarea fue una revisión textual; ninguna prueba automatizada certificaría estas interpretaciones semánticas.
