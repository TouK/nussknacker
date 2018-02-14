package pl.touk.nussknacker.ui.security.api

object Permission extends Enumeration {
  type Permission = Value
  val Read = Value("Read")
  val Write = Value("Write")
  val Deploy = Value("Deploy")
  val Admin = Value("Admin")
}
