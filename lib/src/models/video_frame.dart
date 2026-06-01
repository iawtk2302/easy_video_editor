import 'dart:typed_data';

/// Raw video frame pixels returned by [EasyVideoEditor.getFrame].
///
/// [bytes] contains RGBA8888 pixels, four bytes per pixel, in row-major order.
class VideoFrame {
  const VideoFrame({
    required this.bytes,
    required this.width,
    required this.height,
    required this.positionMs,
  });

  final Uint8List bytes;
  final int width;
  final int height;
  final int positionMs;

  factory VideoFrame.fromMap(Map<dynamic, dynamic> map) {
    final bytes = map['bytes'];
    return VideoFrame(
      bytes: bytes is Uint8List
          ? bytes
          : Uint8List.fromList(List<int>.from(bytes as List<dynamic>)),
      width: (map['width'] as num).toInt(),
      height: (map['height'] as num).toInt(),
      positionMs: (map['positionMs'] as num).toInt(),
    );
  }
}
