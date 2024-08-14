
# @sdtech/rn-face-blur

`@sdtech/rn-face-blur` is a React Native module that allows you to capture videos with real-time face blurring using native modules. This package is designed to integrate seamlessly with your React Native application, providing a simple API for starting, stopping, and configuring video recording with face blurring.

## Features

- **Real-time Face Blurring:** Automatically detect and blur faces in the video during recording.
- **Camera Control:** Switch between front and back cameras, toggle flash, and start/stop recording.
- **Customizable UI:** Easily style and customize the appearance of the camera view.
- **Event Callbacks:** Get updates on camera position, recording status, and more with event callbacks.

## Installation

```sh
npm install @sdtech/rn-face-blur
```

or

```sh
yarn add @sdtech/rn-face-blur
```

### Additional Steps

1. **iOS Setup:**
   - Ensure your project has the necessary permissions in `Info.plist`:
     ```xml
     <key>NSCameraUsageDescription</key>
     <string>We need access to your camera to record videos.</string>
     <key>NSMicrophoneUsageDescription</key>
     <string>We need access to your microphone to record audio with your videos.</string>
     <key>NSPhotoLibraryAddUsageDescription</key>
     <string>We need access to save videos to your photo library.</string>
     ```

2. **Android Setup:**
   - Ensure you have the following permissions in `AndroidManifest.xml`:
     ```xml
     <uses-permission android:name="android.permission.CAMERA" />
     <uses-permission android:name="android.permission.RECORD_AUDIO" />
     <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
     <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
     <uses-feature android:name="android.hardware.camera.flash" android:required="false" />
     ```

## Usage

Here’s an example of how to use the `@sdtech/rn-face-blur` package in your React Native application:

```javascript
import React, { useRef, useState } from 'react';
import { Button, StyleSheet, View } from 'react-native';
import {
  FaceBlurVideoRecording,
  type FaceBlurVideoRecordingRef,
} from '@sdtech/rn-face-blur';

export default function App() {
  const [isRecording, setIsRecording] = useState(false);
  const faceBlurRef = useRef<FaceBlurVideoRecordingRef>(null);

  return (
    <View style={styles.container}>
      <FaceBlurVideoRecording
        ref={faceBlurRef}
        style={styles.box}
        color="#525860"
        onCameraPositionUpdate={(val) =>
          console.log('[onCameraPositionUpdate]', val)
        }
        onRecordingStatusChange={(val) => {
          console.log('[onRecordingStatusChange]', val);
          if (typeof val === 'boolean' && isRecording !== val) {
            setIsRecording(val);
          }
        }}
        onRecordingComplete={(val) => console.log('[onRecordingComplete]', val)}
        onRecordingError={(val) => console.log('onRecordingError', val)}
      />
      <View style={styles.actionContainer}>
        {!isRecording ? (
          <>
            <Button
              title="Start Camera"
              onPress={() => faceBlurRef.current?.startRecording()}
            />
            <Button
              title="Flip Camera"
              onPress={() => faceBlurRef.current?.flipCamera()}
            />
            <Button
              title="Toggle Flash"
              onPress={() => faceBlurRef.current?.toggleFlash()}
            />
          </>
        ) : (
          <Button
            title="Stop Camera"
            onPress={() => faceBlurRef.current?.stopRecording()}
          />
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  box: {
    width: '100%',
    height: '100%',
    marginVertical: 20,
  },
  actionContainer: {
    gap: 4,
    position: 'absolute',
    bottom: 25,
  },
});
```

## API Reference

### `FaceBlurVideoRecording` Component

#### Props

- **`ref`** (optional): A ref object to control the camera and recording actions.
- **`style`** (optional): Style for the camera view.
- **`color`** (optional): The color of the overlay or interface elements.
- **`onCameraPositionUpdate`** (optional): Callback function triggered when the camera position changes (e.g., front to back).
  - **`val`**: The new camera position.
- **`onRecordingStatusChange`** (optional): Callback function triggered when the recording status changes.
  - **`val`**: Boolean indicating if recording is active.
- **`onRecordingComplete`** (optional): Callback function triggered when the recording is completed.
  - **`val`**: The result of the recording, typically the file path or URI.
- **`onRecordingError`** (optional): Callback function triggered when an error occurs during recording.
  - **`val`**: The error message or object.

#### Methods

- **`startRecording()`**: Starts video recording with face blurring.
- **`stopRecording()`**: Stops the video recording.
- **`flipCamera()`**: Switches between the front and back cameras.
- **`toggleFlash()`**: Toggles the camera flash on and off.

## Contributing

If you find a bug or have a feature request, please open an issue or submit a pull request. We welcome contributions from the community to improve this package!

## License

This project is licensed under the MIT License. See the [LICENSE](./LICENSE) file for more details.
