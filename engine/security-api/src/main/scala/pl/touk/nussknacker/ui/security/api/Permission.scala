package pl.touk.nussknacker.ui.security.api

object Permission extends Enumeration {
  type Permission = Value
  val Read, Write, Deploy, Admin = Value
}
