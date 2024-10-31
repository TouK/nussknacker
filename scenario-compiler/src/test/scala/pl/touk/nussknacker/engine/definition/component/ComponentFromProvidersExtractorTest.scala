package pl.touk.nussknacker.engine.definition.component

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import net.ceedubs.ficus.Ficus._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.semver4j.Semver
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{MethodToInvoke, Service}
import pl.touk.nussknacker.engine.definition.component.ComponentFromProvidersExtractorTest.largeMajorVersion
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.modelconfig.{ComponentsUiConfig, DefaultModelConfigLoader}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.test.ClassLoaderWithServices

import scala.concurrent.Future
import scala.jdk.CollectionConverters._

object ComponentFromProvidersExtractorTest {

  // in most tests we use "standard" NU version number, we want to make sure compatibility check works there
  val largeMajorVersion = 1024

}

class ComponentFromProvidersExtractorTest extends AnyFunSuite with Matchers {

  private val loader = new DefaultModelConfigLoader(_ => true)

  test("should discover services") {
    val components = extractComponents(
      "components" -> Map(
        "dynamicTest" -> Map("valueCount" -> 7),
        "auto"        -> Map("disabled" -> true)
      )
    )
    components should have size 7
    components.map(_.name) should contain theSameElementsAs (1 to 7).map(i => s"component-v$i")
    components.map(_.component) should contain theSameElementsAs (1 to 7).map(i => DynamicService(s"v$i"))
  }

  test("should handle multiple providers") {
    val components = extractComponents(
      "components" -> Map(
        "dynamic1" -> Map("providerType" -> "dynamicTest", "valueCount" -> 2),
        "dynamic2" -> Map("providerType" -> "dynamicTest", "componentPrefix" -> "t1-", "valueCount" -> 3),
        "auto"     -> Map("disabled" -> true)
      )
    )
    components should have size 5
    val expectedNames = (1 to 2).map(i => s"component-v$i") ++ (1 to 3).map(i => s"t1-component-v$i")
    components.map(_.name) should contain theSameElementsAs expectedNames
    val expectedServices = (1 to 2).map(i => DynamicService(s"v$i")) ++ (1 to 3).map(i => DynamicService(s"v$i"))
    components.map(_.component) should contain theSameElementsAs expectedServices
  }

  test("should discover components with same name and different component type for same provider") {
    val components = extractComponents(
      "components" -> Map(
        "dynamic1" -> Map("providerType" -> "sameNameDifferentComponentTypeProvider"),
        "auto"     -> Map("disabled" -> true)
      )
    )

    components.size shouldBe 2
    components.map(_.name) shouldBe List("component", "component")
    val implementations = components.map(_.component)
    implementations.head shouldBe a[Service]
    implementations(1) shouldBe a[SinkFactory]
  }

  test("should skip disabled providers") {
    val components = extractComponents(
      "components" -> Map(
        "dynamic1" -> Map("providerType" -> "dynamicTest", "disabled" -> true),
        "dynamic2" -> Map("providerType" -> "dynamicTest", "componentPrefix" -> "t1-", "valueCount" -> 1),
        "auto"     -> Map("disabled" -> true)
      )
    )
    components should have size 1
    components.map(_.name).head shouldBe "t1-component-v1"
  }

  test("should skip incompatible providers") {
    // see DynamicProvider.isCompatible
    val largeVersionNumber = new Semver(s"$largeMajorVersion.2.3")
    intercept[IllegalArgumentException] {
      extractComponents(
        Map("components.dynamicTest.valueCount" -> 7),
        (cl: ClassLoader) =>
          new ComponentsFromProvidersExtractor(
            cl,
            _ => true,
            NussknackerVersion(largeVersionNumber)
          )
      )
    }.getMessage should include(s"is not compatible with NussknackerVersion(${largeVersionNumber.toString})")
  }

  test("should load auto loadable component") {
    val components = extractComponents()
    components should have size 1
    val component = components.head
    component.name shouldBe "auto-component"
    component.component shouldBe AutoService
  }

  test("should skip incompatible auto loadable providers") {
    // see DynamicProvider.isCompatible
    val largeVersionNumber = new Semver(s"$largeMajorVersion.2.3")
    intercept[IllegalArgumentException] {
      extractComponents(
        Map.empty[String, Any],
        (cl: ClassLoader) => new ComponentsFromProvidersExtractor(cl, _ => true, NussknackerVersion(largeVersionNumber))
      )
    }.getMessage should include(s"is not compatible with NussknackerVersion(${largeVersionNumber.toString})")
  }

