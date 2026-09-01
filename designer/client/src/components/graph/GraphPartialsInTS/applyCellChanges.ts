/* eslint-disable i18next/no-literal-string */
import type { Theme } from "@mui/material";
import type { dia } from "jointjs";
import { flatMap, groupBy, isEqual } from "lodash";
import { partition } from "lodash";

import { isEdgeConnected } from "./EdgeUtils";
import { updateChangedCells } from "./updateChangedCells";
import type { UserSettings } from "../../../reducers/userSettings";
import type { ScenarioGraph, ProcessDefinitionData } from "../../../types/scenarioGraph";
import { makeElement } from "../EspNode/element";
import { makeLink } from "../EspNode/link";
import { overrideAdvancedStickyNoteColorToDefault } from "../EspNode/stickyNote/advancedStickyNoteConfig";
import type { ModelWithTool } from "../EspNode/stickyNote/stickyNoteElements";
import { makeStickyNoteElement } from "../EspNode/stickyNote/stickyNoteElements";
import NodeUtils from "../NodeUtils";
import { StickyNoteType } from "../utils/stickyNotesUtils";

export function applyCellChanges(
    paper: dia.Paper,
    scenarioGraph: ScenarioGraph,
    processDefinitionData: ProcessDefinitionData,
    theme: Theme,
    userSettings: UserSettings,
): void {
    const graph = paper.model;
    const areAdvancedStickyNotesEnabled = userSettings["node.advancedStickyNotes"];

    const [stickyNoteElements, scenarioNodeElements] = partition(
        NodeUtils.nodesFromScenarioGraph(scenarioGraph),
        (n) => n.type === StickyNoteType,
    );

    const nodeElements = scenarioNodeElements.map(makeElement(processDefinitionData, theme));
    const stickyNotesModelsWithTools: ModelWithTool[] = stickyNoteElements
        .map((stickyNote) => (areAdvancedStickyNotesEnabled ? overrideAdvancedStickyNoteColorToDefault(stickyNote) : stickyNote))
        .map(makeStickyNoteElement(theme, areAdvancedStickyNotesEnabled));
    const stickyNotesModels = stickyNotesModelsWithTools.map((a) => a.model);

    const edges = NodeUtils.edgesFromScenarioGraph(scenarioGraph);
    const indexed = flatMap(groupBy(edges, "from"), (edges) => edges.map((edge, i) => ({ ...edge, index: i + 1 })));
    const edgeElements = indexed.filter(isEdgeConnected).map((value) => makeLink(value, paper, theme));

    const cells = [...nodeElements, ...edgeElements, ...stickyNotesModels];

    const currentCells = graph.getCells();
    const currentIds = currentCells.map((c) => c.id);
    const newCells = cells.filter((cell) => !currentIds.includes(cell.id));
    const deletedCells = currentCells.filter((oldCell) => !cells.find((cell) => cell.id === oldCell.id));
    const changedCells = cells.filter((cell) => {
        const old = graph.getCell(cell.id);
        return old && !isEqual(old.get("definitionToCompare"), cell.get("definitionToCompare"));
    });

    const stickyNotesToolsToAdd = stickyNotesModelsWithTools.filter(
        (s) => !currentIds.includes(s.model.id) || changedCells.find((a) => a.id == s.model.id),
    );

    graph.removeCells(deletedCells);
    updateChangedCells(graph, changedCells);
    graph.addCells(newCells);

    stickyNotesToolsToAdd.forEach((m) => {
        try {
            const view = m.model.findView(paper);
            if (!view) {
                console.warn(`View not found for stickyNote model: ${m.model.id}`);
                return;
            }
            view.addTools(m.tools);
        } catch (error) {
            console.error(`Failed to add tools to stickyNote view:`, error);
        }
    });
}
