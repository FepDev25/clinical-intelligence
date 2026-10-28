# Integración local de H3

H3 expone `POST /api/v1/extractions` mediante el contrato v0.1.0. El proveedor de arranque es un **fake explícito que devuelve listas vacías**: permite integrar autenticación, validación y transporte; no interpreta la nota. Las pruebas inyectan propuestas controladas para verificar entidades y advertencias. El modelo real se incorporó en H4. Para la integración actual consultar [H5](h5-integration.md); esta guía conserva el arranque con fake y la gestión de credenciales, sin presentar sus vacíos como extracción.

## Arranque y consumo reproducible

Requisitos: Java 21, Maven 3.9+, Python 3, cURL y jq. La primera compilación descarga dependencias de Maven Central. La ejecución no requiere BD, SFM, contenedores ni claves de un proveedor LLM.

Desde la raíz, en una misma terminal:

```sh
mvn verify

export CI_LOCAL_DIR="$(mktemp -d /tmp/clinical-intelligence.XXXXXX)"
python3 - <<'PY'
import hashlib, json, os, pathlib, secrets
directory = pathlib.Path(os.environ["CI_LOCAL_DIR"])
key = "ci_" + secrets.token_urlsafe(32)
files = {
    "api-key": key,
    "credentials.json": json.dumps([{
        "sha256": hashlib.sha256(key.encode("ascii")).hexdigest(),
        "consumerId": "local-integrator",
        "scopes": ["extractions:write"]
    }])
}
for name, content in files.items():
    path = directory / name
    with open(path, "x", opener=lambda path, flags: os.open(path, flags, 0o600)) as stream:
        stream.write(content)
PY

export CI_CREDENTIALS_FILE="${CI_LOCAL_DIR}/credentials.json"
export CI_PROVIDER_MODE=simulated
export CI_API_KEY="$(cat "${CI_LOCAL_DIR}/api-key")"

java -jar target/clinical-intelligence-0.1.0-SNAPSHOT.jar > "${CI_LOCAL_DIR}/server.log" 2>&1 &
CI_LOCAL_PID=$!

jq '.value' openapi/examples/success-request.json | curl \
  --retry 20 --retry-connrefused --retry-delay 1 \
  --url http://127.0.0.1:8080/api/v1/extractions \
  --header 'Content-Type: application/json' \
  --header 'Accept: application/json, application/problem+json' \
  --header "X-API-Key: ${CI_API_KEY}" \
  --data-binary @-
```

El resultado esperado es HTTP 200, `processing.executionMode: simulated`, `reviewRequired: true` y las tres listas vacías. No será idéntico al ejemplo ilustrativo `success-response.json`, que representa una propuesta inyectada en pruebas, no inferencia del fake de arranque. La petición no se persiste.

Para detener el proceso y borrar exclusivamente el directorio temporal creado arriba:

```sh
kill "${CI_LOCAL_PID}"
wait "${CI_LOCAL_PID}" || true
rm -r -- "${CI_LOCAL_DIR}"
unset CI_API_KEY CI_CREDENTIALS_FILE CI_PROVIDER_MODE CI_LOCAL_DIR CI_LOCAL_PID
```

No activar `set -x`, imprimir la clave ni guardarla en el repositorio. Los reintentos del ejemplo son para el fake local mientras arranca; repetir peticiones con un proveedor real puede generar consumo adicional.

## Configuración

| Variable | Uso / valor por defecto |
| --- | --- |
| `CI_PROVIDER_MODE` | Obligatoria: `simulated` para esta guía. H4 añade el modo `gemini` con configuración propia; ausencia o modo desconocido impiden arrancar. |
| `CI_CREDENTIALS_FILE` | Obligatoria: archivo externo JSON con verificadores e identidad/scopes. |
| `CI_BODY_BYTES` | 67.108.864 bytes HTTP sin comprimir (64 MiB). |
| `CI_NOTE_CODE_POINTS` | 1.000.000 puntos de código de la nota original. |
| `CI_CATALOGS` | 1.000 catálogos por petición. |
| `CI_ENTRIES` | 100.000 entradas sumadas. |
| `CI_ALIASES` | 1.000.000 alias sumados. |
| `SERVER_ADDRESS` | `127.0.0.1`. |
| `SERVER_PORT` | `8080`. |

