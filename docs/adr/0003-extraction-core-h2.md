# ADR 0003 — Núcleo independiente de extracción (H2)

> Registro histórico del hito indicado: sus cifras y pendientes describen ese momento. Estado actual: H1–H4 verificados, H5 evaluado y MVP no aprobado. Consultar [H5](../h5-validation.md), [cierre de sesión](../sesion-27-10-2026/README.md) e [índice documental](../README.md).

Fecha: 2026-10-27. Estado: implementado para H2; la exposición HTTP y el proveedor real pertenecen a H3/H4.

Referencias: [plan](../superpowers/plans/2026-10-27-clinical-extraction-mvp-plan.md), [contrato técnico](0002-extraction-wire-contract.md), [código](../../src/main/java/com/clinicalintelligence/extraction), [corpus de desarrollo](../../evaluation/README.md).

## Decisión de estructura y dependencias

Un proyecto Maven, Java 21, sin servidor, SDK LLM, persistencia ni módulos anticipados. Los tipos de la capacidad están en `domain`, la orquestación y políticas en `application`, y el fake explícito en `infrastructure`. No hay anotaciones HTTP o de serialización en el dominio.

H2 usa ICU4J 77.1 para full case folding independiente de localidad, en lugar de sustituirlo por `toLowerCase`. JUnit 5.13.4 y Jackson 2.20.1 son dependencias exclusivas de pruebas; Jackson solo decodifica fixtures de desarrollo. Spring Boot se incorporará al necesitar HTTP y Spring AI al necesitar el adaptador real, verificando entonces su compatibilidad.

Actualización posterior: H3 incorpora Boot/Jackson runtime y H4 elige REST directo en lugar de Spring AI para mantener un único propietario de presupuestos y cancelación; [ADR 0005](0005-gemini-provider-h4.md) documenta esa alternativa. El dominio H2 conserva su independencia del transporte/proveedor.

