# Continuidad posterior a T1–T8 — decisión de campaña pendiente

Este documento es el punto de partida más reciente. El usuario pidió documentar hasta aquí, dejar el repositorio commiteado y continuar en otra sesión. **Decidió pensar la propuesta de presupuesto; no autorizó USD 20 ni ninguna llamada remota T8.** No interpretar «sigue con T8», la aprobación del plan, el cierre documental ni esta continuidad como autorización de gasto.

El producto sigue siendo un prototipo: **MVP no aprobado**, sin nueva medición remota de las correcciones. Las 57 adjudicaciones históricas de v3 continúan pendientes. Las demás capacidades siguen fuera de implementación. El [resumen histórico del 28-10](sesion-28-10-2026/README.md) conserva el estado anterior a estas tareas: sus referencias a fallos sin corregir o al siguiente paso describen aquel momento, no el avance posterior.

## Trabajo conservado

| Tarea | Commit local | Estado al detenerse |
| --- | --- | --- |
| T1, línea base | `c22cc1c` | Análisis reproducible e insumos históricos preservados. |
| T2, referencias | `acf98a0` | Expediente y propuestas preparados; adjudicación formal abierta. |
| T3, evaluador | `ffba41e` | Candidato offline, diagnóstico por dimensión y soporte semántico separado. |
| T4, trazas | `02841c9` | Captura sintética optativa y replay; no logs clínicos ordinarios. |
| T5, asociación | `241eda6` | Controles estructurales de atributos/códigos y regresiones offline. |
| T6, contexto | `67b0a78` | Controles acotados de contexto/contradicciones; sin garantía semántica general. |
| T7, variantes | `b19ee77` | Prompts, thinking y blueprint causal; sin inferencia. |
| T8, preparación | `f038a1e` | Ejecutor, presupuesto, análisis, revisión y congelación verificados. **Ejecución y selección pendientes.** |

Rama: `feat/extraction-core-h2`. Los commits son locales; no se hizo push ni despliegue. El commit documental que contiene este archivo queda después de `f038a1e`; consultar `git log -3 --oneline` para su identificador.

## Punto exacto de T8

Leer [README T8](../evaluation/quality-review/t8/README.md), [presupuesto](../evaluation/quality-review/t8/budget.md), [perfiles y transporte](../evaluation/quality-review/t8/provider-capabilities.md), [ledger](../evaluation/quality-review/t8/progress.md) y [plan aprobado](superpowers/plans/2026-10-28-extraction-quality-review-and-remediation.md).

- Manifiesto: `evaluation/quality-review/t8/freeze.json`.
- SHA-256: `97f6b35f6fd4d5cc084c71e204715d964a688431bc46f219abd91a160190c811`.
- Autorización de ejemplo: `freeze-authorization.example.json`, con `granted:false`; no se creó autorización concedida ni se reclamó el manifiesto.
- Presupuesto **propuesto**, global y conservador estimado: USD 20. No garantiza completar el diseño. La cota extrema a capacidad máxima de todas las generaciones es USD 1 046.642688, **sin autorización**. La próxima sesión debe revisar la conveniencia del experimento y su presupuesto con el usuario antes de ejecutar.
- Diseño: 39 notas de desarrollo expuesto × tres rondas, seis configuraciones y ocho brazos; 702 unidades, hasta 819 generaciones + 819 conteos, 936 evaluaciones locales. Replays de filtros comparten la generación original.
- Flash v2/v3 LOW, v3 MEDIUM/HIGH, detección/clasificación LOW y comparador Argon v3 LOW. LOW entre modelos no implica cómputo equivalente.
- SSE común a todos los brazos para mantener 65 536 tokens combinados de thinking/salida; producción conserva Flash/v2/LOW y transporte ordinario. Cero reintentos, fallback o continuaciones; concurrencia 1, 120 s por etapa/240 s por unidad de dos etapas.
- Reserva antes del conteo, conciliación conservadora y parada por gasto/uso desconocido/error de transporte/integridad. Una autorización se consume desde la primera intención de conteo y no habilita reinicios con otro directorio. Una campaña parcial conserva todos los faltantes y no selecciona candidata.
- Referencias originales normativas; sensibilidad R1 de T2 en copia separada. Ni esa sensibilidad ni un resultado automático adjudican las disputas o aprueban una entrega.

