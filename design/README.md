# Brand sheet

`brand-sheet.html` — open it in any browser. It shows the mark at the sizes Android actually
renders it, with the construction drawing, the launcher masks, the themed-icon layer, the
notification glyph, the lockups and the palette.

The SVG in it is the **same geometry** as the vector drawables the app ships
(`android/app/src/main/res/drawable/ic_launcher_*.xml`). It is a specimen sheet, not a
second source of truth: if the mark changes, change the drawables first and mirror it here.
