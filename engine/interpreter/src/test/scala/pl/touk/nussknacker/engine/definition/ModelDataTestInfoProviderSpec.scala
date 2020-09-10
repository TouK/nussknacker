package pl.touk.nussknacker.engine.definition

import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.compile.validationHelpers.GenericParametersSource
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.node.Source
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.spel.Implicits._

class ModelDataTestInfoProviderSpec extends FunSuite with Matchers {

  private val modelData = LocalModelData(ConfigFactory.empty(), new EmptyProcessConfigCreator {
    override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {
      Map("genericSource" -> WithCategories(GenericParametersSource))
    }
  })

  private val testInfoProvider = new ModelDataTestInfoProvider(modelData)


  test("Should detect capabilities for generic transformation source") {

    val capabilities = testInfoProvider.getTestingCapabilities(MetaData("id", StreamMetaData()), Source("id1", SourceRef("genericSource",
      List(Parameter("par1", "'a,b'"), Parameter("lazyPar1", "11"), Parameter("a", "''"), Parameter("b", "'ter'"))
    )))

    capabilities shouldBe TestingCapabilities(canBeTested = false, canGenerateTestData = true)
  }
}
