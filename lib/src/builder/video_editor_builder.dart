import 'dart:async';
import 'dart:io';

import '../easy_video_editor.dart';
import '../enums/enums.dart';
import '../models/models.dart';

/// A builder class for chaining video operations
class VideoEditorBuilder {
  static final EasyVideoEditor _editor = EasyVideoEditor();
  String _videoPath;
  final List<VideoOperation> _operations = [];

  /// Creates a new video editor builder
  ///
  /// [videoPath] is the path to the input video file
  VideoEditorBuilder({required String videoPath}) : _videoPath = videoPath;

  /// Adds trim operation
  ///
  /// [startTimeMs] Start time in milliseconds
  /// [endTimeMs] End time in milliseconds
  VideoEditorBuilder trim({required int startTimeMs, required int endTimeMs}) {
    _operations.add(VideoOperation(
      VideoOperationType.trim,
      {'startTimeMs': startTimeMs, 'endTimeMs': endTimeMs},
    ));
    return this;
  }

  /// Adds merge operation
  ///
  /// [otherVideoPaths] List of video paths to merge with current video
  VideoEditorBuilder merge({required List<String> otherVideoPaths}) {
    _operations.add(VideoOperation(
      VideoOperationType.merge,
      {'paths': otherVideoPaths},
    ));
    return this;
  }

  /// Adds speed adjustment operation
  ///
  /// [speed] Speed multiplier (e.g., 0.5 for half speed, 2.0 for double speed)
  VideoEditorBuilder speed({required double speed}) {
    _operations.add(VideoOperation(
      VideoOperationType.speed,
      {'speed': speed},
    ));
    return this;
  }

  /// Adds audio removal operation
  VideoEditorBuilder removeAudio() {
    _operations.add(const VideoOperation(VideoOperationType.removeAudio, {}));
    return this;
  }

  /// Adds crop operation
  ///
  /// [aspectRatio] Target aspect ratio from predefined ratios
  VideoEditorBuilder crop({required VideoAspectRatio aspectRatio}) {
    _operations.add(VideoOperation(
      VideoOperationType.crop,
      {'aspectRatio': aspectRatio},
    ));
    return this;
  }

  /// Adds rotation operation
  ///
  /// [degree] Rotation angle (90, 180, or 270 degrees)
  VideoEditorBuilder rotate({required RotationDegree degree}) {
    _operations.add(VideoOperation(
      VideoOperationType.rotate,
      {'degrees': degree.value},
    ));
    return this;
  }

  /// Adds compression operation
  ///
  /// [resolution] Target resolution for the video (e.g., VideoResolution.p720 for 720p).
  /// If not specified, defaults to 720p while maintaining aspect ratio.
  VideoEditorBuilder compress(
      {VideoResolution resolution = VideoResolution.p720}) {
    _operations.add(VideoOperation(
      VideoOperationType.compress,
      {'resolution': resolution},
    ));
    return this;
  }

  /// Adds flip operation
  ///
  /// [flipDirection] Direction to flip the video (horizontal or vertical)
  VideoEditorBuilder flip({required FlipDirection flipDirection}) {
    _operations.add(VideoOperation(
      VideoOperationType.flip,
      {'flipDirection': flipDirection},
    ));
    return this;
  }

  /// Executes all operations in sequence and returns the final video path
  ///
  /// [outputPath] Optional path where the final video will be saved.
  /// If not provided, a default path will be used.
  /// [onProgress] Optional callback to receive progress updates during export (0.0 to 1.0)
  Future<String?> export(
      {String? outputPath, void Function(double progress)? onProgress}) async {
    String? currentPath = _videoPath;
    String? previousPath;

    // Set up progress listener if callback is provided
    StreamSubscription<double>? progressSubscription;
    int totalOperations = _operations.length;
    int currentOperationIndex = 0;

    if (onProgress != null && totalOperations > 0) {
      progressSubscription = _editor.getProgressStream().listen((rawProgress) {
        // Calculate overall progress based on current operation and its progress
        // Each operation contributes 1/totalOperations to the overall progress
        double operationContribution = 1.0 / totalOperations;
        double overallProgress =
            (currentOperationIndex * operationContribution) +
                (rawProgress * operationContribution);

        // Ensure progress stays between 0-1
        onProgress(overallProgress.clamp(0.0, 1.0));
      });
    } else if (onProgress != null) {
      // If no operations, just pass through the progress
      progressSubscription = _editor.getProgressStream().listen(onProgress);
    }

    for (final operation in _operations) {
      if (currentPath == null) break;

      try {
        // Store the current path before executing the operation
        previousPath = currentPath;

        // Execute the operation
        currentPath = await _executeOperation(operation, currentPath);

        // Update operation index for progress tracking
        if (onProgress != null) {
          currentOperationIndex++;
        }

        // If this is not the input video and the operation was successful,
        // delete the intermediate file
        if (previousPath != _videoPath && currentPath != null) {
          try {
            final file = File(previousPath);
            if (await file.exists()) {
              await file.delete();
            }
          } catch (e) {
            print('Warning: Could not delete intermediate file: $previousPath');
          }
        }
      } catch (e) {
        print('Error executing operation ${operation.type.name}: $e');
        return null;
      }
    }

    if (currentPath != null && outputPath != null) {
      try {
        final inputFile = File(currentPath);
        await inputFile.copy(outputPath);

        // Delete the input file if it's not the original video
        if (currentPath != _videoPath) {
          await inputFile.delete();
        }

        currentPath = outputPath;
      } catch (e) {
        print('Error copying to output path: $e');
        return null;
      }
    }

    // Report 100% progress when all operations complete
    if (onProgress != null && totalOperations > 0) {
      onProgress(1.0);
    }

    // Clean up progress subscription
    await progressSubscription?.cancel();

    _videoPath = currentPath ?? _videoPath;
    return currentPath;
  }

