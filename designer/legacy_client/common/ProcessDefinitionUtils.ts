import {flatMap} from "lodash"
import {Category, Component, ComponentGroup, ProcessDefinitionData} from "../types"

function getCategoryComponents(components: Component[], category: Category) {
  return components.filter(component => component.categories.includes(category))
}

export function getCategoryComponentGroups(processDefinitionData: ProcessDefinitionData, category: Category): ComponentGroup[] {
  return (processDefinitionData.componentGroups || []).map(group => {
    return {
      ...group,
      components: getCategoryComponents(group.components, category),
    }
  })
}

export function getFlatCategoryComponents(processDefinitionData: ProcessDefinitionData, category: Category): Component[] {
  const componentGroups = getCategoryComponentGroups(processDefinitionData, category)
  return flatMap(componentGroups, group => group.components)
}

export function filterComponentsByLabel(filters: string[]): (componentGroup: ComponentGroup) => ComponentGroup {
  const predicate = ({label}: Component) => filters.every(searchText => label.toLowerCase().includes(searchText))
  return (componentGroup: ComponentGroup): ComponentGroup => ({
    ...componentGroup,
    components: componentGroup.components.filter(predicate),
  })
}
