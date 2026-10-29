# T4 — Trazabilidad optativa de extracción sintética

Estado: implementada y verificada el 28-10-2026. Solo instrumentación y evaluación local; no se cambiaron reglas de aceptación, prompt, esquema público ni proveedor. No hubo inferencia remota. T2 sigue con adjudicación abierta y el MVP no está aprobado por calidad.

## Fronteras y correlación

La instrumentación introduce un puerto interno `ExtractionTrace`, pasado **por operación**. Las firmas ordinarias conservan su comportamiento y delegan en `ExtractionTrace.NONE`:

- `GeminiProvider.extract(request, trace)` permite observar la propuesta estructurada de esa operación.
- `GeminiProtocol.decode(note, bytes, trace)` emite únicamente el texto de propuesta JSON que pasó el esquema; no el sobre HTTP, cabeceras, clave, uso ni partes etiquetadas como pensamientos.
- `ExtractionService.extract(command, trace)` emite solicitud validada, propuesta tipada recibida, decisiones por mención y resultado o código de fallo controlado.

Para una evaluación que cubra las tres fronteras se debe pasar **el mismo observador nuevo** al proveedor y al servicio. No hay observador almacenado en el proveedor compartido, ThreadLocal, registro global, bean Spring ni interruptor por entorno o body HTTP. `ApiConfiguration` sigue usando las firmas ordinarias sin captura.

| Etapa | Contenido observado | Qué permite distinguir |
| --- | --- | --- |
| Solicitud validada | Nota y localidad realmente pasadas al proveedor | Correlación de operación; no incluye credenciales, tenant ni catálogos completos |
| Propuesta estructurada | Texto JSON del candidato, con cita y occurrence originales | Qué valor/localizador llegó antes de resolver posiciones |
| Propuesta decodificada | Objetos tipados recibidos por el servicio, antes de saneamiento | Posiciones originales en puntos de código y evidencia no localizable |
| Decisión de mención/campo | Índice plano original, tipo, índice original dentro de su familia, destino final o descarte y razones locales | Remapeo después de eliminar menciones; no depende de los IDs finales |
| Resultado/fallo | Resultado aceptado o código controlado | Qué quedó expuesto y qué parte de la ejecución terminó |

`Source.proposalIndex` es la posición en la propuesta tipada plana; `kind/familyIndex` identifica la posición original de la colección del proveedor. Por ejemplo, la segunda mención original de diagnóstico puede terminar en `/diagnoses/0` si la primera se descartó. Se prueba también una propuesta que mezcla familias.

El puerto no decide si el contenido es sintético ni autoriza usos clínicos: no está conectado a peticiones ordinarias. El único recorder/exportador añadido está en **src/test**, usa fixtures locales fijas y no está en el JAR de la aplicación. Un driver futuro de inferencia sintética remota requerirá autorización, presupuesto y procedencia propia; no puede reutilizar las etiquetas `inferenceExecuted:false` de estas reproducciones locales para presentar una inferencia real como replay.

## Decisiones del filtro, no reconstrucciones por heurística

Las razones se registran en las ramas que realmente rechazan:

| Razón | Significado acotado |
| --- | --- |
| `NAME_LITERAL_REJECTED` | Nombre/evidencia no superó la política literal; se eliminó la entidad |
| `TEXT_LITERAL_REJECTED` | Un atributo textual no superó la comprobación literal |
| `CONTEXT_EVIDENCE_REJECTED` | Forma/evidencia de estado o contexto inválida; se reemplazó por unknown |
| `MEDICATION_ASSOCIATION_REJECTED` | Texto literal válido fuera del ámbito de asociación farmacológica permitido |

La última razón refleja el ámbito conservador existente, no una comprensión semántica garantizada. No se cambió la decisión del filtro para producir mejores métricas.

El recorder deriva transiciones comprobables: `PRESERVED`, `ORIGINAL_NULL`, `ORIGINAL_UNKNOWN`, `REMOVED_TO_NULL`, `SANITIZED_TO_UNKNOWN` y `ENTITY_DISCARDED`. Mantiene el valor/evidencia antes y después. `PRESERVED` significa que el saneamiento no lo cambió, **no que sea correcto**. Una evidencia inválida de un estado originalmente unknown también se clasifica como saneamiento si el filtro la eliminó; no se oculta porque el enum final sea igual.

