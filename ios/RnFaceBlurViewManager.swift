import AVFoundation
import CoreImage
import Photos
import UIKit
import Vision

@objc(RnFaceBlurViewManager)
class RnFaceBlurViewManager: RCTViewManager {

  override func view() -> UIView! {
    return RnFaceBlurView()
  }

  override static func requiresMainQueueSetup() -> Bool {
    return true
  }

  @objc func startCamera(_ node: NSNumber) {
    self.bridge.uiManager.addUIBlock { (uiManager, viewRegistry) in
      if let view = viewRegistry?[node] as? RnFaceBlurView {
        DispatchQueue.main.async {
          view.startCamera()
        }
      }
    }
  }

  @objc func stopCamera(_ node: NSNumber) {
    self.bridge.uiManager.addUIBlock { (uiManager, viewRegistry) in
      if let view = viewRegistry?[node] as? RnFaceBlurView {
        DispatchQueue.main.async {
          view.stopCamera()
        }
      }
    }
  }

  @objc func flipCamera(_ node: NSNumber) {
    self.bridge.uiManager.addUIBlock { (uiManager, viewRegistry) in
      if let view = viewRegistry?[node] as? RnFaceBlurView {
        DispatchQueue.main.async {
          view.flipCamera()
        }
      }
    }
  }
}

class RnFaceBlurView: UIView {
  private var drawings: [CAShapeLayer] = []
  private let videoDataOutput = AVCaptureVideoDataOutput()
  private let captureSession = AVCaptureSession()
  private lazy var previewLayer = AVCaptureVideoPreviewLayer(session: captureSession)
  private var movieOutput = AVCaptureMovieFileOutput()
  private var currentCameraPosition: AVCaptureDevice.Position = .front

  private var assetWriter: AVAssetWriter?
  private var assetWriterInput: AVAssetWriterInput?
  private var pixelBufferAdaptor: AVAssetWriterInputPixelBufferAdaptor?

  private var videoOutputURL: URL?
  private var startTime: CMTime?

  // Save Variables
  private var isRecording = false

  override init(frame: CGRect) {
    super.init(frame: frame)
    setupView()
  }

  required init?(coder: NSCoder) {
    super.init(coder: coder)
    setupView()
  }

  @objc func startCamera() {
    if !captureSession.isRunning {
      captureSession.startRunning()
    }
    startRecording()
  }

  @objc func stopCamera() {
    if captureSession.isRunning {
      stopRecording()
      captureSession.stopRunning()
      clearDrawings()
    }
  }

  @objc func flipCamera() {
    captureSession.beginConfiguration()

    guard let currentInput = captureSession.inputs.first as? AVCaptureDeviceInput else {
      captureSession.commitConfiguration()
      return
    }

    captureSession.removeInput(currentInput)

    currentCameraPosition = currentCameraPosition == .front ? .back : .front

    guard
      let newDevice = AVCaptureDevice.DiscoverySession(
        deviceTypes: [.builtInWideAngleCamera], mediaType: .video, position: currentCameraPosition
      ).devices.first
    else {
      fatalError("No camera available")
    }

    let newInput = try! AVCaptureDeviceInput(device: newDevice)

    if captureSession.canAddInput(newInput) {
      captureSession.addInput(newInput)
    } else {
      captureSession.addInput(currentInput)
    }

    captureSession.commitConfiguration()
  }

  private func setupView() {
    addCameraInput()
    showCameraFeed()
    getCameraFrames()
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    previewLayer.frame = self.bounds
  }

  private func addCameraInput() {
    guard
      let device = AVCaptureDevice.DiscoverySession(
        deviceTypes: [.builtInTrueDepthCamera, .builtInDualCamera, .builtInWideAngleCamera],
        mediaType: .video, position: .front
      ).devices.first
    else {
      fatalError("No camera detected. Please use a real camera, not a simulator.")
    }

    let cameraInput = try! AVCaptureDeviceInput(device: device)
    captureSession.addInput(cameraInput)
  }

  private func showCameraFeed() {
    previewLayer.videoGravity = .resizeAspectFill
    self.layer.addSublayer(previewLayer)
    previewLayer.frame = self.bounds
  }

