package pl.touk.nussknacker.ui.security

object Permission extends Enumeration {
  type Permission = Value
  val Read = Value("Read")
  val Write = Value("Write")
  val Deploy = Value("Deploy")

  final val ALL_PERMISSIONS = Set(Read, Write, Deploy)
}
