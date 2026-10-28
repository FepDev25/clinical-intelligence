# T2 — Expediente de revisión de referencias

**Estado:** revisión documental y auxiliar preparada; adjudicación formal abierta. No se modificaron corpus, auditorías, implementación ni decisiones de entrega históricas.

Decisiones y ejemplos: [annotation-decisions.md](../annotation-decisions.md). Registro completo: [disputes.json](../disputes.json). Propuestas de referencia: [reference-proposals.json](reference-proposals.json). Revisión auxiliar original: [independent-agent-review.md](independent-agent-review.md).

## Cobertura por escenario

| Escenario | Pendientes históricos | Rechazados con referencia discutida | Reglas revisadas | Propuestas de cambio |
| --- | ---: | ---: | --- | ---: |
| `dev-admission-02` | 2 | 1 | R6 | 0 |
| `dev-consultation-02` | 0 | 3 | R1, R7 | 1 |
| `dev-consultation-04` | 1 | 0 | R1 | 1 |
| `dev-consultation-06` | 3 | 0 | R1 | 1 |
| `dev-emergency-01` | 2 | 1 | R4, R5 | 0 |
| `dev-outpatient-02` | 1 | 0 | R1 | 1 |
| `dev-outpatient-05` | 3 | 0 | R1 | 1 |
| `dev-procedure-07` | 3 | 0 | R1 | 1 |
| `dev-progress-04` | 3 | 0 | R1 | 1 |
| `dev-progress-06` | 3 | 0 | R1 | 1 |
| `dev-progress-07` | 0 | 2 | R8 | 0 |
| `res-consultation-02` | 3 | 0 | R1 | 1 |
| `res-consultation-03` | 3 | 0 | R1 | 1 |
| `res-consultation-04` | 1 | 2 | R1, R10 | 1 |
| `res-consultation-05` | 1 | 2 | R9 | 0 |
| `res-consultation-06` | 3 | 0 | R1 | 1 |
| `res-emergency-01` | 3 | 0 | R4, R5 | 0 |
| `res-emergency-02` | 2 | 1 | R8 | 0 |
| `res-outpatient-02` | 2 | 1 | R1, R7 | 1 |
| `res-outpatient-05` | 1 | 2 | R1 | 1 |
| `res-procedure-07` | 3 | 0 | R1 | 1 |
| `res-progress-02` | 3 | 0 | R1 | 1 |
| `res-progress-04` | 2 | 1 | R1 | 1 |
| `res-progress-06` | 3 | 0 | R1, R8 | 1 |
| `res-progress-07` | 3 | 0 | R8 | 0 |
| `res-v3-outpatient-01` | 3 | 0 | R2, R3 | 0 |
| `res-v3-procedure-05` | 0 | 3 | R2 | 0 |

Total: 27 escenarios, 76 entradas (57 pendientes y 19 ya rechazadas), 34 cuestiones de referencia y 18 cambios propuestos R1. Las repeticiones de una nota no son evidencia independiente. El registro conserva por intento las razones y los rechazos de respaldo de la auditoría original, incluidos los de campos ajenos a la disputa.

## Decisiones y responsables pendientes

- Responsable de anotación: revisar/adoptar la aclaración de orden frente a fecha de prestación y las 18 propuestas; publicar reglas y corpus versionados antes de otra campaña.
- Responsable de producto: decidir alcance de consulta de seguimiento/control genérico y revisar ADR 0007.
- Revisor clínico/lingüístico independiente: adjudicar Eje, impresión diagnóstica y alcance temporal/correferencia que no sean inequívocos. No se ha asignado ni simulado esa persona.
- Desarrollo puede continuar T3 con ejemplos inequívocos y T4 con capturas sintéticas; no puede declarar cerrada la auditoría ni usar estas propuestas como corpus reservado independiente.

Ruling de ejecución: se entrega el trabajo documental y técnico completo posible en T2, con cada disputa trazada a una propuesta o responsable requerido. La revisión de otro agente del mismo modelo es auxiliar, no revisión clínica humana. Por ello no se marca como realizado el paso de materializar referencias corregidas definitivas. No se usa la ausencia de adjudicación como motivo para descartar casos del denominador.

## Verificaciones de esta tarea

- 9 pruebas nuevas de integridad, observadas primero en rojo por ausencia del validador; una prueba posterior detectó aceptación de corpus de origen incorrecto. Tras corregirlo todas pasan.
- 14 pruebas del analizador T1; 81 del arnés histórico; 5 del control de entrega histórico: todas pasan.
- Registros y patchset verificados: referencias originales, citas Unicode, hashes de fuentes/respuestas, cobertura exacta de 76 entradas y preservación de rechazos.
- Manifiestos T1 y fuentes históricas verificados sin cambios. No se repitió Maven porque solo se añadieron datos/documentos y validación Python; las 137 pruebas Java de T1 siguen siendo evidencia histórica, no una ejecución de T2.
- Cero inferencias remotas; no se abrieron claves, no se editaron servicios de producción.

Evidencia ejecutable en `verification/`; los hashes de cierre se registran en `artifact-checksums.json` de este directorio.

```sh
PYTHONDONTWRITEBYTECODE=1 python3 evaluation/quality-review/t2/validate_t2.py
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s evaluation/quality-review/t2 -p 'test_*.py'
```
