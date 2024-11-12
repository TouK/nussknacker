/* eslint-disable i18next/no-literal-string */
import { dia } from "jointjs";
import { flatMap, groupBy, isEqual } from "lodash";
import { ScenarioGraph, ProcessDefinitionData } from "../../../types";
import { makeElement, makeLink } from "../EspNode";
import NodeUtils from "../NodeUtils";
import { isEdgeConnected } from "./EdgeUtils";
import { updateChangedCells } from "./updateChangedCells";
import { Theme } from "@mui/material";
import { StickyNote } from "../../../common/StickyNote";
import { makeStickyNoteElement, ModelWithTool } from "../EspNode/stickyNoteElements";

export function applyCellChanges(
    paper: dia.Paper,
    scenarioGraph: ScenarioGraph,
    stickyNotes: StickyNote[],
    processDefinitionData: ProcessDefinitionData,
    theme: Theme,
): void {
    const graph = paper.model;

    const nodeElements = NodeUtils.nodesFromScenarioGraph(scenarioGraph).map(makeElement(processDefinitionData, theme));
    const stickyNotesModelsWithTools: ModelWithTool[] = stickyNotes.map(makeStickyNoteElement(processDefinitionData, theme));
    const stickyNotesModels = stickyNotesModelsWithTools.map((a) => a.model);

    const edges = NodeUtils.edgesFromScenarioGraph(scenarioGraph);
    const indexed = flatMap(groupBy(edges, "from"), (edges) => edges.map((edge, i) => ({ ...edge, index: ++i })));
    const edgeElements = indexed.filter(isEdgeConnected).map((value) => makeLink(value, paper, theme));

    const cells = [...nodeElements, ...edgeElements, ...stickyNotesModels];

    const currentCells = graph.getCells();
    const currentIds = currentCells.map((c) => c.id);
    const newCells = cells.filter((cell) => !currentIds.includes(cell.id));
    const newStickyNotesModelsWithTools = stickyNotesModelsWithTools.filter((s) => !currentIds.includes(s.model.id));
    const deletedCells = currentCells.filter((oldCell) => !cells.find((cell) => cell.id === oldCell.id));
    const changedCells = cells.filter((cell) => {
        const old = graph.getCell(cell.id);
        return old && !isEqual(old.get("definitionToCompare"), cell.get("definitionToCompare"));
    });

    graph.removeCells(deletedCells);
    updateChangedCells(graph, changedCells);
    graph.addCells(newCells);
    newStickyNotesModelsWithTools.map((m) => m.model.findView(paper).addTools(m.tool));
}
