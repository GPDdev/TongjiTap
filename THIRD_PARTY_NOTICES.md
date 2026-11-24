# Third-party components

The Apache-2.0 license in this repository applies to TongjiTap project source. It does not relicense third-party SDKs or models.

| Component | Version / source | Terms |
| --- | --- | --- |
| README download badge | [nyxiereal/get-it-on](https://github.com/nyxiereal/get-it-on/blob/066aadb66c553147e4e9613cf1a522ef50806751/badges/svg/github.svg), unchanged SVG at `066aadb66c553147e4e9613cf1a522ef50806751` | Copyright 2026 Nyxiereal; [MIT license](./docs/licenses/get-it-on-MIT.txt). GitHub marks belong to their respective owner. |
| Stride music functionality | GPDdev/StrideTune, `d3b357f49113309c9c67c89ee3ec499fe86b2768` | Incorporated at the project owner's request. Copyright 2026 GPDdev. Upstream had no published LICENSE at integration; its unpublished license draft was not treated as a grant. |
| AndroidX Media3 (ExoPlayer and MediaSession) | 1.8.0 | [Apache-2.0](https://github.com/androidx/media/blob/1.8.0/LICENSE) |
| AndroidX libraries, Guava and Kotlin runtime dependencies | Resolved by Gradle | Respective Apache-2.0 notices in dependency artifacts |
| Google ML Kit text recognition and bundled Latin model | `com.google.mlkit:text-recognition:16.0.1` | [ML Kit Terms and Privacy](https://developers.google.com/ml-kit/terms), incorporating Google APIs Terms of Service |
| Google Play services and transport dependencies of ML Kit | Resolved by Gradle | Their distributed license terms; not relicensed by this project |

ML Kit processes images and recognition results on the device. It can contact Google and send performance and usage metrics. See [Google's Android data disclosure](https://developers.google.com/ml-kit/android-data-disclosure). TongjiTap does not upload or persist screenshots.

Music metadata and audio remain subject to the music provider's terms and rights. The application uses public playlist endpoints and available streams, without login, membership bypass or encrypted-cache decoding.
