#import <React/RCTViewManager.h>

@interface RCT_EXTERN_MODULE(RnFaceBlurViewManager, RCTViewManager)

RCT_EXTERN_METHOD(startCamera:(nonnull NSNumber *)node)
RCT_EXTERN_METHOD(stopCamera:(nonnull NSNumber *)node)
RCT_EXTERN_METHOD(flipCamera:(nonnull NSNumber *)node)
RCT_EXTERN_METHOD(toggleFlash:(nonnull NSNumber *)node)

RCT_EXPORT_VIEW_PROPERTY(onCameraPositionUpdate, RCTDirectEventBlock)
RCT_EXPORT_VIEW_PROPERTY(onRecordingStatusChange, RCTDirectEventBlock)
RCT_EXPORT_VIEW_PROPERTY(onRecordingComplete, RCTDirectEventBlock)
RCT_EXPORT_VIEW_PROPERTY(onRecordingError, RCTDirectEventBlock)

@end
