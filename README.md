# Syncpoint Android Client

This is a very preliminary implementation of a Syncpoint Client for Android.  It connects your application to a server running <a href="https://github.com/couchbaselabs/Syncpoint-API">Syncpoint Server</a>.

## Known Issues
- Slow.  I believe it is the underlying TouchDB that is holding everything back.
- Temporary paring users are not deleted.
- Does not work correctly when Syncpoint Server is not available.
- Does not yet support the single channel mode that iOS offers.

## Building/Using Syncpoint

See the Syncpoint-Android-TestApp for an example of how to use the Syncpoint Client.

Currently you need this repository, as well as the TouchDB-Android and TouchDB-Android-Ektorp repositories available in your Eclipse workspace.
