import * as LayoutUtils from "../../reducers/layoutUtils";
import GraphWrapped from "./GraphWrapped";
import React, { forwardRef } from "react";
import { Graph } from "./Graph";
import { GraphProps } from "./types";

export const FragmentGraphPreview = forwardRef<
    Graph,
    Pick<GraphProps, "processCounts" | "scenario" | "stickyNotes" | "nodeIdPrefixForFragmentTests">
>(function FragmentGraphPreview({ processCounts, scenario, stickyNotes, nodeIdPrefixForFragmentTests }, ref) {
    return (
        <GraphWrapped
            ref={ref}
            processCounts={processCounts}
            scenario={scenario}
            stickyNotes={stickyNotes}
            nodeIdPrefixForFragmentTests={nodeIdPrefixForFragmentTests}
            layout={LayoutUtils.fromMeta(scenario.scenarioGraph)}
            readonly
            divId="nk-graph-fragment"
            isFragment
        />
    );
});
