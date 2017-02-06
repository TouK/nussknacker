package pl.touk.esp.engine.definition

import com.typesafe.config.Config
import pl.touk.esp.engine.api.process._
import pl.touk.esp.engine.api.test.TestDataParser
import pl.touk.esp.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.esp.engine.graph.EspProcess
import shapeless.syntax.typeable._

trait TestInfoProvider {

  def getTestingCapabilities(espProcess: EspProcess) : TestingCapabilities

  def generateTestData(espProcess: EspProcess, size: Int) : Option[Array[Byte]]

}

case class TestingCapabilities(canBeTested: Boolean, canGenerateTestData: Boolean)

trait ConfigCreatorTestInfoProvider extends TestInfoProvider {

  def configCreator: ProcessConfigCreator

  def processConfig: Config

  override def getTestingCapabilities(espProcess: EspProcess) = {
    val canTest = sourceFactory(espProcess).flatMap[TestDataParser[_]](_.testDataParser).isDefined
    val canGenerateData = prepareTestDataGenerator(espProcess).isDefined
    TestingCapabilities(canBeTested = canTest, canGenerateTestData = canGenerateData)
  }

  def sourceFactory(espProcess: EspProcess): Option[SourceFactory[_]] =
    configCreator.sourceFactories(processConfig).get(espProcess.root.data.ref.typ).map(_.value)

  override def generateTestData(espProcess: EspProcess, size: Int) =
    prepareTestDataGenerator(espProcess).map(_.generateTestData(size))

  private def prepareTestDataGenerator(espProcess: EspProcess) : Option[TestDataGenerator] =
    for {
      factory <- sourceFactory(espProcess)
      definition = ObjectWithMethodDef(WithCategories(factory), ProcessObjectDefinitionExtractor.source)
      source = ProcessObjectFactory[Source[Any]](definition).create(espProcess.metaData, espProcess.root.data.ref.parameters)
      asTest <- source.cast[TestDataGenerator]
    } yield asTest
}