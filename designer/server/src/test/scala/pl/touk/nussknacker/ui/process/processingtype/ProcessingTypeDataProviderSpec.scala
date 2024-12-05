package pl.touk.nussknacker.ui.process.processingtype

import cats.effect.unsafe.implicits.global
import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.{Source, SourceFactory}
import pl.touk.nussknacker.engine.testing.{DeploymentManagerProviderStub, LocalModelData}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.test.utils.domain.TestFactory
import pl.touk.nussknacker.ui.UnauthorizedError
import pl.touk.nussknacker.ui.loadableconfig.DesignerRootConfig
import pl.touk.nussknacker.ui.process.processingtype.loader.LocalProcessingTypeDataLoader
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.RealLoggedUser

class ProcessingTypeDataProviderSpec extends AnyFunSuite with Matchers {

  test("allow to access to processing type data only users that has read access to associated category") {
    val provider = ProcessingTypeDataProvider(mockProcessingTypeData("foo", "bar"))

    val fooCategoryUser =
      RealLoggedUser("fooCategoryUser", "fooCategoryUser", Map("fooCategory" -> Set(Permission.Read)))

    provider.forProcessingType("foo")(fooCategoryUser)
    an[UnauthorizedError] shouldBe thrownBy {
      provider.forProcessingType("bar")(fooCategoryUser)
    }
    provider.all(fooCategoryUser).keys should contain theSameElementsAs List("foo")

    val mappedProvider = provider.mapValues(_ => ())
    mappedProvider.forProcessingType("foo")(fooCategoryUser)
    an[UnauthorizedError] shouldBe thrownBy {
      mappedProvider.forProcessingType("bar")(fooCategoryUser)
    }
    mappedProvider.all(fooCategoryUser).keys should contain theSameElementsAs List("foo")
  }

  private def mockProcessingTypeData(processingTypeName: String, processingTypeNames: String*) = {
    val allProcessingTypes = (processingTypeName :: processingTypeNames.toList).toSet
    val modelDependencies  = TestFactory.modelDependencies
    val loader = new LocalProcessingTypeDataLoader(
      modelData = allProcessingTypes.map { name =>
        name -> (
          s"${name}Category",
          LocalModelData(
            ConfigFactory.empty(),
            List(
              ComponentDefinition(s"${name}Component", SourceFactory.noParamUnboundedStreamFactory[Any](new Source {}))
            ),
            componentDefinitionExtractionMode = modelDependencies.componentDefinitionExtractionMode
          )
        )
      }.toMap,
      deploymentManagerProvider = new DeploymentManagerProviderStub
    )
    loader
      .loadProcessingTypeData(
        DesignerRootConfig.from(ConfigFactory.empty()),
        _ => modelDependencies,
        _ => TestFactory.deploymentManagerDependencies
      )
      .unsafeRunSync()
  }

}
