/* eslint-disable i18next/no-literal-string */
import { dia } from "jointjs";
import { flatMap, groupBy, isEqual } from "lodash";
import { ScenarioGraph, ProcessDefinitionData } from "../../../types";
import { makeElement, makeLink } from "../EspNode";
import NodeUtils from "../NodeUtils";
import { isEdgeConnected } from "./EdgeUtils";
import { updateChangedCells } from "./updateChangedCells";

export function applyCellChanges(paper: dia.Paper, scenarioGraph: ScenarioGraph, processDefinitionData: ProcessDefinitionData): void {
    const graph = paper.model;

    const nodeElements = NodeUtils.nodesFromScenarioGraph(scenarioGraph).map(makeElement(processDefinitionData));

    const edges = NodeUtils.edgesFromScenarioGraph(scenarioGraph);
    const indexed = flatMap(groupBy(edges, "from"), (edges) => edges.map((edge, i) => ({ ...edge, index: ++i })));
    const edgeElements = indexed.filter(isEdgeConnected).map((value) => makeLink(value, paper));

    const cells = [...nodeElements, ...edgeElements];

    const currentCells = graph.getCells();
    const currentIds = currentCells.map((c) => c.id);
    const newCells = cells.filter((cell) => !currentIds.includes(cell.id));
    const deletedCells = currentCells.filter((oldCell) => !cells.find((cell) => cell.id === oldCell.id));
    const changedCells = cells.filter((cell) => {
        const old = graph.getCell(cell.id);
        return old && !isEqual(old.get("definitionToCompare"), cell.get("definitionToCompare"));
    });

    graph.removeCells(deletedCells);
    updateChangedCells(graph, changedCells);
    graph.addCells(newCells);
}
