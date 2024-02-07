package pl.touk.nussknacker.ui.api.helpers

import com.typesafe.config.ConfigObject
import enumeratum.{Enum, EnumEntry}
import pl.touk.nussknacker.ui.api.helpers.TestData.Categories.TestCategory
import pl.touk.nussknacker.ui.api.helpers.TestData.Categories.TestCategory.{Category1, Category2}
import pl.touk.nussknacker.ui.api.helpers.TestData.ProcessingTypes.TestProcessingType
import pl.touk.nussknacker.ui.api.helpers.TestData.ProcessingTypes.TestProcessingType.{Streaming, Streaming2}
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion

import scala.jdk.CollectionConverters._

// These test data (processing types and categories) should be used in context of end-to-end tests. Make sure that the
// processing types and categories correspond to the ones defined in the designer.conf file
object TestData {

  object ProcessingTypes {

    sealed trait TestProcessingType extends EnumEntry

    object TestProcessingType extends Enum[TestProcessingType] {
      case object Streaming  extends TestProcessingType
      case object Streaming2 extends TestProcessingType

      override val values: IndexedSeq[TestProcessingType] = findValues

      validateTestDataWithDesignerConfFile()
    }

    implicit class ProcessingTypeStringify(processingType: TestProcessingType) {

      def stringify: String = processingType match {
        case Streaming  => "streaming"
        case Streaming2 => "streaming2"
      }

    }

    def processingTypeBy(category: TestCategory): TestProcessingType = {
      category match {
        case Category1 => Streaming
        case Category2 => Streaming2
      }
    }

    validateTestDataWithDesignerConfFile()
  }

  object Categories {
    sealed trait TestCategory extends EnumEntry

    object TestCategory extends Enum[TestCategory] {
      case object Category1 extends TestCategory
      case object Category2 extends TestCategory

      override val values: IndexedSeq[TestCategory] = findValues

      implicit class CategoryStringify(category: TestCategory) {

        def stringify: String = category match {
          case Category1 => "Category1"
          case Category2 => "Category2"
        }

      }

      validateTestDataWithDesignerConfFile()
    }

  }

  private def validateTestDataWithDesignerConfFile(): Unit = {
    // testing processing types consistency
    val scenarioTypeConfigObject = ConfigWithScalaVersion.TestsConfig
      .getObject("scenarioTypes")

    val processingTypes                = scenarioTypeConfigObject.keySet().asScala.toSet
    val stringifiedTestProcessingTypes = TestProcessingType.values.map(_.stringify).toSet

    if (processingTypes != stringifiedTestProcessingTypes) {
      throw new IllegalStateException(
        s"""
           |${TestProcessingType.getClass.getName} contains the following processing types: [${stringifiedTestProcessingTypes
            .mkString(",")}]
           |but in the TestConfig we have declared the following ones: [${processingTypes.mkString(
            ","
          )}]. In both places
           |we should have the same set of processing types.""".stripMargin
      )
    }

    // testing categories consistency
    val processingTypesWithTheirCategories = processingTypes.map { processingType =>
      val categoryOfTheProcessingType = scenarioTypeConfigObject
        .get(processingType)
        .asInstanceOf[ConfigObject]
        .get("category")
        .unwrapped()
        .asInstanceOf[String]
      (processingType, categoryOfTheProcessingType)
    }

    val allUsedCategories         = processingTypesWithTheirCategories.map { case (_, category) => category }
    val stringifiedTestCategories = TestCategory.values.map(_.stringify).toSet

    if (allUsedCategories != stringifiedTestCategories) {
      throw new IllegalStateException(
        s"""
           |${TestCategory.getClass.getName} contains the following categories: [${stringifiedTestCategories.mkString(
            ","
          )}]
           |but in the TestConfig we used the following ones: [${allUsedCategories.mkString(",")}]. In both places
           |we should have the same set of categories.""".stripMargin
      )
    }

    // testing processing type -> category mapping consistency
    processingTypesWithTheirCategories.foreach { case (processingType, category) =>
      val foundTestCategoryOpt = TestCategory.values.find(_.stringify == category)
      foundTestCategoryOpt match {
        case Some(foundTestCategory) =>
          val testProcessingTypeOfTestCategory = ProcessingTypes.processingTypeBy(foundTestCategory)
          if (testProcessingTypeOfTestCategory.stringify != processingType) {
            throw new IllegalStateException(
              s"""
                 |`processingTypeBy($foundTestCategory)` call returns "${testProcessingTypeOfTestCategory.stringify}"
                 |but in the TestConfig, category [$category] in defined within [$processingType] processing type.
                 |""".stripMargin
            )
          }
        case None =>
          throw new IllegalStateException(s"""Cannot find [$category] in ${TestCategory.getClass}""")
      }
    }
  }

}
