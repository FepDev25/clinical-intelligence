# Cierre de evaluación y revisión — 28 de octubre de 2026

## Resultado y alcance

Clinical Extraction sigue siendo un **prototipo no aprobado para MVP ni uso clínico**. La campaña principal v3 completó 234 operaciones sintéticas autenticadas, tres rondas por split, con HTTP 200 en todas. La integración técnica no demuestra calidad semántica. Prompt `extraction-gemini-v2`, validación `evidence-2`, modelo comunicado `gemini-3.8-flash`, thinking LOW y resto de condiciones/hash en [freeze](../evaluation/runs/2026-10-28-gemini38-v3/freeze.json). No hubo revisión clínica independiente ni cegamiento.

Se examinaron las 234 respuestas mediante resúmenes de entidades y citas. La [segunda revisión](../evaluation/audits/2026-10-28-gemini38-v3-revision2/semantic-audit.json) registra 111 intentos con respaldo revisado, 66 con algún respaldo rechazado y **57 pendientes**. Los 57 están examinados, pero requieren adjudicar la referencia o interpretación; no se consideran aprobados ni se fuerza una certeza clínica. El [registro de disputas](../evaluation/audits/2026-10-28-gemini38-v3-revision2/dispute-register.json) identifica cada intento y también recoge disputas en otros intentos que ya tenían un rechazo claro. Su recálculo [no aprueba](../evaluation/audits/2026-10-28-gemini38-v3-revision2/release-decision.json); `semanticAuditComplete=false`. La auditoría formal **no está cerrada** mientras persistan esos pendientes. El gold, resultados originales y H5 se preservan sin cambios.

Los pendientes abarcan principalmente 41 intentos con temporalidad de prestación ordenada sin fecha explícita, cinco de secuencia temporal, cinco sobre control/revisión como prestación, tres sobre los nuevos verbos/encabezado, dos sobre estado de medicamento mencionado y uno sobre certeza de impresión diagnóstica. Son decisiones a nivel de referencia, no necesariamente errores clínicos. La versión siguiente debe aclarar notas o reglas, con revisión y versión nuevas; no editar el pasado para elevar puntajes. El reservado observado durante auditoría no sirve como conjunto independiente para futuros ajustes; trasladar los escenarios que orienten cambios a regresión y sustituirlos antes de una nueva evaluación.

## Comparación con H5

H5 y v3 no son una comparación experimental limpia: cambiaron prompt, lógica, comparador y dos referencias reservadas; las salidas varían y la auditoría v3 está incompleta. Para limitar el cambio de composición se compararon **22 de los 24 casos principales reservados idénticos**, sin los dos sustitutos v3. Las cifras siguientes son conteos automáticos sin adjudicación, no precisión clínica. Tres rondas separadas; TP/FP/FN agregados por familia:

| Tipo | H5: rondas 1 / 2 / 3 | v3: rondas 1 / 2 / 3 |
| --- | --- | --- |
| Diagnósticos | 25/6/3 · 27/4/1 · 27/4/1 | 27/1/1 · 26/2/2 · 27/1/1 |
| Medicamentos | 18/3/4 · 18/3/4 · 19/2/3 | 18/2/4 · 18/2/4 · 19/1/3 |
| Procedimientos | 15/10/9 · 15/10/9 · 15/11/9 | 15/9/9 · 17/7/7 · 17/7/7 |

Interpretación: en estos casos hay una señal de menos falsos positivos en diagnósticos; medicamentos conservan los mismos TP/FN y algo menos FP; procedimientos mejoran en dos rondas, pero siguen con muchos FP y FN. El conjunto completo v3 reserva tiene precisión automática de procedimientos 57,69 %, 65,38 % y 65,38 %, cobertura igual a esas cifras; medicamento 81,82 %, 81,82 %, 86,36 % de precisión y 75 %, 75 %, 79,17 % de cobertura. Ninguna ronda aprueba todos los tipos. Las referencias discutidas limitan la interpretación fina; tampoco pueden atribuirse todas las diferencias al prompt o al modelo. Los umbrales siguen 95/90 y cero tolerancias.

El análisis parcial identificó citas desplazadas entre cláusulas, omisiones de sujeto y medicamento, dos salidas «tomar» como vía sin respaldo, y negaciones etiquetadas `affirmed` aunque el estado `not_performed` se mantuvo. No se afirma que el modelo declarara realizada la prestación. Los ejemplos adversariales examinados no expusieron entidades de instrucciones externas; no demuestra inmunidad general.

## Qué puede deberse al LLM

Las respuestas observadas son del **sistema completo**, no propuestas crudas conservadas del modelo. No se puede calcular qué proporción se debe a Gemini, al prompt, a la resolución de ocurrencias, a los descartes o al gold. Hay variación entre rondas que justifica estudiar estabilidad, pero no prueba una causa aislada. El alias devuelto coincide con `gemini-3.8-flash`; no se obtuvo identificador inmutable más granular.

