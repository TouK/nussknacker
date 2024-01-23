package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentInfo}
import pl.touk.nussknacker.engine.definition.component._
import pl.touk.nussknacker.engine.definition.component.defaultconfig.DefaultsComponentGroupName
import pl.touk.nussknacker.engine.util.Implicits.RichTupleList
import pl.touk.nussknacker.restmodel.definition._

import scala.collection.immutable.ListMap

object ComponentGroupsPreparer {

  def prepareComponentGroups(components: Map[ComponentInfo, ComponentWithStaticDefinition]): List[UIComponentGroup] = {
    ComponentNodeTemplatePreparer
      .componentNodeTemplatesWithGroupNames(components)
      .map(templateWithGroups =>
        (templateWithGroups.originalGroupName, templateWithGroups.mappedGroupName) -> templateWithGroups.nodeTemplate
      )
      .toGroupedMap
      .toList
      // We sort based on the original group name. It might be tricky when someone instead of using component group
      // mapping feature, would override component group for component which is source or ending component.
      // Maybe instead of sorting based on the original group name we should sort based on rules likes:
      // group contains only (or some?) components that are sources. The same for ending components.
      // What about base components in this case?
      .sortBy {
        case (
              (
                DefaultsComponentGroupName.SourcesGroupName | DefaultsComponentGroupName.FragmentsDefinitionGroupName,
                mappedGroupName
              ),
              _
            ) =>
          (0, mappedGroupName.toLowerCase)
        case ((DefaultsComponentGroupName.BaseGroupName, mappedGroupName), _) =>
          (1, mappedGroupName.toLowerCase)
        case (
              (
                DefaultsComponentGroupName.ServicesGroupName |
                DefaultsComponentGroupName.OptionalEndingCustomGroupName | DefaultsComponentGroupName.SinksGroupName,
                mappedGroupName
              ),
              _
            ) =>
          (3, mappedGroupName.toLowerCase)
        case ((_, mappedGroupName), _) =>
          // We put everything else in the middle
          (2, mappedGroupName.toLowerCase)
      }
      .map { case ((_, mappedGroupName), nodeTemplates) =>
        (mappedGroupName, nodeTemplates)
      }
      // We need to merge node templates that originally where in the other group but after mapping are in the same group
      .foldLeft(ListMap.empty[ComponentGroupName, List[UIComponentNodeTemplate]]) {
        case (acc, (groupName, nodeTemplates)) =>
          val mergedNodeTemplates = acc.getOrElse(groupName, List.empty) ++ nodeTemplates
          acc + (groupName -> mergedNodeTemplates)
      }
      .toList
      .map { case (groupName, nodeTemplates) =>
        UIComponentGroup(groupName, nodeTemplates.sortBy(_.label.toLowerCase))
      }
  }

}
