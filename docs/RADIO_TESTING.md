# Radio verification without operator-run knob tests

| Gate | Automated check | What it does not prove |
| --- | --- | --- |
| TAK and PLI | JVM loopback `RadioTakPipelineTest` plus freshness/permission tests | Real server mTLS, GPS reception, or Doze behavior |
| Buttons and setup | Compose key-input tests on an API 30 emulator at 240×320, run on CI for PRs and manual CI dispatch | Physical XP5900 knob routing, OS screen-off delivery, certificate picker focus |
| Real XP5900 | Not a CI gate while device interaction is paused | ADB `input keyevent` and raw `getevent` cannot substitute for a real knob event reaching the app |

To run the small-screen button tests locally, start an emulator (not the phone), then run:

```bash
JAVA_HOME="$HOME/.jdks/jbr-21.0.11" ANDROID_HOME="$HOME/Android/Sdk" \
  bash scripts/test-radio-buttons.sh emulator-5554
```

The script refuses non-emulator serials, sets a temporary 240×320 / 160-dpi
viewport, installs only on that emulator, runs the two key-input tests and
restores its display. GitHub CI runs the same script on Android 11 / API 30.
The button tests first send a platform D-pad event to leave Android touch mode,
then seed focus on a visible action and check D-pad focus moves
and Select on the onboarding pages. They do **not** prove that an Activity
receives initial focus automatically after waking or opening the app. Tests of
persistent rebinding and channel switching belong next to the input state
machine when it is implemented.

**Acceptance boundary:** A normal app cannot read `/dev/input` to prove a
vendor knob event, and synthetic `adb input keyevent` bypasses Sonim's hardware
routing. Screen-off knob switching and fresh GPS/TAK on the XP5900 remain
unverified until a real hardware stimulus/vendor callback is available. Do not
mark those gates passed from emulator tests or a kernel `getevent` capture.
