import { Box } from "@mui/material";
import React, { useCallback } from "react";
import { useDispatch } from "react-redux";
import { layoutChanged } from "../../actions/nk";
import { useUserSettings } from "../../common/userSettings";
import { ScenarioGraph } from "../../types";
import { Edge } from "./elements/Edge";
import { Node } from "./elements/Node";
import { GraphProvider, GraphProviderProps } from "./GraphProvider";
import { Paper } from "./paper/Paper";

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
            {nodes.map(({ additionalFields, id }) => (
                <Node key={id} id={id} {...additionalFields.layoutData}>
                    {id}
                </Node>
            ))}
            {edges
                .filter(({ from, to }) => from && to)
                .map((edge) => (
                    <Edge key={`${edge.from}--${edge.to}`} {...edge} />
                ))}
            {userSettings["debug.newGraph"] ? (
                <Box sx={{ display: "grid" }}>
                    <Paper sx={{ background: "#CCFFCC" }} interactive />
                    <Paper sx={{ background: "#CCCCFF" }} />
                </Box>
            ) : null}
            {children}
        </GraphProvider>
    );
};
