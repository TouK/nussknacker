import React, { useCallback } from "react";
import { useDispatch } from "react-redux";
import { layoutChanged } from "../../actions/nk";
import { useUserSettings } from "../../common/userSettings";
import { ScenarioGraph } from "../../types";
import { Edge } from "./Edge";
import { GraphProvider, GraphProviderProps } from "./GraphProvider";
import { Node } from "./Node";
import { Paper } from "./Paper";

type Props = React.PropsWithChildren<{
    scenarioGraph: ScenarioGraph;
}>;

const InteractivePaper = (props) => <Paper {...props} interactive />;
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
                <Paper
                    interactive
                    sx={{
                        background: "#CCFFCC",
                    }}
                />
            ) : (
                <Paper
                    sx={{
                        background: "#CCCCFF",
                    }}
                />
            )}
            {children}
        </GraphProvider>
    );
};
