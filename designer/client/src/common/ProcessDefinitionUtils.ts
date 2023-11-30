import { flatMap } from "lodash";
import { Component, ComponentGroup, ProcessDefinitionData } from "../types";

export function getFlatCategoryComponents(processDefinitionData: ProcessDefinitionData): Component[] {
    return flatMap(processDefinitionData.componentGroups, (group) => group.components);
}

export function filterComponentsByLabel(filters: string[]): (componentGroup: ComponentGroup) => ComponentGroup {
    const predicate = ({ label }: Component) => filters.every((searchText) => label.toLowerCase().includes(searchText));
    return (componentGroup: ComponentGroup): ComponentGroup => ({
        ...componentGroup,
        components: componentGroup.components.filter(predicate),
    });
}
