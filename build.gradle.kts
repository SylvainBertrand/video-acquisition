plugins {
   id("us.ihmc.ihmc-build")
   id("us.ihmc.ihmc-cd") version "1.23"
   id("us.ihmc.ihmc-ci") version "7.6"
   id("us.ihmc.log-tools-plugin") version "0.6.3"
}

ihmc {
   group = "us.ihmc"
   version = "0.0.0.0"
   vcsUrl = "https://stash.ihmc.us/projects/ROB"
   openSource = false

   configureDependencyResolution()
   configurePublications()
}

mainDependencies {
   api("org.bytedeco:javacv-platform:1.5")
   api("us.ihmc:ihmc-video-codecs:2.1.6")

   api("us.ihmc:ihmc-realtime:1.4.0")
   api("us.ihmc:euclid:0.17.1")
   api("us.ihmc:euclid-geometry:0.17.1")
   api("us.ihmc:ihmc-pub-sub:0.16.2")
   api("us.ihmc:ihmc-ros2-library:0.20.5")
   api("us.ihmc:ros2-common-interfaces:0.20.5")
   api("us.ihmc:ihmc-commons:0.30.5")
   api("us.ihmc:ihmc-interfaces:0.13.0-210804")
}