Fuentes verificadas: [ICU 77 y soporte Java](https://unicode-org.github.io/icu/download/77.html), [full case folding](https://unicode-org.github.io/icu/userguide/transforms/casemappings.html), [JUnit 5.13.4](https://docs.junit.org/5.13.4/user-guide/), [Jackson 2.20](https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.20), [Surefire 3.5.4](https://maven.apache.org/components/surefire-archives/surefire-3.5.4/maven-surefire-plugin/usage.html).

## Puerto y resultados

`ExtractionProvider` recibe exclusivamente nota original y localidad efectiva. El catálogo no se transmite al proveedor: se resuelve dentro del caso de uso. El proveedor devuelve una propuesta tipada, finalización y metadatos de procesamiento, sin clases del SDK ni IDs resueltos de catálogo.

`ScriptedExtractionProvider` exige una función inyectada explícitamente y siempre identifica su ejecución como `SIMULATED`. No contiene búsquedas por ID de corpus, reglas de extracción clínica, lectura de archivos, credenciales ni llamadas externas. Las pruebas inyectan propuestas controladas; no se presentan como medición de extracción de un LLM.

Las entidades son variantes tipadas de diagnóstico, medicamento y procedimiento. El caso de uso devuelve hallazgos aceptados con IDs locales nuevos, coincidencias derivadas de la entrada, contexto, advertencias y versiones. Los DTOs HTTP se mapearán en H3 al contrato existente, sin convertir objetos internos en el contrato público por conveniencia.

## Política de aceptación

Validar entrada y cotas de nota/catálogos antes de invocar el proveedor. La respuesta global incompleta, nula, ilegible o truncada causa un error controlado; no se transforma en vacío limpio. Un fallo inesperado del proveedor se traduce a código seguro sin conservar mensaje o causa originales.

Una entidad con nombre sin respaldo literal se descarta. Un código o atributo textual inválido se elimina (`null`) con advertencia. Un estado sin evidencia válida se reemplaza por `unknown`, nunca por afirmación o estado vigente. Los estados desconocidos explícitos con evidencia vacía y los atributos ausentes legítimos no producen advertencias por ausencia.

Cada evidencia se comprueba sobre los puntos de código originales, incluyendo emoji, CRLF y Unicode descompuesto. Un sustituto aislado tampoco puede usarse como valor textual aprovechando que Java permite buscar media pareja UTF-16 dentro de una cita válida.

Además de ser literal, la evidencia de una pauta farmacológica debe quedar dentro de una evidencia de afirmación o de estado farmacológico que nombre el mismo medicamento. Esta es una regla interna conservadora del puerto: el adaptador deberá proporcionar un ámbito explícito suficiente para respaldar los atributos, incluyendo bloques con pauta en varias líneas cuando corresponda. Rechaza, por ejemplo, días de control fuera de la cláusula de prescripción o dosis citadas en la cláusula de otro medicamento.

Ese ámbito es también una propuesta no confiable del proveedor. Si una cita amplia incluye varios medicamentos o referencias temporales, la inclusión de texto por sí sola no certifica asociación clínica correcta. H4/H5 deberán evaluar esos errores; no se implementa un motor clínico de expresiones regulares para fingir esa garantía. Esta separación conserva el alcance de validación estructural/literal de H2 y la evaluación semántica acordada.

## Contexto y advertencias

Conservar los valores de contexto sustentados que propone el proveedor, sin convertir negación o antecedente en afirmación actual. No deduplicar por nombre ni fusionar estados incompatibles. Las repeticiones pueden venir agrupadas con sus evidencias según el contrato del proveedor.

El puerto representa advertencias semánticas como código y referencias tipadas a la lista original, no como mensajes o rutas arbitrarios del modelo. El caso de uso verifica esas referencias y las remapea después de los descartes. Solo se propagan contradicciones con al menos dos hallazgos aceptados y avisos de ambigüedad con targets existentes. Los descartes y sus mensajes los genera la aplicación.

La identificación semántica de una contradicción simultánea frente a una secuencia inicial/posterior corresponde a la clasificación del proveedor y su evaluación; H2 preserva las referencias válidas sin inventar una cronología. Un aviso con referencias imposibles es salida no conforme, no una advertencia silenciosamente ignorada.

## Catálogos, inmutabilidad y privacidad

El índice de nombres/alias se construye por petición y tipo de entidad. Aplica NFC, full case folding y espacios Unicode colapsados, preservando acentos, puntuación, presentación y concentración. Devuelve todas las ternas ambiguas, deduplicadas y ordenadas por puntos de código; no usa el orden UTF-16 de Java para IDs con caracteres suplementarios.

Las listas de propuestas, contexto, catálogos y resultados se copian defensivamente. No hay estado clínico mutable compartido en el caso de uso ni caché de índices entre consumidores. Las propuestas conservan valores nulos representables en su límite no confiable para traducirlos en descartes o errores explícitos. Las colecciones del resultado final son inmutables.

El núcleo no escribe logs ni persiste contenido. Sus representaciones diagnósticas sensibles están redactadas; las excepciones públicas contienen códigos controlados. La capa HTTP y el adaptador real deberán mantener estas propiedades también en sus bibliotecas y rutas de error.

## Límites de H2 y tareas de integración

El dominio recibe objetos Java decodificados: `locale == null` representa omisión y se convierte en `es`. H3 deberá rechazar un `null` explícito en JSON antes de mapearlo. La validación de localidad de H2 exige sintaxis bien formada con `Locale.Builder` e idioma primario español; H3 debe añadir la comprobación completa de subtags registrados BCP 47 prometida por el contrato, no confundir sintaxis con registro.

El tamaño del body HTTP, claves JSON duplicadas, campos desconocidos, negociación de contenido, API keys/scopes y Problem Details son responsabilidades de H3. Los presupuestos y condiciones del proveedor real son de H4. La política de entrada de H2 sí limita la nota por puntos de código y las cardinalidades agregadas configurables de catálogos/entradas/alias.

El código productivo no lee el corpus. Maven solo incorpora `development.json` como recurso de pruebas y excluye las anotaciones reservadas. Los tests de desarrollo verifican preservación y resolución de propuestas válidas; no son resultados de calidad de un modelo ni un motor de puntuación de H5.
