# FSL-105 Handedness Classification

Generated: 2026-06-30T12:38:50

## Scope

This report is produced by `scripts_ml/93_classify_fsl105_handedness.py`. It inspects FSL-105 videos with MediaPipe Holistic and classifies each sign as `ONE_HANDED`, `TWO_HANDED`, or `AMBIGUOUS` for VoxGest training decisions.

- FSL root: `C:\BSIT 3RD YEAR\New VovGest\FSL-105 A dataset for recognizing 105 Filipino sign language videos`
- Output directory: `C:\BSIT 3RD YEAR\New VovGest\reports`
- Metadata files read: 3
- Metadata labels found: 105
- Videos processed in this run: 0 (limited run, max_videos=1)
- Failed videos: 0

## Current Dataset Status

The script did not run MediaPipe over videos because extracted video files were not available.

```text
ERROR: No extracted FSL-105 video files were found.
Searched under: C:\BSIT 3RD YEAR\New VovGest\FSL-105 A dataset for recognizing 105 Filipino sign language videos
Expected extensions: .avi, .m4v, .mkv, .mov, .mp4, .webm
Found archive(s) instead:
- C:\BSIT 3RD YEAR\New VovGest\FSL-105 A dataset for recognizing 105 Filipino sign language videos\FSL-105 A dataset for recognizing 105 Filipino sign language videos\clips.zip: 2130 video entries
Extract clips.zip first so metadata paths like clips\17\6.MOV exist. This script will not unzip or modify the dataset.
```

## Statistics Used

- `left_presence_ratio` and `right_presence_ratio`: fraction of decoded frames where MediaPipe detected each hand.
- `both_presence_ratio`: fraction of frames where both hands were detected together.
- `left_motion_total` and `right_motion_total`: summed palm-center movement between consecutive detected frames.
- `left_motion_mean` and `right_motion_mean`: average palm-center movement per valid motion step.
- `dominant_hand`: hand with greater total motion, falling back to presence if motion is tied.
- `non_dominant_presence_ratio`: presence ratio for the hand that is not dominant.
- `non_dominant_motion_ratio`: non-dominant total motion divided by dominant total motion.

## Heuristic

- `MIN_ACTIVE_HAND_PRESENCE = 0.45`
- `MAX_PASSIVE_HAND_PRESENCE_FOR_ONE_HAND = 0.25`
- `MIN_BOTH_HAND_PRESENCE_FOR_TWO_HAND = 0.35`
- `MIN_NON_DOMINANT_MOTION_RATIO_FOR_TWO_HAND = 0.25`
- `MIN_NON_DOMINANT_PRESENCE_RATIO_FOR_TWO_HAND = 0.35`

`ONE_HANDED` means one hand is active enough while the passive hand remains below the one-hand presence ceiling. `TWO_HANDED` means both hands are present together often enough and the non-dominant hand shows support or motion. `AMBIGUOUS` means the video/class does not satisfy either rule strongly enough and should be manually reviewed.

## Likely One-Handed Classes

None recorded in this run.

## Likely Two-Handed Classes

None recorded in this run.

## Manual Review Classes

| label | video_count | final_classification | confidence_note | one_handed_votes | two_handed_votes | ambiguous_votes |
| --- | --- | --- | --- | --- | --- | --- |
| APRIL | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| AUGUST | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| AUNTIE | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| BEER | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| BLACK | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| BLIND | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| BLUE | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| BOY | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| BREAD | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| BROWN | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| CHICKEN | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| COFFEE | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| COLD | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| CORRECT | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| COUSIN | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| CRAB | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| DARK | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| DAUGHTER | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| DEAF | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| DEAF BLIND | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| DECEMBER | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| DON’T KNOW | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| DON’T UNDERSTAND | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| EGG | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| EIGHT | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| FAST | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| FATHER | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| FEBRUARY | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| FISH | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| FIVE | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| FOUR | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| FRIDAY | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| GIRL | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| GOOD AFTERNOON | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| GOOD EVENING | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| GOOD MORNING | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| GRANDFATHER | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| GRANDMOTHER | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| GRAY | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| GREEN | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| HARD OF HEARING | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| HELLO | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| HOT | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| HOW ARE YOU | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| IM FINE | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| JANUARY | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| JUICE | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| JULY | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| JUNE | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| KNOW | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| LIGHT | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| LONGANISA | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| MAN | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| MARCH | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| MARRIED | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| MAY | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| MEAT | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| MILK | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| MONDAY | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| MOTHER | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| NICE TO MEET YOU | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| NINE | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| NO | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| NO SUGAR | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| NOVEMBER | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| OCTOBER | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| ONE | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| ORANGE | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| PARENTS | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| PINK | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| RED | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| RICE | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| SATURDAY | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| SEE YOU TOMORROW | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| SEPTEMBER | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| SEVEN | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| SHRIMP | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| SIX | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| SLOW | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |
| SON | 0 | AMBIGUOUS | manual_review:no_processed_videos_for_label | 0 | 0 | 0 |

Showing 80 of 105 rows. See the CSV report for the full list.

## Output Files

- `reports/fsl105_handedness_by_video.csv`: one row per processed video.
- `reports/fsl105_handedness_by_class.csv`: aggregated class-level handedness decision.
- `reports/fsl105_handedness_manual_review.csv`: ambiguous, weak, or low-sample labels.
- `reports/fsl105_handedness_failed_videos.csv`: videos that failed to open/process.

## VoxGest Impact

- `ONE_HANDED`: compatible with the current 162-feature one-hand temporal pipeline.
- `TWO_HANDED`: may require the 225-feature full-sign pipeline so the second hand is represented.
- `AMBIGUOUS`: should be manually reviewed before inclusion in training.

## Limitations

- MediaPipe detection can miss hands because of blur, occlusion, cropping, lighting, or signer speed.
- A static support hand can produce low motion, so this script treats non-dominant presence as support evidence.
- `--max_videos` runs are inspection/smoke runs and should not be treated as final class labels.
- The script reads extracted video files only. It does not unzip `clips.zip` or modify datasets.
