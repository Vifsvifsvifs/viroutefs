# ViRouteFS 0.12.0-beta.2

This beta is a focused interface cleanup. Network-routing behavior and the fail-closed safety model remain unchanged.

## Simpler daily navigation

- The bottom bar now contains four primary destinations: Control, Routes, Scanner, and More.
- DNS, Tools, and Settings remain available as full screens under More.
- Secondary screens have an explicit return action.

## Cleaner Control screen

- The primary action is network-control on/off.
- The normal phone connection (`System`) is shown as the default route without requiring a VPN profile.
- Built-in route explanations, packet-inspector duplication, and runtime-note cards were removed from the main screen.
- Emergency Block and ByeDPI are compact quick actions.
- Configuration health and native validation remain available without dominating the screen.
- Only user-created VPN/proxy profiles are shown in the VPN profile list.

## Routes and Scanner

- Route type, profile, DNS, language, and theme chips scroll horizontally on narrow phones.
- The installed-app picker no longer shows a redundant action chip on every unselected app.
- Duplicate route-summary details were moved into one advanced disclosure.
- Flow Scanner keeps connection metadata, per-app filtering, pause, and clear actions while removing repeated explanatory cards.

## Visual system

- Screen padding, card radius, spacing, and status pills are consistent.
- Light, dark, and AMOLED themes use the same blue/green product palette.
- Red is reserved for stop, block, and error actions.

Physical arm64-device validation is still required for end-to-end routing, DNS, ByeDPI, and application attribution.
