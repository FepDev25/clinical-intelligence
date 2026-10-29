# T8 — estudio causal: preparación, ejecución pendiente

El usuario autorizó avanzar con T8. Se prepara un ejecutor reproducible y revisable; **todavía no se han ejecutado inferencias ni conteos remotos**. Falta la autorización específica del tope propuesto de USD 20, exigida por el [plan aprobado](../../../docs/superpowers/plans/2026-10-28-extraction-quality-review-and-remediation.md). El MVP continúa sin aprobación y T2 conserva sus disputas.

## Diseño que se ejecutaría

[plan.json](plan.json) es sucesor explícito del blueprint T7, conservado íntegro. [freeze.json](freeze.json) añade binarios, classpaths, fuentes y datos con SHA-256. No se modifica ninguna campaña anterior. La propuesta de presupuesto y sus límites están en [budget.md](budget.md); el soporte documental y la decisión de streaming en [provider-capabilities.md](provider-capabilities.md).

| Brazo | Generación | Filtro real congelado |
| --- | --- | --- |
| Base | Flash v2 LOW | evidence-2, T4 |
| Solo prompt | Flash v3 LOW | evidence-2 |
| Solo filtro | Flash v2 LOW | evidence-4, T6 |
| Combinado | Flash v3 LOW | evidence-4 |
| Thinking medio | Flash v3 MEDIUM | evidence-4 |
| Thinking alto | Flash v3 HIGH | evidence-4 |
| Dos etapas | Flash detección + clasificación LOW | evidence-4 |
| Comparador de modelo | Argon v3 LOW | evidence-4 |

39 notas de desarrollo expuesto × 3 rondas, orden por hash/semilla 1729 y rotación de seis configuraciones. **702 unidades**, máximo **819 generaciones, 819 conteos y 936 evaluaciones locales**. Las ablaciones de filtro comparten exactamente la misma generación. Thinking conserva modelo/prompt/filtro; el comparador cambia modelo/perfil nominal, sin asumir equivalencia de cómputo. La variante de dos etapas se trata como otra arquitectura, sin atribuir su resultado a una única causa.

Todas las generaciones T8 usan SSE, con 65 536 tokens de salida+thinking, 16 MiB, 120 s por etapa y 240 s por unidad de dos etapas, concurrencia 1 y cero reintentos/continuaciones/fallback. Una detección vacía válida omite clasificación. Un fallo de etapa no produce éxito vacío o parcial. Producción conserva Flash/v2/LOW y el transporte ordinario; no hay cambio de contrato HTTP.

## Qué impide una conclusión engañosa

- Se conservan propuesta nativa, uso/identidad, errores y traza de saneamiento de datos sintéticos. No se guarda pensamiento, firma, respuesta REST bruta ni credenciales. La aplicación carga su clave solo después de verificar autorización, integridad y classpath.
- El filtro original se ejecuta desde su JAR real: no se implementa una imitación. Se rechaza una versión distinta y se comprueba con una propuesta sintética deliberadamente incorrecta.
- Cada brazo conserva todas las notas/rondas. Los intentos fallidos o no ejecutados mantienen sus denominadores. Una campaña parcial no puede seleccionar candidata. No se repite un intento para buscar una respuesta favorable.
- Se informa precisión/recall estricto, detección, atributos, catálogo y contexto por dimensión, además de cambios crudo/final y transiciones a unknown. Un contexto que coincidía con el gold antes del filtro y cambia después es una señal de revisión, no un descarte clínicamente falso confirmado.
- Se mantienen las referencias originales y se calcula por separado la sensibilidad de la propuesta R1 de T2 sobre una copia. No se adopta esa propuesta ni se adjudican disputas por mejorar puntuaciones.
- Se comparan rondas completas y diferencias pareadas con bootstrap por originGroup: las rondas de una nota permanecen juntas. Son intervalos descriptivos sobre un corpus expuesto pequeño, no certificación ni evidencia de generalización.
- Las reglas automáticas 95/90 y cero tolerancias se conservan. Una candidata automática es provisional y exige las tres rondas completas; `releaseApproved` sigue false. La auditoría semántica y una evaluación independiente siguen pendientes.

## Reproducción y operación

Entorno Python ya disponible durante esta preparación: `/tmp/clinical-eval-next/bin/python`, con las dependencias del evaluador existente. No se instala un SDK nuevo ni una biblioteca de red adicional.

```sh
mvn -B -ntp -o verify
/tmp/clinical-eval-next/bin/python -m unittest discover -s evaluation/quality-review/t8 -p 'test_*.py'
/tmp/clinical-eval-next/bin/python evaluation/quality-review/t8/study.py verify
```

Para construir **otra versión**, después de empaquetar las fuentes y sin inferencia:

```sh
/tmp/clinical-eval-next/bin/python evaluation/quality-review/t8/study.py prepare --assets target/t8-frozen/OTRA-VERSION --manifest RUTA-NUEVA.json
```

No sobrescribe una congelación. Todos los JAR requeridos deben existir y coincidir con los hashes; si falta uno, falla antes de cargar la clave. Los activos ejecutables viven en `target/t8-frozen/`; no ejecutar `mvn clean` antes de conservar los JAR históricos y la congelación. El snapshot de fuentes no contiene `.env`.

**Solo después de aprobación específica**, con el archivo separado de autorización vinculado al hash exacto:

```sh
/tmp/clinical-eval-next/bin/python evaluation/quality-review/t8/study.py run --authorization RUTA-AUTORIZACION.json --output evaluation/quality-review/t8/runs/CAMPANA-NUEVA
```

La carpeta de salida debe ser nueva y su padre existir. La autorización solo puede reclamarse una vez por manifiesto; después de la primera intención de conteo no hay reanudación. Para detener entre etapas/unidades, crear un archivo `STOP` dentro de esa carpeta; para una interrupción dura, conservar el diario y no reiniciar. Si no existe resumen terminal o falla la integridad, no se fabrica informe ni se reintenta automáticamente.

El ejecutor escribe `run-start.json`, `events.jsonl` sincronizado antes de las llamadas, unidades atómicas, `run-summary.json`, replay y reporte JSON/Markdown. Incluye etapas fallidas en coste/latencia; las comparaciones de filtros comparten costes remotos, que no se duplican en el total global. Las tarifas son estimaciones, no factura ni configuración de un tope en la cuenta del proveedor.

## Estado y evidencia

La preparación se verifica con pruebas Java/Python, contrato/corpus y replay real offline. [progress.md](progress.md) conserva decisiones y observaciones RED→GREEN; [verification](verification/) conserva resultados y revisión. Ningún resultado de esos dobles es una medición de calidad de Gemini. La selección y las conclusiones causales de T8 siguen pendientes de ejecutar y analizar la campaña autorizada.
