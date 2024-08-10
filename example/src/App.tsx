import { StyleSheet, View } from 'react-native';
import { RnFaceBlur } from '@fortis-innovation-labs/rn-face-blur';

export default function App() {
  return (
    <View style={styles.container}>
      <RnFaceBlur color="#FF0000" style={styles.box} />
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
    width: 60,
    height: 60,
    marginVertical: 20,
  },
});
