# Resumen de sesión — 28 de octubre de 2026

**Punto de partida para la próxima sesión:** Clinical Extraction es un prototipo implementado y evaluado; **el MVP no está aprobado por calidad**. Se probaron mejoras y se volvió a medir, pero siguen existiendo fallos y la auditoría formal no está completa. No empezar Clinical Coding ni anunciar aptitud clínica o regulatoria.

Esta es la continuidad más reciente. Leer [AGENTS.md](../../AGENTS.md), este archivo y el [análisis detallado de la sesión](../sesion-28-10-2026-evaluacion.md). El [índice](../README.md) y el [cierre del 27-10](../sesion-27-10-2026/README.md) permiten profundizar; los ledgers anteriores registran decisiones históricas, no reemplazan este estado.

## Qué se hizo

1. Se preparó una revisión de desarrollo del evaluador: advertencias por cobertura de código/destino, preservando las relaciones de contradicción, y referencia corregida de Brisa. Se revisaron las seis decisiones pendientes de H5 sin modificar su auditoría o sus resultados. Los activos de trabajo están en [evaluation/next](../../evaluation/next/README.md).
2. Se implementó el prompt `extraction-gemini-v2` y la validación `evidence-2`, que vincula un atributo de medicamento a una aparición del nombre incluida en su ámbito de evidencia. Se conserva el prompt v1. Las pruebas deterministas no demuestran mejora semántica del modelo; ver [iteración v2](../extraction-iteration-v2.md).
3. Se realizó una primera campaña **parcial** `2026-10-28-gemini38-v2`: 117 operaciones de desarrollo, 116 HTTP 200 y una HTTP 503; no se ejecutó reservado porque sus dos sustitutos eran demasiado parecidos a desarrollo. No hubo reintentos.
4. Se congeló la campaña principal `2026-10-28-gemini38-v3` con dos casos reservados sustitutos. Se completaron 234 operaciones sintéticas: 39 notas por split × tres rondas; todas HTTP 200. Modelo comunicado `gemini-3.8-flash`, thinking LOW, sin cambios entre rondas. El resultado automático de entrega es negativo. Ver [informe de la medición](../evaluacion-28-10-2026-v2.md), [freeze](../../evaluation/runs/2026-10-28-gemini38-v3/freeze.json) y [decisión original](../../evaluation/runs/2026-10-28-gemini38-v3/release-decision.json).
5. Se examinaron las 234 respuestas sintéticas mediante notas y resúmenes de evidencia. La [auditoría revisión 2](../../evaluation/audits/2026-10-28-gemini38-v3-revision2/README.md) registra 111 intentos con respaldo revisado, 66 con algún respaldo rechazado y **57 pendientes**. La auditoría formal sigue incompleta y su [decisión recalculada](../../evaluation/audits/2026-10-28-gemini38-v3-revision2/release-decision.json) es negativa. Son revisiones del agente autor, sin cegamiento ni revisión clínica independiente.
6. Se investigó la atribución de fallos al modelo y al código. Dos ensayos sintéticos locales demostraron que una cita amplia puede permitir una dosis de otro medicamento sin advertencia y que una cita negada puede aceptarse con `assertion=affirmed`. Los ensayos reproducibles están en [evaluation/investigation](../../evaluation/investigation/). **Son fallos pendientes de corrección**, no controles ya implementados.

## Qué mostraron los resultados

En el conjunto reservado completo v3, la precisión automática por ronda fue 96,67/93,33/96,67 % para diagnósticos; 81,82/81,82/86,36 % para medicamentos; y 57,69/65,38/65,38 % para procedimientos. La cobertura de medicamentos fue 75/75/79,17 % y la de procedimientos 57,69/65,38/65,38 %. Los umbrales por tipo y ronda siguen en precisión ≥95 % y cobertura ≥90 %, con cero tolerancias. Estos números comparan con referencias sintéticas, **no son precisión clínica**; el detalle está en el [informe](../evaluacion-28-10-2026-v2.md).

Al comparar solo 22 casos principales reservados comunes con H5 hay señales de menos falsos positivos, especialmente en diagnósticos, y cierta mejora en procedimientos en dos rondas. No es una comparación causal: cambiaron prompt, lógica, evaluador y parte de las referencias, y la auditoría v3 no está completa. No elegir la mejor ronda ni rebajar criterios.

