package pl.touk.nussknacker.engine.util.loader

import java.net.{URL, URLClassLoader}

case class ModelClassLoader private(classLoader: ClassLoader,
                                    urls: List[URL])

object ModelClassLoader {
  //for e.g. testing in process module
  val empty = ModelClassLoader(getClass.getClassLoader, List())

  def apply(urls: List[URL]): ModelClassLoader = {
    ModelClassLoader(new URLClassLoader(urls.toArray, this.getClass.getClassLoader), urls)
  }

}


