package pl.touk.nussknacker.test.utils

import com.typesafe.config.{Config, ConfigObject}

import scala.jdk.CollectionConverters._

private[test] class DesignerTestConfigValidator(config: Config) {

  def validateTestDataWithDesignerConfFile(testProcessingTypesWithCategories: Map[String, String]): Unit = {
    if (configProcessingTypesWithCategories != testProcessingTypesWithCategories) {
      throw new IllegalStateException(
        s"""In configuration, we can find the following (processing type, category) pairs: [${configProcessingTypesWithCategories.toList.sorted}]
           |The passed pairs are: [${testProcessingTypesWithCategories.toList.sorted}].
           |There is inconsistency in these two lists.
           |""".stripMargin
      )
    }

  }

  private lazy val configProcessingTypesWithCategories = {
    val scenarioTypeConfigObject = config.getObject("scenarioTypes")
    val configProcessingTypes    = scenarioTypeConfigObject.keySet().asScala.toSet
    configProcessingTypes.map { processingType =>
      val categoryOfTheProcessingType = scenarioTypeConfigObject
        .get(processingType)
        .asInstanceOf[ConfigObject]
        .get("category")
        .unwrapped()
        .asInstanceOf[String]
      (processingType, categoryOfTheProcessingType)
    }.toMap
  }

}