  Future<String?> _executeOperation(
      VideoOperation operation, String inputPath) async {
    switch (operation.type) {
      case VideoOperationType.trim:
        return await _editor.trimVideo(
          inputPath,
          operation.params['startTimeMs'] as int,
          operation.params['endTimeMs'] as int,
        );

      case VideoOperationType.merge:
        final paths = [
          inputPath,
          ...(operation.params['paths'] as List<String>)
        ];
        return await _editor.mergeVideos(paths);

      case VideoOperationType.speed:
        return await _editor.adjustVideoSpeed(
          inputPath,
          operation.params['speed'] as double,
        );

      case VideoOperationType.removeAudio:
        return await _editor.removeAudio(inputPath);

      case VideoOperationType.crop:
        return await _editor.cropVideo(
          inputPath,
          operation.params['aspectRatio'] as VideoAspectRatio,
        );

      case VideoOperationType.rotate:
        return await _editor.rotateVideo(
          inputPath,
          operation.params['degrees'] as int,
        );

      case VideoOperationType.compress:
        return await _editor.compressVideo(
          inputPath,
          resolution: operation.params['resolution'] as VideoResolution,
        );

      case VideoOperationType.flip:
        return await _editor.flipVideo(
          inputPath,
          operation.params['flipDirection'] as FlipDirection,
        );
    }
  }

  /// Gets the current video path
  String get currentPath => _videoPath;

  /// Cancels any ongoing operation
  ///
  /// Returns true if an operation was canceled, false if no operation was in progress
  static Future<bool> cancel() async {
    final result = await _editor.cancelOperation();
    return result;
  }

  /// Extracts audio from the current video
  ///
  /// [outputPath] Optional path where the extracted audio will be saved.
  /// If not provided, a default path will be used.
  Future<String?> extractAudio({String? outputPath}) async {
    final result = await _editor.extractAudio(_videoPath);

    if (result != null && outputPath != null) {
      try {
        final inputFile = File(result);
        await inputFile.copy(outputPath);

        // Delete the original output since we've moved it
        await inputFile.delete();
        return outputPath;
      } catch (e) {
        print('Error copying audio to output path: $e');
        return null;
      }
    }

    return result;
  }

  /// Generates a thumbnail from the current video
  ///
  /// [positionMs] Time position in milliseconds
  /// [quality] Quality of the thumbnail (0-100)
  /// [height] Optional height of the thumbnail
  /// [width] Optional width of the thumbnail
  /// [outputPath] Optional path where the thumbnail will be saved.
  /// If not provided, a default path will be used.
  Future<String?> generateThumbnail({
    required int positionMs,
    required int quality,
    int? height,
    int? width,
    bool exactFrame = false,
    String? outputPath,
  }) async {
    final result = await _editor.generateThumbnail(
      _videoPath,
      positionMs,
      quality,
      height: height,
      width: width,
      exactFrame: exactFrame,
    );

    if (result != null && outputPath != null) {
      try {
        final inputFile = File(result);
        await inputFile.copy(outputPath);

        // Delete the original output since we've moved it
        await inputFile.delete();
        return outputPath;
      } catch (e) {
        print('Error copying thumbnail to output path: $e');
        return null;
      }
    }

    return result;
  }

  /// Retrieves metadata information about the current video file.
  ///
  /// Returns a [VideoMetadata] object containing information about the video:
  /// - Duration (in milliseconds)
  /// - Width and Height (in pixels)
  /// - Title (if available)
  /// - Author (if available)
  /// - Orientation (rotation in degrees: 0, 90, 180, 270)
  /// - File size (in bytes)
  /// - Creation date (in String)
  Future<VideoMetadata> getVideoMetadata() async {
    return await EasyVideoEditor.getVideoMetadata(_videoPath);
  }
}
