# OmniTAK Radio: privacy notes

_Last updated: 2026-04-25_

OmniTAK Radio is a public fork of OmniTAK Mobile. This document describes
the radio build; it does not change the upstream app's privacy policy.

## TL;DR

- The app does not send your data to the upstream project by design.
- The app contains no analytics SDKs, no crash-reporting SDKs, and no
  third-party trackers.
- The radio build makes no map-tile or ADS-B provider requests. TAK and optional
  mesh/enrollment connections use endpoints configured by the operator.
- The full source code is published under Apache 2.0 and can be audited at
  <https://github.com/ivan-lambda-consulting/omnitak-radio>.

## What the app stores on your device

- **TAK server profiles** (name, host, port, transport, certificate references)
- **User preferences** (callsign and reporting settings; legacy map keys retained for update compatibility)
- **Client certificates** for mTLS, stored in the Android Keystore
- **Legacy map files** may remain on-device after an update; this build does not read them or erase them

This data lives only on your device. Uninstalling the app removes all of it.

## What the app sends over the network

The app only initiates outbound connections to endpoints you configure:

| Endpoint type | What is sent | When |
|---|---|---|
| TAK Server (you configure) | CoT messages: your position (PPLI), contacts and chat messages | While connected to a server you configured |
| Meshtastic radio (you configure) | Mesh packets per the Meshtastic protocol | While connected to a radio you configured |

The fork does not operate these endpoints.

## Permissions the app requests

| Permission | Purpose |
|---|---|
| `INTERNET` | Connecting to configured TAK servers and network radios |
| `ACCESS_NETWORK_STATE` | Detecting connectivity changes to manage server reconnects |
| `ACCESS_FINE_LOCATION` | Reporting your position to your TAK server (PPLI) |
| `ACCESS_COARSE_LOCATION` | Fallback when fine location is denied or unavailable |

The contact roster comes from TAK, not the device address book. QR enrollment
can request camera access; the app does not request SMS or call logs.

## Children

The app is intended for adult users involved in tactical, search-and-rescue,
or outdoor operations. It is not directed to children under 13, and we do not
knowingly handle any data from children under 13.

## Open source

OmniTAK Radio is licensed under Apache 2.0. The full source, including all
network code, is publicly auditable at:

<https://github.com/ivan-lambda-consulting/omnitak-radio>

## Contact

Privacy questions, security disclosures, or general inquiries:

- **Email**: j@engindearing.soy
- **Security**: see [SECURITY.md](SECURITY.md) for responsible disclosure
- **Issues**: <https://github.com/engindearing-projects/OmniTAK-Android/issues>

## Changes to this policy

If we change this policy, the updated version will be committed to the repo
with the new `Last updated` date. The git history is the authoritative log of
changes.
