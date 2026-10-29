# T8 — perfiles y transporte verificados documentalmente

Consulta oficial: 28-10-2026. No se ha comprobado acceso de la cuenta, cuota efectiva, precio contractual ni respuesta remota. Las pruebas del arnés usan servidores locales y datos sintéticos.

| Perfil | Uso en estudio | Precio estándar USD/millón, entrada / salida incl. thinking | Vigencia de la tarifa congelada |
| --- | --- | --- | --- |
| `gemini-3.8-flash` | v2/v3 LOW, v3 MEDIUM/HIGH, detección/clasificación LOW | 0.75 / 3.75 | Hasta 31-12-2026 |
| `gemini-4-argon` | v3 LOW, evidence-4 | 2.00 / 10.00 | Hasta 30-11-2026 |

Fuentes: [precios Developer API](https://ai.google.dev/gemini-api/docs/pricing), [ficha Argon](https://ai.google.dev/gemini-api/docs/models/gemini-4-argon), [thinking](https://ai.google.dev/gemini-api/docs/generate-content/thinking). Las tarifas pueden variar: el ejecutor rechaza fechas posteriores a las vigencias congeladas. No usa descuentos de caché para liberar presupuesto adicional. No es una conciliación con la factura de la cuenta.

## Decisión de transporte

La [guía específica de long decode](https://ai.google.dev/gemini-api/docs/generate-content/long-decode) distingue **32 768 tokens en REST no transmitido** y **65 536 con streaming** para Argon. La ficha genérica por sí sola no basta para verificar esta diferencia. Se usa `streamGenerateContent?alt=sse` en **todos** los brazos T8, manteniendo el mismo máximo de 65 536 tokens combinados de thinking y salida final. CountTokens recibe la misma petición completa. Se omiten los parámetros de muestreo y candidateCount ya retirados en T7.

Un salto por generación; `CONTINUATION` se registra como truncamiento y no se sigue su token. No hay SDK que haga continuaciones, reintentos ni fallback. La acumulación SSE está acotada a 16 MiB y 120 s por etapa, incluido el conteo. Se conservan recibo final, identidad, propuesta estructurada y tiempos; no se conservan partes de pensamiento ni firmas ni envoltura REST bruta. El ensamblador exige un final y recibo compatible; un recibo parcial no libera la reserva.

El cambio de transporte es común a todos los brazos: la base no es una reproducción exacta de la campaña histórica. Producción sigue usando Flash/v2/LOW y su transporte ordinario. La API pública y los catálogos no cambian.

LOW nominal en modelos distintos no representa igual cómputo. El contraste Argon/Flash mide **modelo y perfil operativo bajo la misma tarea**, no superioridad general ni equivalencia de coste de razonamiento. El alias y la versión comunicada se conservan por separado; la ausencia de versión inmutable sigue siendo un límite.
