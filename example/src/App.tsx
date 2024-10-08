import { Button, StyleSheet, View } from 'react-native';
import {
  FaceBlurVideoRecording,
  type FaceBlurVideoRecordingRef,
} from '@sd-tech/rn-camera-face-blur';
import { useRef, useState } from 'react';

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
