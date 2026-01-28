# HANDOFF — FactorLite (libGDX + Kotlin)

Коротко: **2D survivor-like**. Игрок **только двигается** через floating joystick, персонаж **авто-атакует ближайшего врага**, прогресс: **XP → Level Up (оружие/пассивки)** + **сундуки за gold забега** (предметы).

---

## Быстрый запуск (Desktop)

- **Команда**: `.\gradlew.bat :lwjgl3:run`
- **Точка входа desktop**: `lwjgl3/src/main/kotlin/com/factorlite/lwjgl3/Lwjgl3Launcher.kt`
- **Окно**: 960×540, 60 FPS, vsync on.

---

## Android APK

- **Сборка debug APK**: `.\gradlew.bat :android:assembleDebug`
- **Готовый файл (после сборки)**: `android/build/outputs/apk/versioned/factorlite_v.<factorliteVersion>.apk`
- **Версия**: `gradle.properties` → `factorliteVersion` и `factorliteVersionCode`

---

## Структура проекта

- **`core/`**: вся логика игры и “рендер”.
  - `core/src/main/kotlin/com/factorlite/FactorLiteGame.kt` — старт игры, выставляет `GameScreen`.
  - `core/src/main/kotlin/com/factorlite/screens/GameScreen.kt` — “оркестратор” забега: связывает системы, держит состояние, рендерит мир/overlay.
  - `core/src/main/kotlin/com/factorlite/screens/RunUiSystem.kt` — HUD + оверлеи выбора (LevelUp/Chest/Shrine), обработка тапа.
  - `core/src/main/kotlin/com/factorlite/game/SpawnDirector.kt` — темп спавна (бюджет на кадр, crowd-control по alive).
  - `core/src/main/kotlin/com/factorlite/game/EnemySystem.kt` — поведение врагов (в т.ч. дальники).
  - `core/src/main/kotlin/com/factorlite/game/CombatSystem.kt` — проджектайлы (игрок/враги), попадания/коллизии.
  - `core/src/main/kotlin/com/factorlite/game/LootSystem.kt` — XP/Gold орбы + сундуки (лимиты, спавн, открытие).
  - `core/src/main/kotlin/com/factorlite/game/TargetingSystem.kt` — авто-таргет с гистерезисом.
  - `core/src/main/kotlin/com/factorlite/game/PlayerDamageSystem.kt` — HP игрока, i-frames, щит/хил.
  - `core/src/main/kotlin/com/factorlite/game/ShrineSystem.kt` — святыни (зона стояния → выбор глобального бонуса).
  - `core/src/main/kotlin/com/factorlite/progression/RunProgression.kt` — XP/LevelUp, слоты **2 оружия + 2 пассивки**, применение апгрейдов/глобальных бонусов.
  - `core/src/main/kotlin/com/factorlite/progression/UpgradeDirector.kt` — генерация карточек апгрейда с редкостью и строкой “было → стало”.
  - `core/src/main/kotlin/com/factorlite/loot/Items.kt` — предметы из сундуков (rarity, kind, описания), ролл предметов/редкости.
  - `core/src/main/kotlin/com/factorlite/game/RunState.kt` — состояния забега.
  - `core/src/main/kotlin/com/factorlite/input/FloatingJoystick.kt` — ввод джойстика.
- **`lwjgl3/`**: desktop launcher/зависимости LWJGL3.
- **`android/`**: Android launcher/манифест/сборка APK.
- **`assets/`**: общие ассеты (подключены и к Desktop, и к Android через Gradle).

---

## Версии / сборка

- **Kotlin**: `2.0.21`
- **libGDX**: `1.12.1`
- **Java toolchain**: 17
- Gradle multi-module: `core`, `lwjgl3`, `android` (см. `settings.gradle.kts`)

---

## Текущая геймплейная петля (как работает сейчас)

`GameScreen` связывает системы и управляет `RunState`:

- **RunState**:
  - `RUNNING`: всё обновляется.
  - `LEVEL_UP`: мир “стоит”, ждём выбор апгрейда.
  - `CHEST_OPEN`: мир “стоит”, ждём выбор предмета.
  - `SHRINE_OPEN`: мир “стоит”, ждём выбор глобального бонуса.
  - `GAME_OVER`: R/Space → рестарт.
  - `VICTORY`: победа над боссом (после 5 минут).
