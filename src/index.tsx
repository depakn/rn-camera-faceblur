import React, { useImperativeHandle, useRef } from 'react';
import {
  requireNativeComponent,
  UIManager,
  Platform,
  type ViewStyle,
  findNodeHandle,
} from 'react-native';

const LINKING_ERROR =
  `The package '@fortis-innovation-labs/rn-face-blur' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

export type CAMERA_POSITION = 'front' | 'back';

type RnFaceBlurNativeEvents = {
  onCameraPositionUpdate?: (newPosition: CAMERA_POSITION) => void;
  onRecordingStatusChange?: (isRecording: boolean) => void;
  onRecordingComplete?: (fileUrl: string) => void;
  onRecordingError?: (error: Record<string, string>) => void;
};

export type RnFaceBlurProps = {
  color?: string;
  ref?: any;
  style?: ViewStyle;
} & RnFaceBlurNativeEvents;

export type FaceBlurVideoRecordingRef = {
  startRecording: () => void;
  stopRecording: () => void;
  flipCamera: () => void;
  toggleFlash: () => void;
};

export const ComponentName = 'RnFaceBlurView';

const RnFaceBlur =
  UIManager.getViewManagerConfig(ComponentName) != null
    ? requireNativeComponent<RnFaceBlurProps>(ComponentName)
    : () => {
        throw new Error(LINKING_ERROR);
      };

export const FaceBlurVideoRecording = React.forwardRef<
  FaceBlurVideoRecordingRef,
  RnFaceBlurProps
>((props, ref) => {
  const faceblurRef = useRef(null);

  useImperativeHandle(ref, () => {
    return {
      startRecording() {
        sendCommand('startCamera');
      },
      stopRecording() {
        sendCommand('stopCamera');
      },
      flipCamera() {
        sendCommand('flipCamera');
      },
      toggleFlash() {
        sendCommand('toggleFlash');
      },
    };
  }, []);

  const sendCommand = (commandName: string) => {
    if (faceblurRef.current) {
      const nodeHandle = findNodeHandle(faceblurRef.current);
      if (nodeHandle) {
        UIManager.dispatchViewManagerCommand(
          nodeHandle,
          (UIManager.getViewManagerConfig('RnFaceBlurView') as any).Commands[
            commandName
          ],
          []
        );
      }
    }
  };

  const nativeEvents: RnFaceBlurNativeEvents = {
    onCameraPositionUpdate: (event: any) => {
      props.onCameraPositionUpdate &&
        props.onCameraPositionUpdate(event.nativeEvent.position);
    },
    onRecordingStatusChange: (event: any) => {
      props.onRecordingStatusChange &&
        props.onRecordingStatusChange(event.nativeEvent.isRecording);
    },
    onRecordingComplete: (event: any) => {
      props.onRecordingComplete &&
        props.onRecordingComplete(event.nativeEvent.fileUrl);
    },
    onRecordingError: (event: any) => {
      props.onRecordingError && props.onRecordingError(event.nativeEvent.error);
    },
  };

  return <RnFaceBlur {...props} {...nativeEvents} ref={faceblurRef} />;
});
