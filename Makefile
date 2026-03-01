PACKAGE  := it.lo.exp.nulladies
ACTIVITY := .MainActivity
APK      := app/build/outputs/apk/debug/app-debug.apk

.PHONY: build install run release clean logcat

build:
	gradle --no-daemon assembleDebug

install:
	gradle --no-daemon installDebug

run: install
	adb shell am start -n $(PACKAGE)/$(ACTIVITY)

release:
	gradle --no-daemon assembleRelease

clean:
	gradle --no-daemon clean

logcat:
	adb logcat -s "NullaDies"
