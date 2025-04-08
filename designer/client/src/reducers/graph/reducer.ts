/* eslint-disable i18next/no-literal-string */
import { concat, defaultsDeep, isEqual, omit as _omit, partition, pick as _pick, sortBy } from "lodash";
import type { StateWithHistory } from "redux-undo";
import undoable, { ActionTypes as UndoActionTypes, combineFilters, excludeAction } from "redux-undo";

import type { Action, Reducer } from "../../actions/reduxTypes";
import ProcessUtils from "../../common/ProcessUtils";
import NodeUtils from "../../components/graph/NodeUtils";
import { addStickyNotesToNodes, StickyNoteType } from "../../components/graph/utils/stickyNotesUtils";
import type { Scenario } from "../../components/Process/types";
import type { Dimensions, ValidationResult } from "../../types";
import * as LayoutUtils from "../layoutUtils";
import { fromMeta, nodes } from "../layoutUtils";
import { mergeReducers } from "../mergeReducers";
import { batchGroupBy } from "./batchGroupBy";
import { correctFetchedDetails } from "./correctFetchedDetails";
import type { NestedKeyOf } from "./nestedKeyOf";
import { selectionState } from "./selectionState";
import type { GraphState } from "./types";
import {
    addNodesWithLayout,
    adjustBranchParametersAfterDisconnect,
    createEdge,
    enrichNodeWithProcessDependentData,
    updateAfterNodeDelete,
    updateLayoutAfterNodeIdChange,
} from "./utils";

//TODO: We should change namespace from graphReducer to currentlyDisplayedProcess

const emptyGraphState: GraphState = {
    scenarioLoading: false,
    scenario: {
        scenarioGraph: {
            nodes: [],
            edges: [],
            properties: null,
            stickyNotes: [],
        },
    } as Scenario,
    layout: [],
    testCapabilities: null,
    testFormParameters: null,
    selectionState: [],
    processCounts: {},
    testResults: null,
};

export function updateValidationResult(state: GraphState, action: { validationResult: ValidationResult }): ValidationResult {
    return {
        ...action.validationResult,
        // nodeResults is sometimes empty although it shouldn't e.g. when SaveNotAllowed errors happen
        nodeResults: {
            ...ProcessUtils.getValidationResult(state.scenario).nodeResults,
            ...action.validationResult.nodeResults,
        },
    };
}

