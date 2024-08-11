import {
  requireNativeComponent,
  UIManager,
  Platform,
  type ViewStyle,
} from 'react-native';

const LINKING_ERROR =
  `The package '@fortis-innovation-labs/rn-face-blur' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

type RnFaceBlurProps = {
  ref?: any;
  style?: ViewStyle;
};

export const ComponentName = 'RnFaceBlurView';

export const RnFaceBlur =
  UIManager.getViewManagerConfig(ComponentName) != null
    ? requireNativeComponent<RnFaceBlurProps>(ComponentName)
    : () => {
        throw new Error(LINKING_ERROR);
      };
