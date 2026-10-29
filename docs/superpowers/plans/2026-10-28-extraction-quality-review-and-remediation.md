# Plan integral de revisión, corrección y evaluación de Clinical Extraction

> Para quien ejecute: utilizar `superpowers:executing-plans` y completar las tareas con sus verificaciones. Plan aprobado por el usuario; ejecutar las tareas que autorice y registrar su cierre. No autoriza por sí solo inferencia remota. No delegar sin autorización aplicable.

**Estado:** aprobado por el usuario; T1 completada y verificada el 28-10-2026. T2 preparada documentalmente con revisión auxiliar; adjudicación formal abierta. T3 implementada y verificada como evaluador candidato offline. T4 implementada y verificada con captura sintética optativa. T5 implementada y verificada offline; T6–T10 pendientes. Creado el 28-10-2026.

**Objetivo:** investigar y resolver de forma trazable todos los limitantes identificados en código, representación, proveedor y evaluación, y medir si las correcciones mejoran la calidad sin ocultar omisiones, descartes ni incertidumbre.

**Arquitectura:** conservar el monolito modular, la independencia de SFM, el procesamiento stateless y el contrato público 0.1.0. Separar las decisiones sobre referencias, el diagnóstico de etapas, las correcciones deterministas y los experimentos de inferencia. Ninguna variante experimental pasa a producción por ganar una ronda.

**Stack:** Java 21, Spring Boot 3.5.16, Gemini por REST/JDK, JUnit y herramientas Python existentes. No se presupone añadir dependencias.

**Especificación de partida:** [diseño MVP](../specs/2026-10-27-clinical-extraction-mvp-design.md), [contrato](../../../openapi/openapi.json), [última sesión](../../sesion-28-10-2026/README.md), [análisis anterior](../../sesion-28-10-2026-evaluacion.md), [protocolo](../../../evaluation/PROTOCOL.md) y [anotación](../../../evaluation/ANNOTATION.md). Este plan amplía la investigación; no sustituye retrospectivamente aquellos documentos ni afirma que el MVP esté aprobado.

## 1. Alcance y decisiones de enfoque

El alcance comprende revisión de referencias, evaluador, asociación de evidencias, negación, atributos y códigos, sujeto y temporalidad, repeticiones, descartes, prompt, representación interna, parámetros, estabilidad del modelo, robustez, escala y evaluación independiente.

Se propone un programa completo por etapas verificables. Corregir solo los dos ejemplos locales dejaría sin explicación los desacuerdos de contexto. Cambiar directamente de modelo no permitiría separar límites del proveedor y del sistema. Por ello las dos alternativas quedan como intervenciones parciales dentro del programa, no como sustitutos de él.

Las correcciones confirmadas pueden avanzar mientras se adjudican otros escenarios; no es necesario bloquear toda la ingeniería por una referencia pendiente. Sí se bloquea cualquier afirmación de aprobación que dependa de esa referencia.

### Restricciones globales

- Solo datos sintéticos. No abrir `.env`, copiar claves ni acceder a datos reales.
- No editar SFM ni PreAudit Health, levantar contenedores ni ejecutar migraciones.
- Preservar bytes de campañas, auditorías y fuentes congeladas. Crear versiones y directorios nuevos para toda revisión.
- No rebajar precisión 95 %, cobertura 90 % por tipo y ronda ni las cero tolerancias existentes. Las métricas auxiliares no reemplazan esos criterios.
- No convertir una discrepancia automática en error clínico confirmado sin revisar el respaldo.
- No deducir dosis equivalentes, expandir abreviaturas o inventar códigos para mejorar coincidencias.
- Cambios difíciles de revertir o de semántica pública requieren ADR y revisión antes de implementarse. Si cambia el contrato, actualizar OpenAPI, ejemplos, consumidores y pruebas.
- No inferencia remota hasta contar con configuración, estimación, límite de gasto y autorización explícita para el experimento concreto. La autorización histórica no programa otra campaña.
- No reintentos selectivos para mejorar resultados ni selección de la mejor ronda.
- Los commits serán locales y por bloques revisables al ejecutar, dentro de la autorización vigente; no push ni despliegue.

## 2. Evidencia inicial y registro de limitantes

Revisión local realizada en esta conversación: documentación esencial, código del extractor/adaptador/prompt/evaluador, desglose de `evaluation/runs/2026-10-28-gemini38-v3/scores.json` y repetición de los dos ensayos JShell. No se ejecutó una nueva campaña ni la suite completa.

