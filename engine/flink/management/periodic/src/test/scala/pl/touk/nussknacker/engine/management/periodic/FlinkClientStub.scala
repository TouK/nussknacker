package pl.touk.nussknacker.engine.management.periodic

import java.io.File

import scala.concurrent.Future

class FlinkClientStub extends FlinkClient {

  override def uploadJarFileIfNotExists(jarFile: File): Future[String] = ???

  override def runJar(jarId: String, program: PeriodicFlinkRestModel.DeployProcessRequest): Future[Unit] = ???

  override def deleteJarIfExists(jarFileName: String): Future[Unit] = Future.successful(())
}