- **Движение**:
  - Вектор берётся из `joystick.direction`, **Y инвертируется**: `playerVel.set(x, -y)`.
  - Ограничение по “арене” (прямоугольник).
- **Враги**:
  - Спавн волнами по краям арены, темп задаёт `SpawnDirector`.
  - Типы: normal/fast/tank/ranged + элитки (плюс босс на 5-й минуте).
  - Дамаг игроку по контакту + i-frames.
- **Авто-таргет**:
  - Каждые ~0.12s пытаемся выбрать ближайшего, есть “гистерезис” (переключение только если новая цель заметно ближе).
- **Оружие/атака**:
  - Оружия живут в `progression.weapons`, у каждого свой `cooldown`.
  - Сейчас реализованы `BLASTER`, `REVOLVER`, `SWORD`.
  - Есть “моды” на уровне оружия: extra projectiles, pierce, ricochet.
- **XP/LevelUp**:
  - XP и level в `RunProgression`. При убийстве врага начисляется XP, если ап — переход в `LEVEL_UP` и генерация 3 карточек.
- **Сундуки**:
  - Лут (XP/Gold/сундуки) ведёт `LootSystem`.
  - Сундуки открываются **автоматически по близости** если хватает `gold`.
  - Стоимость сундука **удваивается** после открытия.
  - В сундуке 3 предмета на выбор (`Items.kt`), выбор: тап по карточке / 1-2-3 на Desktop.

---

## Где менять баланс/контент (самые частые точки)

- **Спавн врагов / темп сложности**: `core/.../game/SpawnDirector.kt` + `GameScreen.spawnEnemy(...)`
- **Параметры арены**: `GameScreen.arenaHalfW/arenaHalfH`
- **Стартовые статы игрока**: `GameScreen.resetRun` (HP, скорость, стартовое оружие)
- **Слоты 2+2**: `RunProgression.maxWeapons/maxPassives`
- **Кривая XP**: `RunProgression.nextXpGoal(level)`
- **Пассивки и их формулы**:
  - Добавляются через `PassiveKind` (см. `core/src/main/kotlin/com/factorlite/progression/Definitions.kt`)
  - Мультипликаторы/шансы: `RunProgression.getMoveSpeedMultiplier/getDamageMultiplier/getFireRateMultiplier/getCritChance/getCritDamageMultiplier`
- **Оружия**:
  - Энум: `WeaponKind` (в `Definitions.kt`)
  - Реализация атаки — в `GameScreen` (spawnProjectile/параметры паттернов)
  - Карточки апгрейдов — `UpgradeDirector`
- **Предметы из сундуков**:
  - Список: `core/src/main/kotlin/com/factorlite/loot/Items.kt` (`ItemKind`, `makeItemOption`, `rollRarityCommonChest`, `rollItemKind`)
  - Применение эффектов сейчас “вшито” в `GameScreen` (например, `TOXIC_BARREL`, `SHIELD_CHARM`, `BURGER_DROP`).

---

## Грабли / заметки по текущему MVP

- **Шрифт/кириллица**: `GameScreen.rebuildFont()`
  - Ищет `fonts/Roboto-Regular.ttf` через `Gdx.files.internal(...)` (из общей папки `assets/`).
  - Для релиза — просто положить `assets/fonts/Roboto-Regular.ttf` и не полагаться на системные пути.
- **Joystick и координаты**:
  - Тач в libGDX по Y идёт “сверху вниз”, поэтому в движении сделана **инверсия Y**.
  - Отрисовка джойстика сейчас делается через `viewport.unproject` (ок для MVP).
- **Pause-логика**:
  - В `LEVEL_UP` и `CHEST_OPEN` сейчас мир не апдейтится (логика стоит), но рендер продолжается.
  

---

## Что делать дальше (минимальные тех-долги)

- Довести `assets/` до реального контента (шрифт/иконки/спрайты/звук).
- Ввести data-driven defs для оружий/пассивок/предметов (чтобы контент добавлялся без правок логики).
- Полировка темпа/баланса на 5 минут + босс.
