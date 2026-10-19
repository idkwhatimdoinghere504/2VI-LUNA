# 2VI AI PC project connection

Run `start_companion.bat` on the Windows PC. It starts a read-only service for the PC's Desktop and `Documents\1REAL WORK`, and reads skill names/descriptions from `%USERPROFILE%\.agents\skills`. The console shows the PC's local Wi-Fi address and a one-time pairing code. Enter those in 2VI AI while both devices are on the same trusted Wi-Fi.

The bridge does not execute commands or accept file changes. It hides dot-folders, build caches, credential files, and keys; it only previews UTF-8 source and text files up to 256 KB. A file's contents go to Gemini or Claude only if you deliberately attach it to your question. The bridge stops when you close its console or press Ctrl+C.

You can share other project folders by launching the script with additional `--root "C:\path\to\project"` arguments. Keep the service on a trusted private network; the temporary pairing code authorizes access to the listed folders while the bridge is running.
