import UIKit
import AVFoundation
import Vision
import CoreImage
import Photos

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

    guard let newDevice = AVCaptureDevice.DiscoverySession(deviceTypes: [.builtInWideAngleCamera], mediaType: .video, position: currentCameraPosition).devices.first else {
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
    guard let device = AVCaptureDevice.DiscoverySession(deviceTypes: [.builtInTrueDepthCamera, .builtInDualCamera, .builtInWideAngleCamera], mediaType: .video, position: .front).devices.first else {
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
    videoDataOutput.videoSettings = [(kCVPixelBufferPixelFormatTypeKey as NSString): NSNumber(value: kCVPixelFormatType_32BGRA)] as [String: Any]

    videoDataOutput.alwaysDiscardsLateVideoFrames = true
    videoDataOutput.setSampleBufferDelegate(self, queue: DispatchQueue(label: "camera_frame_processing_queue"))

    captureSession.addOutput(videoDataOutput)

    if captureSession.canAddOutput(movieOutput) {
      captureSession.addOutput(movieOutput)
    }

    guard let connection = videoDataOutput.connection(with: .video), connection.isVideoOrientationSupported else {
      return
    }

    connection.videoOrientation = .portrait
  }

  private func startRecording() {
    if !isRecording {
      isRecording = true
      let outputFileName = NSUUID().uuidString
      let outputFilePath = NSTemporaryDirectory() + "\(outputFileName).mov"
      let outputURL = URL(fileURLWithPath: outputFilePath)
      movieOutput.startRecording(to: outputURL, recordingDelegate: self)
    }
  }

  private func stopRecording() {
    if isRecording {
      movieOutput.stopRecording()
      isRecording = false
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

    let imageResultHandler = VNImageRequestHandler(cvPixelBuffer: image, orientation: .leftMirrored, options: [:])
    try? imageResultHandler.perform([faceDetectionRequest])
  }

  private func handleFaceDetectionResults(observedFaces: [VNFaceObservation]) {
    clearDrawings()

    let facesBoundingBoxes: [CAShapeLayer] = observedFaces.map { observedFace in
      let faceBoundingBoxOnScreen = previewLayer.layerRectConverted(fromMetadataOutputRect: observedFace.boundingBox)
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
  func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
    guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
      return
    }
    detectFace(image: pixelBuffer)
  }
}

extension RnFaceBlurView: AVCaptureFileOutputRecordingDelegate {
  func fileOutput(_ output: AVCaptureFileOutput, didFinishRecordingTo outputFileURL: URL, from connections: [AVCaptureConnection], error: Error?) {
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
