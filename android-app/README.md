# Blue Ocean Android App

Android client scaffold for the Blue Ocean private photo vault.

Security requirements:
- Internal app storage only for sensitive files
- `FLAG_SECURE` on vault activities
- No disk image cache for decrypted previews
- No hidden capture or third-party/system app impersonation
- Optional notes/calculator surface must be a real Blue Ocean feature
- Rust core accessed through a narrow JNI bridge
- MEGA upload sends encrypted Blue Ocean blobs only, never plaintext media or metadata
