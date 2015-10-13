-dontobfuscate
-dontoptimize # because of proguard bug
-injar ../target/setup-stool.jar(!templates/man/**)
-outjar ../target/setup-stool-min.jar
-keepattributes *Annotation*

-libraryjars <java.home>/lib/rt.jar:<java.home>/lib/jce.jar

-ignorewarnings

-dontnote com.sun.mail.**
-dontnote javax.annotation.*
-dontwarn javax.enterprise.**
-dontnote javax.enterprise.**
-dontnote org.apache.maven.**
-dontwarn org.apache.maven.**
-dontnote net.oneandone.stool.**
-dontnote ch.qos.logback.**
-dontwarn ch.qos.logback.**
-dontnote org.apache.commons.**
-dontwarn org.apache.commons.**
-dontnote org.apache.http.**
-dontwarn org.apache.http.**
-dontnote com.google.**
-dontwarn com.google.**
-dontnote org.eclipse.**
-dontwarn org.eclipse.**
-dontnote org.codehaus.**
-dontwarn org.codehaus.**
-dontnote net.oneandone.sushi.**
-dontwarn net.oneandone.sushi.**

-keep public class net.oneandone.stool.setup.DebianSetup {
    public static void main(java.lang.String[]);
}

-keep public class ** implements net.oneandone.stool.extensions.Extension {
    public <init>(...);
}

-keep public class ** extends net.oneandone.sushi.fs.Filesystem {
    public <init>(...);
}
