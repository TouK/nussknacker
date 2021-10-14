import {BranchParametersTemplate, NodeType} from "./node";
import {Category} from "./process";

export type ParameterConfig = {
    defaultValue?: string,
    editor?: $TodoType,
    validators?: $TodoType,
    label?: string,
}
export type SingleComponentConfig = {
    params?: Record<string, ParameterConfig>,
    icon?: string,
    docsUrl?: string,
    componentGroup?: string,
}
export type Component = {
    branchParametersTemplate: BranchParametersTemplate,
    categories: Category[],
    node: NodeType,
    label: string,
    type: string,
}
export type ComponentGroup = {
    components: Component[],
    name: string,
}
