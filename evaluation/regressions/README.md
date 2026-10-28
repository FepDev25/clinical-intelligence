# Regresiones identificadas en H5

[Índice de desarrollo v1](h5-development-v1.json) enlaza fallos reales con casos que ya estaban en el corpus sintético y rondas originales preservadas. El gold original describe el resultado deseado; las respuestas reales muestran el fallo. No se presentan estos casos como errores corregidos ni se añade una búsqueda por ID para devolver referencias.

El builder de la siguiente iteración debe usar desarrollo para TDD de políticas/contratos y dobles del proveedor; la calidad del prompt requiere nueva medición real, con todas las rondas y sin seleccionar aciertos. La [nota metodológica](../reports/2026-10-28-gemini38-v1-errata.md) identifica referencias/comparaciones disputadas que requieren una versión explícita antes de reutilizarse como oráculo.

No se copiaron notas ni gold reservados a esta carpeta o a pruebas. Si un resultado reservado se usa para ajustar el extractor, su escenario debe pasar a regresión y reemplazarse antes de otra evaluación independiente; preservar la ejecución v1.
