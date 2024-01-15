import { BranchParametersTemplate, NodeType } from "./node";

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
