# Protocolo de evaluación — versión 1.0.0

Estado: protocolo fijado antes de medir; no hay resultados de un modelo real. Referencias: [reglas de anotación](ANNOTATION.md), [manifest versionado](corpus/v1/manifest.json) y decisiones D7/D10 del [ADR 0001](../docs/adr/0001-extraction-contract-decisions.md).

## 1. Conjuntos y uso

| Conjunto | Composición inicial | Uso |
|---|---|---|
| Desarrollo | 24 principales, 9 exploratorios y 6 adversariales | TDD, regresión y ajustes del extractor. |
| Reservado | 24 principales, 9 exploratorios y 6 adversariales, de otros escenarios | Evaluación de una configuración congelada; no ajustar prompts con sus respuestas. |
| Escala | 10 recetas deterministas | Explorar tamaño, densidad y catálogos; no mezclar con los promedios principales. |

Cada conjunto fijo incluye seis notas principales por familia (consulta externa, evolución, interconsulta, procedimientos), tres exploratorias por familia compleja (ingreso, emergencia, epicrisis) y seis adversariales adicionales. Familia y cohorte se analizan por separado; una nota adversarial de ingreso no reemplaza las tres exploratorias de ingreso.

Por conjunto principal hay 30 diagnósticos, 24 medicamentos y 27 procedimientos de referencia, incluyendo un caso vacío. Es un mínimo inicial útil para detectar regresiones; no proporciona validación clínica ni garantiza generalización. Reportar denominadores reales por familia y entidad; no declarar suficiencia estadística por alcanzar estos tamaños.

El equipo/agent que construye el corpus puede ver los gold durante su autoría y auditoría. El repositorio no proporciona cegamiento criptográfico. Para el build, cargar desarrollo por defecto y evitar usar reservado como ejemplos del prompt o como oráculo de TDD. El comando de auditoría reservada solo imprime agregados, no notas ni anotaciones.

## 2. Congelación de una ejecución futura

Antes de llamar al modelo registrar: versión y hashes del corpus, reglas y contrato; revisión del código; proveedor/modelo y versión exacta cuando se conozca; parámetros de generación; versiones de prompt y validación; límites efectivos; condiciones de ejecución y fecha.

Ejecutar cada nota fija tres veces con la misma configuración y conservar los resultados de las tres rondas por separado. Los umbrales se verifican en cada ronda, sin elegir la mejor ni ocultar variación mediante un promedio. Los reintentos técnicos se registran y respetan el presupuesto operativo; no son nuevas oportunidades para escoger una respuesta favorable.

Mantener resultados sintéticos del arnés de evaluación separados de la observabilidad de producción. La API sigue sin persistencia clínica por defecto. No enviar datos reales ni introducir secretos en archivos de resultados.

## 3. Validar antes de contar

Evaluar la respuesta final aceptada por la API, no solo la propuesta cruda del LLM. Registrar por separado descartes de entidades y atributos, advertencias y fallos del proveedor para distinguir seguridad del filtro de calidad del modelo.

Primero verificar estructura, finalización, evidencia literal, contexto sustentado y pertenencia de IDs. Un error HTTP o una salida global inválida no es un vacío correcto: se registra fallo de operación y todas las entidades esperadas de ese caso cuentan como omitidas. No eliminar el caso del denominador de cobertura. Una ronda principal con fallos de operación no aprueba aunque sus respuestas restantes cumplan porcentajes.

Una respuesta final que expone evidencia inválida o IDs inventados infringe las invariantes y bloquea la aprobación. La auditoría semántica sigue siendo necesaria: tener una cita real no demuestra que una dosis o un antecedente se hayan atribuido bien.

## 4. Alineación de entidades

Los IDs, el orden de las listas y el tamaño exacto de las citas no son criterios de igualdad semántica. Alinear por tipo de entidad, nombre textual y ocurrencia respaldada:

