package pl.touk.nussknacker.engine.util

object JvmVersionUtil {

  lazy val jvmMajorVersion: Int = {
    val version = System.getProperty("java.version")
    if (version.startsWith("1.")) version.split("\\.")(1).toInt
    else version.split("\\.")(0).toInt
  }

}
