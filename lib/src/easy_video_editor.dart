import 'enums/enums.dart';
import 'models/models.dart';
import 'platform/platform.dart';

/// Callback for progress updates during video operations
typedef ProgressCallback = void Function(double progress);

/// Main class for video editing operations
class EasyVideoEditor {
  /// Trims a video to the specified start and end times.
  Future<String?> trimVideo(String videoPath, int startTime, int endTime) {
    return EasyVideoEditorPlatform.instance
        .trimVideo(videoPath, startTime, endTime);
  }

  /// Merges multiple videos into a single video.
  Future<String?> mergeVideos(List<String> videoPaths) {
    return EasyVideoEditorPlatform.instance.mergeVideos(videoPaths);
  }

  /// Extracts audio from a video file.
  Future<String?> extractAudio(String videoPath) {
    return EasyVideoEditorPlatform.instance.extractAudio(videoPath);
  }

  /// Adjusts the playback speed of a video.
  Future<String?> adjustVideoSpeed(String videoPath, double speed) {
    return EasyVideoEditorPlatform.instance.adjustVideoSpeed(videoPath, speed);
  }

  /// Removes audio from a video file.
  Future<String?> removeAudio(String videoPath) {
    return EasyVideoEditorPlatform.instance.removeAudio(videoPath);
  }

  /// Crops a video to the specified aspect ratio.
  ///
  /// [videoPath] is the path to the video file.
  /// [aspectRatio] is the target aspect ratio in format 'width:height' (e.g., '16:9', '1:1').
  Future<String?> cropVideo(String videoPath, VideoAspectRatio aspectRatio) {
    return EasyVideoEditorPlatform.instance.cropVideo(videoPath, aspectRatio);
  }

  /// Rotates a video by the specified degrees.
  Future<String?> rotateVideo(String videoPath, int degrees) {
    return EasyVideoEditorPlatform.instance.rotateVideo(videoPath, degrees);
  }

  /// Generates a thumbnail from a video at the specified position.
  ///
  /// [videoPath] is the path to the video file.
  /// [positionMs] is the position in milliseconds where the thumbnail should be taken.
  /// [quality] is the quality of the generated thumbnail (0-100).
  /// [height] optional height of the thumbnail in pixels.
  /// [width] optional width of the thumbnail in pixels.
  Future<String?> generateThumbnail(
      String videoPath, int positionMs, int quality,
      {int? height, int? width, exactFrame = false}) {
    return EasyVideoEditorPlatform.instance.generateThumbnail(
        videoPath, positionMs, quality,
        height: height, width: width, exactFrame: exactFrame);
  }

  /// Compresses a video by adjusting its resolution and bitrate.
  ///
  /// [videoPath] is the path to the video file.
  /// [resolution] target resolution for the video (e.g., VideoResolution.p720 for 720p).
  /// If not specified, defaults to 720p while maintaining aspect ratio.
  Future<String?> compressVideo(String videoPath,
      {VideoResolution resolution = VideoResolution.p720}) {
    return EasyVideoEditorPlatform.instance.compressVideo(
      videoPath,
      resolution: resolution,
    );
  }

  /// Cancels any currently running operation.
  ///
  /// Returns true if an operation was successfully canceled, false otherwise.
  Future<bool> cancelOperation() {
    return EasyVideoEditorPlatform.instance.cancelOperation();
  }

  /// Gets a stream of progress updates for video operations
  ///
  /// Returns a stream of progress values between 0.0 and 1.0
  Stream<double> getProgressStream() {
    final platform = EasyVideoEditorPlatform.instance;
    if (platform is MethodChannelEasyVideoEditor) {
      return platform.getProgressStream();
    }
    // Return an empty stream if the platform doesn't support progress updates
    return const Stream.empty();
  }

  /// Retrieves metadata information about a video file.
  ///
  /// [videoPath] is the path to the video file.
  ///
  /// Returns a [VideoMetadata] object containing information about the video:
  /// - Duration (in milliseconds)
  /// - Width and Height (in pixels)
  /// - Title (if available)
  /// - Author (if available)
  /// - Orientation (rotation in degrees: 0, 90, 180, 270)
  /// - File size (in bytes)
  /// - Creation date (in String)
  static Future<VideoMetadata> getVideoMetadata(String videoPath) {
    return EasyVideoEditorPlatform.instance.getVideoMetadata(videoPath);
  }

  /// Flips a video horizontally or vertically.
  Future<String?> flipVideo(String videoPath, FlipDirection flipDirection) {
    return EasyVideoEditorPlatform.instance.flipVideo(videoPath, flipDirection);
  }
}
