package pl.touk.nussknacker.ui.util

import better.files.File

import java.nio.file.Path

object Project {

  def root: File = {
    val targetItClassesDir = Path.of(getClass.getResource("/").toURI)
    val rootDir            = targetItClassesDir.getParent.getParent.getParent.getParent.getParent
    File(rootDir)
  }

}
