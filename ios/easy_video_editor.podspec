#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
# Run `pod lib lint easy_video_editor.podspec` to validate before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'easy_video_editor'
  s.version          = '0.1.5'
  s.summary          = 'A lightweight Flutter plugin for video editing without FFmpeg dependency'
  s.description      = <<-DESC
A lightweight Flutter plugin for video editing without FFmpeg dependency. Trim, merge, adjust speed and more on Android & iOS.
                       DESC
  s.homepage         = 'https://github.com/iawtk2302/easy_video_editor'
  s.license          = { :type => 'MIT', :file => '../LICENSE' }
  s.author           = { 'iawtk2302' => 'iawtk2302@gmail.com' }
  s.source           = { :git => 'https://github.com/iawtk2302/easy_video_editor.git', :tag => s.version.to_s }
  s.source_files = 'easy_video_editor/Sources/easy_video_editor/**/*.swift'
  s.dependency 'Flutter'
  s.platform = :ios, '12.0'

  # Flutter.framework does not contain a i386 slice.
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386' }
  s.swift_version = '5.0'
end
