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
  private var videoOrientation: AVCaptureVideoOrientation = .portrait

  private var videoOutputURL: URL?
  private var startTime: CMTime?

  private var faceObservations: [VNFaceObservation] = []

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
    if isRecording {
      return
    }
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
    setVideoOrientation()
  }

  private func setVideoOrientation() {
    if let connection = videoDataOutput.connection(with: .video) {
      if connection.isVideoOrientationSupported {
        connection.videoOrientation = .portrait
        videoOrientation = .portrait
      }
    }
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
          AVVideoWidthKey: NSNumber(value: Float(self.bounds.height)),
          AVVideoHeightKey: NSNumber(value: Float(self.bounds.width)),
        ]

        assetWriterInput = AVAssetWriterInput(mediaType: .video, outputSettings: videoSettings)
        assetWriterInput?.expectsMediaDataInRealTime = true

        // Set the transform on the asset writer input
        assetWriterInput?.transform = videoOrientationTransform()

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

  private func videoOrientationTransform() -> CGAffineTransform {
    var transform = CGAffineTransform.identity

    switch videoOrientation {
    case .portrait:
      transform = CGAffineTransform(rotationAngle: .pi / 2)
    case .portraitUpsideDown:
      transform = CGAffineTransform(rotationAngle: -.pi / 2)
    case .landscapeRight:
      transform = CGAffineTransform(rotationAngle: .pi)
    case .landscapeLeft:
      transform = CGAffineTransform.identity
    @unknown default:
      break
    }

    return transform
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
    faceObservations = observedFaces

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

    guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
      return
    }

    let ciImage = CIImage(cvPixelBuffer: pixelBuffer)
    let context = CIContext()

    // Apply face blurring
    let blurredImage = applyFaceBlur(to: ciImage)

    guard let cgImage = context.createCGImage(blurredImage, from: blurredImage.extent) else {
      return
    }

    let imageSize = CGSize(
      width: CGFloat(CVPixelBufferGetWidth(pixelBuffer)),
      height: CGFloat(CVPixelBufferGetHeight(pixelBuffer)))

    UIGraphicsBeginImageContextWithOptions(imageSize, false, 1.0)
    let graphicsContext = UIGraphicsGetCurrentContext()!

    // Draw the video frame with correct orientation
    graphicsContext.translateBy(x: imageSize.width / 2, y: imageSize.height / 2)
    graphicsContext.rotate(by: .pi / 2)
    graphicsContext.translateBy(x: -imageSize.height / 2, y: -imageSize.width / 2)
    graphicsContext.draw(
      cgImage,
      in: CGRect(origin: .zero, size: CGSize(width: imageSize.height, height: imageSize.width)))

    let orientedImage = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()

    if let cgImage = orientedImage?.cgImage,
      let pixelBuffer = pixelBufferFromCGImage(cgImage)
    {
      pixelBufferAdaptor?.append(pixelBuffer, withPresentationTime: presentationTime)
    }
  }

  private func applyFaceBlur(to image: CIImage) -> CIImage {
    guard !faceObservations.isEmpty else { return image }

    var blurredImage = image

    for observation in faceObservations {
      let boundingBox = observation.boundingBox

      // Convert the coordinates to match the image orientation
      let convertedBoundingBox = CGRect(
        x: 1 - boundingBox.minY - boundingBox.height,  // Changed this line
        y: 1 - boundingBox.minX - boundingBox.width,
        width: boundingBox.height,
        height: boundingBox.width
      )

      let faceBounds = VNImageRectForNormalizedRect(
        convertedBoundingBox, Int(image.extent.width), Int(image.extent.height))

      // Expand the face bounds slightly
      let expandedFaceBounds = faceBounds.insetBy(
        dx: -faceBounds.width * 0.1, dy: -faceBounds.height * 0.1)

      // Create a blurred version of the entire image
      guard let blurFilter = CIFilter(name: "CIGaussianBlur") else { continue }
      blurFilter.setValue(image, forKey: kCIInputImageKey)
      blurFilter.setValue(30, forKey: kCIInputRadiusKey)  // Adjust blur intensity as needed
      guard let blurredFullImage = blurFilter.outputImage else { continue }

      // Create a rounded rectangle mask
      let path = CGPath(
        roundedRect: expandedFaceBounds, cornerWidth: 20, cornerHeight: 20, transform: nil)
      let maskImage = CIImage(cgImage: path.asCGImage())
        .applyingFilter("CIMaskToAlpha")
        .cropped(to: expandedFaceBounds)

      // Blend the blurred image with the original image using the mask
      guard let blendFilter = CIFilter(name: "CIBlendWithMask") else { continue }
      blendFilter.setValue(blurredFullImage, forKey: kCIInputImageKey)
      blendFilter.setValue(blurredImage, forKey: kCIInputBackgroundImageKey)
      blendFilter.setValue(maskImage, forKey: kCIInputMaskImageKey)

      guard let outputImage = blendFilter.outputImage else { continue }

      blurredImage = outputImage
    }

    return blurredImage
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

extension CGPath {
  func asCGImage() -> CGImage {
    let bounds = self.boundingBox
    let renderer = UIGraphicsImageRenderer(bounds: bounds)
    let cgImage = renderer.image { context in
      context.cgContext.addPath(self)
      context.cgContext.setFillColor(UIColor.white.cgColor)
      context.cgContext.fillPath()
    }.cgImage!
    return cgImage
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
