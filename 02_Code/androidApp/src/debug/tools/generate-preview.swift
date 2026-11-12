// Generates an original, silent 8-second QA clip. Not included in release builds.
// Run on macOS: swift generate-preview.swift /absolute/path/to/preview_reel.mp4
import Foundation
import AVFoundation
import CoreGraphics
import CoreVideo

guard CommandLine.arguments.count == 2 else { fatalError("Provide a new output .mp4 path") }
let target = URL(fileURLWithPath: CommandLine.arguments[1])
guard !FileManager.default.fileExists(atPath: target.path) else { fatalError("Refusing to overwrite an existing fixture") }
try FileManager.default.createDirectory(at: target.deletingLastPathComponent(), withIntermediateDirectories: true)
let width = 540, height = 960, fps: Int32 = 24
let writer = try AVAssetWriter(outputURL: target, fileType: .mp4)
let input = AVAssetWriterInput(mediaType: .video, outputSettings: [
    AVVideoCodecKey: AVVideoCodecType.h264, AVVideoWidthKey: width, AVVideoHeightKey: height,
    AVVideoCompressionPropertiesKey: [AVVideoAverageBitRateKey: 450_000],
])
let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: [
    kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32ARGB,
    kCVPixelBufferWidthKey as String: width, kCVPixelBufferHeightKey as String: height,
    kCVPixelBufferCGImageCompatibilityKey as String: true,
    kCVPixelBufferCGBitmapContextCompatibilityKey as String: true,
])
writer.add(input)
guard writer.startWriting() else { fatalError("Cannot start writer: \(String(describing: writer.error))") }
writer.startSession(atSourceTime: .zero)
for frame in 0..<Int(fps * 8) {
    while !input.isReadyForMoreMediaData { Thread.sleep(forTimeInterval: 0.005) }
    autoreleasepool {
        var buffer: CVPixelBuffer?
        CVPixelBufferPoolCreatePixelBuffer(nil, adaptor.pixelBufferPool!, &buffer)
        let pixel = buffer!
        CVPixelBufferLockBaseAddress(pixel, [])
        let ctx = CGContext(data: CVPixelBufferGetBaseAddress(pixel), width: width, height: height,
            bitsPerComponent: 8, bytesPerRow: CVPixelBufferGetBytesPerRow(pixel),
            space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipFirst.rawValue)!
        let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: [
            CGColor(red: 0.06, green: 0.12, blue: 0.18, alpha: 1),
            CGColor(red: 0.18, green: 0.26, blue: 0.34, alpha: 1),
        ] as CFArray, locations: [0, 1])!
        ctx.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: 300, y: height), options: [])
        ctx.saveGState()
        ctx.translateBy(x: 275, y: 490)
        ctx.rotate(by: -.pi / 8)
        ctx.scaleBy(x: 1, y: 0.72)
        ctx.setFillColor(CGColor(red: 0.12, green: 0.32, blue: 0.29, alpha: 1))
        ctx.fill(CGRect(x: -210, y: -320, width: 420, height: 640))
        ctx.setStrokeColor(CGColor(red: 0.64, green: 0.78, blue: 0.76, alpha: 0.55))
        ctx.setLineWidth(2)
        ctx.stroke(CGRect(x: -210, y: -320, width: 420, height: 640))
        ctx.stroke(CGRect(x: -80, y: -320, width: 160, height: 90))
        ctx.stroke(CGRect(x: -80, y: 230, width: 160, height: 90))
        ctx.move(to: CGPoint(x: -210, y: 0)); ctx.addLine(to: CGPoint(x: 210, y: 0)); ctx.strokePath()
        ctx.strokeEllipse(in: CGRect(x: -60, y: -60, width: 120, height: 120))
        let t = Double(frame) / Double(fps)
        for i in 0..<6 {
            let x = sin(t * .pi / 4 + Double(i)) * 130
            let y = cos(t * .pi / 4 + Double(i) * 1.7) * 220
            ctx.setFillColor(i % 2 == 0 ? CGColor(red: 0.65, green: 0.45, blue: 1, alpha: 1)
                : CGColor(red: 1, green: 0.55, blue: 0.7, alpha: 1))
            ctx.fillEllipse(in: CGRect(x: x - 9, y: y - 9, width: 18, height: 18))
        }
        ctx.restoreGState()
        CVPixelBufferUnlockBaseAddress(pixel, [])
        guard adaptor.append(pixel, withPresentationTime: CMTime(value: Int64(frame), timescale: fps)) else { fatalError("Frame rejected") }
    }
}
input.markAsFinished()
let finished = DispatchSemaphore(value: 0)
writer.finishWriting { finished.signal() }
finished.wait()
guard writer.status == .completed else { fatalError("Encoding failed: \(String(describing: writer.error))") }
print("Generated local 8-second UI fixture: \(target.lastPathComponent)")
