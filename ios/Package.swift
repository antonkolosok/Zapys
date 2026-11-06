// swift-tools-version: 5.9
import PackageDescription

let package = Package(name: "ZapysCore", platforms: [.macOS(.v13), .iOS(.v17)], products: [.library(name: "ZapysCore", targets: ["ZapysCore"])], targets: [.systemLibrary(name: "CSQLite", pkgConfig: "sqlite3", providers: [.apt(["libsqlite3-dev"]), .brew(["sqlite3"])]), .target(name: "ZapysCore", dependencies: ["CSQLite"]), .testTarget(name: "ZapysCoreTests", dependencies: ["ZapysCore", "CSQLite"])])

