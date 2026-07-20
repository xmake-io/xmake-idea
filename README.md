<div align="center">
  <a href="https://xmake.io">
    <img width="200" height="200" src="https://github.com/xmake-io/xmake-idea/raw/master/res/logo256.png">
  </a>

  <h1>xmake-idea</h1>

  <div>
    <a href="https://plugins.jetbrains.com/plugin/17406-xmake">
      <img src="https://img.shields.io/jetbrains/plugin/v/17406-xmake.svg?style=flat-square" alt="Version" />
    </a>
    <a href="https://plugins.jetbrains.com/plugin/17406-xmake">
      <img src="https://img.shields.io/jetbrains/plugin/d/17406-xmake.svg?style=flat-square" alt="Downloads" />
    </a>
  </div>
  <div>
    <a href="https://github.com/xmake-io/xmake-idea/blob/master/LICENSE.md">
      <img src="https://img.shields.io/github/license/xmake-io/xmake-idea.svg?colorB=f48041&style=flat-square" alt="license" />
    </a>
    <a href="https://www.reddit.com/r/xmake/">
      <img src="https://img.shields.io/badge/chat-on%20reddit-ff3f34.svg?style=flat-square" alt="Reddit" />
    </a>
    <a href="https://t.me/tbooxorg">
      <img src="https://img.shields.io/badge/chat-on%20telegram-blue.svg?style=flat-square" alt="Telegram" />
    </a>
    <a href="https://jq.qq.com/?_wv=1027&k=5hpwWFv">
      <img src="https://img.shields.io/badge/chat-on%20QQ-ff69b4.svg?style=flat-square" alt="QQ" />
    </a>
    <a href="https://discord.gg/xmake">
      <img src="https://img.shields.io/badge/chat-on%20discord-7289da.svg?style=flat-square" alt="Discord" />
    </a>
    <a href="https://xmake.io/about/sponsor">
      <img src="https://img.shields.io/badge/donate-us-orange.svg?style=flat-square" alt="Donate" />
    </a>
  </div>

  <p>An XMake integration in IntelliJ IDEA</p>
</div>

## Introduction

An XMake integration in IntelliJ IDEA/CLion.

It is deeply integrated with [XMake](https://github.com/xmake-io/xmake) and IntelliJ IDEA to provide a convenient and fast cross-platform C/C++ development and build experience.

It also supports other IntelliJ-based platforms, such as CLion and Android Studio.

The plugin requires IntelliJ Platform 2025.1 or later. CLion and IntelliJ IDEA are currently tested.

Install [XMake](https://github.com/xmake-io/xmake) on the machine or environment where you build the project. Add `xmake` to `PATH` for automatic detection.

Please see [XMake on GitHub](https://github.com/xmake-io/xmake) and the [website](https://xmake.io) to learn more about XMake.

## Features

* Quickstart
* Create project
* Project configuration
* Run configuration
* Menu tools
* Tool windows
* Build and run
* Parse errors and goto file
* C/C++ project navigation
* Debug
* Basic WSL and SSH support

## Installation

1. Install [XMake](https://xmake.io/#/guide/installation) on the machine or environment where you build the project. Add `xmake` to `PATH` for automatic detection.
2. Install [XMake from JetBrains Marketplace](https://plugins.jetbrains.com/plugin/17406-xmake) in CLion or IntelliJ IDEA.
3. Open or create a project as described below, then create an XMake run configuration and select an XMake installation.

## Getting Started

### Open an Existing Project

Open the project directory in the IDE. The directory must contain an `xmake.lua` file.

### Create a New Project

Use `File > New > Project`, choose `XMake`, then configure the project location, XMake installation, module language, and module type.

<p align="center">
  <img src="./res/create_project.png" alt="Create an XMake project with the project wizard" width="90%" />
</p>

<p align="center"><em>XMake project wizard in CLion 2026.1.</em></p>

## Project Settings

You can configure the XMake path and build settings in `Settings > Build, Execution, Deployment > Xmake`.

## Run Configurations

Create an XMake run configuration, then select an XMake installation and target.

<p align="center">
  <img src="./res/run_target.png" alt="Configure and run an XMake target" width="90%" />
</p>

<p align="center"><em>XMake run configuration in CLion.</em></p>

## Build Output

Select `Xmake > Build Project` to build the current project. Build output and reported problems appear in the XMake tool window. Select a problem to open the corresponding source location.

## C/C++ project navigation

  > Only supports CLion (>= 2025.1)

1. The plugin generates `compile_commands.json` for project navigation.
2. You can configure the output path and auto-update behavior in `Settings > Build, Execution, Deployment > Xmake`.
3. To manually generate it, click `Update compile commands` in the main menu or context menu.
4. CLion should automatically detect `compile_commands.json`. If not, you can open it via `File > Open...`.

## Debug

### DAP Debugging (Recommended)

> Only supports CLion (>= 2025.1)

In CLion 2025.1 and later, you can debug XMake targets directly with the Debug Adapter Protocol (DAP), without generating a `CMakeLists.txt` file.

1. Install a supported DAP driver:
   - **LLDB DAP:** `lldb-dap` (recommended)
   - **GDB DAP:** a `gdb` executable with DAP support
2. Open the "Run Configuration" for your XMake target.
3. In the "Debug Configuration" section, you can:
    - Enable "Auto-detect DAP driver" to let the plugin find the driver automatically.
    - Or manually select/input the path to your DAP driver executable (e.g., `/usr/bin/lldb-dap`).
4. Click the Debug button to start debugging.

### Legacy Debugging (CMake)

> Supports CLion (>= 2026.1)

1. Select "Xmake > Update CmakeLists" to create or update the "CMakeLists.txt" file.
2. Click "File > Open..." to choose this file.
3. Choose "Run > Debug..." or "Run > Debug 'project name'" to start debugging.

## How to contribute?

Due to limited personal time, I cannot maintain this plug-in all the time. If you encounter problems, you are welcome to download the plug-in source code to debug it yourself and open pr to contribute.

### Build this project

This project requires JetBrains Runtime (JBR) 21. Use the included Gradle wrapper to build it.

```powershell
.\gradlew.bat build
```

On Linux or macOS, run `./gradlew build` instead.

### Run and debug this project

Run `.\gradlew.bat runIde --stacktrace` on Windows or `./gradlew runIde --stacktrace` on Linux and macOS.

For more details, please visit: [CONTRIBUTING](https://github.com/xmake-io/xmake-idea/blob/master/CONTRIBUTING.md)

## Powered by

[![JetBrains logo.](https://resources.jetbrains.com/storage/products/company/brand/logos/jetbrains.svg)](https://jb.gg/OpenSource)
