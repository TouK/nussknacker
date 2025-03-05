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

addSbtPlugin("ch.epfl.scala"      % "sbt-scalafix"           % {
  sys.env.get("NUSSKNACKER_SCALA_VERSION") match {
    case None | Some("2.13") => "0.14.2"
    case Some("2.12")        => "0.9.11"
    case Some(unsupported)   => throw new IllegalArgumentException(s"Nu doesn't support $unsupported Scala version")
  }
})

addSbtPlugin("org.scalameta"      % "sbt-scalafmt"           % "2.5.4")
// 3.12 is missing some logging when run on JDK 11
addSbtPlugin("org.xerial.sbt"     % "sbt-sonatype"           % "3.11.3")

def forScalaVersion[T](version: String)(provide: PartialFunction[(Int, Int), T]): T = {
  CrossVersion.partialVersion(version) match {
    case Some((major, minor)) if provide.isDefinedAt((major.toInt, minor.toInt)) =>
      provide((major.toInt, minor.toInt))
    case Some(_)                                                                 =>
      throw new IllegalArgumentException(s"Scala version $version is not handled")
    case None                                                                    =>
      throw new IllegalArgumentException(s"Invalid Scala version $version")
  }
}
