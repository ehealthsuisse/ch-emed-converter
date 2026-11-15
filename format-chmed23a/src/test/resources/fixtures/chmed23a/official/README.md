# Official CHMED23A examples

The four documents of `chmed23a/examples.md` in the repository `ig-emediplan/specification`, commit
`0b579aa6f3aff10a017498db57048c6504091653`, copied verbatim (the JSON blocks of "Example 1" to
"Example 4"; the `json5` fence holds strict JSON).

| File | Document | Author |
|------|----------|--------|
| `Example1.json` | medication plan (`medType` 1) | healthcare person (`auth` 1) |
| `Example2.json` | medication plan | healthcare person |
| `Example3.json` | medication plan | patient (`auth` 2) |
| `Example4.json` | prescription (`medType` 3) | healthcare person |

The data is synthetic (invented persons, `fake-e-mail.ch`). Findings that the tests assert
(research R13): non-ASCII free text (F1), fields with usage `-` for the document type (F2), no
document `id` (F3), local patient ids with `sId` (F5), identifiers in unusual formats (F7).

Licence of the source:

```text
MIT License

Copyright (c) 2024 ig-emediplan

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
