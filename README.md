# 2VI AI

2VI AI is a Kotlin/Jetpack Compose Android assistant based on the existing 2VI study app. It uses the supplied portrait as the launcher and in-app logo.

## Included in this workspace copy

- Gemini and Claude API connections, selectable from the assistant screen.
- User-entered provider keys encrypted locally with an Android Keystore AES-GCM key. The GitHub workflow no longer accepts or embeds keys in the APK.
- The existing study assistant features: photo input, camera scans, voice input, saved-answer UI, and study prompts.
- A PC Projects screen that connects to the read-only Python companion, lists selected project files, previews text files, and lets the user add a chosen file as context for a question.
- A PC Skills list built from local `SKILL.md` metadata. The installed `.agents/skills` folder currently contains REA, Pinokio, and Gepeto. The app can show their descriptions; it does not run Codex tools or execute those skills on Android.
- GitHub Actions builds and uploads a debug APK without requiring a fixed nested project path.

## Build an APK with GitHub

Upload this project folder to a GitHub repository, including `.github/workflows/android.yml`. The workflow locates `settings.gradle.kts`, builds `app-debug.apk`, then attaches it to the workflow run as `2VI-AI-debug-apk`. Open the successful run under **Actions** and download that artifact.

## Connect your PC projects

On the Windows PC, run `pc_companion/start_companion.bat`. Enter the printed local address and temporary pairing code under **PC projects** in the Android app. Keep both devices on the same trusted Wi-Fi. Sharing is read-only and stops when the PC companion exits. A file is sent to the selected AI provider only after you preview it and choose **Use in question**.

## Connect an AI provider

In **Profile**, enter your Gemini API key from Google AI Studio or Claude API key from Anthropic Console. The app stores keys using Android Keystore; questions and selected photos go directly to the selected provider. Provider API usage may be billed separately from consumer chat subscriptions.

## Verification status

This workspace does not have Java, Gradle, or Android SDK tools available, so I have not built or tested an APK here. The GitHub Actions workflow is prepared to build it remotely.
