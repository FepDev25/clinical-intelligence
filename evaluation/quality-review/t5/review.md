# Revisión técnica T5

La revisión auxiliar fue de solo lectura; no constituye adjudicación clínica independiente ni cierre de T2. Se contrastaron hallazgos con reproducciones antes de corregirlos.

| Hallazgo | Corrección y comprobación |
| --- | --- |
| Atributo inválido usado como permiso de continuidad: código «Beta 9 mg» podía prestar frecuencia a Alfa | Gramática de continuación independiente de valores propuestos; código con gramática propia. Rojo `review-red.log`; regresión verde final. |
| Otro atributo literal pero mal asociado podía autorizar el cruce | Duración ajena ya no se usa como permiso. Incluida en estudio suplementario antes/después. |
| «ambos» podía prestar pauta atravesando Gamma | Rechazo de cabeza pertinente local interpuesta. |
| Tres nombres coordinados y «ambos» inventaban la pareja destinataria | Rechazo de pareja ambigua, con rojo `pair-red.log`. |
| Etiqueta «código de Beta» con Beta omitido permitía asignación a Alfa | Etiqueta nominal debe corresponder al propietario; regresión suplementaria. |
| Continuación válida «por vía oral, durante 3 días» descartada | Prefijo estructural admitido; control positivo. |
| Escaneo global y repetición de continuaciones daban costes cuadráticos | Índices por unidad/grupo/tipo, caché de anclas y memoización de caminos; sondas preservadas en investigation/. |
| NBSP tras punto ocultaba límite; etiquetas NFD perdían campos | Predicados Unicode y NFC sobre copias; offsets intactos. Dos pruebas rojas en unicode-red.log y verdes en suite final. |

El revisor repitió 36 pruebas de asociación durante la revisión y no encontró nuevos bloqueos después de las correcciones. En el cierre inspeccionó los cambios Unicode y la selección de la revisión existente de Brisa: sin bloqueos nuevos, sin tocar archivos ni lanzar una suite competidora. La suite final del implementador tiene 195 pruebas Java aprobadas.

Se conserva una fuga conocida sin separadores y con segunda entidad omitida. No se extiende esta revisión hacia resolver negación, contexto clínico o todos los límites de escala. Estas restricciones están en README.md y en la política, además de en las pruebas.
