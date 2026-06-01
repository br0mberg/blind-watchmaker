# Genetic Biomorph Evolution Simulator

Симулятор эволюции биоморф (Р. Докинз, «Слепой часовщик») на Clojure + [cljfx](https://github.com/cljfx/cljfx).

## Требования

- JDK 17+
- [Clojure CLI](https://clojure.org/guides/install_clojure)

## Запуск

```bash
clojure -M:run
```

## Архитектура

- **UI** (`biomorph.ui.*`) — декларативный интерфейс cljfx, UDF через `atom` и `handle-event`.
- **Движок** (`biomorph.engine.*`) — протоколы `BiomorphEngine` / `FitnessEvaluator`; текущая реализация — **заглушка** (`StubEngine`) с псевдо-фенотипами и фейковым fitness.
- **Состояние** (`biomorph.state`) — единый map состояния приложения.

Полное описание — в [arch.md](arch.md), постановка — в [task.md](task.md).
