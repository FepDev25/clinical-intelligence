# Verificación de cierre

Cierre de la sesión local del 27-10-2026. Esta comprobación valida el estado entregado; no cambia los resultados H5 ni ejecuta otra campaña remota.

## Comandos reproducibles y resultados de esta sesión

Desde la raíz, Java 21/Maven y un entorno Python con [requirements](../../openapi/tools/requirements.txt) instalados. El entorno temporal usado fue `/tmp/clinical-contract-validation.wygT3R`; su existencia no es requisito permanente.

```sh
mvn -B -ntp -o verify
```

Resultado ejecutado: BUILD SUCCESS, **134 pruebas Java**, cero fallos/errores/omitidas. `-o` usa dependencias ya resueltas; una primera instalación puede necesitar Maven Central. No usa la clave real ni importa el archivo local.

```sh
python -B -m unittest discover -s evaluation/tools -p 'test_*.py'
python -B -m unittest discover -s evaluation/reports -p 'test_*.py'
python -B openapi/tools/validate_contract.py
```

Resultados ejecutados: **66 + 5 pruebas Python**, todas correctas; OpenAPI 3.1.1, 21 esquemas, 29 escenarios y 58 ejemplos. Se rechazaron 30 mutaciones inválidas y se verificaron límites de normalización/evidencia/catálogos/cabeceras. Las pruebas ordinarias no requieren abrir reservado ni usar un proveedor real.

Se verificaron también enlaces Markdown locales, ausencia de cambios en los activos protegidos y hashes del JAR/snapshot/reportes de la ejecución original. El cierre valida archivos y resultados existentes, no vuelve a ejecutar `release.py` para sobrescribir timestamps o los informes históricos. La auditoría local completa de corpus/escala ya está preservada en [local-corpus-audit.json](../../evaluation/runs/2026-10-28-gemini38-v1/local-corpus-audit.json).

El contenido a commitear se revisa por rutas y patrones de credenciales sin abrir el `.env` del usuario; los archivos ignorados, locks y artefactos de compilación no se agregan. Tras los commits se comprueba que el árbol de trabajo quede limpio. No se hace push ni se cambian SFM/PreAudit Health.

La evaluación de calidad sigue **no aprobada**; pruebas verdes no sustituyen [H5](../h5-validation.md). El [handoff](README.md) registra decisiones, resultados y próximos pasos.

## Observación de formato preservada

La primera incorporación de algunos archivos Java/recursos/pruebas reporta líneas en blanco adicionales al final del archivo con `git diff --check`. Se conserva ese formato para no alterar los bytes de la versión evaluada. La comprobación `git -c core.whitespace=-blank-at-eof diff --cached --check` acepta solo esa excepción cosmética y no reporta otros problemas; no se cambia la configuración permanente de Git. Esto no es un fallo de pruebas ni de ejecución.
