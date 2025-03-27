import React, { forwardRef } from "react";

import * as LayoutUtils from "../../reducers/layoutUtils";
import type { Graph } from "./Graph";
import GraphWrapped from "./GraphWrapped";
import type { GraphProps } from "./types";

export const FragmentGraphPreview = forwardRef<Graph, Pick<GraphProps, "processCounts" | "scenario" | "nodeIdPrefixForFragmentTests">>(
    function FragmentGraphPreview({ processCounts, scenario, nodeIdPrefixForFragmentTests }, ref) {
        return (
            <GraphWrapped
                ref={ref}
                processCounts={processCounts}
                scenario={scenario}
                nodeIdPrefixForFragmentTests={nodeIdPrefixForFragmentTests}
                layout={LayoutUtils.fromMeta(scenario.scenarioGraph)}
                readonly
                divId="nk-graph-fragment"
                isFragment
            />
        );
    },
);
