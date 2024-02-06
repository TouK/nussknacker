package pl.touk.nussknacker.ui.api.helpers

import pl.touk.nussknacker.ui.api.helpers.TestData.Categories.TestCategory
import pl.touk.nussknacker.ui.api.helpers.TestData.Categories.TestCategory.{Category1, Category2}
import pl.touk.nussknacker.ui.api.helpers.TestData.ProcessingTypes.TestProcessingType.{Streaming, Streaming2}

object TestData {

  object ProcessingTypes {

    sealed trait TestProcessingType

    object TestProcessingType {
      case object Streaming  extends TestProcessingType
      case object Streaming2 extends TestProcessingType
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

  }

  object Categories {
    sealed trait TestCategory

    object TestCategory {
      case object Category1 extends TestCategory
      case object Category2 extends TestCategory

      implicit class CategoryStringify(category: TestCategory) {

        def stringify: String = category match {
          case Category1 => "Category1"
          case Category2 => "Category2"
        }

      }

    }

    val AllCategories: List[TestCategory] = List(Category1, Category2)
  }

}
