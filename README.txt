PLANNER - ANDROID APP + 4x4 WIDGET (v2)

WHAT'S NEW
- Dark mode button (moon/sun icon, top right). Also colors the status bar and the widget.
- Subjects screen (book icon, top right): add subjects, several days per subject, and
  create a NEW SEMESTER ahead of time. Give it a start date and tick "switch automatically"
  and the app switches to it on that day. You can also tap "Use this semester" any time.
- Backup (Subjects screen > Back up or restore my data): copy it to Notes once in a while.
- 4x4 widget with 4 pages. Tap the < and > arrows to switch:
  1) My Schedule (the whole week as a timetable)  2) My Class Today
  3) My Current Class  4) My Incoming Tasks

IMPORTANT - FIRST UPDATE FROM THE OLD APP
This version uses a fixed signing key (app/debug.keystore) so every future update installs
OVER the old one and keeps your data. The very first time, Android may say the app conflicts
with the old one. If so, open the OLD app > Back up (if your old version has it) or write down
your tasks, uninstall the old app, then install this one. After that you never have to again.
Do not delete app/debug.keystore from your repository.

BUILD THE APK FOR FREE (no Android Studio needed)
1. Make a free account at github.com and create a new repository (any name),
   or use your existing repository and replace the files.
2. Upload ALL files and folders (including the hidden .github folder and app/debug.keystore).
   If .github did not go in: Add file > Create new file, name it  .github/workflows/build-apk.yml
   and paste the contents of build-apk.yml.txt.
3. Open the Actions tab. The "Build APK" run starts by itself (3-5 minutes).
4. When it shows a green tick, open the run, scroll to Artifacts, download "planner-apk".
5. Unzip it on your phone to get app-debug.apk and tap it to install.

ADD THE WIDGET
Touch and hold an empty spot on the home screen > Widgets > Planner > drag it out.
Open the app once first so the widget has your data. Stretch it to 4x4 for the best view.

BUILD WITH ANDROID STUDIO INSTEAD (optional)
File > Open this folder, let it sync (Gradle 8.7, JDK 17), then Build > Build APK.
