// Rebuild the launcher-owned TV banner on macOS: swift scripts/render-banner.swift
import AppKit
import Foundation

let width = 320, height = 180
let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: width, pixelsHigh: height,
    bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
    colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
let context = NSGraphicsContext(bitmapImageRep: bitmap)!
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = context
NSColor(srgbRed: 18/255, green: 26/255, blue: 43/255, alpha: 1).setFill()
NSBezierPath(rect: NSRect(x: 0, y: 0, width: width, height: height)).fill()
let cream = NSColor(srgbRed: 243/255, green: 232/255, blue: 206/255, alpha: 1)
cream.setFill()
for x in [275, 288, 301] {
    NSBezierPath(ovalIn: NSRect(x: x-3, y: 157, width: 6, height: 6)).fill()
}
let title = "MiStick Lite" as NSString
let attrs: [NSAttributedString.Key: Any] = [.font: NSFont.systemFont(ofSize: 35, weight: .light), .foregroundColor: cream]
let size = title.size(withAttributes: attrs)
title.draw(at: NSPoint(x: (320-size.width)/2, y: 71), withAttributes: attrs)
NSGraphicsContext.restoreGraphicsState()
let destination = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "app/src/main/res/drawable-nodpi/tv_banner.png"
try bitmap.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: destination))
