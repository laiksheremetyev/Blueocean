# Android Security Notes

The Blue Ocean Android app must:
- Store sensitive data in internal app storage
- Block screenshots in vault screens with `FLAG_SECURE`
- Avoid disk caches for previews
- Clear session keys when the app backgrounds
- Avoid analytics and unnecessary SDKs
- Present camera and sync behavior clearly to the user
- Keep any notes/calculator surface as a real Blue Ocean feature, not impersonation of another app

The Android app must not:
- Capture photos silently
- Pretend to be a third-party, system, or unrelated application
- Upload photos without explicit user-controlled sync behavior
- Write sensitive media to public external storage
