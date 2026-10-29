# Revisión técnica fresca de T6

Revisor auxiliar en contexto nuevo; lectura de código y reproducciones JShell contra clases compiladas, sin editar archivos, ejecutar servicios o inferencia. No es revisión clínica humana ni adjudicación de T2.

## Hallazgos P2 reproducidos

1. **Cambio de sujeto oculto dentro de una cita amplia.** «Paciente valorado. La madre consulta. Se documenta asma.», con sujeto patient y citas de las dos primeras oraciones y de la cláusula propia, pasaba; acortar la primera cita provocaba descarte. La política examinaba solo el primer sujeto de la cita y los cambios posteriores a su final. Corregido: comprobar continuidad completa, incluido interior de cita y párrafos. Se cubre también citar toda la nota y el préstamo retrospectivo.
2. **Familiares distintos recibían advertencia de contradicción.** «Registro simultáneo sin resolución: hoy la madre tiene asma; hoy el padre niega asma.», con current y family para ambas menciones, generaba CONTRADICTORY_MENTIONS. Corregido: identidad textual explícita, con abstención cuando no se puede establecer compatibilidad. Se añadió también paciente A/B.

El revisor dejó sin juzgar semántica general, adjudicaciones T2, calibración clínica y densidad extrema. Señaló además que «No se sospecha asma» activaba el patrón positivo de sospecha, sin elevarlo a bloqueo por la política de anotación. El implementador lo reprodujo como etiqueta suspected y lo corrigió mediante descarte, sin decidir un diagnóstico alternativo.

## Verificación de las correcciones

`verification/review-red.log`: seis fallos observados (dos hallazgos, pacientes distintos, CRLF y negación de sospecha). `whole-red.log`: dos fugas al citar toda la nota. `apposition-red.log`: falso descarte del inciso explícito del acompañante encontrado por suite completa. Se preservan estados anteriores y no se retocaron golds para aprobar.

Suite final: **269/269 Java** y **165/165 Python**, con los casos anteriores cubiertos. No se pidió otra revisión para la misma pasada de correcciones; se verificaron las regresiones y la suite completa. No se registraron hallazgos menores pendientes del revisor.

La limitación sobre toda la semántica española permanece declarada, con una negación indirecta que todavía pasa en el estudio. Los 657 valores conocidos de desarrollo se comparan completos: una diferencia retrospectiva ya disputada en T2 queda explícita, sin fingir adjudicación clínica.
