# VoxGest ML Engineer Knowledge Base

## 1. What VoxGest Is

Technical: VoxGest is an offline Android edge-computing accessibility prototype
that converts controlled hand gestures into text/speech and converts spoken
responses into a lightweight avatar/fingerspelling display.

Non-technical: VoxGest helps a Deaf or Mute user and a hearing person communicate
with one phone between them, without depending on an internet connection.

## 2. What The Current System Can Do

- Recognize static alphabet/control signs from hand landmarks.
- Recognize 10 dynamic demo words plus `NOTHING`.
- Build sentence output only from accepted tokens.
- Speak confirmed signed text through text-to-speech.
- Show a lightweight avatar response for known words or fingerspell unknown
  words.

## 3. What The Current System Cannot Do Yet

- It cannot translate full ASL grammar.
- It cannot recognize an unrestricted vocabulary.
- It cannot safely enable phrase-level recognition during this hardening pass.
- It does not train on Android.
- Real Android CameraX + MediaPipe + TFLite inference is still being prepared.
- Sprint20 controlled vocabulary expansion is not active yet.

## 4. Why The Project Started With CNN

Technical: CNNs are a familiar first step for image-based gesture recognition
because they can learn visual patterns from raw frames.

Non-technical: The first idea was to let the computer look at hand images and
learn what each sign looks like.

## 5. Why CNN Alone Was Not Enough

Technical: Raw image CNNs were sensitive to background, lighting, camera angle,
hand size, and signer position. They also did not naturally model short motion
patterns unless a temporal component was added.

Non-technical: The model could be distracted by the room or camera setup instead
of focusing on the hand movement.

## 6. Why The System Moved To Landmarks

Technical: MediaPipe landmarks convert the camera image into stable hand and
body coordinates. This reduces background noise and makes preprocessing easier
to match between Python and Android.

Non-technical: Instead of learning from the whole picture, VoxGest learns from
the hand and body points that matter.

## 7. Why Static Letters Use 63 Features

Each hand has 21 landmarks. Each landmark has x, y, and z coordinates.

```text
21 landmarks x 3 coordinates = 63 features
```

The static letter model normalizes the hand around the wrist so the same letter
can work at different sizes and positions.

## 8. Why Dynamic Words Use 30 x 162

Dynamic signs need motion over time. VoxGest uses 30 frames per word sequence.
Each frame has:

```text
pose: 33 landmarks x 3 = 99
hand: 21 landmarks x 3 = 63
total per frame = 162
sequence = 30 x 162
```

Pose gives the upper-body reference; hand landmarks give the signing motion.

## 9. Why LSTM And TCN Are Used

LSTM reads motion as a time sequence and can learn order-dependent gestures.
TCN uses temporal convolution, which can be faster and easier to deploy while
still learning motion patterns. VoxGest compares both because live behavior can
differ from validation accuracy.

## 10. Why NOTHING Is Required

`NOTHING` teaches the model what no valid word looks like: idle hands, partial
signs, transitions, and aborted signs. Without `NOTHING`, the model is more
likely to force every movement into a word.

## 11. Why Dominant-Hand Policy Exists

The same sign can look different with the left or right hand. A configured
dominant hand makes recording, training, testing, and Android preprocessing use
the same hand selection rule.

## 12. Why Auto-Hand Mode Should Not Be Used For Controlled Recording

Auto-hand mode can switch between hands depending on what MediaPipe detects.
Controlled recording needs consistent examples, so use exact `right` or `left`
hand mode.

## 13. Why Some Words Are Accessibility Shortcuts

VoxGest uses a controlled one-hand gesture vocabulary. Standard one-hand ASL
signs are used where available, while selected communication concepts are
represented through one-hand accessibility-adapted shortcuts. These shortcuts
are not intended to replace standard ASL but to support users who may rely on
one functional hand.

## 14. Why Android Must Match Python Preprocessing

The model learns the exact numbers produced by the Python pipeline. If Android
mirrors the camera differently, selects a different hand, or normalizes landmarks
differently, the same sign can become a different input to the model.

## 15. What Has Been Achieved So Far

- Static alphabet recognition pipeline exists.
- Dynamic demo10 + `NOTHING` pipeline exists.
- LSTM and TCN training scripts exist.
- Live word logger captures gate metrics and failure reasons.
- Android dry-run app builds and launches.
- Avatar asset contract and prototype JSON are prepared.
- The latest demo10 TCN run is mostly strong for YES, NO, PLEASE, WATER,
  HELLO, HELP, STOP, DOCTOR, THANKYOU, and NOTHING.
- The remaining right-hand blocker is `NAME`, which is currently being confused
  as accepted `STOP`.

## 16. What Remains Unfinished

- More live repair data is needed for weak labels.
- The `NAME` / `STOP` contrast repair must pass before vocabulary expansion.
- Android real-time CameraX + MediaPipe + TFLite inference is not fully wired.
- Phrase recognition remains disabled.
- Avatar animation rendering is still lightweight/prototype level.
- More ISO/IEC 25010 evaluation evidence must be collected.
- Sprint20 is a planned expansion profile, not a guaranteed production profile.
- Android must remain defaulted to demo10 until sprint20 passes live testing.

## 17. Panel Answer: "Why This Approach?"

Because the project needs offline, real-time, phone-based recognition. Landmarks
reduce background noise, sequence models capture motion, and gates prevent raw
predictions from immediately becoming output.

## 18. Panel Answer: "Why Not Just CNN?"

A CNN alone looks at images and can be affected by lighting, background, and
camera position. VoxGest uses landmarks so the model focuses on hand/body
geometry, then uses LSTM/TCN models for motion.

## 19. Panel Answer: "Is This Real ASL?"

VoxGest uses a controlled one-hand gesture vocabulary. Standard one-hand ASL
signs are used where available, while selected communication concepts are
represented through one-hand accessibility-adapted shortcuts. These shortcuts
are not intended to replace standard ASL but to support users who may rely on
one functional hand.

## 20. Panel Answer: "Why Is STOP Still Weak?"

STOP itself is currently strong in the latest right-hand TCN log, but `NAME` is
being misclassified as accepted `STOP`. The repair goal is not to weaken STOP.
The goal is to record clean contrast samples for `NAME`, preserve the working
STOP version, and label partial or aborted NAME/STOP movements as `NOTHING`.

## 21. Panel Answer: "What Is Your ML Contribution?"

The ML contribution is the end-to-end offline recognition pipeline: landmark
feature design, static and dynamic model contracts, group-aware training,
dominant-hand preprocessing, `NOTHING` negative class design, live gate metrics,
and the token-composer safety layer that prevents raw predictions from changing
user output.

## 22. Panel Answer: "Why Not Expand To 20 Words Immediately?"

Controlled expansion only makes sense after the base demo vocabulary is stable.
The current blocker is the right-hand `NAME` -> `STOP` confusion. Adding more
classes now would make debugging harder and could weaken the defense demo. The
planned sprint20 profile is an expansion experiment that stays behind live-test
gates; demo10 remains the safe default.