| ID | Limitante o pregunta | Evidencia y certeza | Tareas |
| --- | --- | --- | --- |
| L01 | Cita literal no garantiza respaldo semántico; negación aceptada como afirmación | Reproducido: `NEGATED_AS_ASSERTION=AFFIRMED; WARNINGS=0` | T4, T6 |
| L02 | Dosis de otra receta aceptada por cita amplia | Reproducido: `WRONG_OTHER_CLAUSE_DOSE=9 mg; WARNINGS=0` | T4, T5 |
| L03 | Sujeto y temporalidad pueden usar una cláusula ajena | Documentado en auditoría; controles actuales comprueban literalidad | T2, T4, T6 |
| L04 | Apariciones repetidas, citas amplias y agrupación dificultan identificar la mención | Adaptador resuelve ocurrencias exactas; eso no asegura asociación | T3, T5, T6 |
| L05 | Código mencionado literal puede pertenecer a otra entidad | Límite identificado por lectura; falta reproducción dirigida | T4, T5 |
| L06 | `unknown` mezcla abstención del proveedor y saneamiento por descarte | Comportamiento del servicio; faltan propuestas crudas de v3 | T3, T4 |
| L07 | Métrica estricta mezcla detección y clasificación contextual | Desglose local reproducible de resultados congelados | T1, T3 |
| L08 | Referencias ambiguas y auditoría sin independencia | 57 intentos pendientes; revisión del agente autor | T2, T10 |
| L09 | Algunas marcas `unbackedAttributes` nacen de no alineación con gold | Lectura del evaluador; investigar con contraejemplos | T3 |
| L10 | Tareas simultáneas y reglas de tiempo pueden perjudicar el prompt | Hipótesis, no causalidad demostrada | T2, T7, T8 |
| L11 | LOW, modelo y parámetros pueden limitar resultados | No hay comparación causal controlada | T7, T8 |
| L12 | Corpus pequeño, repetido y reservado expuesto | 234 operaciones = 78 notas × 3; principal reservado = 24 notas | T3, T9, T10 |
| L13 | Filtros pueden eliminar información válida; falta atribución por etapa | 128 advertencias de atributos en v3; no equivalen a 128 errores | T4, T5, T6, T8 |
| L14 | Robustez, inyección, longitud y densidad no quedan demostradas por el agregado | Protocolo contempla sondas; falta evidencia suficiente para extrapolar | T9 |
| L15 | Coste, latencia y versionado insuficientes para elegir una variante solo por calidad | Alias comunicado y condiciones de campaña; sin versión inmutable más granular | T7, T8, T10 |

### Desglose inicial que debe conservarse como análisis auxiliar

Sobre los casos principales reservados de v3, con el comparador automático original y sin nueva adjudicación:

| Tipo | Alineadas por nombre/aparición, rondas 1/2/3 | Contexto completo correcto, rondas 1/2/3 |
| --- | --- | --- |
| Diagnósticos | 30/30/30 de 30 | 29/28/29 |
| Medicamentos | 22/22/22 de 24 | 18/18/19 |
| Procedimientos | 26/26/26 de 26 | 15/17/17 |

Las diferencias de procedimientos son 11/9/9 `CONTEXT_MISMATCH`. Sus dimensiones discrepantes son temporalidad 9/5/6, sujeto 3/4/5 y afirmación 0/1/0; una entidad puede diferir en varias dimensiones. En este subconjunto no aparecen discrepancias de `performanceStatus`. Estos conteos no certifican asociación semántica ni eliminan los rechazos de auditoría.

## 3. Mapa de archivos y separación de artefactos

Archivos existentes a revisar/modificar solo cuando corresponda su tarea:

- `src/main/java/com/clinicalintelligence/extraction/application/ExtractionService.java`: saneamiento y asociación.
- `src/main/java/com/clinicalintelligence/extraction/application/EvidencePolicy.java`: comprobación literal.
- `src/main/java/com/clinicalintelligence/extraction/infrastructure/gemini/GeminiProtocol.java`: petición, decodificación y ocurrencias.
- `src/main/java/com/clinicalintelligence/extraction/infrastructure/gemini/GeminiSettings.java` y `GeminiProvider.java`: configuración y ejecución.
- `src/main/resources/gemini/extraction-v2.txt` y `proposal-v1.schema.json`: referencias de diseño; conservar versiones previas al introducir variantes.
- Pruebas existentes `ExtractionServiceTest`, `EvidencePolicyTest`, `GeminiProtocolTest`, `GeminiProviderTest` y `WireContractTest`.
- `evaluation/next/score_extractions_v2.py`, `run_evaluation_v3.py`, `release_v3.py`: referencias congeladas; no modificarlas como implementación de la siguiente versión.