La revisión encontró citas de «paciente», «hoy» y «actual» provenientes de cláusulas ajenas, sujetos y entidades omitidos, `tomar` devuelto como vía sin evidencia y negaciones etiquetadas como `affirmed` pese a mantener `performanceStatus=not_performed`. No afirmar que esas salidas declararon una prestación realizada. También existen referencias con temporalidad o alcance discutidos; no atribuir automáticamente todas las diferencias al extractor.

No es posible cuantificar cuánto corresponde al modelo: se midió el sistema completo y no se conservaron propuestas crudas del LLM para esta campaña. LOW frente a MEDIUM/HIGH u otro modelo son hipótesis a probar de forma controlada, no soluciones demostradas. La guía actual de Google recomienda revisar los parámetros `temperature` y `candidate_count`; no se demostró que causen estos fallos.

## Pendientes, en orden

1. **Completar la adjudicación de las 57 decisiones pendientes**, sin inventar certezas ni modificar retroactivamente el gold. El [registro de disputas](../../evaluation/audits/2026-10-28-gemini38-v3-revision2/dispute-register.json) incluye también dudas de referencia en intentos con otro respaldo rechazado. Aclarar especialmente temporalidad de orden frente a prestación, `control`/`revisión` como procedimiento, secuencias temporales y los dos casos nuevos. Cuando proceda, crear reglas y referencias nuevas y versionadas; preservar todos los informes anteriores.
2. **Corregir los fallos deterministas reproducidos**, con TDD y datos sintéticos: asociación de atributos entre medicamentos, respaldo de negación y citas repetidas. Diseñar rechazos o advertencias conservadores y medir falsos descartes; no prometer comprensión semántica garantizada. Revisar además los descartes `unknown` y la asociación de contexto.
3. Preparar un estudio de desarrollo controlado con notas y condiciones congeladas para separar proveedor, prompt y lógica. Comparar niveles de razonamiento y, si aporta valor, otro modelo con las mismas condiciones; registrar coste, latencia y descartes. Propuestas crudas solo como artefactos sintéticos de evaluación controlada, nunca como logs de producción.
4. Antes de otra campaña, sustituir por escenarios independientes los casos reservados que orienten cambios; el reservado actual fue examinado y no constituye un conjunto ciego nuevo. Integrar y congelar el evaluador, corpus, código, prompt y presupuesto de esa campaña; no alterar resultados previos ni rebajar criterios.
5. Mantener pendiente la revisión clínica independiente y la evaluación de privacidad, autorización y condiciones del proveedor para cualquier uso real. Este repositorio y sus pruebas usan solo datos ficticios.

## Artefactos y límites de reproducción

- [H5 v1](../../evaluation/runs/2026-10-28-gemini38-v1/), [campaña parcial v2](../../evaluation/runs/2026-10-28-gemini38-v2/) y [campaña principal v3](../../evaluation/runs/2026-10-28-gemini38-v3/) son ejecuciones diferentes; cada una conserva sus respuestas, hashes y condiciones. Las dos [revisiones de auditoría](../../evaluation/audits/) están separadas de la ejecución original.
- La cota de v3 fue USD 0,868215 y la parcial v2 USD 1,71007425, de los cuales USD 1,277952 son reserva conservadora por el HTTP 503. Suma USD 2,57828925; no es factura. La autorización de esta campaña no programa otra inferencia ni presupuesto adicional.
- Los JAR congelados en `target/h5-frozen/` están ignorados por Git y `mvn clean` los elimina. Los snapshots y hashes conservan la fuente; no asumir que una recompilación produzca los mismos bytes. No abrir, buscar, mostrar o copiar `.env` ni claves.
- Rama al cierre: `feat/extraction-core-h2`. Consultar `git status --short` y `git log --oneline` para el estado real; no hacer push, despliegue ni migraciones como parte de esta continuidad.
- La verificación previa de la iteración registró 137 pruebas Java y 81 Python; estos conteos son históricos hasta volver a ejecutarlos. El cierre de esta sesión verifica integridad y documentación sin inferencia remota.
- `git diff --cached --check` detectó líneas vacías finales en `evaluation/next/run_evaluation_v2.py`, `run_evaluation_v3.py` y `score_extractions_v2.py`. Son fuentes incluidas en congelaciones y manifiestos; se conservan sus bytes para no invalidar evidencia. El resto del diff se comprueba excluyendo únicamente esa regla de espacio.

El siguiente paso mínimo es revisar las disputas y definir reglas de referencia explícitas antes de cambiar el extractor o repetir llamadas al proveedor.
