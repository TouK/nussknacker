import * as LayoutUtils from "../../reducers/layoutUtils";
import GraphWrapped from "./GraphWrapped";
import React, { forwardRef } from "react";
import { Graph } from "./Graph";
import { GraphProps } from "./types";

export const FragmentGraphPreview = forwardRef<
    Graph,
    Pick<GraphProps, "processCounts" | "processToDisplay" | "nodeIdPrefixForFragmentTests">
>(function FragmentGraphPreview({ processCounts, processToDisplay, nodeIdPrefixForFragmentTests }, ref) {
    return (
        <GraphWrapped
            ref={ref}
            processCounts={processCounts}
            processToDisplay={processToDisplay}
            nodeIdPrefixForFragmentTests={nodeIdPrefixForFragmentTests}
            layout={LayoutUtils.fromMeta(processToDisplay)}
            readonly
            divId="nk-graph-fragment"
            isFragment
        />
    );
});
