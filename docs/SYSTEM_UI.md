# How the system UI is handled / Как устроен системный интерфейс

[Русский](#русский) · [English](#english)

## Русский

Что выяснилось про интерфейс Mobvoi TicWatch Pro 3 (`com.mobvoi.wear.refsysui`, Wear OS 3.5).
На этом построена логика действий в
[`ActionPerformer`](../app/src/main/java/xyz/quenix/wristgestures/service/ActionPerformer.kt).

- **Циферблат** — полноэкранный вертикальный пейджер. Прокрутка вперёд открывает уведомления,
  назад — быстрые настройки. Поэтому поворот от себя на циферблате открывает уведомления, а
  к себе — быстрые настройки.
- **Быстрые настройки** — контейнер, который умеет прокручиваться только вперёд, и эта прокрутка
  закрывает шторку. Поворот от себя в шторке возвращает на циферблат.
- **Панель уведомлений** — полноэкранный контейнер со списком внутри. Пока список можно листать
  вверх, «прокрутку назад» получает он. В самом верху её предлагает только контейнер, и
  прокрутка контейнера назад закрывает панель. Поэтому служба всегда выбирает **самый вложенный**
  прокручиваемый элемент из элементов одинакового размера.
- Кнопка **Home** открывает список приложений, а не циферблат. **«Назад» на циферблате** тоже
  открывает и закрывает список приложений, поэтому на циферблате «назад» не выполняется.
  Циферблат распознаётся по тому, что на экране нет текста и кликабельных элементов (у его
  разметки есть только описание вроде «Циферблат 13:07»).
- Обычные приложения (Настройки, список приложений, Telegram) поддерживают стандартные действия
  прокрутки спец. возможностей, поэтому жесты работают в них без доработок.
- **Принудительная остановка** приложения (`am force-stop` или «Остановить» в настройках)
  выключает его службу спец. возможностей: Android сам удаляет её из списка включённых.

На других часах может быть иначе. Отладочная сборка печатает структуру экрана перед каждым
действием: `adb logcat -s WristGestures`.

## English

Findings on the Mobvoi TicWatch Pro 3 system UI (`com.mobvoi.wear.refsysui`, Wear OS 3.5).
The action logic in
[`ActionPerformer`](../app/src/main/java/xyz/quenix/wristgestures/service/ActionPerformer.kt)
is built around them.

- The **watch face** is a full-screen vertical pager. Scrolling it forward opens notifications,
  scrolling it back opens quick settings. That is why flick out on the watch face opens
  notifications and flick in opens quick settings.
- **Quick settings** is a container that can only scroll forward, and scrolling it forward
  closes the shade. Flick out in quick settings returns to the watch face.
- The **notification panel** is a full-screen container with a list inside. While the list can
  scroll up, it gets the "scroll back" action; at the top only the container offers it, and
  scrolling the container back closes the panel. That is why the service always picks the
  **deepest** scrollable node among nodes of the same size.
- The **Home** key opens the app list, not the watch face, and **Back on the watch face** also
  toggles the app list. So "back" is skipped on the watch face. The watch face is recognized by
  having no text and nothing clickable on screen (its layout only has a content description
  such as "Watch face 13:07").
- Regular apps (Settings, app list, Telegram) support the standard accessibility scroll actions,
  so gestures work in them without special handling.
- **Force-stopping** the app (`am force-stop` or "Force stop" in settings) disables its
  accessibility service: Android removes it from the enabled list.

Other watches may differ. The debug build prints the screen structure before every action:
`adb logcat -s WristGestures`.
