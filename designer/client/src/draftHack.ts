/* eslint-disable i18next/no-literal-string */
import { getUserSettings } from "./reducers/selectors/userSettings";
import { store } from "./store/provider";
import type { StickyNoteNodeType } from "./types/node";
import type { ScenarioGraph } from "./types/scenarioGraph";

const DRAFT_KEY = "__NUSSKNACKER_DRAFT__";

export function isDraftHackEnabled(): boolean {
    const state = store.getState();
    const settings = getUserSettings(state);
    return !!settings["debug.scenario.enableDraftHack"];
}

/**
 * Creates a minimal ScenarioGraph containing a single sticky note
 * with the original scenarioGraph data serialized as JSON.
 */
export function packDraftIntoStickyNote(scenarioGraph: ScenarioGraph): ScenarioGraph {
    const content =
        DRAFT_KEY +
        JSON.stringify({
            nodes: scenarioGraph.nodes,
            edges: scenarioGraph.edges,
            stickyNotes: scenarioGraph.stickyNotes,
        });
    const stickyNote: StickyNoteNodeType = {
        id: "draft-sticky-note",
        name: "draft-sticky-note",
        type: "StickyNoteNode",
        isDisabled: false,
        content,
        dimensions: { width: 300, height: 50 },
        color: "#00ff33",
        additionalFields: {
            layoutData: { x: 0, y: 0 },
        },
    };
    return {
        nodes: [],
        edges: [],
        properties: scenarioGraph.properties,
        stickyNotes: [stickyNote],
        testCases: scenarioGraph.testCases || { list: [] },
    };
}

/**
 * Checks if a fetched ScenarioGraph contains a draft sticky note.
 * If so, unpacks it and returns the restored ScenarioGraph.
 * Otherwise returns null.
 */
export function unpackDraftFromStickyNote(scenarioGraph: ScenarioGraph): ScenarioGraph | null {
    const allNotes = [...(scenarioGraph.stickyNotes || [])];
    for (const node of allNotes) {
        const content = (node as StickyNoteNodeType).content;
        if (content && content.startsWith(DRAFT_KEY)) {
            try {
                const data = JSON.parse(content.slice(DRAFT_KEY.length));
                return {
                    ...scenarioGraph,
                    nodes: data.nodes || [],
                    edges: data.edges || [],
                    stickyNotes: data.stickyNotes || [],
                };
            } catch {
                return null;
            }
        }
    }
    return null;
}
