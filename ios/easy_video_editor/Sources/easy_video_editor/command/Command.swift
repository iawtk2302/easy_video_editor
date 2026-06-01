import Flutter

protocol Command {
    func execute(call: FlutterMethodCall, result: @escaping FlutterResult)
} 