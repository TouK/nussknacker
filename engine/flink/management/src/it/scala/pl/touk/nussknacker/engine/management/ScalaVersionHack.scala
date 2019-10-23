package pl.touk.nussknacker.engine.management

trait ScalaVersionHack {

  val scalaBinaryVersion: String = util.Properties.versionNumberString.replaceAll("(\\d+\\.\\d+)\\..*$", "$1")

  {
    System.setProperty("scala.binary.version", scalaBinaryVersion)
  }

}
