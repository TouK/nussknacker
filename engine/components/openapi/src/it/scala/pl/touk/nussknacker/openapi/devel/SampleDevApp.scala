package pl.touk.nussknacker.openapi.devel

import pl.touk.nussknacker.engine.util.config.ConfigFactoryExt
import pl.touk.nussknacker.ui.{NusskanckerDefaultAppRouter, NussknackerAppInitializer}

import java.net.URI

object SampleDevApp extends App {
  new NussknackerAppInitializer(ConfigFactoryExt.load(
    resources = List(URI.create("classpath:devel/openapi.conf"), URI.create("classpath:devel/main.conf")),
    classLoader = getClass.getClassLoader)).init(NusskanckerDefaultAppRouter)
}
