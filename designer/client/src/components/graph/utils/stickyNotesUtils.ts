import { partition } from "lodash";

import type { ComponentDefinition, ScenarioGraph } from "../../../types/scenarioGraph";
import type { Scenario } from "../../Process/types";

export const StickyNoteType = "StickyNoteNode";

export const StickyNoteDefinition: ComponentDefinition = {
    parameters: [],
    returnType: null,
    icon: `/assets/components/${StickyNoteType}.svg`,
    docsUrl: null,
    outputParameters: null,
    label: "",
};

// We add sticky notes to nodes to treat them as nodes on the front-end (FE), but on the back-end (BE), they are only present to be saved. We do not process them as nodes on the BE.
// This allows us to avoid handling edge cases with 'loose nodes'.
// `addStickyNotesToNodes` - Merges sticky notes with nodes.
// `extractStickyNotesFromNodes` - Separates sticky notes from nodes.
export function addStickyNotesToNodes(data: Scenario): Scenario {
    const stickyNotesWithType = data.scenarioGraph.stickyNotes.map((stickyNote) => ({
        ...stickyNote,
        name: stickyNote.id,
        type: StickyNoteType,
        errors: [],
    }));
    return {
        ...data,
        scenarioGraph: {
            ...data.scenarioGraph,
            nodes: [...data.scenarioGraph.nodes, ...stickyNotesWithType],
        },
    };
}

export function extractStickyNotesFromNodes(graph: ScenarioGraph): ScenarioGraph {
    const [stickyNotes, nodes] = partition(graph.nodes, (node) => node?.type === StickyNoteType);
    return {
        ...graph,
        nodes: nodes,
        stickyNotes: stickyNotes,
    };
}
