# Validación H3 — API independiente consumible

> Registro histórico del hito indicado: sus cifras y pendientes describen ese momento. Estado actual: H1–H4 verificados, H5 evaluado y MVP no aprobado. Consultar [H5](h5-validation.md), [cierre de sesión](sesion-27-10-2026/README.md) e [índice documental](README.md).

Fecha: 2026-10-27. API HTTP implementada y verificada con proveedor simulado. Revisión independiente final completada, sin incidencias críticas ni importantes.

## Resultado

`POST /api/v1/extractions` invoca el núcleo H2 mediante un adaptador HTTP separado. Autentica API keys por consumidor, exige `extractions:write`, valida JSON/UTF-8 y límites antes del proveedor y devuelve el contrato OpenAPI v0.1.0 con Problem Details, correlación y `no-store`.

Entradas relevantes: [aplicación](../src/main/java/com/clinicalintelligence/ClinicalIntelligenceApplication.java), [adaptador HTTP](../src/main/java/com/clinicalintelligence/http), [POM](../pom.xml), [pruebas HTTP](../src/test/java/com/clinicalintelligence/http), [ADR 0004](adr/0004-extraction-http-h3.md), [guía de consumo](h3-integration.md), [registro de ejecución](h3-progress.md).

## Evidencia ejecutada

| Verificación | Resultado |
| --- | --- |
| `mvn -B -ntp -o verify` (verificación final) | BUILD SUCCESS; 107 pruebas Java, cero fallos/errores/omitidas; JAR ejecutable generado sin resolución de dependencias por red. |
| Servidor HTTP real de pruebas | Puerto efímero de loopback; diez ejemplos públicos de éxito y errores de autenticación, scope, validación, medios, límites y proveedor. |
| Respuestas frente a OpenAPI | JSON Schema 2020-12 con formatos habilitados; nombres, enums, campos obligatorios, nulabilidad y rechazo de propiedades adicionales. |
| Concurrencia HTTP | 20 peticiones de dos consumidores, notas/catálogos distintos y UUIDs únicos; sin mezcla de resultados. |
| Límites | Frontera exacta de bytes y byte adicional; cuerpos enviados por fragmentos; nota por puntos de código (emoji); cardinalidades agregadas de catálogos, entradas y alias. |
| Arranque | Ausencia de credenciales o de selección explícita del fake y modos no implementados impiden arrancar; sin fallback al fake. |
| `java -jar` y cURL externo | 200 con `simulated`, listas vacías, `reviewRequired`, localidad preservada y cabeceras/UUID coincidentes; 401 sin clave. Proceso detenido y credenciales temporales eliminadas. |
| Privacidad del smoke | La clave y un marcador sintético de nota no aparecen en el log del proceso. |
| Generación documentada de credenciales | Snippet ejecutado; verificadores correctos, archivos 0600 y ninguna clave impresa. |
| Inspección del JAR | Incluye HTTP; excluye corpus, anotaciones reservadas, JUnit y validador de esquemas exclusivo de pruebas. |
| Dependencias resueltas | Boot 3.5.16; JUnit API/engine/params 5.13.4; Jackson core/databind 2.21.4 y annotations 2.21 alineados por BOM. |
| Herramientas Python de H1 | 32 pruebas aprobadas; OpenAPI válido (21 esquemas, 29 escenarios, 58 ejemplos; 30 mutaciones rechazadas); 39 casos de desarrollo válidos, hashes conservados. |

Las pruebas conservan como artefactos locales respuestas exclusivamente sintéticas en `target/h3-contract/`. No contienen credenciales ni anotaciones reservadas. Las pruebas de contrato inyectan propuestas conocidas para verificar transporte y políticas; no evalúan un modelo.

## TDD y límites

El registro documenta fallos iniciales de credenciales/localidades y mapeo; las seis pruebas HTTP iniciales fallaron con 404 antes de implementar la operación. Las dos pruebas de arranque demostraron que faltaba el cierre por configuración. Después de implementar se verificaron los ejemplos y fronteras adicionales, manteniendo el núcleo H2 verde.

El revisor independiente inspeccionó código, contrato y pruebas, ejecutó el JAR desde un directorio temporal y verificó el snapshot contra IANA sin modificar el checkout ni abrir reservado. No encontró incidencias críticas/importantes. Quedan dos observaciones menores:

- La negociación `Accept` no distingue parámetros de representación como `profile`: una combinación poco habitual puede aceptar JSON plano pese a excluirlo en otro rango. El uso ordinario `application/json` y exclusiones por `q=0` sí está cubierto. Se difiere el ajuste con su regresión específica.
- El filtro sintáctico heredado de OpenAPI clasifica formas BCP 47 con idioma primario de un carácter, como `i-klingon` o `x-demo`, como 400 en lugar de 422. No afecta las etiquetas españolas. Una aclaración o cambio requiere mantener sincronizados contrato y validador.

Los comportamientos que el revisor dejó fuera de H3 y las razones/costes de mantener ese alcance están registrados uno por uno en el registro de ejecución: calidad real, controles operativos del proveedor, TLS/proxy remoto, errores previos al filtro, semántica de extensiones, evaluación clínica del núcleo y reservado.

La configuración adopta archivos externos con verificadores de claves aleatorias, rotación y revocación al reiniciar. El registro IANA se incluye como snapshot; no se descarga durante peticiones. Se mantienen los máximos iniciales altos del contrato y su configuración por despliegue. Los costes y razones de estas decisiones están en el ADR y registro.

El fake de arranque produce propuestas vacías, sin inferencia. No hay proveedor real, rate limiting ni presupuestos operativos del modelo en H3. La ausencia de logs sensibles se verificó con la configuración entregada; habilitar trazas o proxies requiere revisar sus propias rutas de registro. HTTP remoto sin TLS se rechaza; proxies externos y rechazos previos al filtro pueden no seguir Problem Details.

No se abrieron anotaciones reservadas, no se enviaron notas a proveedores ni se modificaron SFM/PreAudit Health. Siguiente incremento: H4, decidir/documentar proveedor y modelo, implementar el adaptador real y sus controles operativos antes de evaluar calidad en H5.
