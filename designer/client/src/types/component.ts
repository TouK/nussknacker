import { BranchParametersTemplate, NodeType } from "./node";

export type ParameterConfig = {
    defaultValue?: string;
    editor?: $TodoType;
    label?: string;
};
export type SingleComponentConfig = {
    params?: Record<string, ParameterConfig>;
    icon?: string;
    docsUrl?: string;
    componentGroup?: string;
};
export type Component = {
    branchParametersTemplate: BranchParametersTemplate;
    node: NodeType;
    label: string;
    type: string;
};
export type ComponentGroup = {
    components: Component[];
    name: string;
};
