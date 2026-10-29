# T5 — Política estructural aplicada para evidence-3

Decisión de implementación dentro del plan aprobado: añadir un guard de asociación literal independiente de las citas de contexto propuestas. No cambiar el significado público de campos ni resolver T2. Se conservarán scopes de afirmación/prescripción requeridos por evidence-2 para pautas; los códigos tendrán además asociación al nombre, separada del catálogo.

## Reglas verificables

1. Resolver TODAS las apariciones literales del valor dentro de las evidencias válidas. No escoger retrospectivamente la aparición que dé un resultado favorable. Los índices públicos siguen en puntos de código; la política usa offsets UTF-16 internos sin normalizar la nota. Solo las copias usadas en predicados gramaticales se normalizan a NFC y espacios Unicode; no se alteran valores, evidencia ni offsets.
2. Separar unidades de texto por fin de oración con espacio, punto y coma, coma no decimal, salto de línea y coordinadores. Conservar grupo de oración para relaciones explícitamente compartidas. No partir decimales, códigos con puntos internos ni abreviaturas de letras y puntos como v.o.
3. Un atributo local posterior al nombre necesita una aparición seleccionada de ese nombre que posea la unidad: otra cabeza nombrada pertinente entre ambos impide trasladar el atributo. Los códigos consideran cabezas de todos los tipos; las pautas, medicamentos. Las cabezas proceden de evidencias de nombres válidas de la propuesta; no se pretende descubrir todos los medicamentos omitidos por el proveedor.
4. Las continuaciones deben ser explícitas y contiguas: etiquetas dosis/frecuencia/vía/duración/código, fragmentos numéricos/de cantidad o pautas con prefijos estructurales reconocidos, o anáfora restringida como «su dosis». No atravesar otra receta no reconocida, un párrafo vacío o un cambio de oración sin una continuación anafórica explícita. El reconocimiento de continuaciones es estructural, no validación clínica de la pauta.
5. Una relación compartida («Alfa y Beta, ambos …») requiere dos cabezas distintas identificadas, coordinación literal sin atributos interpuestos, pertenencia del nombre seleccionado a esa pareja y marcador compartido. No rechazar todo bloque multientidad ni distribuir valores por simple cercanía.
6. Código antepuesto admite la forma local de encabezado, sin otra cabeza previa que ya sea su propietaria. No interpretar pertenencia a un catálogo como respaldo de asociación textual.
7. Ante soporte estructural insuficiente, eliminar el atributo con ATTRIBUTE_DISCARDED y razón de asociación en la traza; mantener la entidad y los otros atributos válidos. No completar valores ni convertir unidades. No deduplicar nombres ni fusionar estados.

## Límites que deben permanecer visibles

Es un guard conservador y lexical, no un parser clínico: las construcciones no incluidas pueden perder campos; nombres omitidos por el proveedor, relaciones complejas sin separadores y anáforas ambiguas requieren evaluación semántica. Una asociación que pasa no queda certificada. No tocar negación/tiempo/sujeto de T6 ni adoptar el ADR 0007.

La verificación de la versión sigue estos requisitos: fijar regresiones positivas y negativas, observar rojos reales, comparar el mismo estudio sintético contra el JAR T4 preservado y publicar descartes correctos, fugas y falsos descartes por campo. No seleccionar criterios después del resultado ni atribuir calidad al modelo: las propuestas son dobles deterministas. Se conservará también un contraejemplo residual sin separadores ni segundo nombre propuesto, para no ocultar el límite.

Las continuaciones se reconocen sin confiar en otros atributos propuestos como permiso de cruce: un campo rechazado no puede autorizar otro. Un código tiene gramática propia y no atraviesa etiquetas de otra entidad. Los marcadores compartidos no pueden atravesar una tercera cabeza ni escoger arbitrariamente dos de tres nombres coordinados. Se indexan cabezas y anclas por unidad/grupo y se memorizan continuaciones durante cada comprobación.
