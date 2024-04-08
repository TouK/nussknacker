libraryDependencies ++= Seq(
  "com.github.pathikrit" %% "better-files" % "3.9.2",
  "com.lihaoyi"          %% "os-lib"       % "0.9.1",
)

addDependencyTreePlugin
addSbtPlugin("com.eed3si9n"       % "sbt-assembly"           % "1.1.0")
addSbtPlugin("com.eed3si9n"       % "sbt-buildinfo"          % "0.9.0")
addSbtPlugin("org.jmotor.sbt"     % "sbt-dependency-updates" % "1.2.7")
addSbtPlugin("com.typesafe.sbt"   % "sbt-git"                % "1.0.1")
addSbtPlugin("uk.co.randomcoding" % "sbt-git-hooks"          % "0.2.0")
addSbtPlugin("com.lightbend.sbt"  % "sbt-javaagent"          % "0.1.6")
addSbtPlugin("pl.project13.scala" % "sbt-jmh"                % "0.4.6")
addSbtPlugin("com.github.sbt"     % "sbt-native-packager"    % "1.9.13")
addSbtPlugin("com.jsuereth"       % "sbt-pgp"                % "2.0.2")
addSbtPlugin("com.github.gseitz"  % "sbt-release"            % "1.0.10")
addSbtPlugin("ch.epfl.scala"      % "sbt-scalafix"           % "0.11.1")
addSbtPlugin("org.scalameta"      % "sbt-scalafmt"           % "2.5.2")
addSbtPlugin("org.xerial.sbt"     % "sbt-sonatype"           % "3.9.7")
