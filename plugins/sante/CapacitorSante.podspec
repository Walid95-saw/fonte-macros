require 'json'

paquet = JSON.parse(File.read(File.join(__dir__, 'package.json')))

# Declare le greffon comme une dependance CocoaPods du projet iOS.
# C'est ce qui evite de modifier le fichier projet Xcode a la main : « npx cap sync ios »
# ajoute ce pod au Podfile, et Xcode compile les sources Swift automatiquement.
Pod::Spec.new do |s|
  s.name = 'CapacitorSante'
  s.version = paquet['version']
  s.summary = paquet['description']
  s.license = paquet['license']
  s.homepage = 'https://github.com/'
  s.author = 'Fonte & Macros'
  s.source = { :git => 'https://github.com/', :tag => s.version.to_s }
  s.source_files = 'ios/Plugin/**/*.{swift,h,m,c,cc,mm,cpp}'
  s.ios.deployment_target = '13.0'
  s.dependency 'Capacitor'
  s.swift_version = '5.1'
  s.frameworks = 'HealthKit'
end