1. Comparar nombres usando NFC, case folding y espacios colapsados. No eliminar tildes ni sustituir sinónimos, códigos o equivalencias clínicas.
2. Localizar las apariciones literales de `name.value` dentro de sus evidencias válidas. Su posición absoluta debe identificar al menos un anclaje permitido del gold, incluyendo los de repeticiones agrupadas.
3. Asociar una predicción como máximo a una entidad de referencia y viceversa. Con referencias repetidas, priorizar la ocurrencia identificada; no usar el contexto predicho para elegir retrospectivamente el gold que dé más puntos.
4. Si una evidencia amplia abarca varias menciones incompatibles y no permite una asociación inequívoca, marcar el caso para adjudicación. No asignar siempre la primera ni descartar el caso del reporte. Una ronda con adjudicaciones sin resolver no puede declararse aprobada.

Una entidad alineada es correcta solo si afirmación, temporalidad, sujeto y estado específico de medicamento/procedimiento coinciden con la referencia. `unknown` debe coincidir con `unknown`; no equivale a «cualquier valor». Un contexto erróneo produce una predicción incorrecta y una referencia no recuperada. Las diferencias de cita se aceptan si el respaldo literal y semántico sigue siendo válido.

Las repeticiones equivalentes agrupadas cuentan como una entidad. Devolver dos veces esa entidad genera un acierto como máximo y un falso positivo adicional. Menciones con estados incompatibles no se agrupan para reducir omisiones. La cobertura de anclajes repetidos se puede reportar aparte de cobertura de entidades; no se inventa un umbral nuevo de anclajes.

## 5. Métricas y denominadores

Para cada tipo de entidad en la cohorte principal:

- TP: predicciones alineadas con referencia y contexto correctos.
- FP: predicciones sin referencia, duplicados sobrantes o contexto incorrecto.
- FN: referencias sin predicción correcta, incluyendo las de operaciones fallidas.
- Precisión = TP / (TP + FP).
- Cobertura = TP / (TP + FN).

Se suman TP/FP/FN de los casos de una ronda antes de dividir (micro por tipo). No juntar diagnósticos, medicamentos y procedimientos para ocultar el fallo de uno de ellos. Publicar también el desglose por familia, por cohorte, por tamaño y por localidad declarada, con los denominadores de cada grupo. No atribuir a una región calidad demostrada solo por aceptar su `locale`.

Un denominador cero se informa como `N/A`, no como 100 %. Si no hay predicciones pero sí referencias, cobertura es 0 y precisión es `N/A`; eso no aprueba. Si no hay referencias ni predicciones, ambas son `N/A` para ese caso y se cuenta un vacío correcto. Si aparecen entidades en un caso vacío, son FP. Registrar por separado vacío limpio y vacío con descartes advertidos.

Ejemplos de conteo, independientes del corpus:

| Situación | TP | FP | FN | Resultado |
|---|---|---|---|---|
| Dos referencias; solo una se recupera y se duplica | 1 | 1 | 1 | Precisión 50 %, cobertura 50 %. |
| Un diagnóstico negado se devuelve afirmado | 0 | 1 | 1 | Precisión/cobertura 0 % y error crítico. |
| Sin referencias; se devuelve una entidad | 0 | 1 | 0 | Precisión 0 %, cobertura N/A. |
| Operación fallida con dos referencias | 0 | 0 | 2 | Precisión N/A, cobertura 0 % y fallo operativo. |

## 6. Atributos, catálogos y advertencias

Para entidades alineadas, evaluar además cada atributo textual y `mentionedCode`. Valores presentes necesitan respaldo correcto de esa entidad, no solo aparecer en otra parte de la nota. Normalizar solo NFC/case/espacios para comparar texto; no calcular equivalencias de dosis, unidades ni duraciones. Una discrepancia legítima de anotación se adjudica y versiona, nunca se corrige silenciosamente para subir el resultado.

Valor correcto presente: TP de atributo. Valor esperado omitido: FN de atributo. Valor adicional, incorrecto o asociado a otra entidad: FP de atributo; si reemplaza un valor esperado, también FN. `null` frente a `null` se registra como ausencia correctamente preservada, no como un TP que infle precisión. Reportar métricas por atributo; los umbrales acordados de 95/90 son de entidades, no se inventa otro porcentaje de aceptación para atributos.

Contar como críticos los valores de dosis, frecuencia o duración sin respaldo semántico, incluso si su cadena aparece en otra receta. Los otros atributos sin respaldo también se reportan como incumplimiento de evidencia; no se aceptan por pasar los porcentajes globales.

