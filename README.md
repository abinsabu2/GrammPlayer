# Gramm Player

Gramm Player is an Android application that allows you to play media from your Telegram account. It uses the official TDLib (Telegram Database Library) to securely access your media without storing your data on any servers.

## Features

*   **Secure Login:** Securely log in to your Telegram account.
*   **Media Playback:** Play videos and audio from your chats, channels, and groups.
*   **User-Friendly Interface:** A simple and intuitive interface for browsing and playing your media.
*   **Privacy-Focused:** Your personal data is stored locally on your device and is never collected by us.

## Getting Started

To build and run this project, you will need [Android Studio](https://developer.android.com/studio).

1.  Clone the repository:
    ```bash
    git clone https://github.com/abinsabu2/GrammPlayer.git
    ```
2.  Open the project in Android Studio.
3.  Build the project. This will download all the necessary dependencies.
4.  Run the app on an Android device or emulator.

## Project Structure

*   `app/src/main/java/com/aes/grammplayer/`: Contains the main source code for the application.
*   `app/src/main/res/`: Contains the resources for the application, such as layouts, drawables, and strings.
*   `build.gradle.kts`: The main Gradle build file for the project.
*   `privacy-policy.html`, `terms-conditions.html`, `about-me.html`: HTML files for the Privacy Policy, Terms & Conditions, and About Me pages.

## Privacy Policy and Terms & Conditions

This project includes `privacy-policy.html` and `terms-conditions.html` files. To make them accessible from within the app, you need to host them online. You can use GitHub Pages for this:

1.  Create a public GitHub repository named `GrammPlayer`.
2.  Push the contents of this project to the repository.
3.  In your repository's settings, go to the "Pages" section and enable GitHub Pages for the `main` branch.
4.  Update the URLs in `app/src/main/java/com/aes/grammplayer/TermsActivity.kt` to point to your GitHub Pages URLs.

## Contributing

Contributions are welcome! Please feel free to submit a pull request.

## References

*   [TDLib (Telegram Database Library)](https://core.telegram.org/tdlib): The official TDLib documentation.
*   [Telegram API](https://core.telegram.org/api): The official Telegram API documentation.

## License

This project is licensed under the MIT License. See the `LICENSE` file for details.
