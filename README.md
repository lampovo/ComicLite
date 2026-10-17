# ComicLite Basic

A small, fast comic reader for old Android devices and e-ink readers (Android 4.2 and newer).

- Opens **CBZ** (zip), **CBR** (rar) and **folders of images** (jpg, png, webp, gif, bmp).
- Opens big archives at once: only the index is read, pages are unpacked when needed.
- The next and previous pages are prepared in the background, so turning a page is instant.
- Tap the right or left side of the screen, or use the volume / page keys.
- Remembers the page of every comic, even after it is moved to another folder.
- White background and no animations — made for e-ink screens.

## Build

Open the folder in Android Studio, or run:

```
./gradlew assembleRelease
```

The release build is signed with the debug key so it installs right away. For distribution, sign it with your own key.

## License

MIT — see [LICENSE](LICENSE). Third-party code: see [NOTICE](NOTICE).

---

# ComicLite Basic (по-русски)

Маленькая быстрая читалка комиксов для старых Android-устройств и электронных книг (Android 4.2 и новее).

- Открывает **CBZ**, **CBR** и **папки с картинками** (jpg, png, webp, gif, bmp).
- Большие архивы открываются сразу: читается только оглавление, страницы распаковываются по мере чтения.
- Следующая и предыдущая страницы готовятся заранее, поэтому листается без задержки.
- Листать — тапом по правому или левому краю экрана или кнопками громкости / листания.
- Запоминает страницу в каждом комиксе, даже если файл переложить в другую папку.
- Белый фон и никаких анимаций — сделано для экранов e-ink.

Лицензия MIT.