Artefactos nuevos propuestos:

- `evaluation/quality-review/`: inventario, análisis offline, reglas propuestas, regresiones sintéticas y resultados de desarrollo; sin claves ni datos reales.
- `evaluation/quality-review/analyze_baseline.py` y `test_analyze_baseline.py`: desglose reproducible sin modificar campañas.
- `evaluation/quality-review/score_extractions_v3.py` y `test_score_extractions_v3.py`: siguiente comparador, con trazabilidad al anterior.
- `evaluation/quality-review/annotation-decisions.md` y `disputes.json`: decisiones de referencia enlazadas a su origen.
- `evaluation/quality-review/experiment-plan.json`: condiciones y presupuesto propuestos; no ejecuta llamadas.
- Nuevas fuentes de runner/control dentro de `evaluation/quality-review/`, con manifiesto propio antes de usarse.
- Nuevo corpus y campañas en directorios versionados distintos de v1/v2/v3; elegir versión una vez cerradas las reglas.
- Informe final en `docs/`, enlazado desde `docs/README.md` y la continuidad de sesión.

No crear todos esos archivos por anticipación: cada tarea crea los que necesita. Los nombres son destino de trabajo, no activos ya implementados.

## 4. Secuencia de trabajo y criterios por tarea

En tareas de código: primero prueba que falle por la causa investigada, después corrección, prueba específica y regresión pertinente. Registrar comando, resultado y limitaciones. No cerrar una tarea solo por haber escrito código.

### T1. Congelar la línea base y reproducir el diagnóstico

**Depende de:** aprobación del plan. **Archivos:** nuevo analizador y pruebas en `evaluation/quality-review/`; lectura de scores, respuestas, freeze y auditoría v3.

- [x] Registrar revisión Git, estado del árbol y hashes de los insumos, sin incluir secretos ni borrar artefactos de `target/h5-frozen/`.
- [x] Implementar `analyze_baseline(scores, cases)` con salida por split/cohorte/ronda/tipo: TP/FP/FN, desalineaciones, duplicados y diferencias por dimensión; rechazar claves duplicadas o casos faltantes.
- [x] Probar que una entidad con dos dimensiones erróneas cuente una vez como entidad incorrecta y dos diferencias de campo.
- [x] Reproducir la tabla anterior desde archivos identificados por hash; distinguir alineación literal de aprobación semántica.
- [x] Repetir ambos JShell sintéticos y registrar salida, fuente y condiciones. Si las clases compiladas no corresponden al código actual, compilar sin `clean` antes de interpretar el resultado.
- [x] Registrar verificación Java/Python inicial; un fallo existente se documenta como línea base, no se atribuye a cambios futuros.

**Salida:** informe reproducible e inventario de errores por etapa conocida, con atribución desconocida explícita. No modificar resultados históricos.

### T2. Cerrar reglas y adjudicar referencias

**Depende de:** T1. **Archivos:** nuevas decisiones de anotación y registro de disputas; revisión de `ANNOTATION.md`, contrato y auditoría revisión 2 sin alterarlos.

- [x] Agrupar los 57 intentos pendientes por escenario y regla; incluir también disputas de referencia en intentos ya rechazados por otra causa.
- [x] Para cada grupo registrar nota, campo, lecturas admisibles, evidencia, regla vigente, decisión propuesta y quién la revisó. No resolver tres repeticiones del mismo caso como tres validaciones independientes.
- [x] Definir con ejemplos positivos y negativos: tiempo de orden frente a prestación, pasado verbal, encabezados, sujeto elíptico/familiar, impresión diagnóstica, estado de medicamento, control/revisión como procedimiento y secuencia frente a contradicción.
- [x] Diferenciar información realmente desconocida de una nota sintética mal especificada. Si hay varias lecturas válidas, aclarar una nueva nota o versionar explícitamente las alternativas de anotación; no escoger la lectura favorable después de medir.
- [x] Obtener revisión independiente y registrar desacuerdo antes y después de adjudicar. La revisión por otro modelo será auxiliar; no se etiquetará como revisión clínica humana.
- [ ] Versionar referencias corregidas y su justificación. Mantener una comparación descriptiva con el gold histórico, claramente separada de la decisión original.
- [x] Si se cambia el significado público de temporalidad u otro campo, proponer ADR antes de modificar implementación o contrato.

