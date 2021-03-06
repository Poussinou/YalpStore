# Yalp Store

<a href="https://f-droid.org/repository/browse/?fdid=com.github.yeriomin.yalpstore" target="_blank">
  <img src="https://f-droid.org/badge/get-it-on.png" height="80"/>
</a>

## What does it do?
Yalp Store lets you download apps from Google Play Store **as apk files**. It searches for **updates** of installed apps when it starts and lets you **search** for other apps. Thats it. Yalp saves downloaded apks to your default download folder so you can later open it in your favorite file manager app and tap each one to install the apps.

## Why would I use it?
If you are content with Google Play Store app, you will not need this app.

The point of Yalp Store is to be small and independent from Google Services Framework. As time passed, Google Services Framework and Google Play Store apps grew in size, which made them almost too big for old phones (Nexus One has 150Mb memory available for apps, half of it would be taken by Google apps). Another reason to use Yalp Store is if you frequently flash experimental ROMs. This often breaks gapps and even prevents their reinstallation. In this situation Yalp will still work.

## How does it work?
Yalp Store uses the same (protobuf) API the android Play Store app uses. You are going to need a google account to use it. Please, keep in mind that technically **Yalp Store violates** [Android Market Terms of Service](https://www.google.com/intl/en_us/mobile/android/market-tos.html) (§3.3). In theory, you might get your account disabled by using Yalp Store. Thats why you might want to register a separate gmail account and use it at least once to log in to the Play Store android app on any device.

In practice, though, software like Yalp, Google Play Crawler and Raccoon has been used for years and it seems to be safe.

Yalp Store is derived from the following projects:
* https://github.com/Akdeniz/google-play-crawler
* https://github.com/onyxbits/Raccoon
