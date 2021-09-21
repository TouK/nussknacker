package pl.touk.nussknacker.ui.security.api

object Permission extends Enumeration {
  type Permission = Value
  val Read = Value("Read")
  val Write = Value("Write")
  val Deploy = Value("Deploy")
  val Demo = Value("Demo")

  final val ALL_PERMISSIONS = Set(Read, Write, Deploy, Demo)
}
