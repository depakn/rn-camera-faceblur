import {
  Button,
  findNodeHandle,
  StyleSheet,
  UIManager,
  View,
} from 'react-native';
import { RnFaceBlur } from '@fortis-innovation-labs/rn-face-blur';
import { useRef } from 'react';

export default function App() {
  const faceBlurRef = useRef(null);

  const sendCommand = (commandName: string) => {
    const nodeHandle = findNodeHandle(faceBlurRef.current);
    UIManager.dispatchViewManagerCommand(
      nodeHandle,
      (UIManager.getViewManagerConfig('RnFaceBlurView') as any).Commands[
        commandName
      ],
      []
    );
  };

  return (
    <View style={styles.container}>
      <RnFaceBlur ref={faceBlurRef} style={styles.box} />
      <Button title="Start Camera" onPress={() => sendCommand('startCamera')} />
      <Button title="Stop Camera" onPress={() => sendCommand('stopCamera')} />
      <Button title="Flip Camera" onPress={() => sendCommand('flipCamera')} />
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
    width: 300,
    height: 500,
    marginVertical: 20,
  },
});
