// swift-tools-version: 5.9
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "easy_video_editor",
    platforms: [
        .iOS("13.0")
    ],
    products: [
        .library(name: "easy-video-editor", targets: ["easy_video_editor"])
    ],
    dependencies: [],
    targets: [
        .target(
            name: "easy_video_editor",
            dependencies: [],
            resources: [
                // If this plugin adds a privacy manifest or other bundled resources, process them here.
            ]
        )
    ]
)