La propuesta tipada usa enums Java en mayúsculas. El resultado final de las cápsulas se genera con el `WireContract.encode` existente y usa los enums del contrato en minúsculas. Los `outputPath` de las transiciones resuelven contra ese resultado. En este driver sin HTTP se usa el UUID del resultado del servicio; no se simula una cabecera de autenticación ni una petición HTTP real.

## Herramienta local y nueve cápsulas

La herramienta es `SyntheticTraceReplayTest` y su helper `SyntheticTraceCapture`, ambos de alcance test. Sin propiedad de exportación solo producen capturas temporales en memoria. Para repetir la exportación usar un directorio **nuevo**:

```sh
mvn -B -ntp -o -Dtest=SyntheticTraceReplayTest \
  -Dci.synthetic.trace.output=/tmp/clinical-t4-new-replay test
```

`CREATE_NEW` impide sobrescribir cápsulas existentes. La propiedad solo se lee en el test, nunca en la aplicación. No hay argumento para cargar notas de usuario ni llamadas a Gemini en ese driver. Los ensayos de proveedor de la suite completa usan un servidor loopback y clave ficticia.

Las [nueve cápsulas conservadas](traces/) tienen IDs de operación distintos, estado de captura válido y cierre de operación. El recorder detecta reutilización o eventos tardíos como captura inválida en vez de mezclar silenciosamente solicitudes. Snapshot devuelve árboles independientes; su modificación no cambia el estado almacenado.

| Fixture | Qué muestra | Atribución permitida en este ensayo |
| --- | --- | --- |
| `preserved` | Propuesta y resultado conservan la mención | No hay pérdida local; no acredita corrección clínica |
| `entity-index-remap` | Primera entidad descartada y segunda remapeada a índice cero | Rechazo literal local e identidad de origen conservada |
| `invalid-occurrence` | El localizador pide una aparición inexistente; la decodificación produce evidencia con posición -1 y el núcleo descarta | Localizador de entrada inválido seguido de rechazo literal; no prueba bug del decoder |
| `invalid-context-occurrence` | Afirmación conocida con localizador inválido termina unknown | Permite separar unknown por saneamiento de unknown original |
| `wrong-valid-occurrence` | El nombre apunta a la primera mención negada y la afirmación cita la segunda; ambas citas son literales y sobreviven | Asociación upstream incompatible en la fixture y límite semántico del filtro; el decoder resolvió la aparición pedida |
| `provider-omission` | Propuesta deliberadamente vacía ante nota sintética con diagnóstico | Omisión introducida en la propuesta, sin descarte local; no es una medición de Gemini |
| `unknown-null-and-discards` | Unknown y null originales se conservan; un texto inventado y duración de control se eliminan por razones distintas | Rechazo literal y rechazo de asociación distinguibles |
| `other-entity-code` | Código de Beta permanece asociado a Alfa porque pasa la comprobación literal | L05 queda reproducido; T4 no corrige asociación de códigos, corresponde a T5 |
| `malformed-output` | `{}` no cumple esquema; no se captura texto estructurado aceptable ni propuesta tipada | Salida no conforme y fallo controlado, nunca vacío correcto |

Las ocho respuestas terminadas se comprobaron con el validador contractual Python y sus destinos de salida resolvieron correctamente. La novena es un fallo controlado. Se trata de propuestas **inyectadas**, no salidas nuevas de un modelo.

## Taxonomía para próximas investigaciones

- **Omisión de propuesta:** el elemento esperado no figura en la propuesta upstream. Requiere una referencia inequívoca; no inferirlo solo de resultado final vacío.
- **Asociación/contexto upstream:** el desacuerdo ya existe en la propuesta, con respaldo adjudicado. Si la referencia es discutible, marcar discrepancia por adjudicar en vez de culpar al modelo.
- **Decodificación/localización:** diferencia entre cita/occurrence recibidos y posición/valor tipados. Un localizador inválido no demuestra un error de implementación del decoder.
- **Rechazo literal/contextual local:** razón exacta emitida por el filtro, incluido saneamiento a unknown.
- **Rechazo de asociación local:** ámbito farmacológico insuficiente según la política actual; medir después si hubo falsos descartes.
- **Discrepancia de referencia:** requiere decisión externa documentada conforme a T2; la captura por sí sola no la resuelve.
- **Causa no determinada:** falta una frontera, hay captura inválida, el proveedor falló o no hay respaldo suficiente para adjudicar.