**Seguimiento T2:** [expediente y verificaciones](../../../evaluation/quality-review/t2/README.md). Revisión independiente auxiliar por otro agente realizada; revisión clínica humana y decisiones de producto pendientes. Las referencias corregidas tienen un patchset propuesto versionado; no se materializa corpus definitivo hasta aprobar las reglas. Por eso el paso de versionar referencias definitivas permanece abierto.

**Aceptación:** cada disputa tiene decisión razonada o bloqueo explícito con responsable requerido; ninguna se elimina del denominador para aprobar. Una tarea documental puede estar preparada sin que la auditoría esté cerrada.

### T3. Revisar y verificar el evaluador

**Depende de:** T1; las reglas semánticas finales dependen de T2. **Archivos:** nuevo comparador y pruebas; nueva especificación de métricas.

- [x] Conservar métricas estrictas e incorporar diagnóstico de detección, cada dimensión contextual, entidad completa, atributos, catálogos, descartes y respaldo auditado.
- [x] Crear contraejemplo donde un atributo literal respaldado pertenezca a una predicción no alineada. Separar discrepancia de referencia, sospecha de falta de respaldo y rechazo semántico confirmado; las dudas continúan bloqueando, no se convierten en aprobaciones.
- [x] Verificar que cambios de ID y orden no alteren puntuación; duplicados penalicen; contexto incorrecto produzca FP y FN; fallos HTTP mantengan omisiones; `null/null` no infle atributos.
- [x] Probar citas alternativas válidas, normalización NFC/case/espacios, tildes preservadas, valores repetidos en distintas entidades, anclajes ambiguos y variantes textuales que requieren adjudicación.
- [x] Probar advertencias divididas/agrupadas y relaciones de contradicción; los destinos deben seguir la alineación correcta.
- [x] Probar auditorías incompletas, resoluciones sin justificación, índices inválidos y cambios de hashes; el control debe fallar de forma conservadora.
- [x] Añadir incertidumbre descriptiva agrupando por nota/origen, sin tratar rondas como muestras independientes. Si se usa remuestreo, fijar semilla, unidad y método; no inventar suficiencia estadística.
- [x] Comparar evaluador viejo/nuevo offline y explicar cada cambio de conteo antes de usar el nuevo para elegir variantes.

**Seguimiento T3:** [implementación, métricas y comparación reproducible](../../../evaluation/quality-review/t3/README.md). Se conservan gold y umbrales; las resoluciones se vinculan a la versión/lista exacta, las discrepancias siguen bloqueando y T2 permanece abierta. No se integró aún el candidato al runner de una nueva campaña.

**Aceptación:** las propiedades tienen pruebas deterministas; ninguna métrica auxiliar sustituye el criterio de entrega ni presenta desalineación como alucinación confirmada.

### T4. Observar el recorrido de propuestas y descartes

**Depende de:** T1. **Archivos:** adaptador/protocolo, servicio y sus pruebas; herramienta de captura sintética bajo `evaluation/quality-review/`.

- [x] Diseñar captura explícita para pruebas con tres fronteras: respuesta estructurada del proveedor, propuesta tipada tras resolver ocurrencias y respuesta final saneada.
- [x] Usar un mecanismo de evaluación inyectado/deshabilitado por defecto; la aplicación ordinaria no debe persistir notas o respuestas crudas ni emitirlas en logs.
- [x] Asociar registros por operación y posición original de mención/campo, incluyendo descartes; no depender solo de IDs finales que cambian al eliminar entidades.
- [x] Distinguir `unknown` original de `unknown` por saneamiento y `null` original de atributo eliminado, sin cambiar por ello el contrato público.
- [x] Probar propuesta correcta conservada, cita incorrecta descartada, ocurrencia equivocada y código asociado a otra entidad. Probar ausencia de captura/log clínico en ejecución ordinaria y en errores.
- [x] Definir clasificación: omisión del proveedor, asociación/contexto del proveedor, decodificación, rechazo literal, rechazo de asociación, discrepancia de referencia o causa aún no determinada.

