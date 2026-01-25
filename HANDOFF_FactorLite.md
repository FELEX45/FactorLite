# HANDOFF — FactorLite (libGDX + Kotlin)

Коротко: **2D survivor-like**. Игрок **только двигается** через floating joystick, персонаж **авто-атакует ближайшего врага**, прогресс: **XP → Level Up (оружие/пассивки)** + **сундуки за gold забега** (предметы).

---

## Быстрый запуск (Desktop)

- **Команда**: `.\gradlew.bat :lwjgl3:run`
- **Точка входа desktop**: `lwjgl3/src/main/kotlin/com/factorlite/lwjgl3/Lwjgl3Launcher.kt`
- **Окно**: 960×540, 60 FPS, vsync on.

---

## Структура проекта

- **`core/`**: вся логика игры и “рендер”.
  - `core/src/main/kotlin/com/factorlite/FactorLiteGame.kt` — старт игры, выставляет `GameScreen`.
  - `core/src/main/kotlin/com/factorlite/screens/GameScreen.kt` — **почти весь MVP сейчас здесь**: управление, мир, враги, снаряды, прогрессия, сундуки, HUD.
  - `core/src/main/kotlin/com/factorlite/progression/RunProgression.kt` — XP/LevelUp, слоты **2 оружия + 2 пассивки**, генерация и применение апгрейдов.
  - `core/src/main/kotlin/com/factorlite/loot/Items.kt` — предметы из сундуков (rarity, kind, описания), ролл предметов/редкости.
  - `core/src/main/kotlin/com/factorlite/game/RunState.kt` — состояния забега.
  - `core/src/main/kotlin/com/factorlite/input/FloatingJoystick.kt` — ввод джойстика.
- **`lwjgl3/`**: desktop launcher/зависимости LWJGL3.

---

## Версии / сборка

- **Kotlin**: `2.0.21`
- **libGDX**: `1.12.1`
- **Java toolchain**: 17
- Gradle multi-module: `core`, `lwjgl3` (см. `settings.gradle.kts`)

---

## Текущая геймплейная петля (как работает сейчас)

Вся петля собрана в `GameScreen`:

- **RunState**:
  - `RUNNING`: всё обновляется.
  - `LEVEL_UP`: мир “стоит”, ждём выбор апгрейда.
  - `CHEST_OPEN`: мир “стоит”, ждём выбор предмета.
  - `GAME_OVER`: R/Space → рестарт.
- **Движение**:
  - Вектор берётся из `joystick.direction`, **Y инвертируется**: `playerVel.set(x, -y)`.
  - Ограничение по “арене” (прямоугольник).
- **Враги**:
  - Спавн волнами по краям арены, интервал уменьшается со временем (`updateSpawns`).
  - 3 типа (normal/fast/tank) — вероятности растут с `runTime`.
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
  - Спавнятся раз в ~9s, открываются **автоматически по близости** если хватает `gold`.
  - Стоимость сундука **удваивается** после открытия.
  - В сундуке 3 предмета на выбор (`Items.kt`), выбор: тап по карточке / 1-2-3 на Desktop.

---

## Где менять баланс/контент (самые частые точки)

- **Спавн врагов / темп сложности**: `GameScreen.updateSpawns`, `GameScreen.spawnEnemy`
  - Интервалы: `maxInterval/minInterval`, кривая по `runTime`.
  - Хп/скорость/награды врагов задаются в `spawnEnemy`.
- **Параметры арены**: `GameScreen.arenaHalfW/arenaHalfH`
- **Стартовые статы игрока**: `GameScreen.resetRun` (HP, скорость, стартовое оружие)
- **Слоты 2+2**: `RunProgression.maxWeapons/maxPassives`
- **Кривая XP**: `RunProgression.nextXpGoal(level)`
- **Пассивки и их формулы**:
  - Добавляются через `PassiveKind` (см. `core/src/main/kotlin/com/factorlite/progression/Definitions.kt`)
  - Мультипликаторы/шансы: `RunProgression.getMoveSpeedMultiplier/getDamageMultiplier/getFireRateMultiplier/getCritChance/getCritDamageMultiplier`
- **Оружия**:
  - Энум: `WeaponKind` (в `Definitions.kt`)
  - Реализация атаки — в `GameScreen.updateAttacking` + `fireBlaster/fireRevolver/swingSword`
  - Апгрейды/моды выдаются через `RunProgression.makeUpgradeChoices` и применяются `applyUpgrade/applyWeaponMod`
- **Предметы из сундуков**:
  - Список: `core/src/main/kotlin/com/factorlite/loot/Items.kt` (`ItemKind`, `makeItemOption`, `rollRarityCommonChest`, `rollItemKind`)
  - Применение эффектов сейчас “вшито” в `GameScreen` (например, `TOXIC_BARREL`, `SHIELD_CHARM`, `BURGER_DROP`).

---

## Грабли / заметки по текущему MVP

- **Шрифт/кириллица**: `GameScreen.rebuildFont()`
  - Пытается загрузить `fonts/Roboto-Regular.ttf` из assets.
  - Фоллбек на `C:/Windows/Fonts/arial.ttf`.
  - Если ничего нет — фоллбек на `BitmapFont()` (кириллица может исчезнуть).
  - Рекомендация: положить TTF в `assets/fonts/Roboto-Regular.ttf` и использовать только `Gdx.files.internal(...)`.
- **Joystick и координаты**:
  - Тач в libGDX по Y идёт “сверху вниз”, поэтому в движении сделана **инверсия Y**.
  - Отрисовка джойстика сейчас делается через `viewport.unproject` (ок для MVP).
- **Pause-логика**:
  - В `LEVEL_UP` и `CHEST_OPEN` сейчас мир не апдейтится (логика стоит), но рендер продолжается.
- **Монолит `GameScreen`**:
  - Сейчас это “всё-в-одном” для скорости прототипа. Следующий шаг — выносить системы (combat/spawn/progression/ui).

---

## Что делать дальше (минимальные тех-долги)

- Разнести `GameScreen` на системы/классы (хотя бы: Combat/Spawning/Progression/UI).
- Ввести data-driven defs для оружий/пассивок/предметов.
- Добавить нормальные ассеты (спрайты/шрифты), убрать зависимость от системного Arial.
