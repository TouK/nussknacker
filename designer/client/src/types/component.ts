import { BranchParametersTemplate, NodeType } from "./node";

export type Component = {
    branchParametersTemplate: BranchParametersTemplate;
    node: NodeType;
    label: string;
    componentId: string;
    disabled?: () => boolean;
};
export type ComponentGroup = {
    components: Component[];
    name: string;
};
