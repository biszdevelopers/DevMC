# Architecture

`BundlerPlugin` is the composition root. It owns the file-backed JSON database, static catalog snapshot, local service registry, and module manager. Domain modules receive those dependencies through `ModuleContext`.

Dependent plugins compile against Bundler directly and obtain concrete services from `BundlerPlugin.instance()`. There is no separate façade module. `jsonDatabase()` exposes the shared JSON files directly; profile and moderation lookups remain asynchronous so disk access stays off the server thread.

Authentication is a direct extension point: an external plugin calls `BundlerPlugin.instance().authenticationGateway(gateway)`. With Bundler authentication enabled, only `VERIFIED` decisions allow a login; no registered gateway denies every login.

ProtocolLib and Citizens are implementation details. `ProtocolLibPacketBridge` and `CitizensNpcBridge` are the only classes allowed to import their APIs. A missing plugin makes only the related optional module unavailable.