Puede conocerse tanto un problema upstream como la acción local que lo bloqueó. Esta taxonomía no fuerza una sola causa por operación ni convierte advertencias en errores independientes.

## Privacidad, fallos y tiempo

No se añaden logs ni archivos en código productivo. Los callbacks reciben datos solo en el camino optativo; `NONE` no crea buffer de texto, contadores de origen ni listas de rechazos de diagnóstico. Las observaciones ordinarias de coste/latencia siguen sin contenido clínico. Las pruebas revisan ausencia de nota, clave y excepciones sensibles en logs/telemetría, y exclusión de partes de pensamientos/sobre de transporte de la propuesta capturada.

Se aíslan `RuntimeException` del receptor, conservando resultado/código controlado sin imprimir el mensaje de la excepción. Esto no es un sandbox para código arbitrario: un receptor explícito debe trabajar en memoria y terminar; puede demorar el retorno al llamador. La instantánea y su serialización se hacen después de completar la operación.

La revisión detectó que un receptor lento de propuesta podía ocupar el worker/permiso sujeto a deadline de Gemini. Se reprodujo en rojo con `OVERLOADED`. Ahora el worker solo escribe en un buffer local y el receptor externo recibe el texto después de fijar el resultado, la observación de latencia y liberar la capacidad de generación. Una prueba bloquea ese receptor con un latch mientras una segunda generación con concurrencia uno termina; ambas conservan COMPLETE. No se promete invariancia temporal absoluta del driver de evaluación.

Solo se conserva texto que pasó el esquema. Una salida malformada o truncada no se guarda como texto arbitrario ni se hace pasar por una propuesta vacía; la frontera faltante queda explícita con fallo controlado.

## Verificación e integridad

- `mvn -B -ntp -o verify`: **156 pruebas Java**, cero fallos/errores/omitidas; BUILD SUCCESS. Son 137 previas más 16 de replay/captura y tres nuevas del proveedor.
- Python: **152 pruebas pasan** (57 quality-review, 9 T2, 81 herramientas y 5 control de entrega).
- OpenAPI: 21 esquemas, 29 escenarios, 58 ejemplos y 30 mutaciones inválidas verificados.
- Nueve cápsulas únicas y cerradas; ocho respuestas finales válidas según contrato. La verificación ordinaria sin propiedad de exportación no cambió sus archivos.
- Revisión auxiliar con un hallazgo corregido y vuelto a comprobar: [review.md](review.md).
- Recorder y fixtures excluidos del JAR ejecutable; dos JAR históricos congelados preservados; no se ejecutó `clean`.

Evidencia y comprobaciones en [verification/](verification/). Los logs Maven crudos conservan espacios finales propios de `[INFO]`; se excluyen solo esos logs de la comprobación de formato, no se alteran para mejorarla.

Los 82 insumos del manifiesto T1 tienen **79 sin cambios y tres fuentes productivas modificadas de forma autorizada**: servicio, protocolo y proveedor. El manifiesto original se conserva. Artefactos T1/T2/T3, campañas y auditorías siguen intactos: 102 entradas de checksums verificadas. La nueva interfaz es un archivo nuevo, no un reemplazo de fuente histórica. `integrity.json` detalla antes/después.

Por diseño, los comandos de T1/T3 que exigen igualdad con las fuentes actuales de aquella línea base detectarán estas tres diferencias. No resetear hashes para hacerlos pasar: una reproducción histórica con esa exigencia necesita las fuentes de su commit congelado; una nueva campaña necesita su propia congelación. Las pruebas de evaluadores siguen pasando sobre los resultados históricos.

Hallazgo de continuidad: el log legado de `ApiConfiguration` declara `validation=evidence-1` aunque el resultado del servicio informa `evidence-2`. T4 no usa esa etiqueta como autoridad; la cápsula conserva `Result.processing`. Se registra para alinear telemetría en T7, sin reescribir métricas ni campañas anteriores.

## Continuación

T5 puede usar las trazas para revisar asociaciones de dosis/códigos y medir falsos descartes. T6 atenderá contexto y negación. T4 aporta visibilidad, no corrección semántica: código cruzado, citas amplias y negación aceptada siguen siendo limitaciones. No se repitió inferencia ni se cerraron las disputas clínicas/de producto de T2.
