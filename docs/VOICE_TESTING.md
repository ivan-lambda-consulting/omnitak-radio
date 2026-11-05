# Voice lab (Mumble + ATAK Vx)

TAK transports CoT/chat/PLI; Mumble transports voice. Do not import this test
server as a TAK server or replace an existing TAK connection. The first voice
channel is the Mumble root channel, not a TAK group or Meshtastic channel.

## Bring up the local relay

Use a trusted LAN. The XP5900 and a second device with ATAK + Vx must join the
same Wi-Fi as this PC's LAN interface. No internet port forwarding. Both
TCP **and** UDP 64738 are required for normal voice. The container uses the
official `mumblevoip/mumble-server:v1.5.915` image.

Generate separate random passwords outside the repository. Never commit or
print them in build logs:

```sh
export VOICE_LAN_IP=<this-PC's-trusted-LAN-IP>
export VOICE_SECRETS_DIR=/tmp/opencode/omnitak-voice-lab
mkdir -p "$VOICE_SECRETS_DIR"
umask 077
openssl rand -hex 24 > "$VOICE_SECRETS_DIR/voice_password"
openssl rand -hex 24 > "$VOICE_SECRETS_DIR/admin_password"
docker compose -f scripts/voice-lab/compose.yaml config --quiet
docker compose -f scripts/voice-lab/compose.yaml up -d
docker compose -f scripts/voice-lab/compose.yaml ps
```

The passwords are only in the private files. Read `voice_password` locally to
enter it on both test clients; do **not** use `admin_password` for clients.
Stop the lab with `docker compose -f scripts/voice-lab/compose.yaml down`.
The named volume retains its certificate and channels; removing the volume
would invalidate imported client trust, so do not use `down -v` casually.

Export the **public** Mumble certificate to a file and compare its SHA-256
fingerprint out of band before trusting it on the XP5900:

```sh
openssl s_client -connect "$VOICE_LAN_IP:64738" -showcerts </dev/null 2>/dev/null |
  openssl x509 -out "$VOICE_SECRETS_DIR/voice-server.pem"
openssl x509 -in "$VOICE_SECRETS_DIR/voice-server.pem" -noout -fingerprint -sha256
```

In OmniTAK, select **Voice** on Home. Enter LAN IP, port `64738`, a distinct
voice username and the test voice password. Select **Trust voice certificate**
and choose the public PEM, then compare the displayed fingerprint with the PC.
Select **Save & connect voice** and grant microphone access. Voice settings use
Keystore-backed encrypted preferences; the trusted certificate is stored in
app-private storage. A different or missing certificate must not connect.

Configure ATAK Vx on the second device in Mumble-server mode with the same
host/port/password and a different username. Select the root channel on both.
Only after the second device is ready: hold OmniTAK's **HOLD TO TALK** with
Select or touch, release to stop; test two-way audio and no stuck transmit.
**Learn hardware PTT** captures a non-alphabetic physical key; **Reset** drops
the binding. No Sonim key code is preassigned.

## Evidence and limits

- Opt-in emulator test `VoiceLanConnectionTest` imports the PEM, verifies
  connect/PTT/release, and proves a different pin cannot connect. It needs
  `voice_host`, `voice_password`, `voice_pem` and `voice_bad_pem` instrumentation
  extras plus `RECORD_AUDIO` granted to the emulator. It never targets the
  connected phone by default.
- Screen-off XP5900 key delivery is still **unverified**. When the Activity
  stops, an active PTT is released for safety; the on-screen PTT is a functional
  fallback, not proof of screen-off hardware transmit. A hold is also capped at
  30 seconds until lost key-up behavior is measured.
- Humla (and its nested native libraries) is GPLv3+ code built from the pinned
  `third_party/humla` source; see its LICENSE. The repository's Apache-2.0
  source retains its notices, but distributing a combined app requires a
  GPLv3-compatible source/notice review. The upstream pinned Opus/CELT/Speex
  revisions are old. The build copies the pinned Opus source and applies the
  [upstream CVE-2017-0381 fix](https://github.com/xiph/opus/commit/70a3d641b760b3d313b6025f82aed93a460720e5)
  before compiling, without modifying the tracked submodule. That single fix
  is not a full native-library audit: assess the rest before installing a voice
  build on a device carrying live TAK credentials or shipping a release APK.
