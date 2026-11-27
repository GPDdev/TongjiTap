# Third-party components

The Apache-2.0 license in this repository applies to TongjiTap project source. It does not relicense third-party libraries.

| Component | Version / source | Terms |
| --- | --- | --- |
| Stride music functionality | GPDdev/StrideTune, `d3b357f49113309c9c67c89ee3ec499fe86b2768` | Incorporated at the project owner's request. Copyright 2026 GPDdev. Upstream had no published LICENSE at integration; its unpublished license draft was not treated as a grant. |
| AndroidX Media3 (ExoPlayer and MediaSession) | 1.8.0 | [Apache-2.0](https://github.com/androidx/media/blob/1.8.0/LICENSE) |
| AndroidX Activity | 1.6.0 | Apache-2.0, with its notice included in the dependency artifact |
| AndroidX libraries, Guava and Kotlin runtime dependencies | Resolved by Gradle | Respective Apache-2.0 notices in dependency artifacts |

Music metadata and audio remain subject to the music provider's terms and rights. The application uses public playlist endpoints and available streams, without login, membership bypass or encrypted-cache decoding.
