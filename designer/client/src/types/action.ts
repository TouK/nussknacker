import { NodeId } from "./node";
import { ActionName } from "../components/Process/types";

export interface ActionParameterConfig {
    editor: any;
    label: string;
    defaultValue: string | null;
    hintText: string | null;
}

export type ActionParameterName = string;

export interface ActionNodeParameters {
    nodeId: NodeId;
    parameters: { [key: ActionParameterName]: ActionParameterConfig };
}

export type ActionParameters = { [key: ActionName]: ActionNodeParameters[] };
