# Correcciones de desarrollo 1.0.1

**Actualización de continuidad:** el texto siguiente describe el estado previo a la medición posterior. La campaña v3 ya se ejecutó y no aprobó; su auditoría mantiene 57 intentos pendientes. Consultar el [resumen de sesión](../../docs/sesion-28-10-2026/README.md) y el [análisis](../../docs/sesion-28-10-2026-evaluacion.md) antes de reutilizar estos activos. El arnés y el corpus para una futura campaña deben congelarse de nuevo, no reanudar v1 ni v3 con activos cambiados.

Esta es una versión de trabajo separada de H5 v1, no una nueva evaluación ni un corpus reservado independiente. `manifest.json` registra hashes de las fuentes y dependencias utilizadas. No se modificaron los artefactos congelados ni se recalcularon los resultados históricos. El arnés `tools/run_evaluation.py` sigue usando v1: **no usarlo para afirmar que esta versión ha sido evaluada**. Antes de una nueva campaña hay que integrar y congelar explícitamente evaluador, corpus y control de entrega, comprobar sus dependencias y preparar un reservado independiente según el cierre de sesión.

## Correcciones

- En `development.json`, `dev-outpatient-05` identifica la segunda aparición de Brisa (la prescripción) en lugar de la primera (el envase). Se mantienen dosis y demás anotaciones. El campo `annotationVersion` sigue en 1.0.0 porque las reglas y el esquema no cambiaron; la revisión de los datos se identifica mediante el manifiesto 1.0.1. La concentración del envase no se convierte en dosis. Es una revisión textual del autor, no una adjudicación clínica independiente.
- `score_extractions_v2.py` deriva del evaluador v1. En advertencias que no son de contradicción compara cobertura por par código/destino alineado, independientemente de cómo se agrupen. En `CONTRADICTORY_MENTIONS` conserva el grupo completo: cambiar las relaciones entre destinos no satisface la referencia. Los pares o relaciones no requeridos siguen contándose como adicionales y los semánticos requieren revisión; códigos distintos no se sustituyen. Advertencias sin destino se comparan por código. Los conteos mezclan pares y relaciones y no son directamente comparables con los conteos por grupo v1. La validación del contrato sigue precediendo la puntuación.

La [revisión posterior de los seis intentos](revision-h5.md) documenta sus conclusiones y la referencia cuya certeza sigue siendo ambigua. La auditoría histórica queda intacta. Dos escenarios reservados examinados quedan expuestos y deben sustituirse antes de una evaluación independiente; se registran en [reserved-exposure.json](reserved-exposure.json). No se ha realizado inferencia remota ni se ha aprobado el MVP.

`analyze_development.py` analiza registros JSONL exclusivamente de desarrollo, comprueba los hashes del manifiesto y la identidad, ronda y hash de cada solicitud, y escribe resultados provisionales por stdout sin modificar los registros. Rechaza registros reservados y duplicados; no decide aprobación. Ejemplo: `python evaluation/next/analyze_development.py /ruta/registros-development.jsonl`. No usar su salida como decisión de entrega.

## Verificación de desarrollo

Desde la raíz, con las dependencias de `openapi/tools/requirements.txt` instaladas:

```sh
PYTHONDONTWRITEBYTECODE=1 python -m unittest discover -s evaluation/tools -p 'test_*.py'
PYTHONPATH=evaluation/tools python -c 'from validate_corpus import read_json,validate_cases,audit_coverage; from pathlib import Path; cases=read_json(Path("evaluation/next/development.json")); print(validate_cases(cases,"development")); print(audit_coverage(cases))'
```

Las pruebas específicas están en `tools/test_next_scoring.py`. Esta validación no constituye una evaluación de calidad del modelo ni habilita la siguiente campaña.
