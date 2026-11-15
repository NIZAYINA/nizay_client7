# Nizay client (Fabric 1.21.11)

Состав:
- `src/` — Fabric-мод (меню по **Right Shift**, 30 эффектов, крылья, HUD, AuctionHelper)
- `tools/gen_textures.py` — рисует все 31 спрайт частиц с нуля (уже запущен, PNG лежат в resources)
- `launcher/` — Electron-лаунчер по макетам (Главная / Аккаунты / Настройки / Информация)

## Сборка через GitHub (проще всего)
1. Создайте репозиторий на github.com и загрузите в него содержимое этой папки (Add file → Upload files, либо `git push`).
2. Вкладка **Actions** → **Build Nizay** → **Run workflow** (запускается и сам при каждом push).
3. Через 5–10 минут внизу страницы запуска, в **Artifacts**, скачайте `nizay-launcher` (там `Nizay client.exe`, мод уже внутри) и `nizay-mod`.
4. Хотите готовый релиз — создайте тег: `git tag v0.1.0 && git push --tags`, файлы появятся в **Releases**.
Если шаг «Мод» красный — пришлите лог, поправлю; лаунчер при этом всё равно соберётся.

## Сборка мода локально
1. Java 21, затем сверьте версии в `gradle.properties` с https://fabricmc.net/develop/ (loom, fabric-api для 1.21.11).
2. `gradle build` → `build/libs/nizay-client-0.1.0.jar`.
3. Положите jar в `launcher/mod/` (лаунчер сам скопирует его в `mods/` и скачает Fabric API).

## Лаунчер
Требуется Node.js 20+.
- Запуск: двойной клик по `launcher/start.bat` (или `npm install && npm start`).
- Сборка `Nizay client.exe` с иконкой: `launcher/build-exe.bat` → папка `launcher/dist/`.
- Иконку можно перерисовать: `python3 tools/gen_icon.py`.

## Эффекты
1–15 — по референсам: Stardust Trail, Prism Ripple, Aether Orbs, Ember Drift, Frost Bloom, Petal Storm, Pulse Rings,
Void Rift, Silk Ribbons, Firefly Cloud, Crystal Shards, Comet Burst, Halo, Rune Circle, Mist Veil.
16–30 — мои: Neon Helix, Thunder Sparks, Lava Bloom, Starfall, Soul Wisps, Bubble Pop, Gravity Rings, Pixel Rain,
Heartbeat, Shadow Smoke, Rainbow Trail, Sun Flares, Moon Dust, Wind Swirl, Confetti Pop.

## AuctionHelper (FunTime)
Right Shift → Auction → включить. Цены и тексты меню — в `.minecraft/config/nizay.json`, блок `ah`:
- `prices` — `"id предмета": цена`. Выставляются только предметы из этого списка.
- `ahTitleRegex`, `myLotsTitleRegex`, `myLotsSlot`, `expiredLoreRegex` — **подгоните под реальные меню сервера**
  (я их не видел, значения по умолчанию — предположения).
- Команда выставления: `/ah sell <цена>` (`sellCommand`).
Использование макросов может нарушать правила сервера и приводить к бану — на ваш риск.

## Не реализовано / требует проверки
- **Небо (Visual → Sky)**: пресеты и настройки сохраняются, но сам рендер неба не подключён
  (API рендера 1.21.11 нужно сверять при компиляции).
- **Крылья**: v1 собраны из частиц-перьев (без render-layer), поэтому они «шагают» по тикам.
- Код писался без доступа к сети и **не компилировался**; имена классов Mojang для 1.21.11
  (SingleQuadParticle, MouseButtonEvent, HudElementRegistry, Inventory.setSelectedSlot, ClientLevel.addParticle)
  могут потребовать мелких правок после первой сборки.

---
## v0.2.0 — что изменилось
- **Интерфейс**: меню и HUD рисуются в физических пикселях с антиалиасингом (`gui/Ui.java`), шрифт Inter (TTF), скруглённые карточки, тени, анимированные тумблеры/слайдеры. Тумблеры стали крупнее (большая область клика).
- **Небо**: теперь реально подменяет ванильное, в том числе на серверах, где небо отключено (хуки в `LevelRenderer`, схема из мода Nuit, MIT). Кубические карты генерирует `tools/gen_skies.py`. В Незере не включается.
- **Крылья и анимации**: частицы «на один тик» с интерполяцией между текущей и предсказанной позицией — не отстают от игрока, частиц в 3 раза меньше.
- **HUD**: ватермарк-плашка (лого | пинг | FPS) и плашка координат; в чате (T) таскаются мышью, за точку в углу — размер.
- **Optimization**: вкладка в меню (настройки видео + лимитер эффектов).
- **Accounts**: кнопка на титульном экране и вкладка в меню; список берётся из `<gameDir>/nizay-accounts.json`, который пишет лаунчер.

---
## v0.3.0
- Интерфейс рисуется 9-slice текстурами (`tools/gen_ui.py`) вместо сотен `fill` — резко меньше нагрузка на FPS. Фон меню без размытия.
- Новое главное меню (`NizayTitleScreen`), вкладка Function (Zoom, Fullbright, Hit Effects), Friends, Configs (код `NZY1:...`), Cooldowns в HUD.
- Главный переключатель Enable all (запоминает прежнее состояние), запоминание вкладки и прокрутки.
- **Друзья**: нужен relay-сервер: `cd server && node server.js` (порт 8787, лучше за https). Адрес укажите в лаунчере, вкладка «Друзья».
- Скрипты ассетов: `tools/gen_skies.py`, `gen_previews.py`, `gen_ui.py`, `boost_particles.py`.
