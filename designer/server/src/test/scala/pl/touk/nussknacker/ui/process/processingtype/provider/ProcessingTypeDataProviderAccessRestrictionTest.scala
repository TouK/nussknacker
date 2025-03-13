package pl.touk.nussknacker.ui.process.processingtype.provider

import cats.effect.unsafe.implicits.global
import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.{ConfigWithUnresolvedVersion, ProcessingTypeConfig}
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.test.mock.WithTestDeploymentManagerClassLoader
import pl.touk.nussknacker.test.utils.domain.{TestFactory, TestProcessingTypeDataProviderFactory}
import pl.touk.nussknacker.ui.UnauthorizedError
import pl.touk.nussknacker.ui.configloader.ProcessingTypeConfigs
import pl.touk.nussknacker.ui.process.processingtype.{ModelClassLoaderDependencies, ModelClassLoaderProvider}
import pl.touk.nussknacker.ui.security.api.RealLoggedUser

class ProcessingTypeDataProviderAccessRestrictionTest
    extends AnyFunSuite
    with WithTestDeploymentManagerClassLoader
    with Matchers {

  private val modelClasspath: List[String] = {
    List(
      s"./defaultModel/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/defaultModel.jar",
      s"./engine/flink/components/base-unbounded/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/flinkBaseUnbounded.jar",
    )
  }

  test("allow to access to processing type data only users that has read access to associated category") {
    val provider = mockProcessingTypeDataProvider("foo", "bar")

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

  private def mockProcessingTypeDataProvider(processingTypeName: String, processingTypeNames: String*) = {
    val allProcessingTypes = (processingTypeName :: processingTypeNames.toList).toSet
    val modelDependencies  = TestFactory.modelDependencies

    val processingTypeConfigs = ProcessingTypeConfigs(allProcessingTypes.toList.map { processingType =>
      processingType -> ProcessingTypeConfig(
        deploymentManagerType = "stub",
        engineSetupName = None,
        classPath = modelClasspath,
        deploymentConfig = ConfigFactory.empty(),
        modelConfig = ConfigWithUnresolvedVersion(ConfigFactory.empty()),
        category = s"${processingType}Category"
      )
    }.toMap)
    TestProcessingTypeDataProviderFactory.create(
      processingTypeConfigs = processingTypeConfigs,
      modelClassLoaderProvider = ModelClassLoaderProvider(
        allProcessingTypes.map(_ -> ModelClassLoaderDependencies(modelClasspath, workingDirectoryOpt = None)).toMap,
        deploymentManagersClassLoader
      ),
      modelDependencies = modelDependencies,
      deploymentManagersClassLoader = deploymentManagersClassLoader,
      deploymentManagerDependencies = TestFactory.deploymentManagerDependencies,
      dbRef = None
    )
  }

}
