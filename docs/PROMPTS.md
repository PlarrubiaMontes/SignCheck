# Prompts para Claude Code (guía personal)

Guía para trabajar el proyecto paso a paso con Claude Code. No hace falta copiar el contexto:
Claude Code lee `CLAUDE.md` automáticamente y, desde ahí, los documentos de `docs/`.

## Antes de empezar

1. Crea la carpeta `signcheck/`, mete dentro `CLAUDE.md` y la carpeta `docs/`.
2. `git init` y primer commit con solo la documentación (`docs: add project context for Claude Code`).
3. Abre Claude Code en la raíz del proyecto (`cd signcheck && claude`).
4. Para tareas grandes, usa el **modo plan** (Shift+Tab) para que proponga el plan antes de tocar nada.

## Cómo pedir cada tarea

Plantilla general:

> Lee CLAUDE.md y docs/. Vamos con la tarea X.Y del ROADMAP. Antes de escribir código, explícame
> el plan y qué conceptos nuevos hay. Cuando termine, pasa los tests, marca la tarea y propón el mensaje de commit.

Después de cada tarea:

- Revisa el diff tú mismo (`git diff`) antes de commitear.
- Pide explicaciones de lo que no entiendas: *"Explícame esta clase como si me lo preguntaran en una entrevista."*
- Commit tú, con el mensaje que te propone (o el tuyo).

## Prompts por fase

### Fase 0
> Lee CLAUDE.md y docs/. Empezamos por la tarea 0.1 del ROADMAP. Para la licencia, explícame la diferencia entre MIT y Apache-2.0 y si hay algún problema por usar DSS (LGPL 2.1) como dependencia.

> Tarea 0.2. Comprueba en Maven Central la última versión estable de Spring Boot y dime si es compatible con DSS 6.x antes de crear el proyecto.

> Tarea 0.3. Crea el workflow de GitHub Actions y explícame cada paso del YAML.

### Fase 1
> Tarea 1.1. Busca la última versión de DSS 6.x con soporte de Trusted List v6 y dime qué módulos vamos a necesitar y para qué sirve cada uno.

> Tarea 1.2. Antes de programar la PKI de pruebas, explícame qué es una CA raíz, una intermedia, una CRL y por qué necesitamos un firmante caducado y otro revocado.

> Tarea 1.3 y luego 1.4, de una en una. En la 1.4 quiero entender bien el ValidationReportMapper: qué datos sacamos del SimpleReport y cuáles del DiagnosticData.

> Tarea 1.5.

### Fase 2
> Tarea 2.1. Explícame la diferencia entre XAdES enveloped, enveloping y detached con un ejemplo de cómo queda el XML.

> Tarea 2.2, 2.3, 2.4 y 2.5, de una en una.

### Fase 3
> Tarea 3.1. Explícame los tres modos de confianza y cuándo se usaría cada uno.

> Tarea 3.2. Explícame qué es la LOTL, cómo se valida su firma y qué cambió con TLv6. Usa la documentación oficial de DSS para la versión que tenemos.

> Tareas 3.3, 3.4 y 3.5.

### Fase 4, 5 y 6
> Tarea 4.1 (y siguientes, una a una).

> Tarea 5.1. Usa la última versión estable de Angular y explícame la estructura que crea.

> Tarea 6.3. Escribe el README en inglés pensando en un reclutador técnico que tiene 2 minutos: qué hace, captura, cómo arrancarlo y decisiones técnicas.

### Fase 7
> Tarea 7.1. Recuerda la regla: todo material de prueba marcado como TEST y sin datos reales.

## Prompts útiles en cualquier momento

- *"Haz una revisión de código de lo último que hemos hecho como si fueras un senior: seguridad, tests que faltan y cosas mejorables."*
- *"Prepárame 5 preguntas de entrevista sobre lo que hemos hecho en esta fase y corrige mis respuestas."*
- *"Actualiza el README con el estado actual del proyecto."*
- *"Algo no cuadra entre docs/ y el código; propón qué cambiar en la documentación."*
