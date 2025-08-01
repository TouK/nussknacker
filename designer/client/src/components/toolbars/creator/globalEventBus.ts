import type { g } from "jointjs";
import type { Unsubscribe } from "nanoevents";
import { createNanoEvents } from "nanoevents";

import type { PanelSide } from "../../../actions/nk/ui/panelSide";
import type { Edge, NodeType } from "../../../types";
import type { ToolBoxProps } from "./ToolBox";

export type OpenNodeSelectorParams = {
    side?: PanelSide;
    fromPoint?: g.PlainPoint;
    withEdge?: Edge;
    filters?: ToolBoxProps["filters"];
};

export type CloseNodeSelectorParams = {
    side?: PanelSide;
    onPoint?: g.PlainPoint;
    node?: NodeType;
    edge?: Edge;
};

type NuEvents = {
    openNodeSelector: (data: OpenNodeSelectorParams) => void;
    closeNodeSelector: (data: CloseNodeSelectorParams) => void;
};

export type TempGlobalActions =
    | {
          type: "OPEN_NODE_SELECTOR";
          data: OpenNodeSelectorParams;
      }
    | {
          type: "CLOSE_NODE_SELECTOR";
          data: CloseNodeSelectorParams;
      };

class GlobalEventBus {
    private emitter = createNanoEvents<NuEvents>();

    emit<K extends keyof NuEvents>(event: K, ...args: Parameters<NuEvents[K]>): void {
        this.emitter.emit(event, ...args);
    }

    on<K extends keyof NuEvents>(event: K, callback: NuEvents[K]): Unsubscribe {
        return this.emitter.on(event, callback);
    }

    once<K extends keyof NuEvents>(event: K, callback: NuEvents[K]): Unsubscribe {
        const unbind = this.on(event, (...args) => {
            unbind();
            callback.apply(this, args);
        });
        return unbind;
    }
}

export const globalEventBus = new GlobalEventBus();
