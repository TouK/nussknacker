package pl.touk.nussknacker.engine.component

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import com.vdurmont.semver4j.Semver
import net.ceedubs.ficus.Ficus._
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.component.{Component, ComponentDefinition, ComponentId, ComponentProvider, NussknackerVersion, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Sink, SinkFactory, WithCategories}
import pl.touk.nussknacker.engine.api.{MethodToInvoke, Service}
import pl.touk.nussknacker.engine.component.ComponentExtractorTest.largeMajorVersion
import pl.touk.nussknacker.engine.modelconfig.DefaultModelConfigLoader
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming
import pl.touk.nussknacker.test.ClassLoaderWithServices

import scala.collection.JavaConverters._
import scala.concurrent.Future

object ComponentExtractorTest {

  //in most tests we use "standard" NU version number, we want to make sure compatibility check works there
  val largeMajorVersion = 1024

}

class ComponentExtractorTest extends FunSuite with Matchers {

  private val loader = new DefaultModelConfigLoader

  test("should discover services") {
    val components = extractComponents[Service]("components" -> Map(
      "dynamicTest" -> Map("valueCount" -> 7),
      "auto" -> Map("disabled" -> true)
    ))
    components.services shouldBe (1 to 7).map(i => {
      val service = DynamicService(s"v$i")
      s"component-v$i" -> WithCategories(service, None, SingleComponentConfig.zero.copy(componentId = Some(ComponentId.create(s"component-v$i"))))
    }).toMap
  }

  test("should handle multiple providers") {
    val components = extractComponents[Service](
      "components" -> Map(
        "dynamic1" -> Map("providerType" -> "dynamicTest", "valueCount" -> 2),
        "dynamic2" -> Map("providerType" -> "dynamicTest", "componentPrefix" -> "t1-", "valueCount" -> 3),
        "auto" -> Map("disabled" -> true)
      ))

    components.services shouldBe ((1 to 2).map(i => {
      s"component-v$i" -> WithCategories(DynamicService(s"v$i"), None, SingleComponentConfig.zero.copy(componentId = Some(ComponentId.create(s"component-v$i"))))
    }) ++
      (1 to 3).map(i => {
        s"t1-component-v$i" -> WithCategories(DynamicService(s"v$i"), None, SingleComponentConfig.zero.copy(componentId = Some(ComponentId.create(s"t1-component-v$i"))))
      })).toMap
  }

  test("should detect duplicate config") {
    intercept[IllegalArgumentException] {
      extractComponents[Service](
        "components" -> Map(
          "dynamic1" -> Map("providerType" -> "dynamicTest", "valueCount" -> 1),
          "dynamic2" -> Map("providerType" -> "dynamicTest", "valueCount" -> 2)
        ))
    }.getMessage should include("component-v1")
  }

  test("should detect duplicated config inside same provider") {
    intercept[IllegalArgumentException] {
      extractComponents[Service](
        "components" -> Map(
          "dynamic1" -> Map("providerType" -> "sameNameSameComponentTypeProvider"),
        ))
    }.getMessage should include("component")
  }

  test("should discaver components with same name and different component type for same provider") {
    val components = extractComponents[Component](
      "components" -> Map(
        "dynamic1" -> Map("providerType" -> "sameNameDifferentComponentTypeProvider"),
        "auto" -> Map("disabled" -> true)
      ))
    components.services shouldBe Map("component" -> WithCategories(DynamicService("component"), None, SingleComponentConfig.zero.copy(componentId = Some(ComponentId.create("component")))))
    components.sinkFactories.size shouldBe 1
  }

  test("should skip disabled providers") {
    val components = extractComponents[Service](
      "components" -> Map(
        "dynamic1" -> Map("providerType" -> "dynamicTest", "disabled" -> true),
        "dynamic2" -> Map("providerType" -> "dynamicTest", "componentPrefix" -> "t1-", "valueCount" -> 1),
        "auto" -> Map("disabled" -> true)
      ))
    components.services shouldBe Map("t1-component-v1" -> WithCategories(DynamicService("v1"), None, SingleComponentConfig.zero.copy(componentId = Some(ComponentId.create("t1-component-v1")))))
  }

  test("should skip incompatible providers") {
    //see DynamicProvider.isCompatible
    val largeVersionNumber = new Semver(s"$largeMajorVersion.2.3")
    intercept[IllegalArgumentException] {
      extractComponents[Service](Map("components.dynamicTest.valueCount" -> 7),
        (cl: ClassLoader) => ComponentExtractor(cl, NussknackerVersion(largeVersionNumber)))
    }.getMessage should include(s"is not compatible with NussknackerVersion(${largeVersionNumber.toString})")
  }

  test("should load auto loadable component") {
    val components = extractComponents[Service]()
    val service = AutoService
    components.services shouldBe Map("auto-component" -> WithCategories(service, None, SingleComponentConfig.zero.copy(componentId = Some(ComponentId.create("auto-component")))))
  }

