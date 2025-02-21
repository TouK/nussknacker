libraryDependencies ++= Seq(
  "com.github.pathikrit" %% "better-files" % "3.9.2",
  "com.lihaoyi"          %% "os-lib"       % "0.11.3",
)

addDependencyTreePlugin
addSbtPlugin("com.eed3si9n"       % "sbt-assembly"           % "1.1.0")
addSbtPlugin("com.eed3si9n"       % "sbt-buildinfo"          % "0.11.0")
addSbtPlugin("org.jmotor.sbt"     % "sbt-dependency-updates" % "1.2.9")
addSbtPlugin("com.typesafe.sbt"   % "sbt-git"                % "1.0.2")
addSbtPlugin("uk.co.randomcoding" % "sbt-git-hooks"          % "0.2.0")
addSbtPlugin("com.github.sbt"     % "sbt-javaagent"          % "0.1.8")
addSbtPlugin("pl.project13.scala" % "sbt-jmh"                % "0.4.7")
addSbtPlugin("com.github.sbt"     % "sbt-native-packager"    % "1.11.1")
addSbtPlugin("com.github.sbt"     % "sbt-pgp"                % "2.3.1")
addSbtPlugin("com.github.sbt"     % "sbt-release"            % "1.4.0")
addSbtPlugin("ch.epfl.scala"      % "sbt-scalafix"           % "0.14.0")
addSbtPlugin("org.scalameta"      % "sbt-scalafmt"           % "2.5.4")
// 3.12 is missing some logging when run on JDK 11
addSbtPlugin("org.xerial.sbt"     % "sbt-sonatype"           % "3.11.3")
