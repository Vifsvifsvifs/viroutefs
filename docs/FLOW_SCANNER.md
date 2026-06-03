# Flow Scanner (FS)

**FS** means **Flow Scanner**.

The FS tab title is **Flow Scanner** and the Russian subtitle is:

> кто куда подключается и почему

FS is a friendly network-event explanation module. It is not a raw packet table and it does not use third-party branding.

## 0.4.1-alpha behavior

The screen is a demo/concept UI:

- explains that events appear only after the user explicitly enables future local VPN mode;
- shows that there is no hidden interception;
- provides an app text selector placeholder;
- has a **Старт анализа** button;
- marks events as **Демонстрационный режим**;
- displays compact sample flow event cards.

Sample event fields:

- app;
- domain/IP and port;
- DNS result/policy;
- selected route/profile;
- why the route was selected;
- status.

## Privacy and safety boundary

0.4.1-alpha does not implement packet capture, background interception, root capture or hidden monitoring. Future local VPN observation must remain explicit, local-first and user-triggered.
