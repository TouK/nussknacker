import { BranchParametersTemplate, NodeType } from "./node";

export type SingleComponentConfig = {
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