La resolución de catálogo se compara por estado y conjunto completo de ternas, con el orden contractual y sin candidatos extras o faltantes. Reportar exactitud de resolución por tipo y número de casos sin catálogo; `not_provided` no debe inflar la precisión de coincidencias. Un ID ajeno al catálogo es una infracción de cero tolerancia.

Las advertencias requeridas se comparan por código y entidades/campos afectados después de alinear, sin exigir los mismos IDs, índices de array ni texto humano del gold. Advertencias adicionales válidas por descartes internos son admisibles, pero se cuentan y se informan; no sustituyen hallazgos esperados ni justifican omisiones fuera del objetivo de cobertura. La forma de vacío debe seguir distinguiendo ausencia legítima de descarte total.

## 7. Criterios de aprobación ya acordados

En cada una de las tres rondas reservadas, la cohorte principal debe alcanzar precisión >= 95 % y cobertura >= 90 % en cada tipo de entidad. Usar conteos enteros para el criterio, sin redondear antes de decidir: `100*TP >= 95*(TP+FP)` y `100*TP >= 90*(TP+FN)`, con denominadores positivos.

En las notas fijas principales, adversariales y exploratorias se exige cero exposición de evidencias/IDs inválidos, cero atributos sin respaldo y cero errores críticos de contexto. Incluyen negación o condición promovida a afirmación, antecedentes promovidos a actuales, familiar/otro promovido a paciente, planes u órdenes promovidos a realización, y suspensión promovida a prescripción vigente. Una mención incierta no puede promoverse a un estado conocido sin respaldo.

Precisión/cobertura exploratorias se reportan separadas, sin exigirles los porcentajes principales ni prometer soporte equivalente. Sus fallos críticos siguen siendo visibles y no se excluyen para declarar aprobación. Las adversariales tienen también reporte separado y verificaciones de no obedecer instrucciones ajenas al contenido clínico.

No declarar el MVP terminado si faltan rondas, hay errores operativos principales, adjudicaciones abiertas o criterios incumplidos. Los umbrales no se reducen después de ver los resultados. Ninguna aprobación de este corpus certifica uso con pacientes reales.

## 8. Escala y calibración

Las recetas exploran longitud (1.000, 10.000, 50.000, 200.000, 500.000 y 1.000.000 puntos de código), catálogos (10.000 y 100.000 entradas) y densidad (10 y 100 procedimientos). Ubican hallazgos al inicio, medio y final. Las variantes comparten origen solo dentro del split de escala.

El relleno administrativo permite verificar volumen y posiciones, pero no simula razonamiento clínico de un documento real largo. Analizarlo junto a las notas complejas escritas, sin atribuir igual calidad semántica a un millón de caracteres de relleno y a una epicrisis de esa longitud.

Materializar en memoria, comprobar el body JSON UTF-8 real que se enviaría y el presupuesto de contexto antes de cualquier llamada futura. Registrar tamaño, posición, densidad, catálogo, calidad, errores, latencia, tokens y coste disponible por condición. No llamar al modelo desde el validador ni ejecutar la grilla remota automáticamente.

Un 422 por contexto del proveedor identifica una condición no procesable, no una extracción vacía ni una medida de precisión. No truncar ni introducir fragmentación para hacer pasar la sonda. Recomendar solo rangos efectivamente evaluados y describir huecos de la grilla; no extrapolar una frontera exacta entre dos tamaños probados. Los fallos de escala delimitan rangos y se informan, sin mezclarlos con el promedio principal.

## 9. Reporte y evolución

El reporte futuro incluirá las tres rondas, TP/FP/FN y denominadores, fallos operativos, adjudicaciones, errores críticos, descartes, advertencias, tamaños y consumo. Guardar salidas sintéticas y versiones para reproducir el análisis. No tratar resultados generados por el propio anotador como validación independiente del gold.

Si se usan resultados reservados para ajustar el extractor, mover esos casos a regresión y reemplazarlos por otros de distinto origen antes de otra evaluación independiente. Mantener las métricas originales y la trazabilidad. Los hashes detectan cambios de artefactos; no garantizan secreto, ausencia de sesgo ni independencia semántica de los escenarios.
