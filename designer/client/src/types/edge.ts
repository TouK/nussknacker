import type { ExpressionObj } from "../components/graph/node-modal/editors/expression/types";
import type { NodeId } from "./node";

export enum EdgeKind {
    filterFalse = "FilterFalse",
    filterTrue = "FilterTrue",
    switchDefault = "SwitchDefault",
    switchNext = "NextSwitch",
    fragmentOutput = "FragmentOutput",
    customNodeOutput = "CustomNodeOutput",
}

export type EdgeType = {
    type: EdgeKind;
    name?: string;
    condition?: ExpressionObj;
};

/** `undefined` stands for the unnamed main output of a component with no declared edge entries. */
export type AvailableEdgeType = EdgeType | undefined;

export type Edge = {
    _id?: string;
    from: NodeId;
    to: NodeId;
    edgeType?: EdgeType;
};
