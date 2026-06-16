# Discreet Mode

## Position

Blue Ocean may include a discreet everyday utility surface, such as notes or a calculator, while keeping the private vault behind PIN unlock.

This must be implemented as a real feature of Blue Ocean, not as impersonation of a third-party or system app.

## Allowed

- App name such as `Blue Ocean Notes` or `Blue Ocean Calculator`
- A working notes or calculator screen as the default unlocked surface
- User-controlled private vault entry through a PIN gesture or PIN field
- Decoy notes or calculator history that are normal app data
- Explicit in-app camera capture after vault unlock
- Encrypted storage that stays out of the system gallery and public filesystem

## Not Allowed

- Using another app's name, icon, package identity, UI copy, or branding
- Pretending to be a system app
- Hidden camera capture
- Background capture without visible user action
- Silent or misleading cloud upload
- Any behavior where the user cannot understand what Blue Ocean does after setup

## MVP Recommendation

Start with `Blue Ocean Notes`:
- It is simpler than calculator unlock parsing.
- It can provide a normal notes list as the first screen.
- A private vault button or PIN-protected note can unlock the encrypted photo area.
- The vault camera remains visible and explicit after unlock.