  test("should skip incompatible auto loadable providers") {
    //see DynamicProvider.isCompatible
    val largeVersionNumber = new Semver(s"$largeMajorVersion.2.3")
    intercept[IllegalArgumentException] {
      extractComponents[Service](Map.empty[String, Any], (cl: ClassLoader) => ComponentExtractor(cl, NussknackerVersion(largeVersionNumber)))
    }.getMessage should include(s"is not compatible with NussknackerVersion(${largeVersionNumber.toString})")
  }

  test("should load compatible provider when found compatible and incompatible implementations") {
    extractProvider(List(
      (classOf[ComponentProvider], classOf[DynamicProvider]),
      (classOf[ComponentProvider], classOf[PreviousVersionDynamicProviderDuplicate]),
    ), Map("components" -> Map(
      "dynamic1" -> Map("providerType" -> "dynamicTest"),
      "auto" -> Map("disabled" -> true)
    )))
  }

  test("should not throw when loaded duplicated providers but not used") {
    extractProvider(List(
      (classOf[ComponentProvider], classOf[DynamicProvider]),
      (classOf[ComponentProvider], classOf[DynamicProviderDuplicate]),
      (classOf[ComponentProvider], classOf[PreviousVersionDynamicProviderDuplicate]),
      (classOf[ComponentProvider], classOf[AnotherPreviousVersionDynamicProviderDuplicate]),
    ))
  }

  private def extractComponents[T <: Component](map: (String, Any)*): ComponentExtractor.ComponentsGroupedByType =
    extractComponents(map.toMap, ComponentExtractor(_))

  private def extractComponents[T <: Component](map: Map[String, Any], makeExtractor: ClassLoader => ComponentExtractor) = {
    ClassLoaderWithServices.withCustomServices(List(
      (classOf[ComponentProvider], classOf[DynamicProvider]),
      (classOf[ComponentProvider], classOf[SameNameSameComponentTypeProvider]),
      (classOf[ComponentProvider], classOf[SameNameDifferentComponentTypeProvider]),
      (classOf[ComponentProvider], classOf[AutoLoadedProvider])), getClass.getClassLoader) { cl =>
      val extractor = makeExtractor(cl)
      val resolved = loader.resolveInputConfigDuringExecution(fromMap(map.toSeq: _*), cl)
      extractor.extractComponents(ProcessObjectDependencies(resolved.config, DefaultNamespacedObjectNaming))
    }
  }

  private def extractProvider(providers: List[(Class[_], Class[_])], config: Map[String, Any] = Map()) = {
    ClassLoaderWithServices.withCustomServices(providers, getClass.getClassLoader) { cl =>
      val extractor = ComponentExtractor(cl)
      val resolved = loader.resolveInputConfigDuringExecution(fromMap(config.toSeq: _*), cl)
      extractor.extractComponents(ProcessObjectDependencies(resolved.config, DefaultNamespacedObjectNaming))
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

class DynamicProviderDuplicate extends DynamicProvider

class PreviousVersionDynamicProviderDuplicate extends DynamicProvider {
  override def isCompatible(version: NussknackerVersion): Boolean = false
}

class AnotherPreviousVersionDynamicProviderDuplicate extends DynamicProvider {
  override def isCompatible(version: NussknackerVersion): Boolean = false
}

class SameNameSameComponentTypeProvider extends ComponentProvider {

  override def providerName: String = "sameNameSameComponentTypeProvider"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    List(
      ComponentDefinition(s"component", DynamicService("component")),
      ComponentDefinition(s"component", DynamicService("component"))
    )
  }

  override def isCompatible(version: NussknackerVersion): Boolean = version.value.getMajor < ComponentExtractorTest.largeMajorVersion

}

class SameNameDifferentComponentTypeProvider extends ComponentProvider {

  override def providerName: String = "sameNameDifferentComponentTypeProvider"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    List(
      ComponentDefinition(s"component", DynamicService("component")),
      ComponentDefinition(s"component", SinkFactory.noParam(new Sink {}))
    )
  }

  override def isCompatible(version: NussknackerVersion): Boolean = version.value.getMajor < ComponentExtractorTest.largeMajorVersion

}

case class DynamicService(valueToReturn: String) extends Service {

  @MethodToInvoke
  def invoke(): Future[String] = {
    Future.successful(valueToReturn)
  }
}

class AutoLoadedProvider extends ComponentProvider {
  override def providerName: String = "auto"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = List(
    ComponentDefinition("auto-component", AutoService)
  )

  override def isCompatible(version: NussknackerVersion): Boolean = version.value.getMajor < ComponentExtractorTest.largeMajorVersion

  override def isAutoLoaded: Boolean = true
}

object AutoService extends Service {
  @MethodToInvoke
  def invoke(): Future[String] = {
    ???
  }
}
