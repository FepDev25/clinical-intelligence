# Revisión técnica fresca T7

Revisión en contexto separado del implementador, de solo lectura, sobre diff desde `67b0a78`, implementación nueva, recursos, tests y diseño. No es una revisión clínica ni una comprobación remota del proveedor.

Conclusión del revisor: **sin defectos materiales reproducibles dentro de T7**. Consideró coherentes la configuración cerrada y producción v2/LOW, el rechazo de prompts de etapas por el proveedor ordinario, la identidad de telemetría y el límite de clasificación por tipos/nombres/anclas. La equivalencia clínica de una agrupación no se da por validada.

Confirmó el diseño factorial y sus conteos: 585 unidades, hasta 702 generaciones, 702 conteos y 819 evaluaciones locales. La reutilización de propuestas mantiene pareada la comparación de filtros. Ejecutó `experiment_plan.py --check` y siete pruebas Python, satisfactorias; no Maven ni inferencia, sin modificar archivos.

**Observación menor diferida a T8:** el blueprint permite que falten los JAR locales porque solo valida un diseño no ejecutable. La futura prevalidación de ejecución/replay debe exigir existencia y checksum. Se deja expresamente en README, diseño y ledger, sin afirmar que T7 tiene esa prevalidación ejecutable.

El revisor no juzgó exactitud clínica de v3, adjudicación T2 ni soporte efectivo de la cuenta a partir de dobles. Transporte de dos etapas, deadline/presupuesto agregados y captura reproducible del estudio corresponden a T8.

Tras la revisión, la suite completa descubrió un defecto de aislamiento en la nueva prueba de logs (logger global OFF dejado por StartupTest). Se corrigió solo el test para fijar/restaurar su nivel. Ver `verification/java-final.log` (error previo) y `verification/java-verified.log` (285/285). No se solicitó otra revisión de esa misma pasada; se verificaron regresiones y suite completa.