  private func getCameraFrames() {
    videoDataOutput.videoSettings =
      [(kCVPixelBufferPixelFormatTypeKey as NSString): NSNumber(value: kCVPixelFormatType_32BGRA)]
      as [String: Any]

    videoDataOutput.alwaysDiscardsLateVideoFrames = true
    videoDataOutput.setSampleBufferDelegate(
      self, queue: DispatchQueue(label: "camera_frame_processing_queue"))

    captureSession.addOutput(videoDataOutput)

    if captureSession.canAddOutput(movieOutput) {
      captureSession.addOutput(movieOutput)
    }

    guard let connection = videoDataOutput.connection(with: .video),
      connection.isVideoOrientationSupported
    else {
      return
    }

    connection.videoOrientation = .portrait
  }

  private func startRecording() {
    if !isRecording {
      isRecording = true
      let outputFileName = NSUUID().uuidString
      let outputFilePath = NSTemporaryDirectory() + "\(outputFileName).mov"
      videoOutputURL = URL(fileURLWithPath: outputFilePath)

      guard let videoOutputURL = videoOutputURL else { return }

      do {
        assetWriter = try AVAssetWriter(outputURL: videoOutputURL, fileType: .mov)

        let videoSettings: [String: Any] = [
          AVVideoCodecKey: AVVideoCodecType.h264,
          AVVideoWidthKey: NSNumber(value: Float(self.bounds.width)),
          AVVideoHeightKey: NSNumber(value: Float(self.bounds.height)),
        ]

        assetWriterInput = AVAssetWriterInput(mediaType: .video, outputSettings: videoSettings)
        assetWriterInput?.expectsMediaDataInRealTime = true

        let sourcePixelBufferAttributes: [String: Any] = [
          kCVPixelBufferPixelFormatTypeKey as String: NSNumber(value: kCVPixelFormatType_32ARGB),
          kCVPixelBufferWidthKey as String: NSNumber(value: Float(self.bounds.width)),
          kCVPixelBufferHeightKey as String: NSNumber(value: Float(self.bounds.height)),
        ]

        pixelBufferAdaptor = AVAssetWriterInputPixelBufferAdaptor(
          assetWriterInput: assetWriterInput!,
          sourcePixelBufferAttributes: sourcePixelBufferAttributes)

        if assetWriter!.canAdd(assetWriterInput!) {
          assetWriter!.add(assetWriterInput!)
        }

        assetWriter!.startWriting()
        startTime = nil
      } catch {
        print("Error setting up asset writer: \(error)")
        return
      }
    }
  }

  private func stopRecording() {
    if isRecording {
      isRecording = false
      assetWriterInput?.markAsFinished()
      assetWriter?.finishWriting { [weak self] in
        guard let self = self, let videoOutputURL = self.videoOutputURL else { return }
        self.saveVideoToPhotos(outputFileURL: videoOutputURL)
      }
    }
  }

  private func detectFace(image: CVPixelBuffer) {
    let faceDetectionRequest = VNDetectFaceLandmarksRequest { vnRequest, error in
      DispatchQueue.main.async {
        if let results = vnRequest.results as? [VNFaceObservation], results.count > 0 {
          self.handleFaceDetectionResults(observedFaces: results)
        } else {
          self.clearDrawings()
        }
      }
    }

    let imageResultHandler = VNImageRequestHandler(
      cvPixelBuffer: image, orientation: .leftMirrored, options: [:])
    try? imageResultHandler.perform([faceDetectionRequest])
  }

  private func handleFaceDetectionResults(observedFaces: [VNFaceObservation]) {
    clearDrawings()

    let facesBoundingBoxes: [CAShapeLayer] = observedFaces.map { observedFace in
      let faceBoundingBoxOnScreen = previewLayer.layerRectConverted(
        fromMetadataOutputRect: observedFace.boundingBox)
      let faceBoundingBoxPath = CGPath(rect: faceBoundingBoxOnScreen, transform: nil)
      let faceBoundingBoxShape = CAShapeLayer()

      faceBoundingBoxShape.path = faceBoundingBoxPath
      faceBoundingBoxShape.fillColor = UIColor.clear.cgColor
      faceBoundingBoxShape.strokeColor = UIColor.green.cgColor

      return faceBoundingBoxShape
    }

    facesBoundingBoxes.forEach { faceBoundingBox in
      self.layer.addSublayer(faceBoundingBox)
      drawings = facesBoundingBoxes
    }
  }

