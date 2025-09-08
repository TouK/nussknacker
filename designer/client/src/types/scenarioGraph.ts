import type { Editor } from "../components/graph/node-modal/editors/types";
import type { ComponentGroup } from "./component";
import type { TypingResult, UIParameter } from "./definition";
import type { Edge, EdgeType } from "./edge";
import type { NodeType, PropertiesType } from "./node";

export type ScenarioGraph = {
    nodes: NodeType[];
    edges: Edge[];
    properties: PropertiesType;
    stickyNotes: NodeType[];
};

export type Category = string;

export type ProcessAdditionalFields = {
    description?: string | null;
    properties: { [key: string]: string };
    metaDataType: string;
    showDescription?: boolean;
};

export interface UIScenarioProperty {
    defaultValue?: string;
    editor: Editor;
    label?: string;
    hintText?: string;
}

export interface UiScenarioProperties {
    propertiesConfig: { [key: string]: UIScenarioProperty };
    docsUrl?: string;
}
//"ReturnType" is builtin type alias
export interface ReturnedType {
    display: string;
    type: string;
    refClazzName: string;
    params: TypingResult[];
}

export interface ComponentDefinition {
    parameters: UIParameter[];
    returnType: ReturnedType | null;
    icon: string;
    docsUrl?: string;
    // For fragments only
    outputParameters?: string[] | null;
    label: string;
}

export interface ProcessDefinitionData {
    components?: Record<string, ComponentDefinition>;
    classes?: TypingResult[];
    componentGroups?: ComponentGroup[];
    scenarioProperties?: UiScenarioProperties;
    edgesForNodes?: EdgesForNode[];
}

export type EdgesForNode = {
    componentId: string;
    edges: EdgeType[];
    canChooseNodes: boolean;
    isForInputDefinition: boolean;
};
