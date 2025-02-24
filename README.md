# Komunika

Komunika Prototype is an Android application that bridges the gap between deaf/mute and non-deaf individuals. By integrating advanced sign language detection and voice recognition technologies, the app translates between Filipino Sign Language (FSL) and spoken language—facilitating effective communication in diverse scenarios.

## Features

- **Sign Language Detection:**  
  Utilizes MediaPipe’s hand and pose landmark detection to recognize sign language gestures in real time.

- **Voice-to-FSL and FSL-to-Voice Conversion:**  
  Converts spoken language to sign language (and vice versa) using offline speech recognition (Vosk) and text-to-speech (TTS) technologies.

- **Multi-Device Communication:**  
  Leverages Google Nearby Connections API to enable seamless communication between devices, allowing for group or paired interactions.

- **Single-Device Mode:**  
  Offers a standalone mode where both voice and sign translations are handled on a single device.

- **Vocabulary List:**  
  Includes an extensive list of common words, phrases, and questions along with video demonstrations of their corresponding signs for easy learning.

- **User Profile Management:**  
  Provides features to set up and edit your profile (username, user type, and profile image) for personalized use.

## Technologies Used

- **Android (Kotlin):** Application development and UI.
- **MediaPipe:** For hand and pose landmark detection.
- **Google Nearby Connections API:** For establishing connections between devices.
- **Vosk:** Offline speech recognition.
- **Android CameraX:** For capturing live video for analysis.
- **Text-to-Speech (TTS):** For converting translated text back into speech.

## Installation

1. **Clone the Repository:**

   ```bash
   git clone https://github.com/yourusername/komunika-prototype.git

2. **Open in Android Studio:**
    Launch Android Studio and open the cloned project.

3. **Build the Project**
    Ensure that all dependencies are downloaded and build the project to install the necessary libraries.

## Usage

- **Single Phone:** Translate voice to FSL and text to voice on a single device.

- **Starting Lobby:** When you launch the app, the Starting Lobby allows you to set up your profile by entering a username, choosing a user type (Deaf/Mute or Non-Deaf), and providing a Service ID.
  
- **Multi-Device Mode:**
    - **NonSignersToSigners:** For non-deaf users to communicate with signers.
    - **SignersToNonSigners:** For signers to interact with non-deaf users.

- **Vocabulary List:** Browse sign language vocabulary videos across different categories.

### Starting Lobby

- When you launch the app, the Starting Lobby allows you to set up your profile by entering a username, choosing a user type (Deaf/Mute or Non-Deaf), and providing a Service ID.

### Home Screen

- After the lobby, the Home Screen presents a carousel displaying various modes:
  - **Single Phone Mode:** Translate voice to FSL and text to voice on a single device.
  - **Multi-Device Mode:**
    - **NonSignersToSigners:** For non-deaf users to communicate with signers.
    - **SignersToNonSigners:** For signers to interact with non-deaf users.
  - **Vocabulary List:** Browse sign language vocabulary videos across different categories.

### Communication

- Depending on your selection, the app will either establish a connection with nearby devices or work independently to provide real-time translation.

## Code Structure

- **StartingLobbyActivity.kt:**  
  Manages the initial user setup and navigates to the appropriate communication mode based on the user type.

- **HomeScreenActivity.kt:**  
  Serves as the main dashboard with a carousel for mode selection and profile management.

- **NonSignersToSignersActivity.kt & SignersToNonSignersActivity.kt:**  
  Handle multi-device communication using the Google Nearby Connections API.

- **SinglePhoneActivity.kt:**  
  Supports a single-device translation mode, combining voice recognition and sign language detection.

- **PoseLandmarkerHelper.kt:**  
  Implements pose detection using MediaPipe, facilitating landmark detection in real time.

- **HandLandmarkerHelper.kt:**  
  Manages hand detection and keypoint extraction using MediaPipe.

- **VocabularyListActivity.kt:**  
  Provides a categorized list of vocabulary with video resources to aid in learning FSL.

## Contribution Guidelines

Contributions are welcome! If you have suggestions or improvements:

1. Fork the repository.
2. Create a new branch for your feature or bug fix.
3. Commit your changes with clear messages.
4. Submit a pull request.

For major changes, please open an issue first to discuss what you would like to change.

## License

- This project is licensed under the MIT License. See the LICENSE file for details.

## Acknowledgements

- **MediaPipe**
- **Google Nearby Connections API**
- **Vosk Speech Recognition**
- **Android community and open-source contributors**
