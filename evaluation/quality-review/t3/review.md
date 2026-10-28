# Revisión auxiliar de ingeniería de T3

Revisor: agente separado `t3_evaluator_review`, mismo modelo, conversación nueva sin historial de implementación. Revisión de ingeniería y consistencia; no clínica. No editó archivos ni ejecutó inferencia remota.

La primera revisión encontró tres defectos P2 reproducibles:

1. Dos duplicados idénticos excepto ID, con un atributo rechazado en uno, cambiaban la puntuación al invertir la lista y trasladar el puntero de auditoría. La clave canónica empataba y se conservaba el orden.
2. Convertir `invalidAttributes` a conjunto antes de validar ocultaba punteros duplicados de auditoría.
3. `discardedAttributes` contaba avisos; agrupando dos destinos en un aviso pasaba de dos a uno aunque fueran los mismos descartes.

El implementador añadió regresiones, observó los fallos y corrigió una sola vez los tres puntos. La revisión final del agente confirmó:

- Desempate estable con los campos rechazados para contenido idéntico; motor y diagnóstico usan la misma función.
- Validación de la auditoría original para conservar el rechazo de duplicados.
- Conteo de destinos únicos y un campo separado para cantidad de avisos.

Comprobaciones ejecutadas por el revisor tras las correcciones: 55 pruebas de quality-review pasaron; `compare(draws=1000, seed=1729)` reprodujo las 234 operaciones, conservó TP/FP/FN de entidades automáticos y auditados y no dejó resoluciones sin traducir. Las seis combinaciones split/ronda siguieron rechazadas. No encontró nuevas regresiones en esa revisión acotada.

Después se añadieron dos pruebas de caracterización de anclajes/atributos repetidos, sin cambiar el algoritmo. La suite final del implementador tiene 57 pruebas en quality-review (36 evaluador, 7 vinculación, 14 T1), más 9 T2 y 86 existentes; sus logs se conservan en verification. No se atribuyen esas dos pruebas adicionales a una ejecución del revisor que no las incluyó.

Los criterios 95/90 permanecen intactos. La revisión no cierra las disputas de T2 ni demuestra calidad clínica. Las métricas nuevas requieren congelación e integración explícita antes de usarse en una campaña.
