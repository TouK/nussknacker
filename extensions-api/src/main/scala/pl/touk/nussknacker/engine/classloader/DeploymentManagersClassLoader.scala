package pl.touk.nussknacker.engine.classloader

import java.net.URL
import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader

class DeploymentManagersClassLoader private[classloader] (urls: Seq[URL], parent: ClassLoader)
    extends URLClassLoader(urls, parent)
