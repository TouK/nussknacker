import { ExpressionObj } from "../components/graph/node-modal/editors/expression/types";
import { NodeId } from "./node";

export enum EdgeKind {
    filterFalse = "FilterFalse",
    filterTrue = "FilterTrue",
    switchDefault = "SwitchDefault",
    switchNext = "NextSwitch",
    fragmentOutput = "FragmentOutput",
}

export type EdgeType = {
    type: EdgeKind;
    name?: string;
    condition?: ExpressionObj;
};

export type Edge = {
    _id?: string;
    from: NodeId;
    to: NodeId;
    edgeType?: EdgeType;
};
