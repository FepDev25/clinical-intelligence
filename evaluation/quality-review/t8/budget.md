# T8 — presupuesto propuesto, todavía sin autorización

Se propone un **tope global conservador estimado de USD 20** para una campaña sintética vinculada al SHA-256 del manifiesto `freeze.json`. No se ejecutó countTokens ni generación remota. Este documento y la plantilla `granted:false` no conceden autorización.

39 notas expuestas × 3 rondas × 6 configuraciones = **702 unidades**. La variante de dos etapas puede requerir otra generación: máximo **819 generaciones + 819 conteos = 1 638 peticiones HTTP** y **936 evaluaciones locales** en ocho brazos. Reutilizar la propuesta con otro filtro no incurre en una nueva inferencia.

## Reserva y conciliación

Antes incluso del conteo se exige margen para una generación a capacidad máxima: 1 048 576 tokens de entrada y 65 536 de salida+thinking. Es deliberadamente conservador, además de que el conteo debe caber en la ventana restando la salida reservada.

| Modelo | Reserva máxima por generación |
| --- | --- |
| Flash | USD 1.032192 |
| Argon | USD 2.752512 |

La cota teórica de todas las generaciones a estos máximos es **USD 1 046.642688** (702 Flash + 117 Argon). **No es el presupuesto propuesto ni autorizado.** La factura real esperable para notas cortas será distinta; sin uso remoto observado del perfil no se presenta una estimación central como garantía.

El tope propuesto de USD 20 **no garantiza completar la campaña**. El ejecutor libera la diferencia de la reserva únicamente ante contadores finales conciliables; usa entrada completa y salida+thinking, sin abaratar por caché. Ante uso ausente, inconsistente, fuera de capacidad o identidad ajena, retiene la reserva y detiene todo. Ante error HTTP/transporte, no reintenta. Los importes son estimaciones por tarifa documentada, no un límite configurado en la cuenta del proveedor; no cubren consumos de otros procesos.

Cada intención se escribe y sincroniza antes de su petición; las unidades y recibos tienen checksums. Una autorización de manifiesto se reclama una sola vez con creación exclusiva antes de cargar la clave; tras cualquier intención de conteo queda consumida, incluso si la aplicación se interrumpe. No hay reanudación automática ni posibilidad legítima de reutilizarla en otro directorio. Una nueva campaña necesita otro manifiesto y autorización. Una interrupción antes de cualquier intención permite corregir la configuración sin gastar.

Las tarifas congeladas y su vencimiento están en [provider-capabilities.md](provider-capabilities.md). El máximo de salida no se reduce para acomodar USD 20. Una detención por margen insuficiente puede dejar parte del tope sin consumir; los intentos faltantes seguirán figurando en los denominadores y no habrá selección de candidata.

## Autorización pendiente

El [plan aprobado](../../../docs/superpowers/plans/2026-10-28-extraction-quality-review-and-remediation.md) exige: «No inferencia remota hasta contar con configuración, estimación, límite de gasto y autorización explícita para el experimento concreto». Al aprobar, se crea un archivo separado con `granted:true`, SHA exacto del manifiesto, `maxUsd:"20"` y referencia a la aprobación. El agente no abre `.env`; únicamente la aplicación Java carga su credencial, después de verificar autorización, binarios y classpath.
