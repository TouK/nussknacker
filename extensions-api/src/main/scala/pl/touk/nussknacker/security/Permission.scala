package pl.touk.nussknacker.security

import pl.touk.nussknacker.security

object Permission extends Enumeration {
  type Permission = Value
  val Read             = Value("Read")
  val Write            = Value("Write")
  val Deploy           = Value("Deploy")
  val Demo             = Value("Demo")
  val OverrideUsername = Value("OverrideUsername") // Used for migration username forwarding

  final val ALL_PERMISSIONS: Set[security.Permission.Permission] = Set(Read, Write, Deploy, Demo, OverrideUsername)
}
