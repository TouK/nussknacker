package pl.touk.nussknacker.engine.component

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import com.vdurmont.semver4j.Semver
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.{MethodToInvoke, Service}
import pl.touk.nussknacker.engine.api.component.{Component, ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, WithCategories}
import pl.touk.nussknacker.engine.modelconfig.DefaultModelConfigLoader
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming
import pl.touk.nussknacker.test.ClassLoaderWithServices
import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.engine.component.ComponentExtractorTest.largeMajorVersion

import scala.concurrent.Future
import scala.collection.JavaConverters._

object ComponentExtractorTest {

  //in most tests we use "standard" NU version number, we want to make sure compatibility check works there
  val largeMajorVersion = 1024

}

class ComponentExtractorTest extends FunSuite with Matchers {

  private val loader = new DefaultModelConfigLoader

  test("should discover services") {
    val components = extractComponents[Service]("components.dynamicTest.valueCount" -> 7)
    components shouldBe (1 to 7).map(i => s"component-v$i" -> WithCategories(DynamicService(s"v$i"))).toMap
  }

  test("should handle multiple providers") {
    val components = extractComponents[Service](
      "components" -> Map(
        "dynamic1" -> Map("providerType" -> "dynamicTest", "valueCount" -> 2),
        "dynamic2" -> Map("providerType" -> "dynamicTest", "componentPrefix" -> "t1-", "valueCount" -> 3)
    ))

    components shouldBe ((1 to 2).map(i => s"component-v$i" -> WithCategories(DynamicService(s"v$i"))) ++
      (1 to 3).map(i => s"t1-component-v$i" -> WithCategories(DynamicService(s"v$i")))).toMap
  }

  test("should detect duplicate config") {
    intercept[IllegalArgumentException] {
      extractComponents[Service](
        "components" -> Map(
          "dynamic1" -> Map("providerType" -> "dynamicTest", "valueCount" -> 2),
          "dynamic2" -> Map("providerType" -> "dynamicTest", "valueCount" -> 3)
        ))
    }.getMessage should include("component-v1, component-v2")
  }

  test("should skip disabled providers") {
    val components = extractComponents[Service](
      "components" -> Map(
        "dynamic1" -> Map("providerType" -> "dynamicTest", "disabled" -> true),
        "dynamic2" -> Map("providerType" -> "dynamicTest", "componentPrefix" -> "t1-", "valueCount" -> 1)
    ))
    components shouldBe Map("t1-component-v1" -> WithCategories(DynamicService("v1")))
  }

  test("should skip incompatible providers") {
    //see DynamicProvider.isCompatible
    val largeVersionNumber = new Semver(s"$largeMajorVersion.2.3")
    intercept[IllegalArgumentException] {
      extractComponents[Service](Map("components.dynamicTest.valueCount" -> 7),
        (cl:ClassLoader) => ComponentExtractor(cl, NussknackerVersion(largeVersionNumber)))
    }.getMessage should include(s"is not compatible with NussknackerVersion(${largeVersionNumber.toString})")
  }

  private def extractComponents[T <: Component](map: (String, Any)*): Map[String, WithCategories[T]] =
    extractComponents(map.toMap, ComponentExtractor(_))

  private def extractComponents[T <: Component](map: Map[String, Any], makeExtractor: ClassLoader => ComponentExtractor): Map[String, WithCategories[T]] = {
    ClassLoaderWithServices.withCustomServices(List((classOf[ComponentProvider], classOf[DynamicProvider])), getClass.getClassLoader) { cl =>
      val extractor = makeExtractor(cl)
      val resolved = loader.resolveInputConfigDuringExecution(fromMap(map.toSeq: _*), cl)
      extractor.extract(ProcessObjectDependencies(resolved.config, DefaultNamespacedObjectNaming)).mapValues(k => k.copy(value = k.value.asInstanceOf[T]))
    }
  }

  private def fromMap(map: (String, Any)*): Config = ConfigFactory.parseMap(map.toMap.mapValues {
    case map: Map[String, Any]@unchecked => fromMap(map.toSeq: _*).root()
    case other => other
  }.asJava)

}


//Sample showing how to achieve dynamic component count based on configuration, evaluated on NK side (e.g. discover of services from external registry)
class DynamicProvider extends ComponentProvider {

  override def providerName: String = "dynamicTest"

  override def resolveConfigForExecution(config: Config): Config = {
    val number = config.getAs[Int]("valueCount").getOrElse(0)
    config.withValue("values", ConfigValueFactory.fromIterable((1 to number).map(i => s"v$i").asJava))
  }

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    config.getAs[List[String]]("values").getOrElse(Nil).map { value: String =>
      ComponentDefinition(s"component-$value", DynamicService(value))
    }
  }

  override def isCompatible(version: NussknackerVersion): Boolean = version.value.getMajor < ComponentExtractorTest.largeMajorVersion

}

case class DynamicService(valueToReturn: String) extends Service {

  @MethodToInvoke
  def invoke(): Future[String] = {
    Future.successful(valueToReturn)
  }
}

