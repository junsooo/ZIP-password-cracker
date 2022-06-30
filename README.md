# ZIP-password-cracker

<img src="https://user-images.githubusercontent.com/22411406/176683464-c3c09faf-067b-40c9-b005-dec07dec7db2.png" alt="app logo" width="150"/>

## Overview
Android app for ZIP password cracker(finder) using simple dictionary attack

## How to download
Available at [Google Play Store](https://play.google.com/store/apps/details?id=com.better_life.zip_password_cracker&hl=ko&gl=US)
<br>100,000+ Downloads

## Key features
- Select file from android storage
- Read "pass.txt" and check that line is same with zip file's password and notify user

## App screenshot
<img src="https://user-images.githubusercontent.com/22411406/176708761-3bd52abd-f4cc-4abd-98e5-c11ebe46e1c6.png" alt="app screenshot" width="500"/>

## How to use
 - Click "SELECT FILE" button and get file from android file manager
 - Click "START!" button to find password
 - If password is found(means pass.txt contains zip file's password), popup notifies password.

## Warning(for code)
 - I deleted some code for some purposes.(security, license)
   - assets/pass.txt(You can find some dictionary for dictionary attack on the internet)
   - java/com/better_life/zip_password_cracker/FileUtils.java: (akhilesh0707's answer: https://stackoverflow.com/questions/13209494/how-to-get-the-full-file-path-from-uri)
 - Please contact me if you have some questions for other code part of this project.

## Explanation for code
 - MainActivity.kt: TODO

## Tech stack
 - Kotlin, Java, Android Studio

## Team
 - PLH: App logo designer
 - Junsoo Lee

## License
 - Zip4j: Apache License version 2.0

## TODO
 - [ ] Use bigger dictionary to find password(Some kinds of multi-threading technique will be needed)
 - [ ] Support feature for brute-force method
 - [ ] Make app for App Store
