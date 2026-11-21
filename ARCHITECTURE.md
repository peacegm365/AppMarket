# Architecture

AppMarket follows a Clean Architecture dependency direction:

```text
app:android / app:desktop -> app:shared -> domain
          \-------------> data -------> domain
```

## Modules

- `domain`: platform-independent models and repository contracts. It has no Android, Compose,
  Ktor, DataStore, or Koin dependency.
- `data`: repository implementations, persistence, Xiaomi API integration, and Android/Desktop
  platform adapters. `dataModules` exposes its Koin graph.
- `app:shared`: shared Compose UI, ViewModels, and the injected `UiPlatform` app-shell contract.
- `app:android`: Android application/activity and the Android Koin composition root.
- `app:desktop`: desktop entry point and the desktop Koin composition root.

## Dependency Injection

Stateful services are created by Koin with constructor injection. Application entry points only
register platform-owned primitives such as Android `Context`, then load `dataModules`,
`uiPlatformModule`, and `viewModelModule`.

## Update Discovery

Update discovery sends one Standard `/apm/updateinfo/v2` request containing every real installed
package plus a `com.miui.core` marker. If the device exposes that package, its real metadata is
preserved; otherwise the data layer appends `com.miui.core@0`. Xiaomi uses the package name to enable
`miuiApp` matching, while `0` deliberately avoids pretending to know an installed version. All
parallel package fields remain index-aligned.

The marker is request context only. Responses are reconciled against the real installed-package map,
so a synthetic marker or any other non-installed package can never become a visible update. Both
`listApp` and `miuiApp` are parsed from the same response; system visibility remains a UI preference.

The installation pipeline is Android-only. `domain.repository.InstallRepository` exposes the
platform-neutral request and event contract; `data/androidMain` owns HTTPS streaming, MediaStore,
PackageInstaller sessions, Standard/Root/Shizuku backends, and the third-party installer pipeline.
Third-party installation always publishes the package to Download before launching the selected
handler. Android framework entry points such as the foreground service, explicit Intent launcher,
and install-result/package-change receivers remain thin components in `app/android` and resolve
their collaborators through Koin. Desktop does not register an installer implementation.
Install tasks persist their phase but keep failure details, dialogs, and permission actions as
transient process-local state. The Compose root owns selectable, scrollable dialogs and delegates
platform settings navigation through `UiPlatform`. Notifications are limited to foreground-service
progress and never carry installation errors or permission actions.

For eligible single-APK updates, the install request carries a delta source containing both the
full-package fallback and patch metadata. `data/androidMain` downloads and verifies the patch in a
temporary workspace, synthesizes and verifies the complete APK, then streams that APK through the
same installer/Download outputs used by full-package requests. Split APK updates always use their
full artifacts.

## Proxy

The proxy feature does not ship a core binary and never opens a local port. `data/androidMain`
implements the AnyTLS protocol directly: the TLS session authenticates with `sha256(password)`,
negotiates settings, and multiplexes 7-byte-header frames (`SYN` / `PSH` / `FIN` / `ALERT` ...).
`TunnelSocketFactory` + `TunnelDns` are installed on the shared OkHttp/Ktor clients, so traffic that
matches `ProxyDecision` is tunnelled, everything else connects directly. `AndroidAnyTlsProxyController`
implements the platform-neutral `ClashCoreController` contract (`start` / `refreshNodes` /
`selectNode` / `testDelays`) and also publishes node latency plus a `timeouts` set so the UI can show
`超时` for nodes that fail the full handshake within the per-node timeout. Subscription entries that
encode account metadata (remaining traffic, expiry) are separated into `subscriptionInfo` instead of
being listed as nodes. The one `vless://...security=reality` entry in a mixed subscription is not
supported, because REALITY requires uTLS with XTLS Vision.
