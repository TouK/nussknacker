package pl.touk.nussknacker.engine.definition

import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.compile.validationHelpers.{GenericParametersSource, GenericParametersSourceNoGenerate, GenericParametersSourceNoTestSupport}
import pl.touk.nussknacker.engine.api.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.api.graph.node.Source
import pl.touk.nussknacker.engine.api.graph.source.SourceRef
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.spel.Implicits._

class ModelDataTestInfoProviderSpec extends FunSuite with Matchers {

  private val modelData = LocalModelData(ConfigFactory.empty(), new EmptyProcessConfigCreator {
    override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {
      Map(
        "genericSource" -> WithCategories(new GenericParametersSource),
        "genericSourceNoSupport" -> WithCategories(new GenericParametersSourceNoTestSupport),
        "genericSourceNoGenerate" -> WithCategories(new GenericParametersSourceNoGenerate)
      )
    }
  })

  private val testInfoProvider = new ModelDataTestInfoProvider(modelData)

  test("Should detect capabilities for generic transformation source: with support and generate test data") {

    val capabilities = testInfoProvider.getTestingCapabilities(MetaData("id", StreamMetaData()), Source("id1", SourceRef("genericSource",
      List(Parameter("par1", "'a,b'"), Parameter("lazyPar1", "11"), Parameter("a", "''"), Parameter("b", "'ter'"))
    )))

    capabilities shouldBe TestingCapabilities(canBeTested = true, canGenerateTestData = true)
  }

  test("Should detect capabilities for generic transformation source: with support, no generate test data") {

    val capabilities = testInfoProvider.getTestingCapabilities(MetaData("id", StreamMetaData()), Source("id1", SourceRef("genericSourceNoGenerate",
      List(Parameter("par1", "'a,b'"), Parameter("lazyPar1", "11"), Parameter("a", "''"), Parameter("b", "'ter'"))
    )))

    capabilities shouldBe TestingCapabilities(canBeTested = true, canGenerateTestData = false)
  }

  test("Should detect capabilities for generic transformation source: no support, no generate test data") {

    val capabilities = testInfoProvider.getTestingCapabilities(MetaData("id", StreamMetaData()), Source("id1", SourceRef("genericSourceNoSupport",
      List(Parameter("par1", "'a,b'"), Parameter("lazyPar1", "11"), Parameter("a", "''"), Parameter("b", "'ter'"))
    )))

    capabilities shouldBe TestingCapabilities(canBeTested = false, canGenerateTestData = false)
  }
}
