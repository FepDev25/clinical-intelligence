# Revisión auxiliar de ingeniería T4

Revisor: agente separado `t4_trace_review`, mismo modelo y conversación nueva. Solo lectura, sin datos reales ni inferencia remota; no adjudicación clínica.

Primera revisión: sin hallazgos de captura ordinaria, persistencia automática, claves, bloques de pensamientos ni logs clínicos nuevos. Correlación de índices originales correcta por inspección. Ejecutó 60 pruebas focalizadas offline sin fallos.

Hallazgo P2: el callback de texto estructurado se ejecutaba dentro del worker sujeto al deadline de Gemini. Un receptor lento podía convertir una respuesta válida en timeout o conservar ocupado el permiso. Aislar RuntimeException no solucionaba esa interferencia temporal.

Se añadió una regresión con latches: con concurrencia uno y el primer receptor bloqueado, una segunda generación debía completar. Se observó primero error OVERLOADED. La corrección usa buffer por operación dentro del worker y entrega fuera de ese tramo, tras fijar resultado y observación de latencia. NONE no crea buffer.

La revisión final confirmó el hallazgo corregido y ejecutó 27 pruebas focalizadas, cero fallos/errores. No encontró nuevos bloqueantes. Recomendó mantener explícito que un receptor programático puede demorar el retorno al llamador y debe ser acotado; no se afirma invariancia temporal total.

Después se añadieron tres pruebas de caracterización: índices planos/frente a familia en una propuesta mezclada, formato final mediante el mapper existente y equivalencia explícita entre ejecución ordinaria y observada (salvo UUID). Esta última se observó en rojo al no resolver la ruta pública dentro del snapshot; se corrigió el helper de alcance test para usar WireContract.encode. No se cambió el filtro ni el protocolo productivo por esa corrección. Las verificaciones finales completas corresponden al implementador, no a una nueva ejecución del revisor.