const graphReducer: Reducer<GraphState> = (state = emptyGraphState, action) => {
    switch (action.type) {
        case "PROCESS_FETCH":
        case "PROCESS_LOADING": {
            return {
                ...state,
                scenarioLoading: true,
            };
        }
        case "TEST_RESULTS_LOADING": {
            return {
                ...state,
                testResultsLoading: true,
            };
        }
        case "UPDATE_IMPORTED_PROCESS": {
            const oldNodeIds = sortBy(state.scenario.scenarioGraph.nodes.map((n) => n.id));
            const newNodeids = sortBy(action.scenarioGraph.nodes.map((n) => n.id));
            const newLayout = isEqual(oldNodeIds, newNodeids) ? state.layout : null;

            return {
                ...state,
                scenarioLoading: false,
                layout: newLayout,
                scenario: {
                    ...state.scenario,
                    ...action,
                },
            };
        }
        case "UPDATE_TEST_CAPABILITIES": {
            return {
                ...state,
                testCapabilities: action.capabilities,
            };
        }
        case "UPDATE_TEST_FORM_PARAMETERS": {
            return {
                ...state,
                testFormParameters: action.testFormParameters,
            };
        }
        case "DISPLAY_PROCESS": {
            const scenario = addStickyNotesToNodes(action.scenario);
            return {
                ...state,
                scenario,
                scenarioLoading: false,
                layout: fromMeta(scenario.scenarioGraph),
            };
        }
        case "CORRECT_INVALID_SCENARIO": {
            const scenario = correctFetchedDetails(state.scenario, action.processDefinitionData);
            return {
                ...state,
                scenario,
            };
        }
        case "ARCHIVED": {
            return {
                ...state,
                scenario: {
                    ...state.scenario,
                    isArchived: true,
                },
            };
        }
        case "PROCESS_VERSIONS_LOADED": {
            const { history } = action;
            return {
                ...state,
                scenario: {
                    ...state.scenario,
                    history: history,
                },
            };
        }
        case "TEST_RESULTS_FAILED": {
            return {
                ...state,
                testResultsLoading: false,
            };
        }
        case "LOADING_FAILED": {
            return {
                ...state,
                scenarioLoading: false,
            };
        }
        case "CLEAR_PROCESS": {
            return emptyGraphState;
        }
        case "EDIT_NODE": {
            const newLayout = updateLayoutAfterNodeIdChange(state.layout, action.before.id, action.after.id);

            return {
                ...state,
                layout: newLayout,
                scenario: {
                    ...state.scenario,
                    scenarioGraph: { ...action.scenarioGraphAfterChange },
                    validationResult: updateValidationResult(state, action),
                },
            };
        }
        case "EDIT_PROPERTIES": {
            return {
                ...state,
                scenario: {
                    ...state.scenario,
                    scenarioGraph: { ...action.scenarioGraphAfterChange },
                    validationResult: updateValidationResult(state, action),
                },
            };
        }
        case "EDIT_LABELS": {
            return {
                ...state,
                scenario: {
                    ...state.scenario,
                    labels: action.labels,
                },
            };
        }
        case "DELETE_NODES": {
            return action.ids.reduce((state, idToDelete) => {
                return updateAfterNodeDelete(state, idToDelete);
            }, state);
        }
        case "NODES_CONNECTED": {
            const currentEdges = NodeUtils.edgesFromScenarioGraph(state.scenario.scenarioGraph);
            const newEdge = NodeUtils.getEdgeForConnection({
                fromNode: action.fromNode,
                toNode: action.toNode,
                edgeType: action.edgeType,
                processDefinition: action.processDefinitionData,
                scenarioGraph: state.scenario.scenarioGraph,
            });

            const newEdges = currentEdges.includes(newEdge)
                ? currentEdges.map((edge) =>
                      edge === newEdge
                          ? {
                                ...newEdge,
                                to: action.toNode.id,
                            }
                          : edge,
                  )
                : concat(currentEdges, newEdge);

            return {
                ...state,
                scenario: {
                    ...state.scenario,
                    scenarioGraph: {
                        ...state.scenario.scenarioGraph,
                        nodes: state.scenario.scenarioGraph.nodes.map((n) =>
                            action.toNode.id !== n.id ? n : enrichNodeWithProcessDependentData(n, action.processDefinitionData, newEdges),
                        ),
                        edges: newEdges,
                    },
                },
            };
        }
        case "NODES_DISCONNECTED": {
            const nodesToSet = adjustBranchParametersAfterDisconnect(state.scenario.scenarioGraph.nodes, [action]);
            return {
                ...state,
                scenario: {
                    ...state.scenario,
                    scenarioGraph: {
                        ...state.scenario.scenarioGraph,
                        edges: state.scenario.scenarioGraph.edges
                            .map((e) => (e.from === action.from && e.to === action.to ? { ...e, to: "" } : e))
                            .filter(Boolean),
                        nodes: nodesToSet,
                    },
                },
            };
        }
        case "NODE_ADDED": {
            return addNodesWithLayout(state, {
                nodes: action.nodes,
                layout: action.layout,
            });
        }
        case "STICKY_NOTE_UPDATED": {
            const { nodes = [], ...scenarioGraph } = state.scenario.scenarioGraph;
            const updatedNodes = nodes.map((node) =>
                node.id === action.id
                    ? {
                          ...node,
                          dimensions: action.dimensions,
                          content: action.content ? action.content : node.content,
                      }
                    : node,
            );
            return {
                ...state,
                scenario: {
                    ...state.scenario,
                    scenarioGraph: {
                        ...scenarioGraph,
                        nodes: updatedNodes,
                    },
                },
            };
        }
        case "STICKY_NOTE_SET_ERRORS": {
            const { nodes = [], ...scenarioGraph } = state.scenario.scenarioGraph;
            const [stickyNotes, graphNodes] = partition(nodes, (node) => node.type === StickyNoteType);
            const stickyNotesUpdated = stickyNotes.map((stickyNote) => {
                return action.stickyNoteErrors[stickyNote.id]
                    ? {
                          ...stickyNote,
                          errors: action.stickyNoteErrors[stickyNote.id],
                      }
                    : stickyNote;
            });
            return {
                ...state,
                scenario: {
                    ...state.scenario,
                    scenarioGraph: {
                        ...scenarioGraph,
                        nodes: [...graphNodes, ...stickyNotesUpdated],
                    },
                },
            };
        }
        case "NODES_WITH_EDGES_ADDED": {
            const { nodes, layout, idMapping, processDefinitionData, edges } = action;

            const edgesWithValidIds = edges.map((edge) => ({
                ...edge,
                from: idMapping[edge.from],
                to: idMapping[edge.to],
            }));

            const adjustedEdges = edgesWithValidIds.reduce((edges, edge) => {
                const fromNode = nodes.find((n) => n.id === edge.from);
                const toNode = nodes.find((n) => n.id === edge.to);
                const currentNodeEdges = NodeUtils.getOutputEdges(fromNode.id, edges);
                const newEdge = createEdge(fromNode, toNode, edge.edgeType, currentNodeEdges, processDefinitionData);
                return edges.concat(newEdge);
            }, state.scenario.scenarioGraph.edges);

            return addNodesWithLayout(state, {
                nodes,
                layout,
                edges: adjustedEdges,
            });
        }
        case "VALIDATION_RESULT": {
            return {
                ...state,
                scenario: {
                    ...state.scenario,
                    validationResult: updateValidationResult(state, action),
                },
            };
        }
        //TODO: handle it differently?
        case "LAYOUT_CHANGED": {
            return {
                ...state,
                layout: action.layout,
            };
        }
        case "DISPLAY_PROCESS_COUNTS": {
            return {
                ...state,
                processCounts: action.processCounts,
                processCountsRefresh: action.refresh,
            };
        }
        case "DISPLAY_TEST_RESULTS_DETAILS": {
            return {
                ...state,
                testResults: action.testResults,
                testData: {
                    ...state.testData,
                    [action.testData?.sourceId]: action.testData?.parameterExpressions,
                },
                scenarioLoading: false,
                testResultsLoading: false,
            };
        }
        case "HIDE_RUN_PROCESS_DETAILS": {
            return {
                ...state,
                testResults: null,
                processCounts: null,
                processCountsRefresh: null,
            };
        }
        default:
            return state;
    }
};

