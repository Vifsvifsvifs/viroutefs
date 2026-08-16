# ViRouteFS 0.14.0-beta.8

This beta fixes VLESS/XHTTP connections in the app-private Xray runtime and
adds password-protected PKCS#12 identities to the OpenVPN profile editor.

## VLESS/XHTTP connection fix

* Standalone Xray on Android does not receive v2rayNG's platform DNS bridge.
  It previously tried to resolve an XHTTP server through an unavailable local
  `[::1]:53` resolver. TCP reachability therefore succeeded while the tunnel
  failed with `connection closed`.
* ViRouteFS now resolves each Xray server through Android before starting the
  child process, preferring IPv4 when both address families are available.
* The original hostname is preserved separately for TLS SNI and the XHTTP Host
  header; only the transport destination is replaced by the resolved address.
* The reported failure and fix were reproduced with the same profile and Xray
  build on a connected Android phone. A direct HTTPS probe then returned 204
  through XHTTP over HTTP/3.

## OpenVPN PKCS#12 identities

* The OpenVPN certificate screen now accepts `.p12` and `.pfx` files and has a
  dedicated password field.
* The container is opened locally. ViRouteFS extracts the client certificate,
  PKCS#8 private key and, when present, CA certificates from its chain.
* A CA already selected by the user is not overwritten. Client certificate and
  key are updated together.
* The PKCS#12 password and source container are not saved. Extracted profile
  secrets continue to be encrypted with Android Keystore.
* Empty containers, wrong passwords, missing keys, missing certificates and
  ambiguous containers with several private keys are rejected explicitly.

## Verification boundary

The full unit-test suite, Android lint, signed release build, APK signature,
native hashes and 16 KiB alignment remain release gates. The actual user
OpenVPN identity cannot be tested until its `.p12/.pfx` file and password are
selected on the device.
