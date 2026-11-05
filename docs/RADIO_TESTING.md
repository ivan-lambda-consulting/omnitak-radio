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
viewport, installs only on that emulator, runs the button and form key-input tests and
restores its display. GitHub CI runs the same script on Android 11 / API 30.
Use a disposable emulator: `adb install -r` preserves any app data already on it.
The button tests first send a platform D-pad event to leave Android touch mode,
then seed focus on a visible action and check D-pad focus moves
and Select on onboarding and server setup. Server forms additionally test IME
Next/Done, hardware Enter, Save focus and a fake Save callback; chat tests
exercise Send without connecting to a real server. They do **not** prove that an Activity
receives initial focus automatically after waking or opening the app. Tests of
persistent rebinding and channel switching belong next to the input state
machine when it is implemented.

Current intended keypad flow: Enter/IME Next advances through server fields;
Done on the password field focuses Save, then Select activates it. In a chat
editor Enter sends a nonempty message, while Shift+Enter leaves multiline
typing available. The certificate document picker and the XP5900's own IME
still need a direct device check.

The Servers screen can pick an ATAK ZIP containing `server.pref`, a client
`.p12`, and optionally a PEM or PKCS12 CA truststore. It previews the endpoint
before importing or connecting; ZIP size and extracted data are bounded.
The emulator checks Import focus and simulates a selected ZIP to exercise the
preview without writing a server; it does not prove that the XP5900's system
document picker is button-operable.

**Acceptance boundary:** A normal app cannot read `/dev/input` to prove a
vendor knob event, and synthetic `adb input keyevent` bypasses Sonim's hardware
routing. Screen-off knob switching and fresh GPS/TAK on the XP5900 remain
unverified until a real hardware stimulus/vendor callback is available. Do not
mark those gates passed from emulator tests or a kernel `getevent` capture.

For layout work on a connected XP5900, ADB already provides the needed
inspection loop: `exec-out screencap -p` for a temporary screenshot,
`uiautomator dump` for focus/bounds, and `input keyevent` for D-pad/Back.
Keep captures outside the repository, remove the temporary hierarchy file
from the phone, and avoid Send, Save, Import and connection controls when
inspecting real TAK data. Android Studio's device mirror is not exposed to
the agent; an extra MCP server is unnecessary for this limited workflow.
