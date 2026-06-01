import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:easy_video_editor/src/platform/easy_video_editor_method_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const MethodChannel channel = MethodChannel('easy_video_editor');
  late MethodChannelEasyVideoEditor platform;
  final calls = <MethodCall>[];

  setUp(() {
    platform = MethodChannelEasyVideoEditor();
    calls.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      channel,
      (MethodCall methodCall) async {
        calls.add(methodCall);
        if (methodCall.method == 'getFrame') {
          return <String, dynamic>{
            'bytes': Uint8List.fromList(<int>[255, 0, 0, 255]),
            'width': 1,
            'height': 1,
            'positionMs': 250,
          };
        }
        return '42';
      },
    );
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test('getFrame sends frame extraction arguments to the method channel',
      () async {
    await platform.getFrame(
      'video.mp4',
      250,
      width: 320,
      height: 180,
      exactFrame: true,
    );

    expect(calls, hasLength(1));
    expect(calls.single.method, 'getFrame');
    expect(calls.single.arguments, <String, dynamic>{
      'videoPath': 'video.mp4',
      'positionMs': 250,
      'exactFrame': true,
      'height': 180,
      'width': 320,
    });
  });

  test('getFrame converts raw frame map into a VideoFrame', () async {
    final frame = await platform.getFrame('video.mp4', 250);

    expect(frame, isNotNull);
    expect(frame!.bytes, Uint8List.fromList(<int>[255, 0, 0, 255]));
    expect(frame.width, 1);
    expect(frame.height, 1);
    expect(frame.positionMs, 250);
  });
}
