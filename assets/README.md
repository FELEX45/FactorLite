# Assets (FactorLite)

Эта папка — **единый источник ассетов** для Desktop (`lwjgl3`) и Android.

Структура (минимум для проекта):
- `fonts/` — TTF/OTF шрифты
- `textures/` — PNG атласы/спрайты
- `sfx/` — короткие звуки (wav/ogg)
- `music/` — музыка (ogg)

Подключение:
- Desktop: Gradle добавляет `../assets` как `resources` (см. `lwjgl3/build.gradle.kts`)
- Android: Gradle добавляет `../assets` как `assets` (см. `android/build.gradle.kts`)

