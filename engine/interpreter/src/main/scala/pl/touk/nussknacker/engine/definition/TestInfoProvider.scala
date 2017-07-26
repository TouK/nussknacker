package pl.touk.nussknacker.engine.definition

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.{MetaData, process}
import pl.touk.nussknacker.engine.api.process.{ProcessConfigCreator, SourceFactory, TestDataGenerator, WithCategories}
import pl.touk.nussknacker.engine.api.test.TestDataParser
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.graph.node.Source
import shapeless.syntax.typeable._

trait TestInfoProvider {

  def getTestingCapabilities(metaData: MetaData, source: Source) : TestingCapabilities

  def generateTestData(metaData: MetaData, source: Source, size: Int) : Option[Array[Byte]]

}

case class TestingCapabilities(canBeTested: Boolean, canGenerateTestData: Boolean)

trait ConfigCreatorTestInfoProvider extends TestInfoProvider {

  def configCreator: ProcessConfigCreator

  def processConfig: Config

  override def getTestingCapabilities(metaData: MetaData, source: Source) = {
    val canTest = sourceFactory(source).flatMap[TestDataParser[_]](_.testDataParser).isDefined
    val canGenerateData = prepareTestDataGenerator(metaData, source).isDefined
    TestingCapabilities(canBeTested = canTest, canGenerateTestData = canGenerateData)
  }

  private def sourceFactory(source: Source): Option[SourceFactory[_]] =
    configCreator.sourceFactories(processConfig).get(source.ref.typ).map(_.value)

  override def generateTestData(metaData: MetaData, source: Source, size: Int) =
    prepareTestDataGenerator(metaData, source).map(_.generateTestData(size))

  private def prepareTestDataGenerator(metaData: MetaData, source: Source) : Option[TestDataGenerator] =
    for {
      factory <- sourceFactory(source)
      definition = ObjectWithMethodDef(WithCategories(factory), ProcessObjectDefinitionExtractor.source)
      sourceObj = ProcessObjectFactory[process.Source[Any]](definition).create(metaData, source.ref.parameters)
      asTest <- sourceObj.cast[TestDataGenerator]
    } yield asTest
}