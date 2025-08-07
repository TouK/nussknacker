import type { g } from "jointjs";

import type { PanelSide } from "../../../actions/nk/ui/panelSide";
import type { AppAction } from "../../../store/storeHelpers";
import type { Edge, NodeType } from "../../../types";
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
              point?: g.PlainPoint;
              node?: NodeType;
              edge?: Edge;
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

export const selectComponent = (side: PanelSide, node: NodeType, point, edge): AppAction => ({
    type: "CLOSE_NODE_SELECTOR",
    data: { side, node, point, edge },
});
