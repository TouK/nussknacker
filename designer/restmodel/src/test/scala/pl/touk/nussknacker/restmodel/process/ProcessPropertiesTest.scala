package pl.touk.nussknacker.restmodel.process

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.MetaDataTestData._
import org.scalatest.prop.TableDrivenPropertyChecks._
import pl.touk.nussknacker.engine.api.{ProcessAdditionalFields, TypeSpecificData}
import pl.touk.nussknacker.restmodel.displayedgraph.ProcessProperties

class ProcessPropertiesTest extends AnyFunSuite with Matchers {

  private val id = "Id"

  test("convert type specific data to process properties and back") {
    forAll(fullMetaDataCases) {
      (properties: Map[String, String], metaDataName: String, typeSpecificData: TypeSpecificData) => {
        // meta data => process properties
        val processProperties = ProcessProperties(
          typeSpecificProperties = typeSpecificData,
          additionalFields = None)

        processProperties.propertiesType shouldBe metaDataName
        processProperties.additionalFields.properties shouldBe properties

        // process properties => meta data
        val metaData = processProperties.toMetaData(id)
        metaData.typeSpecificData shouldBe typeSpecificData
        metaData.additionalFields shouldBe None
      }
    }
  }

  test("convert type specific data with other properties by joining to process properties and back") {
    forAll(fullMetaDataCases) {
      (properties: Map[String, String], metaDataName: String, typeSpecificData: TypeSpecificData) => {
        // meta data => process properties
        val processProperties = ProcessProperties(
          typeSpecificProperties = typeSpecificData,
          additionalFields = Some(ProcessAdditionalFields(None, genericProperties)))

        processProperties.propertiesType shouldBe metaDataName
        processProperties.additionalFields.properties shouldBe properties ++ genericProperties

        // process properties => meta data
        val metaData = processProperties.toMetaData(id)
        metaData.typeSpecificData shouldBe typeSpecificData
        metaData.additionalFields shouldBe Some(ProcessAdditionalFields(None, genericProperties))
      }
    }
  }

  // We write the invalid properties in the additionalProperties field and set null in corresponding fields in TypeSpecificData.
  test("convert empty type specific data with invalid overwriting properties to process properties and back") {
    forAll(invalidMetaDataCases) {
      (overwritingInvalidProperties: Map[String, String], metaDataName: String, emptyTypeSpecificData: TypeSpecificData) => {
        // meta data => process properties
        val processProperties = ProcessProperties(
          typeSpecificProperties = emptyTypeSpecificData,
          additionalFields = Some(ProcessAdditionalFields(None, overwritingInvalidProperties)))

        processProperties.propertiesType shouldBe metaDataName
        processProperties.additionalFields.properties shouldBe overwritingInvalidProperties

        // process properties => meta data
        val metaData = processProperties.toMetaData(id)
        metaData.typeSpecificData shouldBe emptyTypeSpecificData
        metaData.additionalFields shouldBe Some(ProcessAdditionalFields(None, overwritingInvalidProperties))
      }
    }
  }

  // If there is a value with the same name in additionalProperties and TypeSpecificData, we overwrite these additional
  // properties when merging during the conversion metadata => processProperties. This could happen if a scenario had
  // additional properties with the same name as type specific properties.
  test("convert full type specific data with invalid properties to process properties and back") {

    val fullTypeSpecificDataWithInvalidPropertiesCases = Table(
      ("invalidProperties", "metaDataName", "typeSpecificData"),
      (flinkInvalidProperties, flinkMetaDataName, flinkFullTypeData),
      (liteStreamInvalidProperties, liteStreamMetaDataName, liteStreamFullTypeData)
    )

    forAll(fullTypeSpecificDataWithInvalidPropertiesCases) {
      (invalidProperties: Map[String, String], metaDataName: String, fullTypeSpecificData: TypeSpecificData) => {
        // meta data => process properties
        val processProperties = ProcessProperties(
          typeSpecificProperties = fullTypeSpecificData,
          additionalFields = Some(ProcessAdditionalFields(None, invalidProperties)))

        processProperties.propertiesType shouldBe metaDataName
        processProperties.additionalFields.properties shouldBe fullTypeSpecificData.toProperties

        // process properties => meta data
        val metaData = processProperties.toMetaData(id)
        metaData.typeSpecificData shouldBe fullTypeSpecificData
        metaData.additionalFields shouldBe None
      }
    }
  }

}
