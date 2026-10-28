# Consumir Clinical Extraction v0.1

El contrato es [OpenAPI](../openapi/openapi.json). La operación `POST /api/v1/extractions` recibe una nota original y catálogos opcionales; devuelve propuestas con evidencia y `reviewRequired: true`. No necesita SFM, PreAudit Health, base de datos ni historial conversacional. La aplicación consumidora decide qué aceptar, editar o guardar.

## Configuración local

1. Usar Java 21 y construir con `mvn verify`.
2. Generar la credencial de consumidor y su archivo de verificadores fuera del repositorio siguiendo [H3](h3-integration.md). Esa credencial identifica al integrador y requiere `extractions:write`.
3. Seleccionar explícitamente `CI_PROVIDER_MODE=gemini`, configurar `CI_CREDENTIALS_FILE` y proporcionar `GOOGLE_API_KEY` a la aplicación según [H4](h4-integration.md). La clave de Google y la del consumidor son distintas. El modo `simulated` sirve para transporte y devuelve vacío; no realiza inferencia.
4. Arrancar en loopback. Si la clave está en el archivo local, solo el proceso Java lo importa:

```sh
java -jar target/clinical-intelligence-0.1.0-SNAPSHOT.jar \
  --server.address=127.0.0.1 \
  '--spring.config.import=optional:file:.env[.properties]'
```

No imprimir claves ni activar trazas de cuerpos. Un modo o una configuración incompletos impiden el arranque. Para acceso remoto es necesario HTTPS; esta entrega no publica el servidor.

## Ejemplo sintético externo

Con la variable `CI_API_KEY` ya configurada según H3, desde otra terminal:

```sh
curl --url http://127.0.0.1:8080/api/v1/extractions \
  --header 'Content-Type: application/json' \
  --header 'Accept: application/json, application/problem+json' \
  --header "X-API-Key: ${CI_API_KEY}" \
  --data-binary '{"clinicalNote":"Diagnóstico actual del paciente: asma.","locale":"es-EC"}'
```

La llamada utiliza Google y genera consumo. No hay reintentos automáticos en este ejemplo. Comprobar `processing.executionMode=provider`, `provider=google-gemini` y las versiones de procesamiento. La salida puede variar; un HTTP 200 no certifica la interpretación.

Los offsets de evidencia son puntos de código Unicode `[start,end)` sobre la nota original: no normalizarla antes de usarlos. En JavaScript no equivalen necesariamente a índices UTF-16. Los catálogos de la petición se resuelven localmente con nombre/alias explícito; no se envían al modelo y no se completan códigos por conocimiento médico.

## Revisar el resultado

- Revisar cada entidad, su contexto, estado de prescripción/realización y la cita que sustenta cada atributo. Una cita literal puede estar atribuida a la entidad equivocada.
- Conservar `unknown`, `null`, ausencia de catálogo y ambigüedad. No convertir una orden en procedimiento realizado ni un antecedente en diagnóstico actual.
- Mostrar las advertencias y sus targets. Vacío sin advertencias, vacío con descartes y fallo de operación tienen significados distintos.
- Usar los candidatos de catálogo tal como se entregan; no resolver arbitrariamente una ambigüedad. No aceptar automáticamente un diagnóstico, tratamiento o cargo.

## Errores y límites

El esquema normativo y todos los códigos están en [OpenAPI y ejemplos](../openapi/README.md). Los errores usan `application/problem+json`, `Cache-Control: no-store` y `X-Request-Id` correlacionado con el cuerpo. Para soporte, registrar ese identificador y el código técnico, sin la nota ni claves.

| Situación | Respuesta esperada |
| --- | --- |
| Credencial ausente/inválida o scope insuficiente | 401 / 403 antes de invocar al modelo. |
| JSON, localidad o campos inválidos | Error de validación del contrato. |
| Body, nota o catálogo sobre el límite configurado | 413, sin recortes. |
| Petición que no cabe en el contexto del modelo | 422 `PROVIDER_CAPACITY_EXCEEDED`, sin fragmentación implícita. |
| Proveedor indisponible, salida no conforme o truncada | Error explícito; no se convierte en vacío exitoso. |
| Saturación o timeout | Error operativo según contrato; no repetir automáticamente sin un presupuesto propio. |

Los valores altos de [H3](h3-integration.md) y [H4](h4-integration.md) son protecciones técnicas. No garantizan calidad a ese tamaño. La evaluación de notas fijas y la grilla de escala local se reportan por separado en [H5](h5-validation.md); la grilla remota no se ejecuta automáticamente.

## Datos y alcance de la entrega

La API no persiste notas ni resultados por defecto y la observabilidad de producción excluye contenido clínico. El arnés H5 guarda únicamente solicitudes/salidas sintéticas para reproducir métricas; no debe usarse como almacenamiento de pacientes.

Inferencia remota significa que Google recibe la nota. Facturación habilitada, servidor privado y uso de `generateContent` no equivalen a retención externa cero ni autorización de uso clínico. Las condiciones y comprobaciones pendientes están en [H4](h4-integration.md). Esta entrega y sus ejemplos usan solo datos sintéticos; no se afirma certificación regulatoria ni validación clínica independiente.
