# MAPUA14 supported vocabulary contract

Status: experimental developer-diagnostic profile only.

The authoritative machine-readable vocabulary is:

`android_dry_run/app/src/main/assets/model/mapua14_rescue_v1/class_labels_mapua14_v1.json`

It is selected by `runtime_manifest.json` through `labels_file` and pinned by
SHA-256:

`af398236fd62da6c5bafbe0b60d21bc8a155c48aeb45987090c1b14a20cb9ef0`

The exact ordered IDs are:

```text
0 EIGHT
1 FIVE
2 FOUR
3 HELLO
4 NINE
5 NO
6 ONE
7 SEVEN
8 SIX
9 TEN
10 THANK_YOU
11 THREE
12 TWO
13 YES
```

`MAPUA14_LIVE_SEGMENT_V1` changes temporal runtime processing only. It uses the
same model and this same 14-label asset as `MAPUA14_RESCUE_V1`; it does not add
vocabulary.

Any future UI or guide shown while this debug profile is active must derive its
supported list and presentation text from the selected runtime manifest and
label asset. It must not use a manually invented marketing list or imply that
this experimental classifier recognizes arbitrary FSL words. Standard FSL-105
remains a separate profile and is unchanged.

