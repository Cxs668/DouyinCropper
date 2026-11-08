# 抖音 9:16 图片批量裁剪（Android）

这是一个 Android 原生 Java 项目。选择多张图片后，逐张居中裁剪为 9:16，输出 1080×1920 JPEG（质量 95），保存到 `Pictures/Douyin9x16`。每张图只有在输出文件保存成功后，才会加入源文件删除请求；Android 系统会显示删除授权确认，用户可以取消。

## 兼容性
- minSdk 29（Android 10）
- targetSdk 35
- 不包含 ABI 专属 native 库，因此生成的 APK 是通用 APK，可供 ARM32/ARM64 等 Android 设备安装（系统版本仍需 Android 10 或更高）。

## 不用 Android Studio：用 GitHub 云端生成 APK

1. 登录 GitHub，创建一个空仓库，例如 `DouyinCropper`。
2. 将本项目文件夹中的**全部内容**上传到仓库根目录（包括隐藏的 `.github` 文件夹）。
3. 打开仓库的 **Actions** 页面，选择 **Build Android APK**，点击 **Run workflow**。如果首次使用 Actions，按页面提示启用。
4. 等待任务显示绿色成功状态。
5. 打开该次运行页面，在 **Artifacts** 区域下载 `DouyinCropper-universal-debug`，解压得到 `app-debug.apk`。
6. 将 APK 发送到 Android 手机，点击文件安装；若系统提示，允许当前文件管理器安装未知应用。

也可以在仓库推送到 `main` 或 `master` 分支时自动构建。

## 本地构建（可选）
需要 JDK 17、Gradle 8.9 和 Android SDK Platform 35：

```bash
gradle assembleDebug
```

输出：`app/build/outputs/apk/debug/app-debug.apk`

## 重要说明
- 当前提供的是未签名发布密钥的 debug APK 构建流程，适合个人测试和侧载安装；正式上架或长期发布应配置自己的 release signing key。
- 裁剪采用居中取景，不会智能识别人脸或主体；竖图/横图边缘可能被裁掉。
- 删除源图需要 Android 系统授权确认。若系统不支持对所选来源执行删除请求，应用会保留原图并提示手动清理。
