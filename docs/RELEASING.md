# Выпуск релиза / Publishing a release

[Русский](#русский) · [English](#english)

## Русский

Релизы собирает [.github/workflows/release.yml](../.github/workflows/release.yml), когда в
репозиторий отправлен тег `v*`. Workflow запускает тесты, собирает release APK, подписанный
ключом релиза, и прикладывает к релизу на GitHub два одинаковых файла:

- `WristGestures-<версия>.apk` — с версией в имени;
- `WristGestures.apk` — с постоянным именем, на него ведёт кнопка «Скачать» в README
  (`releases/latest/download/WristGestures.apk`).

Версия берётся из тега: `v1.2.3` → versionName `1.2.3`, versionCode `10203`.

```bash
git tag v1.0.3
git push origin v1.0.3
```

### Однократная настройка

Ключ подписи в репозитории не хранится. Добавьте его в
**Settings → Secrets and variables → Actions → New repository secret**:

| Секрет | Значение |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | файл keystore в base64 (`base64 -w0 release.jks`) |
| `SIGNING_KEYSTORE_PASSWORD` | пароль keystore |
| `SIGNING_KEY_ALIAS` | псевдоним ключа |
| `SIGNING_KEY_PASSWORD` | пароль ключа |

Для локальной release-сборки те же данные кладутся в `keystore.properties` в корне проекта
(он в `.gitignore`):

```properties
storeFile=keystore/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Без ключа release-сборка подписывается локальным отладочным ключом. Такой APK не обновит
официальный релиз.

> **Храните резервную копию keystore и паролей.** Если ключ потеряется, новые версии нельзя
> будет поставить обновлением: всем пользователям придётся сначала удалить приложение.

После смены ключа обновите отпечаток сертификата в README (раздел «Проверка подлинности APK»):

```bash
apksigner verify --print-certs WristGestures.apk
```

## English

Releases are built by [.github/workflows/release.yml](../.github/workflows/release.yml) when a
tag `v*` is pushed. The workflow runs the unit tests, builds the release APK signed with the
release key, and attaches two identical files to the GitHub release:

- `WristGestures-<version>.apk` — with the version in the name;
- `WristGestures.apk` — with a fixed name, used by the Download button in the README
  (`releases/latest/download/WristGestures.apk`).

The version comes from the tag: `v1.2.3` → versionName `1.2.3`, versionCode `10203`.

```bash
git tag v1.0.3
git push origin v1.0.3
```

### One-time setup

The signing key is not stored in the repository. Add it under
**Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | the keystore file encoded with base64 (`base64 -w0 release.jks`) |
| `SIGNING_KEYSTORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | key alias |
| `SIGNING_KEY_PASSWORD` | key password |

For local release builds put the same data into `keystore.properties` in the project root
(git-ignored):

```properties
storeFile=keystore/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without a key the release build is signed with the local debug key. Such an APK cannot update
the official release.

> **Keep a backup of the keystore and passwords.** If the key is lost, new versions can no
> longer be installed as updates: every user would have to uninstall the app first.

If the key ever changes, update the certificate fingerprint in the README ("Verifying the APK"):

```bash
apksigner verify --print-certs WristGestures.apk
```
