# Evaluación de extracción v2 — 28 de octubre de 2026

**Actualización de auditoría:** se examinaron las respuestas y se registraron 171 decisiones; 63 intentos siguen pendientes de adjudicación. El [informe de auditoría parcial](../evaluation/audits/2026-10-28-gemini38-v3/README.md) y su recálculo separado mantienen la decisión negativa. La sección inferior conserva el estado previo a esa revisión.

**Resultado: MVP no aprobado.** La campaña principal `2026-10-28-gemini38-v3` completó 234 operaciones sintéticas (39 notas por split, tres rondas). Todos los intentos devolvieron HTTP 200. La decisión automática de entrega es negativa y provisional porque falta la auditoría semántica de las respuestas y de las adjudicaciones. No se hizo revisión clínica independiente y no se afirma aptitud clínica.

La configuración usó `gemini-3.8-flash`, prompt `extraction-gemini-v2`, validación `evidence-2`, comparador 1.0.1, temperatura 1, thinking LOW, cero reintentos y las mismas protecciones técnicas de H5. Solo se enviaron notas sintéticas; referencias y catálogos no se enviaron al modelo. Las seis rondas usaron los mismos activos congelados. El servicio fue local; la inferencia fue remota con Google.

## Resultados automáticos principales

Las cifras son coincidencias con las referencias, no precisión clínica. La referencia reservada tiene 30 diagnósticos, 24 medicamentos y 26 procedimientos principales por ronda. Los criterios acordados son precisión ≥95 % y cobertura ≥90 % por tipo en cada ronda reservada; además se exigen cero tolerancias y auditoría completa. No se elige la mejor ronda.

| Ronda reservada | Diagnósticos: precisión / cobertura | Medicamentos: precisión / cobertura | Procedimientos: precisión / cobertura |
| --- | --- | --- | --- |
| 1 | 96,67 % / 96,67 % | 81,82 % / 75,00 % | 57,69 % / 57,69 % |
| 2 | 93,33 % / 93,33 % | 81,82 % / 75,00 % | 65,38 % / 65,38 % |
| 3 | 96,67 % / 96,67 % | 86,36 % / 79,17 % | 65,38 % / 65,38 % |

El comparador marcó en reservado 8, 7 y 7 dimensiones de contexto crítico y 1, 0 y 1 atributos sin respaldo por ronda, respectivamente. No detectó violaciones literales de evidencia/IDs ni contaminación de instrucciones externas. Estas marcas son automáticas; una cita literal válida no garantiza respaldo semántico. Sin auditoría no se convierten automáticamente en errores clínicos confirmados. Las advertencias y adjudicaciones pendientes también bloquean la aprobación.

### Referencias y límites de independencia

Se excluyeron los dos escenarios H5 revisados y se generaron dos nuevos. Los 37 restantes se reutilizaron de H5; la autora de las nuevas notas también conocía la evaluación previa. No se trata de una evaluación ciega ni de un conjunto totalmente nuevo de origen independiente. La validación comprobó separación literal, IDs y grupos de origen, no independencia semántica.

Las dos referencias nuevas requieren revisión específica: expresiones verbales en pasado como suspensión, solicitud y no realización pueden admitir distintas interpretaciones temporales; la anotación `unknown` podría ser discutible bajo las reglas que permiten usar tiempo verbal. No se cambió el gold después de medir. Sin esos dos casos, en los otros 22 casos principales reservados las ternas TP/FP/FN de procedimientos son 15/9/9, 17/7/7 y 17/7/7; medicamentos 18/2/4, 18/2/4 y 19/1/3. Son análisis descriptivos, no sustituyen el protocolo, y los resultados automáticos siguen sin auditoría. Si los resultados reservados orientan cambios futuros, sus escenarios deberán trasladarse a regresión y ser sustituidos antes de otra evaluación independiente.

## Campaña parcial anterior y presupuesto

Se congeló una primera candidata `2026-10-28-gemini38-v2`; sus sustitutos reservados eran demasiado similares a casos de desarrollo. No se ejecutó su reservado. Sus 117 operaciones de desarrollo terminaron antes de detener la campaña: 116 HTTP 200 y una HTTP 503, conservada sin reintento. Su cota registrada es USD 1,71007425, incluyendo una reserva conservadora de USD 1,277952 para el intento sin uso conciliable. No es factura.

La campaña principal se congeló después con presupuesto restante USD 8,28992575, de forma que el máximo combinado seguía siendo USD 10. Su cota registrada es USD 0,86821500; la suma de ambas es USD 2,57828925. La tarifa estándar pública de Gemini era USD 0,75 por millón de tokens de entrada y USD 3,75 por millón de salida hasta diciembre de 2026; el tier y la factura de la cuenta no se verificaron. [Tarifas de Google](https://ai.google.dev/gemini-api/docs/pricing). Mediana de latencia de la campaña principal 7,1305 s, p95 13,169 s, máximo 21,381 s; no es prueba de carga.

## Auditoría y artefactos

No se ha completado revisión textual individual de las 234 respuestas: `semanticAuditComplete` es falso y las adjudicaciones del comparador siguen abiertas. El archivo `release-decision.json` refleja explícitamente ese bloqueo; no se ha creado una auditoría artificial. La conclusión operativa es que **no puede aprobarse el MVP**. Falta revisar respaldo semántico y las referencias discutidas antes de atribuir causas finas o declarar completa la evaluación.

- [Ejecución principal](../evaluation/runs/2026-10-28-gemini38-v3/) y [decisión](../evaluation/runs/2026-10-28-gemini38-v3/release-decision.json).
- [Ejecución parcial](../evaluation/runs/2026-10-28-gemini38-v2/).
- [Referencias y metodología de evaluación](../evaluation/next/README.md); [corpus reservado v3](../evaluation/corpus/v3/manifest.json).
- La ejecución H5 original no se modificó. La suite Python final pasó 81 pruebas y la verificación Java anterior pasó 137; no equivalen a validación clínica.
