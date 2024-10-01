import { NodeId } from "./node";

export interface ActivityParameterConfig {
    editor: any;
    label: string;
    defaultValue: string | null;
    hintText: string | null;
}

export type ActivityParameterName = string;

export interface ActivityNodeParameters {
    nodeId: NodeId;
    parameters: { [key: ActivityParameterName]: ActivityParameterConfig };
}

export type ActivityName = string;

export type ActivityParameters = { [key: ActivityName]: ActivityNodeParameters[] };
