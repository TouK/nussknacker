import type { g } from "jointjs";

import type { PanelSide } from "../../../actions/nk/ui/panelSide";
import type { AppAction } from "../../../store/storeHelpers";
import type { Edge } from "../../../types/edge";
import type { NodeType } from "../../../types/node";
import type { ToolBoxProps } from "./ToolBox";
import { ComponentFilter } from "./ToolBox";

export type NodeSelectorActions =
    | {
          type: "OPEN_NODE_SELECTOR";
          data: {
              side?: PanelSide;
              fromPoint?: g.PlainPoint;
              withEdge?: Edge;
              filters?: ToolBoxProps["filters"];
          };
      }
    | {
          type: "CLOSE_NODE_SELECTOR";
          data: {
              side?: PanelSide;
              node?: NodeType;
          };
      };

export const openNodeSelector = (
    panelSide: PanelSide,
    position: g.Point,
    isLinkReversed: boolean,
    edgeData,
    from,
    to: string,
): AppAction => ({
    type: "OPEN_NODE_SELECTOR",
    data: {
        side: panelSide,
        fromPoint: position,
        filters: [isLinkReversed ? ComponentFilter.removeNoOutputs : ComponentFilter.removeNoInputs],
        withEdge: {
            ...edgeData,
            from,
            to,
        },
    },
});

export const closeNodeSelector = (side: PanelSide): AppAction => ({
    type: "CLOSE_NODE_SELECTOR",
    data: { side },
});

export const selectComponent = (side: PanelSide, node: NodeType): AppAction => ({
    type: "CLOSE_NODE_SELECTOR",
    data: { side, node },
});