Google indica que la salida estructurada puede ser JSON válido y aun así contener valores semánticamente incorrectos: [Structured outputs](https://ai.google.dev/gemini-api/docs/structured-output). Esta implementación ya usa esquema, pero ello no prueba asociación o negación. El servicio usa LOW; Google documenta niveles low/medium/high y medium por defecto para 3.8: [thinking](https://ai.google.dev/gemini-api/docs/generate-content/thinking). Comparar LOW con MEDIUM/HIGH es una hipótesis experimental, no una garantía de mejora clínica. La [guía actual de Google](https://ai.google.dev/gemini-api/docs/generate-content/whats-new-gemini-3.8) aconseja retirar `temperature` y `candidate_count`; el código los envía. No está demostrado que fueran efectivos, que causaran errores o que la guía fuera idéntica al medir. No reducir temperatura como solución sin evidencia.

## Problemas de código constatados y límites

1. `ExtractionService.grounded` comprueba que una cita conocida exista literalmente, sin validar que respalde el campo o su relación con la entidad. El ensayo sintético [negated-as-affirmed.jsh](../evaluation/investigation/negated-as-affirmed.jsh) devolvió `AFFIRMED` para una nota que niega asma, sin advertencias. No es un corrector semántico y no debe presentarse como tal.
2. La comprobación de atributos de medicamento solo requiere que una cita amplia contenga el nombre y el atributo. [other-medication-dose.jsh](../evaluation/investigation/other-medication-dose.jsh) hizo aceptar `9 mg` para Alfa, aunque el texto lo asigna a Beta, sin advertencia. `evidence-2` mejora la asociación por aparición pero no resuelve citas que abarcan varias cláusulas.
3. `GeminiProtocol.evidence` resuelve la ocurrencia exacta indicada por el proveedor; una ocurrencia literal válida elegida de la cláusula equivocada sigue siendo válida sintácticamente. Los índices Unicode correctos no demuestran asociación semántica.
4. `unknown` con evidencia vacía se acepta; también puede producirse al descartar una cita inválida con `ATTRIBUTE_DISCARDED`. En v3 se registraron 128 advertencias de descarte de atributos, 83 para sujeto y 25 para temporalidad. Son advertencias, no conteos de errores independientes ni prueba de cuál etapa originó cada valor. No se conserva la propuesta cruda en esta campaña.
5. Las colecciones no agrupan automáticamente entidades equivalentes y los textos externos se protegen principalmente por instrucciones y filtros literales; deduplicar solo por nombre o aplicar reglas ciegas de negación podría destruir menciones con contextos distintos. Las respuestas de ejemplo y las pruebas con fake no miden calidad de inferencia.

Ensayos locales, sin red ni pacientes, con JDK 21 y proyecto compilado:

```sh
jshell --class-path target/classes evaluation/investigation/other-medication-dose.jsh
jshell --class-path target/classes evaluation/investigation/negated-as-affirmed.jsh
```

Las pruebas usan citas deliberadamente amplias para demostrar la limitación; no prueban que cada fallo observado en la campaña tuviera exactamente esa causa.

## Próximos pasos recomendados

1. Corregir el **método de evaluación antes de optimizar**: adjudicar las 57 disputas con reglas claras y, cuando corresponda, referencias sintéticas nuevas y versionadas; revisar particularmente fecha de orden frente a prestación y «control» frente a procedimiento. Conservar las ejecuciones e informes originales.
2. Añadir regresiones de desarrollo para las dos reproducciones locales, citas de sujeto/tiempo repetidas, negación y campos desconocidos. Diseñar controles que rechacen o adviertan asociaciones insuficientes sin fingir una comprensión clínica garantizada. Evaluar riesgo de falsos descartes y mantener revisión humana.
3. En un estudio **de desarrollo** congelado y sintético, comparar por separado LOW/MEDIUM/HIGH y, si interesa, otro modelo, con las mismas notas, esquema, código y métricas; repetir rondas y medir costes, latencia y descartes. Capturar propuestas crudas solo en artefactos sintéticos controlados, nunca en logs de producción, para distinguir fallo de modelo y filtro. Analizar variaciones sin seleccionar la mejor ronda.
4. Revisar por separado la compatibilidad de parámetros Google y probar cualquier cambio, sin atribuirle causalidad. Antes de usar datos reales siguen faltando evaluación clínica independiente y marco de privacidad/contratación.
5. Preparar y congelar un reservado independiente nuevo y un presupuesto para la siguiente campaña; no ajustar contra el reservado ya observado ni cambiar umbrales. Ninguna inferencia adicional se ejecutó durante esta revisión.

## Coste y verificaciones

La campaña v3 registró cota USD 0,868215; la campaña v2 parcial previa USD 1,71007425, con una reserva conservadora de USD 1,277952 por HTTP 503; suma USD 2,57828925, no factura. Los dos ensayos JShell son locales y sintéticos. Se conservan fuente, respuestas y hashes de ambas campañas y H5. La verificación de Java de la iteración pasó 137 pruebas; el arnés Python pasó 81 en el cierre de evaluación anterior. La revisión actual vuelve a verificar hashes y estructura de auditoría; no ejecuta evaluación remota.
