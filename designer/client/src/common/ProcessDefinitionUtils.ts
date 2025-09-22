import type { Component, ComponentGroup } from "../types/component";

export function filterComponentsByLabel(filters: string[]): (componentGroup: ComponentGroup) => ComponentGroup {
    const predicate = ({ label }: Component) => filters.every((searchText) => label.toLowerCase().includes(searchText));
    return (componentGroup: ComponentGroup): ComponentGroup => ({
        ...componentGroup,
        components: componentGroup.components.filter(predicate),
    });
}
