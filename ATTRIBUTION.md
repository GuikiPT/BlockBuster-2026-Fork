# ATTRIBUTION — third-party code bundled in this mod

This mod is a **full port** of Blockbuster 2.7.2 (Forge 1.12.2) to Fabric
1.20.4, with the needed functionality of its dependencies (McLib, Metamorph,
Aperture), the Metamorph add-on **Chameleon**, and one vendored library
(open_imaging `GifDecoder`) **reimplemented / bundled inside the single mod
jar**. Every body of code that ships in
`blockbuster-3.0.0-port.jar` is enumerated below with its **exact** upstream
license terms, read verbatim from the source trees in this repository — not
paraphrased.

## Combined-work license

Blockbuster and Aperture are **GPL-3.0**. GPLv3 is the strongest (copyleft)
license among the bundled components, so the **combined work ships under
GPL-3.0-only** (see `fabric.mod.json` `"license"` and the full text in
`LICENSE`, GNU GPL v3, 29 June 2007). The MIT-licensed components (McLib,
Metamorph, Chameleon) and the Apache-2.0 `GifDecoder` are GPLv3-compatible; their notices
are preserved verbatim below and must remain in any redistribution.

| Component | Upstream | Version | License | Source of truth (this repo) |
|---|---|---|---|---|
| Blockbuster | McHorse | 2.7.2 | GPL-3.0 | `blockbuster-1.12/LICENSE` |
| McLib | McHorse | 2.4.3 | MIT | `.tools/legacy-src/mclib/LICENSE.md` |
| Metamorph | McHorse | 1.4 | MIT | `.tools/legacy-src/metamorph/LICENSE.md` |
| Aperture | McHorse | 1.8.2 | GPL-3.0 | `.tools/legacy-src/aperture/LICENSE` |
| Chameleon | McHorse (maintained by Chryfi) | 1.2.2 | MIT | `chameleon/LICENSE` |
| open_imaging `GifDecoder` | Dhyan Blum | (vendored) | Apache-2.0 | `blockbuster-1.12/.../at/dhyan/open_imaging/GifDecoder.java` header |
| javax.vecmath | (Maven Central) | 1.5.2 | (upstream jar-in-jar) | bundled via Gradle `include`; ships its own license in the nested jar |

---

## Blockbuster 2.7.2 — GNU GPL v3

The original Blockbuster mod by McHorse. Licensed under the **GNU General Public
License, Version 3, 29 June 2007**. First lines of `blockbuster-1.12/LICENSE`:

```
                    GNU GENERAL PUBLIC LICENSE
                       Version 3, 29 June 2007

 Copyright (C) 2007 Free Software Foundation, Inc. <https://fsf.org/>
 Everyone is permitted to copy and distribute verbatim copies
 of this license document, but changing it is not allowed.
```

The full 674-line GPLv3 text is shipped in this module's `LICENSE` file (copied
verbatim from `blockbuster-1.12/LICENSE`) and, renamed `LICENSE_blockbuster`,
inside the release jar.

---

## Aperture 1.8.2 — GNU GPL v3

Camera/cinematic system by McHorse. Licensed under the **GNU General Public
License, Version 3, 29 June 2007** — identical text to Blockbuster's `LICENSE`
(verified: `.tools/legacy-src/aperture/LICENSE`, 674 lines, byte-identical GPLv3
preamble). Covered by the shared `LICENSE` in this module.

---

## McLib 2.4.3 — MIT

UI / config / math library by McHorse. The complete license text, verbatim from
`.tools/legacy-src/mclib/LICENSE.md`:

```
The MIT License (MIT)

Copyright (c) 2018 McHorse

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is furnished
to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
DEALINGS IN THE SOFTWARE.
```

---

## Metamorph 1.4 — MIT

Morphing system by McHorse. The complete license text, verbatim from
`.tools/legacy-src/metamorph/LICENSE.md` (identical to McLib's except the
copyright year):

```
The MIT License (MIT)

Copyright (c) 2019 McHorse

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is furnished
to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
DEALINGS IN THE SOFTWARE.
```

---

## Chameleon 1.2.2 — MIT

Bedrock (`.geo.json`) model + Molang animation morphs, a Metamorph add-on by
McHorse, since maintained by Chryfi with contributions from MiaoNLI and
Tactsohg. The complete license text, verbatim from `chameleon/LICENSE`
(identical to McLib's except the copyright year):

```
MIT License

Copyright (c) 2020 McHorse

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

The mod id is `chameleon_morph`, not `chameleon` — a 1.12.2 collision
workaround with an unrelated mod of the same name, preserved because it is what
names the `config/chameleon/` folder the port still reads.

---

## open_imaging `GifDecoder` — Apache License 2.0

GIF-decoding class used for animated skins (`at.dhyan.open_imaging.GifDecoder`).
The Apache-2.0 notice, verbatim from the file header in
`blockbuster-1.12/src/main/java/at/dhyan/open_imaging/GifDecoder.java`:

```
Copyright 2014 Dhyan Blum

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

Apache-2.0 code inside a GPLv3 combined work is permitted (one-way
compatibility); this NOTICE-style attribution must survive verbatim in any
redistribution, and the copyright/license header is retained on the ported
source file.

---

## javax.vecmath 1.5.2 (bundled via jar-in-jar)

Vector/matrix math library (`javax.vecmath.*`) retained for signature
diff-ability against the legacy McLib/Blockbuster math (roadmap P13). It is
**not** reimplemented — the upstream artifact `javax.vecmath:vecmath:1.5.2` from
Maven Central is nested inside the mod jar via Gradle Loom's `include`. Its own
upstream license travels inside that nested jar; its terms are not restated here
to avoid transcribing a license not present in this repository's source trees.

---

## Test fixtures (NOT redistributed in the jar)

The captured Blockbuster 2.7.2 data under
`blockbuster-fabric/src/test/resources/fixtures/` (models, records, scenes,
configs — see `SAMPLES.md`) is the user's own captured content used only for
headless parity tests. It is a **test resource** and is deliberately **excluded
from the release jar** (`ReleaseJarTest` asserts its absence when a built jar is
present). It is not upstream mod content and is not redistributed.
