import AVFoundation
import UIKit

@objc(RnFaceBlurViewManager)
class RnFaceBlurViewManager: RCTViewManager {

  override func view() -> (RnFaceBlurView) {
    return RnFaceBlurView()
  }

  @objc override static func requiresMainQueueSetup() -> Bool {
    return true
  }
}

class RnFaceBlurView : UIView {
  private var captureSession: AVCaptureSession?
  private var videoPreviewLayer: AVCaptureVideoPreviewLayer?
  private var videoOutput: AVCaptureMovieFileOutput?
  private var currentCamera: AVCaptureDevice?
  private var cameraPosition: AVCaptureDevice.Position = .front

  @objc var color: String = "" {
    didSet {
      // self.backgroundColor = hexStringToUIColor(hexColor: color)
    }
  }

  override init(frame: CGRect) {
      super.init(frame: frame)
      setupCamera()
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    videoPreviewLayer?.frame = self.bounds
  }

  required init?(coder aDecoder: NSCoder) {
      super.init(coder: aDecoder)
      setupCamera()
  }

  private func setupCamera() {
    captureSession = AVCaptureSession()
    captureSession?.sessionPreset = .high
    guard let captureSession = captureSession else {
        print("Failed to create AVCaptureSession")
        return
    }

    currentCamera = cameraWithPosition(position: cameraPosition)

    if let currentCamera = currentCamera {
        do {
            let input = try AVCaptureDeviceInput(device: currentCamera)
            captureSession.addInput(input)
        } catch {
            print("Failed to create AVCaptureDeviceInput: \(error)")
            return
        }
    } else {
        print("Failed to find camera for position: \(cameraPosition)")
        return
    }

    videoPreviewLayer = AVCaptureVideoPreviewLayer(session: captureSession)
    videoPreviewLayer?.videoGravity = .resizeAspectFill
    videoPreviewLayer?.frame = self.bounds
    layer.addSublayer(videoPreviewLayer!)

    videoOutput = AVCaptureMovieFileOutput()
    captureSession.addOutput(videoOutput!)
    captureSession.startRunning()
  }

  private func cameraWithPosition(position: AVCaptureDevice.Position) -> AVCaptureDevice? {
    let devices = AVCaptureDevice.devices(for: .video)
    let device = devices.first { $0.position == position }

    if device == nil {
        print("No camera found for position: \(position)")
    }

    return device
  }

  func startRecording() {
      guard let output = videoOutput else { return }
      let outputPath = NSTemporaryDirectory() + "output.mov"
      let outputURL = URL(fileURLWithPath: outputPath)
      output.startRecording(to: outputURL, recordingDelegate: self)
  }

  func stopRecording() {
      videoOutput?.stopRecording()
  }

  func flipCamera() {
    guard let captureSession = captureSession else { return }

    captureSession.beginConfiguration()

    // Remove existing input
    if let currentInput = captureSession.inputs.first as? AVCaptureDeviceInput {
        captureSession.removeInput(currentInput)
    }

    // Switch camera
    cameraPosition = (cameraPosition == .back) ? .front : .back
    currentCamera = cameraWithPosition(position: cameraPosition)

    // Add new input
    guard let newInput = try? AVCaptureDeviceInput(device: currentCamera!) else { return }
    captureSession.addInput(newInput)

    captureSession.commitConfiguration()
}
}

extension RnFaceBlurView: AVCaptureFileOutputRecordingDelegate {
    func fileOutput(_ output: AVCaptureFileOutput, didFinishRecordingTo outputFileURL: URL, from connections: [AVCaptureConnection], error: Error?) {
        // Handle the recorded video (save it, etc.)
    }
}
