import { BranchParametersTemplate, NodeType } from "./node";

export type Component = {
    branchParametersTemplate: BranchParametersTemplate;
    node: NodeType;
    label: string;
    componentId: string;
    disabled?: () => boolean;
    tooltip?: string;
};
export type ComponentGroup = {
    components: Component[];
    name: string;
};