**No hubo countTokens ni generación remota T8.** No se abrió `.env` ni se cargaron credenciales durante las verificaciones. El reporte parcial guardado como prueba es simulado explícitamente: una propuesta vacía y los faltantes previstos, no una medición del modelo.

## Verificación y revisión conservadas

La preparación registró **317 pruebas Java y 191 Python**, contrato con 21 esquemas/29 escenarios/58 ejemplos/30 mutaciones inválidas y corpus de 39 casos. Los comandos y logs están en [verification](../evaluation/quality-review/t8/verification/); los conteos pertenecen a aquella ejecución, no sustituyen una comprobación futura tras modificar código.

El replay real de los JAR confirmó que evidence-2 conserva una afirmación sintética incorrecta en «El paciente niega asma», mientras evidence-4 la lleva a unknown; ambos conservan la propuesta cruda. Una versión de filtro ajena es rechazada. La prueba parcial recorrió replay y reporte manteniendo las 936 observaciones previstas, sin candidata ni aprobación.

La revisión independiente encontró cuatro problemas importantes, corregidos con pruebas RED→GREEN: recibo presente frente a uso conciliado, ganancias falsas del filtro al puntuar crudos con contrato inválido, trazabilidad de entradas/salidas y fallos del replay, y estabilidad de valores reales por campo/escenario entre rondas. Sigue como pendiente menor automatizar la comprobación de frescura de clases de test al preparar; se mitigó ejecutando el build completo antes de congelar y conservando su evidencia.

Acceso de la cuenta, precio contractual, comportamiento remoto SSE, calidad clínica y preparación para entrega no se verificaron con estas pruebas. Las tarifas documentadas vencen: Argon 30-11-2026 y Flash 31-12-2026. Si cambian fecha, diseño, código, tarifas o activos, preparar otra congelación y presupuesto revisable; no editar retrospectivamente los manifiestos para que pasen.

## Cómo retomar sin perder los activos

1. Leer `AGENTS.md` y esta continuidad; comprobar `git status --short` y `git log -3 --oneline`.
2. Conservar `target/t8-frozen/prepared-v1/`, `target/t5-baseline/application.jar` (evidence-2), `target/t7-baseline/application.jar` (evidence-4), demás baselines y `target/h5-frozen/`. Son activos ignorados por Git: **no ejecutar `mvn clean` ni asumir que un clone contiene los JAR**. Los hashes/classpaths completos están en el manifiesto; si falta un binario, el ejecutor debe fallar antes de cargar la clave. Una recompilación no garantiza bytes idénticos.
3. La verificación offline disponible es:

   ```sh
   /tmp/clinical-eval-next/bin/python evaluation/quality-review/t8/study.py verify
   ```

   No carga credenciales ni hace llamadas. El entorno Python bajo `/tmp` puede no sobrevivir; consultar las dependencias existentes en `openapi/tools/requirements.txt` antes de reconstruirlo. No sustituir un fallo de integridad por una inferencia de prueba.
4. Retomar **la decisión sobre diseño y presupuesto** con el usuario. No crear `granted:true`, enviar conteos ni generaciones, iniciar T9 remoto o elegir candidata mientras no haya aprobación explícita del experimento concreto. No repetir la solicitud como si hubiera una decisión ya tomada.
5. Solo si se autoriza posteriormente el manifiesto exacto y su tope, crear el archivo separado con esa referencia y ejecutar el comando documentado en T8. Si el usuario prefiere cambios, preparar una nueva versión revisable; no gastar mientras se decide.

El cierre actual modifica únicamente continuidad/documentación. Los hashes históricos y los artefactos de T8 se conservan: los cambios posteriores en `docs/README.md` y el plan son deltas documentales legítimos respecto al checksum de entrega T8, no motivo para reescribirlo. Se preservan también los espacios de los logs Maven originales, que pueden aparecer en `git diff --check`; el código y la documentación nuevos se comprueban por separado.