**Seguimiento T4:** [captura optativa, cápsulas y verificación](../../../evaluation/quality-review/t4/README.md). Puerto por operación sin configuración HTTP, recorder/exportador solo de test, nueve replays sintéticos. Se preserva comportamiento del filtro; la entrega externa de la captura del proveedor queda fuera de su worker con deadline.

**Aceptación:** un caso sintético permite atribuir pérdidas a una etapa; el mecanismo no introduce retención clínica en producción ni requiere revelar razonamiento interno del modelo.

### T5. Corregir asociaciones de atributos, códigos y menciones repetidas

**Depende de:** T4 y reglas aplicables de T2. **Archivos:** servicio/políticas y pruebas Java; corpus de regresiones nuevo.

- [x] Convertir la dosis cruzada en prueba roja y añadir controles: receta válida en varias líneas, dos medicamentos con igual dosis, pronombres, envase frente a dosis, frecuencia compartida explícita y duración de control.
- [x] Reproducir por separado código perteneciente a otra entidad; contrastar asociación textual con resolución local de catálogo, que cumple otra función.
- [x] Diseñar asociación por mención/cláusula/bloque con límites verificables; no usar proximidad como garantía semántica universal ni prohibir todo bloque multientidad.
- [x] Documentar qué asociaciones pueden rechazarse determinísticamente y cuáles solo pueden advertirse o permanecer bajo revisión. Si hace falta una representación interna nueva, especificarla y probarla antes de sustituirla.
- [x] Probar citas repetidas, ocurrencias superpuestas, emoji, Unicode descompuesto y CRLF; no perder corrección de índices al añadir asociación.
- [x] Implementar una intervención por vez y medir falsos descartes sobre casos positivos, además de errores bloqueados. No fijar un umbral nuevo después de observar resultados.

**Aceptación:** las reproducciones correspondientes dejan de pasar inadvertidas dentro del alcance definido y los controles positivos conservan los valores respaldados. Las limitaciones restantes quedan declaradas, no disfrazadas de validación semántica completa.

**Cierre T5:** [informe, comparación real antes/después y límites](../../../evaluation/quality-review/t5/README.md). 195 pruebas Java y 158 Python aprobadas; 28 atributos positivos preservados, 21 asociaciones incorrectas bloqueadas y una fuga residual declarada. Sin inferencia ni aprobación del MVP.

### T6. Negación, sujeto, tiempo, duplicados y contradicciones

**Depende de:** T2 y T4; coordinar representación con T5. **Archivos:** servicio/políticas, protocolo y pruebas, regresiones sintéticas.

- [ ] Prueba roja de negación aceptada como afirmación y casos contrastantes: negación distante, alcance coordinado, doble negación y negación de otra entidad.
- [ ] Pares con paciente/familiar/otro, antecedente explícito y sujeto elíptico. Una cita literal ajena no debe bastar para sostener el sujeto.
- [ ] Pares de orden hoy/prestación mañana, procedimiento realizado ayer y fecha desconocida. Separar estado de prestación y tiempo conforme a T2.
- [ ] Probar suspensión frente a prescripción vigente; secuencia resuelta frente a contradicción simultánea; entidad repetida equivalente frente a menciones incompatibles.
- [ ] Evaluar controles de coherencia y asociación acotados. No convertir incoherencias aparentes en correcciones clínicas automáticas ni deduplicar solo por nombre.
- [ ] Medir errores críticos expuestos, abstenciones, advertencias y falsos rechazos por campo, antes y después.

**Aceptación:** controles deterministas cubren los patrones declarados sin promover desconocidos a estados conocidos. Casos semánticos no resolubles localmente quedan como límite del sistema y objetivo experimental de T8.

### T7. Preparar variantes de prompt y configuración reproducibles

**Depende de:** T2, T3 y T4. **Archivos:** versiones nuevas de prompt/esquema cuando proceda, protocolo/settings y pruebas del adaptador; plan experimental.

