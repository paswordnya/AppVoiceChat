// swift-tools-version:5.9
import PackageDescription

// Local SPM wrapper around the Kotlin Multiplatform `shared` module's
// XCFramework output (PRD-KMP-Migration-v2.md §5.3/§19 Sprint 6). Not
// CocoaPods — matches the tooling decision already made for this project.
//
// Regenerate the referenced XCFramework after any `shared/` change with:
//   cd PipShared && ./gradlew :shared:assemblePipSharedDebugXCFramework
// (Release builds: assemblePipSharedReleaseXCFramework — point `path`
// at .../XCFrameworks/release/PipShared.xcframework for a Release archive.)
let package = Package(
    name: "PipSharedKit",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "PipSharedKit", targets: ["PipShared"]),
    ],
    targets: [
        .binaryTarget(
            name: "PipShared",
            path: "../shared/build/XCFrameworks/debug/PipShared.xcframework"
        ),
    ]
)
