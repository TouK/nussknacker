import * as LayoutUtils from "../../reducers/layoutUtils";
import GraphWrapped from "./GraphWrapped";
import React, { forwardRef } from "react";
import { Graph } from "./Graph";
import { GraphProps } from "./types";

export const FragmentGraphPreview = forwardRef<Graph, Pick<GraphProps, "processCounts" | "process" | "nodeIdPrefixForFragmentTests">>(
    function FragmentGraphPreview({ processCounts, process, nodeIdPrefixForFragmentTests }, ref) {
        return (
            <GraphWrapped
                ref={ref}
                processCounts={processCounts}
                process={process}
                nodeIdPrefixForFragmentTests={nodeIdPrefixForFragmentTests}
                layout={LayoutUtils.fromMeta(process.json)}
                readonly
                divId="nk-graph-fragment"
                isFragment
            />
        );
    },
);