  test("should load compatible provider when found compatible and incompatible implementations") {
    extractProvider(
      List(
        (classOf[ComponentProvider], classOf[DynamicProvider]),
        (classOf[ComponentProvider], classOf[PreviousVersionDynamicProviderDuplicate]),
      ),
      Map(
        "components" -> Map(
          "dynamic1" -> Map("providerType" -> "dynamicTest"),
          "auto"     -> Map("disabled" -> true)
        )
      )
    )
  }

  test("should not throw when loaded duplicated providers but not used") {
    extractProvider(
      List(
        (classOf[ComponentProvider], classOf[DynamicProvider]),
        (classOf[ComponentProvider], classOf[DynamicProviderDuplicate]),
        (classOf[ComponentProvider], classOf[PreviousVersionDynamicProviderDuplicate]),
        (classOf[ComponentProvider], classOf[AnotherPreviousVersionDynamicProviderDuplicate]),
      )
    )
  }

  private def extractComponents(
      componentsConfig: (String, Any)*
  ): List[ComponentDefinitionWithImplementation] =
    extractComponents(
      componentsConfig.toMap,
      ComponentsFromProvidersExtractor(_, _ => true)
    ).components

  private def extractComponents(
      componentsConfig: Map[String, Any],
      makeExtractor: ClassLoader => ComponentsFromProvidersExtractor
  ) = {
    ClassLoaderWithServices.withCustomServices(
      List(
        (classOf[ComponentProvider], classOf[DynamicProvider]),
        (classOf[ComponentProvider], classOf[SameNameSameComponentTypeProvider]),
        (classOf[ComponentProvider], classOf[SameNameDifferentComponentTypeProvider]),
        (classOf[ComponentProvider], classOf[AutoLoadedProvider])
      ),
      getClass.getClassLoader
    ) { cl =>
      val extractor = makeExtractor(cl)
      val resolved =
        loader.resolveInputConfigDuringExecution(ConfigWithUnresolvedVersion(fromMap(componentsConfig.toSeq: _*)), cl)
      extractor.extractComponents(
        ProcessObjectDependencies.withConfig(resolved.config),
        ComponentsUiConfig.Empty,
        id => DesignerWideComponentId(id.toString),
        Map.empty,
        ComponentDefinitionExtractionMode.FinalDefinition
      )
    }
  }

  private def extractProvider(providers: List[(Class[_], Class[_])], config: Map[String, Any] = Map()) = {
    ClassLoaderWithServices.withCustomServices(providers, getClass.getClassLoader) { cl =>
      val extractor = ComponentsFromProvidersExtractor(cl, _ => true)
      val resolved =
        loader.resolveInputConfigDuringExecution(ConfigWithUnresolvedVersion(fromMap(config.toSeq: _*)), cl)
      extractor.extractComponents(
        ProcessObjectDependencies.withConfig(resolved.config),
        ComponentsUiConfig.Empty,
        id => DesignerWideComponentId(id.toString),
        Map.empty,
        ComponentDefinitionExtractionMode.FinalDefinition
      )
    }
  }

  private def fromMap(map: (String, Any)*): Config = ConfigFactory.parseMap(map.toMap.mapValuesNow {
    case map: Map[String, Any] @unchecked => fromMap(map.toSeq: _*).root()
    case other                            => other
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

  override def isCompatible(version: NussknackerVersion): Boolean =
    version.value.getMajor < ComponentFromProvidersExtractorTest.largeMajorVersion

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

  override def isCompatible(version: NussknackerVersion): Boolean =
    version.value.getMajor < ComponentFromProvidersExtractorTest.largeMajorVersion

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

  override def isCompatible(version: NussknackerVersion): Boolean =
    version.value.getMajor < ComponentFromProvidersExtractorTest.largeMajorVersion

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

  override def isCompatible(version: NussknackerVersion): Boolean =
    version.value.getMajor < ComponentFromProvidersExtractorTest.largeMajorVersion

  override def isAutoLoaded: Boolean = true
}

object AutoService extends Service {

  @MethodToInvoke
  def invoke(): Future[String] = {
    ???
  }

}
