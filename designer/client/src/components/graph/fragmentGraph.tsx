import * as LayoutUtils from "../../reducers/layoutUtils";
import GraphWrapped from "./GraphWrapped";
import React, { forwardRef } from "react";
import { Graph } from "./Graph";
import { GraphProps } from "./types";

export const FragmentGraphPreview = forwardRef<Graph, Pick<GraphProps, "processCounts" | "scenario" | "nodeIdPrefixForFragmentTests">>(
    function FragmentGraphPreview({ processCounts, scenario, nodeIdPrefixForFragmentTests }, ref) {
        return (
            <GraphWrapped
                ref={ref}
                processCounts={processCounts}
                scenario={scenario}
                nodeIdPrefixForFragmentTests={nodeIdPrefixForFragmentTests}
                layout={LayoutUtils.fromMeta(scenario.json)}
                readonly
                divId="nk-graph-fragment"
                isFragment
            />
        );
    },
);
