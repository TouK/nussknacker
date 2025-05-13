import type { EditorMode } from "../components/graph/node-modal/editors/expression/types";
import type { ActionName } from "../components/Process/types";
import type { NodeId } from "./node";

export interface ActionParameterConfig {
    editor: any;
    label: string;
    defaultValue: {
        language: EditorMode | string;
        expression: string;
    } | null;
    hintText: string | null;
}

export type ActionParameterName = string;

export interface ActionNodeParameters {
    nodeId: NodeId;
    componentId: string;
    parameters: { [key: ActionParameterName]: ActionParameterConfig };
}

export type ActionParameters = { [key: ActionName]: ActionNodeParameters[] };
