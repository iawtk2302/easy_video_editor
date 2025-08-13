import 'package:easy_video_editor/src/enums/enums.dart';
import 'package:easy_video_editor/src/models/video_metadata.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'easy_video_editor_method_channel.dart';

/// Callback for progress updates during video operations
typedef ProgressCallback = void Function(double progress);

/// A platform interface for the easy_video_editor plugin that provides video editing
/// capabilities without FFmpeg dependency.
///
/// Platform-specific implementations should extend this class.
/// iOS requires iOS 13.0 or later.
abstract class EasyVideoEditorPlatform extends PlatformInterface {
  /// Constructs a EasyVideoEditorPlatform.
  EasyVideoEditorPlatform() : super(token: _token);

  static final Object _token = Object();

  static EasyVideoEditorPlatform _instance = MethodChannelEasyVideoEditor();

  /// The default instance of [EasyVideoEditorPlatform] to use.
  ///
  /// Defaults to [MethodChannelEasyVideoEditor].
  static EasyVideoEditorPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [EasyVideoEditorPlatform] when
  /// they register themselves.
  static set instance(EasyVideoEditorPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  /// Trims a video to the specified time range with real-time preview support.
  ///
  /// [videoPath] is the path to the source video file.
  /// [startTimeMs] is the starting point in milliseconds.
  /// [endTimeMs] is the ending point in milliseconds.
  ///
  /// Returns the path to the trimmed video file, or null if the operation fails.
  Future<String?> trimVideo(String videoPath, int startTimeMs, int endTimeMs) {
    throw UnimplementedError('trimVideo() has not been implemented.');
  }

  /// Merges multiple videos into a single video with seamless transitions.
  ///
  /// [videoPaths] is a list of paths to the source video files to be merged.
  ///
  /// Returns the path to the merged video file, or null if the operation fails.
  Future<String?> mergeVideos(List<String> videoPaths) {
    throw UnimplementedError('mergeVideos() has not been implemented.');
  }

  /// Extracts audio from a video file.
  ///
  /// [videoPath] is the path to the source video file.
  ///
  /// The output format depends on the platform:
  /// - iOS: M4A format
  /// - Android: AAC format
  ///
  /// Returns the path to the extracted audio file, or null if the operation fails.
  Future<String?> extractAudio(String videoPath) {
    throw UnimplementedError('extractAudio() has not been implemented.');
  }

  /// Adjusts the playback speed of a video.
  ///
  /// [videoPath] is the path to the source video file.
  /// [speed] is the speed multiplier (e.g., 0.5 for slow motion, 2.0 for fast forward).
  ///
  /// Returns the path to the processed video file, or null if the operation fails.
  Future<String?> adjustVideoSpeed(String videoPath, double speed) {
    throw UnimplementedError('adjustVideoSpeed() has not been implemented.');
  }

  /// Removes the audio track from a video file.
  ///
  /// [videoPath] is the path to the source video file.
  ///
  /// Returns the path to the muted video file, or null if the operation fails.
  Future<String?> removeAudio(String videoPath) {
    throw UnimplementedError('removeAudio() has not been implemented.');
  }

  /// Crops a video to the specified aspect ratio.
  ///
  /// [videoPath] is the path to the source video file.
  /// [aspectRatio] defines the target aspect ratio for cropping.
  ///
  /// Returns the path to the cropped video file, or null if the operation fails.
  Future<String?> cropVideo(String videoPath, VideoAspectRatio aspectRatio) {
    throw UnimplementedError('cropVideo() has not been implemented.');
  }

  /// Rotates a video by the specified degrees.
  ///
  /// [videoPath] is the path to the source video file.
  /// [degrees] is the rotation angle in degrees (should be a multiple of 90).
  ///
  /// Returns the path to the rotated video file, or null if the operation fails.
  Future<String?> rotateVideo(String videoPath, int degrees) {
    throw UnimplementedError('rotateVideo() has not been implemented.');
  }

  /// Generates a thumbnail from a video at the specified position.
  ///
  /// [videoPath] is the path to the source video file.
  /// [positionMs] is the position in milliseconds where the thumbnail should be taken.
  /// [quality] is the compression quality of the generated thumbnail (0-100).
  /// [height] optional height of the thumbnail in pixels.
  /// [width] optional width of the thumbnail in pixels.
  ///
  /// Returns the path to the generated thumbnail, or null if the operation fails.
  Future<String?> generateThumbnail(
      String videoPath, int positionMs, int quality,
      {int? height, int? width, bool exactFrame = false}) {
    throw UnimplementedError('generateThumbnail() has not been implemented.');
  }

  /// Compresses a video with specified quality settings.
  ///
  /// [videoPath] is the path to the source video file.
  /// [resolution] specifies the target resolution for compression, defaults to 720p.
  ///
  /// Returns the path to the compressed video file, or null if the operation fails.
  Future<String?> compressVideo(String videoPath,
      {VideoResolution resolution = VideoResolution.p720}) {
    throw UnimplementedError('compressVideo() has not been implemented.');
  }

  /// Cancels any currently running operation.
  ///
  /// Returns true if an operation was successfully canceled, false otherwise.
  Future<bool> cancelOperation() {
    throw UnimplementedError('cancelOperation() has not been implemented.');
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
  Future<VideoMetadata> getVideoMetadata(String videoPath) {
    throw UnimplementedError('getVideoMetadata() has not been implemented.');
  }

  /// Flips a video horizontally or vertically.
  ///
  /// [videoPath] is the path to the source video file.
  /// [flipDirection] defines the direction to flip the video.
  ///
  /// Returns the path to the flipped video file, or null if the operation fails.
  Future<String?> flipVideo(String videoPath, FlipDirection flipDirection) {
    throw UnimplementedError('flipVideo() has not been implemented.');
  }
}
