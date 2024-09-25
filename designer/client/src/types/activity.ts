import { NodeId } from "./node";

export interface ActivityParameterConfig {
    editor: any;
    label: string;
    defaultValue: string | null;
    hintText: string | null;
}

export interface ActivityNodeParameters {
    nodeId: NodeId;
    parameters: Record<string, ActivityParameterConfig>;
}

export type ActivityName = string;

export type ActivityParameters = Record<ActivityName, ActivityNodeParameters[]>;
