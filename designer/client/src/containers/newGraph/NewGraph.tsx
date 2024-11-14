import { Box } from "@mui/material";
import React, { useCallback } from "react";
import { useDispatch } from "react-redux";
import { layoutChanged } from "../../actions/nk";
import { useUserSettings } from "../../common/userSettings";
import { NodeGroupContent } from "../../components/graph/node-modal/node/NodeGroupContent";
import { DebugNodeInspector } from "../../components/graph/node-modal/NodeDetailsContent/DebugNodeInspector";
import { ScenarioGraph } from "../../types";
import { Edge } from "./elements/Edge";
import { Node } from "./elements/Node";
import { GraphProvider, GraphProviderProps } from "./GraphProvider";
import { InteractivePaper } from "./InteractivePaper";
import { PreviewPaper } from "./PreviewPaper";

type Props = React.PropsWithChildren<{
    scenarioGraph: ScenarioGraph;
}>;

export const NewGraph = ({ scenarioGraph, children }: Props) => {
    const { nodes, edges } = scenarioGraph;

    const [userSettings] = useUserSettings();
    const dispatch = useDispatch();

    const onLayoutChange = useCallback<GraphProviderProps["onLayoutChange"]>(
        (layout) => {
            dispatch(layoutChanged(layout.map(({ id, ...position }) => ({ id, position }))));
        },
        [dispatch],
    );

    return (
        <GraphProvider onLayoutChange={onLayoutChange}>
            {nodes.map((node) => (
                <Node key={node.id} id={node.id} label={node.id} {...node.additionalFields.layoutData}>
                    <Box>
                        <NodeGroupContent node={node} edges={edges} />
                    </Box>
                </Node>
            ))}
            {edges
                .filter(({ from, to }) => from && to)
                .map((edge) => (
                    <Edge key={`${edge.from}--${edge.to}`} {...edge} />
                ))}
            {userSettings["debug.newGraph"] ? (
                <Box sx={{ display: "grid" }}>
                    <InteractivePaper />
                    <PreviewPaper />
                </Box>
            ) : null}
            {children}
        </GraphProvider>
    );
};
