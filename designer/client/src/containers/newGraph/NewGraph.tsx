import { dia } from "jointjs";
import React, { useRef } from "react";
import { useUserSettings } from "../../common/userSettings";
import { ScenarioGraph } from "../../types";
import { Edge } from "./Edge";
import { GraphProvider } from "./GraphProvider";
import { Node } from "./Node";
import { Paper } from "./Paper";

type Props = {
    scenarioGraph: ScenarioGraph;
};

export const NewGraph = ({ scenarioGraph }: Props) => {
    const { nodes, edges } = scenarioGraph;

    const [userSettings] = useUserSettings();
    const graphRef = useRef<dia.Graph>(null);

    return (
        <GraphProvider ref={graphRef}>
            {nodes.map(({ additionalFields, id }) => (
                <Node key={id} id={id} {...additionalFields.layoutData}>
                    {id}
                </Node>
            ))}
            {edges.map((edge) => (
                <Edge key={`${edge.from}--${edge.to}`} {...edge} />
            ))}
            <Paper
                sx={{
                    background: "#CCFFCC",
                    visibility: userSettings["debug.newGraph"] ? "visible" : "hidden",
                }}
            />
        </GraphProvider>
    );
};
