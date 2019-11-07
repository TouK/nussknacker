package pl.touk.nussknacker.engine.management

trait ScalaVersionHack {

  val scalaBinaryVersion: String = util.Properties.versionNumberString.replaceAll("(\\d+\\.\\d+)\\..*$", "$1")

}
