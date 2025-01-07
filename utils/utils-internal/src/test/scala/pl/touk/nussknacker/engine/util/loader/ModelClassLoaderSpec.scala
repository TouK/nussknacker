package pl.touk.nussknacker.engine.util.loader

import cats.effect.unsafe.implicits.global
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.net.{URL, URLClassLoader}
import java.nio.file.Path

class ModelClassLoaderSpec extends AnyFunSuite with Matchers with BeforeAndAfterAll {

  private val (deploymentManagersClassLoader, releaseDeploymentManagersClassLoaderResources) =
    DeploymentManagersClassLoader
      .create(List.empty)
      .allocated
      .unsafeRunSync()

  override protected def afterAll(): Unit = {
    releaseDeploymentManagersClassLoaderResources.unsafeRunSync()
    super.afterAll()
  }

  test("should detect nested URLs in classloader") {
    def resource(file: String): URL = getClass.getResource("/modelClassLoader" + file)
    val nonFileUrl                  = new URL("http://dummy.com")

    val urls = List(resource(""), nonFileUrl)

    val loader = ModelClassLoader(
      urls.map(_.toURI.toString),
      workingDirectoryOpt = None,
      deploymentManagersClassLoader = deploymentManagersClassLoader,
      jarExtension = ".jara"
    )

    // we're not using .jar to avoid messing with .gitignore
    val expected = Set(
      resource("/first.jara"),
      resource("/a/second.jara"),
      resource("/b/c/fourth.jara"),
      resource("/b/third.jara"),
      resource("/c/"),
      nonFileUrl
    )
    loader.urls.toSet shouldBe expected
    loader.asInstanceOf[URLClassLoader].getURLs.toSet shouldBe expected
  }

  test("should resolve classpath using working directory when defined") {
    val loader = ModelClassLoader(
      urls = List("relative/path", "/absolute/path"),
      workingDirectoryOpt = Some(Path.of("/some/working/directory")),
      deploymentManagersClassLoader = deploymentManagersClassLoader
    )
    loader.urls shouldEqual List(new URL("file:/some/working/directory/relative/path"), new URL("file:/absolute/path"))
  }

}
