package pl.touk.nussknacker.restmodel.process

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import pl.touk.nussknacker.engine.api.{ProcessAdditionalFields, TypeSpecificData}
import pl.touk.nussknacker.engine.api.TypeSpecificDataTestData.{fullMetaDataCases, nonTypeSpecificProperties}
import pl.touk.nussknacker.engine.api.graph.ProcessProperties
import pl.touk.nussknacker.engine.api.process.ProcessName

class ProcessPropertiesTest extends AnyFunSuite with Matchers {

  private val id = "testId"

  test("construct ProcessProperties from TypeSpecificData") {
    forAll(fullMetaDataCases) {
      (fullProperties: Map[String, String], metaDataName: String, typeSpecificData: TypeSpecificData) =>
        {
          val processProperties = ProcessProperties(typeSpecificData)
          processProperties shouldBe ProcessProperties(
            ProcessAdditionalFields(None, typeSpecificData.toMap, metaDataName)
          )
          processProperties.typeSpecificProperties shouldBe typeSpecificData
          processProperties.additionalFields.properties shouldBe fullProperties
        }
    }
  }

  test("convert ProcessProperties to MetaData") {
    forAll(fullMetaDataCases) {
      (fullProperties: Map[String, String], metaDataName: String, typeSpecificData: TypeSpecificData) =>
        {
          val metaData = ProcessProperties(typeSpecificData).toMetaData(ProcessName(id))
          metaData.typeSpecificData shouldBe typeSpecificData
          metaData.additionalFields shouldBe ProcessAdditionalFields(None, fullProperties, metaDataName)
        }
    }
  }

  test("construct ProcessProperties from TypeSpecificData and scenario properties") {
    forAll(fullMetaDataCases) {
      (fullProperties: Map[String, String], metaDataName: String, typeSpecificData: TypeSpecificData) =>
        {
          val processProperties = ProcessProperties(
            ProcessAdditionalFields(None, typeSpecificData.toMap ++ nonTypeSpecificProperties, metaDataName)
          )
          processProperties.typeSpecificProperties shouldBe typeSpecificData
          processProperties.additionalFields.properties shouldBe fullProperties ++ nonTypeSpecificProperties
        }
    }
  }

}