Cada límite debe ser un entero positivo representable; `2147483647` está excluido para permitir detectar el byte de exceso sin overflow. Cambiarlo exige documentar los valores efectivos del despliegue. Son protecciones técnicas provisionales, no garantías de capacidad o calidad. Un exceso produce 413; no hay recorte.

La configuración de credenciales es una lista no vacía de objetos con exactamente `sha256` (64 caracteres hexadecimales minúsculos), `consumerId` y `scopes` (lista sin duplicados, puede estar vacía). El archivo no puede superar 1 MiB. El token generado tiene prefijo `ci_` y 43 caracteres base64url. SHA-256 es un verificador para claves aleatorias de 256 bits, no un esquema para contraseñas humanas.

Se rechazan verificadores duplicados, identidades inválidas, campos extra y configuración incompleta. La identidad y permisos se derivan de la clave; el consumidor no los establece en el body. Una clave sin `extractions:write` recibe 403 antes de leer la nota.

Para rotar, añadir el nuevo verificador con su identidad/scopes y reiniciar las instancias; después retirar el anterior y reiniciar de nuevo. La revocación tiene efecto cuando cada instancia carga el archivo actualizado. No hay recarga automática ni promesa de revocación instantánea entre instancias.

## Transporte y errores

Solo se acepta JSON UTF-8 y `Content-Encoding` ausente o `identity`. Autenticación y scope preceden a la decodificación. La nota conserva espacios, CRLF y Unicode originales. Se rechazan claves duplicadas, campos desconocidos, tipos incompatibles, nulos explícitos, Unicode inválido y contenido posterior al documento JSON.

Se valida BCP 47 contra la copia IANA versionada del recurso [BCP 47](../src/main/resources/bcp47/SOURCE.md). Se admiten etiquetas españolas registradas, extensiones BCP 47 y uso privado válidos. La localidad exacta recibida se conserva; si se omite, `es`. No se detecta el idioma de la nota ni se validan preferencias clínicas/culturales de extensiones Unicode.

Éxito usa `application/json`; errores, `application/problem+json`. Ambos incluyen `Cache-Control: no-store` y un `X-Request-Id` generado por el servidor que coincide con el cuerpo. No se refleja un ID recibido. 401 añade el desafío de API key; 405 incluye `Allow: POST`. Las rutas adicionales no forman parte de esta API; reciben un rechazo controlado tras autenticación.

El acceso HTTP está permitido en loopback. Para acceso remoto se requiere HTTPS, mediante TLS del servidor o un proxy local que termine TLS y solo se comunique con el backend por loopback. No se confía en cabeceras forwarded para convertir tráfico remoto HTTP en seguro. Configurar el proxy es responsabilidad del despliegue; H3 no publica servicios.

No hay logs de bodies, notas, claves o resultados en la configuración entregada. No habilitar access logs, trazas de cuerpos o depuración HTTP que capturen esos datos. Los errores generados por proxies o por el contenedor antes de entrar al filtro pueden quedar fuera del formato de la aplicación.

H4 implementó capacidad del proveedor, saturación y presupuestos operativos; sus fallos y límites se describen en [la guía Gemini](h4-integration.md). El 429 comercial por consumidor no se implementó como cuota propia. No hay endpoints administrativos, persistencia clínica ni gestión comercial de consumidores.

## Verificación

`mvn verify` ejecuta las pruebas del núcleo, credenciales, localidades, arranque y HTTP real en un puerto efímero de loopback. Valida las respuestas contra los esquemas de OpenAPI, reproduce los diez ejemplos públicos de éxito mediante propuestas controladas y comprueba concurrencia entre consumidores. Los ejemplos públicos y el corpus de desarrollo son datos de prueba; las anotaciones reservadas siguen fuera de TDD y del JAR.

Decisiones: [ADR 0004](adr/0004-extraction-http-h3.md). Evidencia y límites del hito: [validación de H3](h3-validation.md).

