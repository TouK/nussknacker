package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.component.ComponentGroupName
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.definition.component._
import pl.touk.nussknacker.engine.definition.component.defaultconfig.DefaultsComponentGroupName
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.util.Implicits.RichTupleList
import pl.touk.nussknacker.restmodel.definition._
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.collection.immutable.ListMap

class ComponentGroupsPreparer(componentsGroupMapping: Map[ComponentGroupName, Option[ComponentGroupName]]) {

  def prepareComponentGroups(
      user: LoggedUser,
      definitions: ModelDefinition[ComponentStaticDefinition],
      processCategoryService: ProcessCategoryService,
      processingType: ProcessingType
  ): List[ComponentGroup] = {
    ComponentNodeTemplatePreparer
      .componentNodeTemplatesWithGroupNames(user, definitions, processCategoryService, processingType)
      .toGroupedMap
      .toList
      .map { case (originalGroupName, nodeTemplates) =>
        val nonHiddenMappedGroupName = componentsGroupMapping.getOrElse(originalGroupName, Some(originalGroupName))
        (originalGroupName, nonHiddenMappedGroupName, nodeTemplates)
      }
      .flatMap { case (originalGroupName, nonHiddenMappedGroupName, nodeTemplates) =>
        nonHiddenMappedGroupName.map { mappedGroupName =>
          (originalGroupName, mappedGroupName, nodeTemplates)
        }
      }
      // We sort mostly by original group name because
      .sortBy {
        case (
              DefaultsComponentGroupName.SourcesGroupName | DefaultsComponentGroupName.FragmentsDefinitionGroupName,
              mappedGroupName,
              _
            ) =>
          (0, mappedGroupName.toLowerCase)
        case (DefaultsComponentGroupName.BaseGroupName, mappedGroupName, _) =>
          (1, mappedGroupName.toLowerCase)
        case (
              DefaultsComponentGroupName.ServicesGroupName | DefaultsComponentGroupName.OptionalEndingCustomGroupName |
              DefaultsComponentGroupName.SinksGroupName,
              mappedGroupName,
              _
            ) =>
          (3, mappedGroupName.toLowerCase)
        case (_, mappedGroupName, _) =>
          // We put everything else in the middle
          (2, mappedGroupName.toLowerCase)
      }
      .map { case (_, mappedGroupName, nodeTemplates) =>
        (mappedGroupName, nodeTemplates)
      }
      // We need to merge node templates that originally where in the other group but after mapping are in the same group
      .foldLeft(ListMap.empty[ComponentGroupName, List[ComponentNodeTemplate]]) {
        case (acc, (groupName, nodeTemplates)) =>
          val mergedNodeTemplates = acc.getOrElse(groupName, List.empty) ++ nodeTemplates
          acc + (groupName -> mergedNodeTemplates)
      }
      .toList
      .map { case (groupName, nodeTemplates) =>
        ComponentGroup(groupName, nodeTemplates.sortBy(_.label.toLowerCase))
      }
  }

}