- [ ] Alinear etiquetas de telemetría con las versiones efectivas: T4 detectó `validation=evidence-1` fijo en ApiConfiguration frente a `evidence-2` del resultado. No usar el log legado como autoridad ni reescribir métricas históricas.
- [ ] Verificar documentación oficial vigente del endpoint/modelo y compatibilidad de thinking, temperatura, número de candidatos, esquema y límites. Registrar fecha/fuentes; una recomendación actual no demuestra causalidad histórica.
- [ ] Hacer configurables las condiciones del estudio de forma explícita, manteniendo sin fallback la configuración de producción. Pruebas del JSON enviado deben comprobar cada parámetro y rechazo de valores no soportados.
- [ ] Preparar variante de prompt con reglas T2, citas vinculadas a mención y política de agrupación; conservar prompt v2 y registrar hashes.
- [ ] Diseñar una variante de desarrollo que separe detección y clasificación/asociación, manteniendo el contrato final. Documentar riesgos de omisiones iniciales, coste y latencia acumulada.
- [ ] Definir ablaciones: base, solo prompt, solo filtro/representación y combinación. Fijar notas, número de rondas, métricas, orden de ejecución y límites antes de llamar al proveedor.

**Aceptación:** variantes identificables y probadas con dobles deterministas; ninguna se presenta como mejora de calidad antes de medirse.

### T8. Ejecutar el estudio causal de desarrollo

**Depende de:** T3–T7, autorización de inferencia y presupuesto. **Archivos:** plan experimental congelado, runner y nuevos resultados de desarrollo.

- [ ] Congelar notas de desarrollo, código, prompts, parámetros y presupuesto por brazo; fijar número de llamadas y reglas de interrupción. Preparar el coste total incluyendo fallos y reintentos técnicos permitidos.
- [ ] Comparar nivel actual frente a otro nivel soportado con modelo/prompt/filtro constantes; después comparar otro modelo bajo tareas y condiciones equivalentes. No confundir equivalencia de tarea con soporte idéntico de parámetros entre proveedores.
- [ ] Ejecutar ablaciones T7 por separado, con orden alternado o aleatorizado registrado para reducir confusión por hora/carga del proveedor.
- [ ] Capturar únicamente artefactos sintéticos según T4; informar calidad cruda y final, pérdida por filtro, coherencia contextual y estabilidad por escenario/campo.
- [ ] Reportar todas las rondas, errores operativos, coste estimado conciliable, latencia, tokens y versión efectiva disponible. Si no hay identificador inmutable, explicitar la limitación.
- [ ] Elegir una candidata usando reglas previas; si ninguna cumple, registrar el resultado negativo y la siguiente hipótesis, sin buscar una ronda favorable.

**Aceptación:** se puede distinguir qué intervención aporta y qué degrada. No se atribuye todo al modelo ni se adopta una variante solo por reducir FP mientras pierde cobertura.

### T9. Robustez y límites de operación

**Depende de:** T3–T7 para pruebas locales; inferencia condicionada al presupuesto de T8 o autorización específica. **Archivos:** nuevos generadores/casos y pruebas; reutilizar recetas de escala como referencia.

- [ ] Crear pares mínimos que cambien solo negación, sujeto, tiempo o estado; medir si cambian los campos esperados y permanecen iguales los demás.
- [ ] Variar orden de medicamentos, cláusulas y nombres ficticios; añadir distractores y entidades con dosis idénticas. Conservar grupo de origen para evitar fuga entre splits.
- [ ] Probar inyección directa/indirecta en notas y contenido externo citado, incluidas menciones clínicas legítimas junto a instrucciones maliciosas. Medir también omisiones por sobrebloqueo.
- [ ] Probar densidad, posición inicio/medio/final, repeticiones y notas largas clínicamente complejas por separado del relleno administrativo.
- [ ] Verificar cancelación, truncamiento, salida malformada, límites de cuerpo/contexto y sobrecarga con dobles locales; no reducir límites técnicos para mejorar puntuaciones.
- [ ] Delimitar rangos efectivamente probados, huecos y fallos. No extrapolar precisión clínica, inmunidad a inyección ni rendimiento bajo carga a partir de unas pocas sondas.

**Aceptación:** matriz de escenarios y resultados por condición, con regresiones explícitas y límites operativos documentados.

### T10. Evaluación independiente y decisión de entrega

**Depende de:** T2–T9 cerradas en el alcance acordado; revisión independiente, presupuesto y autorización nueva.

