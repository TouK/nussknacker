package pl.touk.esp.engine.management.util

object JarFileFinder {

  def findJarPath(configCreatorClass: Class[_]): String = {
    val loader = configCreatorClass.getClassLoader
    val classesFileName = configCreatorClass.getName.replace('.', '/') + ".class"
    loader.getResource(classesFileName).getFile.replaceAll("!.*", "").replace("file:", "")
  }

}