  private func clearDrawings() {
    drawings.forEach { drawing in drawing.removeFromSuperlayer() }
    drawings.removeAll()
  }
}

extension RnFaceBlurView: AVCaptureVideoDataOutputSampleBufferDelegate {
  func captureOutput(
    _ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer,
    from connection: AVCaptureConnection
  ) {
    guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
      return
    }

    detectFace(image: pixelBuffer)

    if isRecording {
      processVideoFrame(sampleBuffer: sampleBuffer)
    }
  }

  private func processVideoFrame(sampleBuffer: CMSampleBuffer) {
    guard let assetWriter = assetWriter,
      let assetWriterInput = assetWriterInput,
      assetWriterInput.isReadyForMoreMediaData
    else {
      return
    }

    let presentationTime = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)

    if startTime == nil {
      startTime = presentationTime
      assetWriter.startSession(atSourceTime: presentationTime)
    }

    // Create an image context and draw the overlay
    UIGraphicsBeginImageContextWithOptions(self.bounds.size, false, 0.0)
    let context = UIGraphicsGetCurrentContext()!

    // Draw the video frame
    let ciImage = CIImage(cvPixelBuffer: CMSampleBufferGetImageBuffer(sampleBuffer)!)
    let cgImage = CIContext().createCGImage(ciImage, from: ciImage.extent)!
    context.draw(cgImage, in: self.bounds)

    // Draw the face detection overlay
    for drawing in drawings {
      drawing.render(in: context)
    }

    let overlayImage = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()

    if let cgImage = overlayImage?.cgImage,
      let pixelBuffer = pixelBufferFromCGImage(cgImage)
    {
      pixelBufferAdaptor?.append(pixelBuffer, withPresentationTime: presentationTime)
    }
  }

  private func pixelBufferFromCGImage(_ image: CGImage) -> CVPixelBuffer? {
    let width = image.width
    let height = image.height

    let attributes: [String: Any] = [
      kCVPixelBufferCGImageCompatibilityKey as String: kCFBooleanTrue!,
      kCVPixelBufferCGBitmapContextCompatibilityKey as String: kCFBooleanTrue!,
    ]

    var pixelBuffer: CVPixelBuffer?
    let status = CVPixelBufferCreate(
      kCFAllocatorDefault, width, height, kCVPixelFormatType_32ARGB, attributes as CFDictionary,
      &pixelBuffer)

    guard status == kCVReturnSuccess, let pixelBuffer = pixelBuffer else {
      return nil
    }

    CVPixelBufferLockBaseAddress(pixelBuffer, CVPixelBufferLockFlags(rawValue: 0))
    let pixelData = CVPixelBufferGetBaseAddress(pixelBuffer)

    let rgbColorSpace = CGColorSpaceCreateDeviceRGB()
    let context = CGContext(
      data: pixelData,
      width: width,
      height: height,
      bitsPerComponent: 8,
      bytesPerRow: CVPixelBufferGetBytesPerRow(pixelBuffer),
      space: rgbColorSpace,
      bitmapInfo: CGImageAlphaInfo.noneSkipFirst.rawValue)

    context?.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
    CVPixelBufferUnlockBaseAddress(pixelBuffer, CVPixelBufferLockFlags(rawValue: 0))

    return pixelBuffer
  }
}

extension RnFaceBlurView: AVCaptureFileOutputRecordingDelegate {
  func fileOutput(
    _ output: AVCaptureFileOutput, didFinishRecordingTo outputFileURL: URL,
    from connections: [AVCaptureConnection], error: Error?
  ) {
    if error == nil {
      saveVideoToPhotos(outputFileURL: outputFileURL)
    } else {
      print("Error recording movie: \(error?.localizedDescription ?? "")")
    }
  }

  private func saveVideoToPhotos(outputFileURL: URL) {
    PHPhotoLibrary.requestAuthorization { status in
      if status == .authorized {
        PHPhotoLibrary.shared().performChanges({
          PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: outputFileURL)
        }) { success, error in
          if success {
            print("Video saved to Photos")
          } else {
            print("Error saving video: \(error?.localizedDescription ?? "")")
          }
        }
      } else {
        print("Permission to access Photos is not granted")
      }
    }
  }
}
