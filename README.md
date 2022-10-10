## SicMu Player for Android

Every songs of the phone are put in a unique big song list.

Songs are sorted and grouped by folders, artists, albums and album's track.

Works on rather old devices (from Android 4.0).


4.3" screen:

![Tree folder list](misc/screenshots/screen4.3_tree_resized.png)&nbsp;
![Artist list](misc/screenshots/screen4.3_artists_coverdetails_resized.png)
![Fullscreen cover art](misc/screenshots/screen4.3_bigcover_resized.png)

[Video (outdated)](http://youtu.be/LGyjDfwimzA)

[More screenshots](Screenshots.md)

### Installation

[<img alt="Get it on F-Droid" height="80" src="https://f-droid.org/badge/get-it-on.png">](https://f-droid.org/repository/browse/?fdid=souch.smp)
[<img alt="Get it on Google Play" height="80" src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png">](https://play.google.com/store/apps/details?id=souch.smp)

### Detailed features

- sort mode:
    - sort by folder tree, useful for big music list (default)
    - sort by artists, albums and track number
    - sort by folders, artists, albums and track number, flattening folder hierarchy
- cover art image
- songs navigation:
    - repeat mode (all, group, one track)
    - shuffle mode
    - seek bar
    - auto repeat seek buttons
    - loop on part of the song (A to B repeat feature)
    - adjustable playback speed
    - shake the phone to go to next song
    - rate songs and filter by rating
- User Interface:
    - notification with media controls
    - disable / enable lockscreen
    - configurable font size
- bluetooth & scrobble
    - bluetooth support (play through bluetooth device)
    - media buttons support (next, prev, play/pause) from external device (bluetooth headphones...)
    - support [Simple Last.fm Scrobbler](https://github.com/tgwizard/sls) or [Scrobble Droid](https://code.google.com/p/scrobbledroid) (disabled by default in settings)
- play mp3, ogg, flac, midi, wav, 3gp... see android mediaplayer supported media formats (depends on android version).
- light and fast: starts in 0.5s and uses 40Mo of RAM with 18Go of music (3000 files, 200 folders) on an old 2*1.7GHz ARM processor.


### Help

- see help section in app's settings


### Todo (perhaps :-)

- remove file
- browse folder
- block song/folder
- group by genre?
- search?
- pinned section (upper group level stay at top until another one appears)?
- mp3 tag editor ?
- playlist?
- audioeffect/equalizer
- migrate to kotlin

Detailed todo list available in [TODO.txt](misc/TODO.txt).


### Credits

Lot's of time saved thanks to Sue Smith's [tutorials on creating a Music Player on Android](http://code.tutsplus.com/tutorials/create-a-music-player-on-android-project-setup--mobile-22764).

Use some icons done by Daniele De Santis (Creative Commons Attribution 3.0 Unported), found on iconfinder.com.

Seekbar style created by Jérôme Van Der Linden (Creative Commons Attribution 3.0 Unported), found on http://android-holo-colors.com.

RepeatingImageButton.java and MediaButtonIntentReceiver.java file mostly copied from official android music player (Apache License, Version 2.0).


### Developer

Compiled with Android Studio.
Tested on Kitkat (4.4.4), Samsung S3 (7.1), Android 11...

Feel free to add GitLab issues (feature request, bugs...).
If you need a feature that is in the todolist, open a feature request on gitlab to speed up its development.

A UML class diagram describe a bit the architecture for people that would want to develop the software [UmlClass.png](misc/UmlClass.png).

SicMu player pronounced zicmu. S is for Super Simple Sorted Souch player.


### Donation

If you like the app, donate to reward me for the hard work ! :
[donate](https://www.paypal.com/donate/?hosted_button_id=QAPVFX7NZ8BTE)


### License

SicMu Player is licensed under the GPLv3. See file [LICENSE](LICENSE) for more details.