const reducer: Reducer<GraphState> = mergeReducers(graphReducer, {
    scenario: {
        scenarioGraph: {
            nodes,
        },
    },
    selectionState,
});

export type GraphStateWithHistory = StateWithHistory<GraphState>;

const pick = <T extends NonNullable<unknown>>(object: T, props: NestedKeyOf<T>[]) => _pick(object, props);
const omit = <T extends NonNullable<unknown>>(object: T, props: NestedKeyOf<T>[]) => _omit(object, props);

const pickKeys: NestedKeyOf<GraphState>[] = ["scenario", "layout", "selectionState"];
const omitKeys: NestedKeyOf<GraphState>[] = ["scenario.validationResult", "scenario.history"];

const getUndoableState = (state: GraphState) => omit(pick(state, pickKeys), omitKeys.concat(["scenario.validationResult"]));
const getNonUndoableState = (state: GraphState) => defaultsDeep(omit(state, pickKeys), pick(state, omitKeys));

const undoableReducer = undoable<GraphState, Action>(reducer, {
    ignoreInitialState: true,
    clearHistoryType: [UndoActionTypes.CLEAR_HISTORY, "PROCESS_FETCH"],
    groupBy: batchGroupBy.init(),
    filter: combineFilters((action, nextState, prevState) => {
        return !isEqual(getUndoableState(nextState), getUndoableState(prevState._latestUnfiltered));
    }, excludeAction(["VALIDATION_RESULT", "STICKY_NOTE_SET_ERRORS", "UPDATE_IMPORTED_PROCESS", "PROCESS_STATE_LOADED", "UPDATE_TEST_CAPABILITIES", "UPDATE_BACKEND_NOTIFICATIONS", "PROCESS_DEFINITION_DATA", "PROCESS_TOOLBARS_CONFIGURATION_LOADED", "CORRECT_INVALID_SCENARIO", "GET_SCENARIO_ACTIVITIES", "LOGGED_USER", "REGISTER_TOOLBARS", "UI_SETTINGS", "MARK_BACKEND_NOTIFICATION_READ", "UPDATE_TEST_FORM_PARAMETERS"])),
});

// apply only undoable changes for undo actions
function fixUndoableHistory(state: GraphStateWithHistory, action: Action): GraphStateWithHistory {
    const nextState = undoableReducer(state, action);

    if (Object.values(UndoActionTypes).includes(action.type)) {
        const present = defaultsDeep(getUndoableState(nextState.present), getNonUndoableState(state?.present));
        return { ...nextState, present };
    }

    return nextState;
}

export const reducerWithUndo: Reducer<GraphStateWithHistory> = (state, action) => {
    const history = fixUndoableHistory(state, action);

    return history;
};