- [ ] Construir escenarios nuevos de origen independiente para reemplazar todo reservado usado para orientar cambios; no basta renombrar notas o sustituir dos ejemplos.
- [ ] Revisar referencias antes de ejecutar; registrar autoría, independencia, desacuerdos y adjudicación. Elegir tamaño por cobertura de riesgos y precisión de estimación deseada, sin declarar suficiente el número histórico.
- [ ] Integrar y congelar evaluador, runner y control nuevos. Revisar constantes históricas como `totalRecordedOperations == 234` de `release_v3.py`: la nueva completitud se deriva del manifiesto congelado, con pruebas de casos/rondas faltantes, extras y duplicados.
- [ ] Congelar candidata y presupuesto; ejecutar tres rondas por split conforme al protocolo aprobado, conservando fallos operativos y denominadores.
- [ ] Auditar respaldo de cada respuesta y completar adjudicaciones sin cambiar el gold histórico. Revisar de forma independiente los errores críticos y una muestra de aciertos; cumplir además la cobertura de auditoría exigida para aprobar.
- [ ] Aplicar 95/90 por tipo/ronda y cero tolerancias. Reportar las métricas auxiliares e incertidumbre sin convertirlas en excepciones.
- [ ] Publicar informe de aceptación o rechazo con calidad, regresiones, estabilidad, coste, latencia, límites y pendientes. Una aprobación sintética no equivale a aptitud con pacientes reales.

**Aceptación:** decisión reproducible, auditoría completa y trazabilidad. Si falla, el resultado del programa es un diagnóstico más preciso y una candidata rechazada, nunca una aprobación por agotamiento del esfuerzo.

## 5. Verificación prevista y cierre de bloques

Comandos de referencia para ejecución futura, desde la raíz; no se ejecutaron como parte de crear este plan:

```sh
mvn -Dtest=ExtractionServiceTest,EvidencePolicyTest test
mvn -Dtest=GeminiProtocolTest,GeminiProviderTest,WireContractTest test
mvn verify
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s evaluation/tools -p 'test_*.py'
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s evaluation/reports -p 'test_*.py'
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s evaluation/quality-review -p 'test_*.py'
python3 openapi/tools/validate_contract.py
git diff --check
```

Confirmar argumentos/requisitos de cada herramienta al ejecutar. Las nuevas pruebas deben poder ejecutarse offline; ninguna suite ordinaria inicia inferencia. Evitar `mvn clean` porque elimina JAR congelados ignorados por Git. Verificar hashes históricos antes y después del bloque sin actualizar hashes para ocultar cambios.

Cada bloque de entrega registra: IDs L atendidos, pruebas rojas y verdes, archivos cambiados, resultados cuantificados, falsos descartes, decisiones, limitaciones y siguiente dependencia. No tachar una hipótesis porque se añadió una advertencia; indicar si quedó corregida, refutada, mitigada con límites o pendiente.

Bloques sugeridos para revisión/commits locales: diagnóstico y referencias (T1–T2), evaluador (T3), observación (T4), controles de asociación (T5), controles de contexto (T6), configuración/variantes (T7), experimentos y robustez (T8–T9), evaluación y documentación (T10). Los resultados remotos se conservan con sus propios manifiestos.

## 6. Condiciones de finalización y pendientes externos

- [ ] Todos los L01–L15 tienen una conclusión con evidencia y enlaces; ningún hallazgo queda solo en la conversación.
- [ ] Todos los errores reproducidos tienen regresión y una resolución explícita, incluyendo los que no puedan solucionarse con un control determinista fiable.
- [ ] Se conoce la pérdida introducida por filtros sobre el conjunto de desarrollo revisado.
- [ ] Las comparaciones causales separan cambios de prompt, código, parámetros y modelo.
- [ ] Las pruebas del evaluador protegen conteos, alineación, auditoría y decisión de entrega.
- [ ] La nueva campaña usa referencias revisadas e independientes y mantiene los criterios originales.
- [ ] El índice documental y la continuidad enlazan el informe final y distinguen pruebas históricas de verificaciones recién ejecutadas.

La revisión clínica humana independiente, las condiciones de privacidad/contratación del proveedor y la autorización para datos reales siguen siendo requisitos externos; no se dan por satisfechos por terminar ingeniería. Si falta revisor o presupuesto, continuar tareas offline independientes y dejar el tramo dependiente identificado, sin simular su ejecución.

**Seguimiento:** el usuario aprobó el plan y solicitó ejecutar T1, T2, T3 y después T4 y T5. Su [informe y evidencia](../../../evaluation/quality-review/README.md) registran el cierre. T2 tiene expediente preparado con adjudicación abierta; T3 está implementada y verificada offline; T4 está implementada y verificada; T5 está implementada y verificada offline; T6–T10 permanecen pendientes; la aprobación del plan no autoriza una campaña remota sin condiciones y presupuesto concretos.
