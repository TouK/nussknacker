import type { dia } from "jointjs";
import type React from "react";
import { useEffect } from "react";

import { getProcessDefinitionData } from "../../reducers/selectors/getProcessDefinitionData";
import { getScenarioGraph } from "../../reducers/selectors/graph";
import { useAppSelector } from "../../store/configureStore";
import type { Graph } from "./Graph";
import NodeUtils from "./NodeUtils";
import { Events } from "./types";

export function usePortMagnetToggle(graphRef: React.MutableRefObject<Graph>) {
    const scenarioGraph = useAppSelector(getScenarioGraph);
    const processDefinitionData = useAppSelector(getProcessDefinitionData);
    useEffect(() => {
        const graph = graphRef.current;
        const callback = (cellView: dia.CellView) => {
            const model = cellView.model;
            if (model.isElement()) {
                const node = NodeUtils.getNodeById(model.id.toString(), scenarioGraph);
                const nodeOutputs = NodeUtils.nodeOutputs(node.id, scenarioGraph);
                const canHaveMoreOutputs = NodeUtils.canHaveMoreOutputs(node, nodeOutputs, processDefinitionData);
                const nodeInputs = NodeUtils.nodeInputs(node.id, scenarioGraph);
                const canHaveMoreInputs = NodeUtils.canHaveMoreInputs(node, nodeInputs, processDefinitionData);
                model.getPorts().forEach((port) => {
                    model.portProp(port.id, "attrs/circle/magnet", port.id === "Out" ? canHaveMoreOutputs : canHaveMoreInputs);
                });
            }
        };
        graph.processGraphPaper.on(Events.CELL_MOUSEOVER, callback);
        return () => {
            graph.processGraphPaper.off(Events.CELL_MOUSEOVER, callback);
        };
    }, [graphRef, processDefinitionData, scenarioGraph]);
}
