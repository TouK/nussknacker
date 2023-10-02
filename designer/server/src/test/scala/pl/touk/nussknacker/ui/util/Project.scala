package pl.touk.nussknacker.ui.util

import better.files._

import java.nio.file.{Path => JPath}

object Project {

  def root: File = {
    val targetItClassesDir = JPath.of(getClass.getResource("/").toURI)
    val rootDir = targetItClassesDir.getParent.getParent.getParent.getParent.getParent
    File(rootDir)
  }
}
