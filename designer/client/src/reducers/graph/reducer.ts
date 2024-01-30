/* eslint-disable i18next/no-literal-string */
import { concat, defaultsDeep, isEqual, omit as _omit, pick as _pick, sortBy, uniq, xor, zipObject } from "lodash";
import undoable, { ActionTypes as UndoActionTypes, combineFilters, excludeAction, StateWithHistory } from "redux-undo";
import { Action, Reducer } from "../../actions/reduxTypes";
import * as GraphUtils from "../../components/graph/utils/graphUtils";
import * as LayoutUtils from "../layoutUtils";
import { nodes } from "../layoutUtils";
import { mergeReducers } from "../mergeReducers";
import { GraphState } from "./types";
import {
    addNodesWithLayout,
    adjustBranchParametersAfterDisconnect,
    createEdge,
    enrichNodeWithProcessDependentData,
    prepareNewNodesWithLayout,
    updateAfterNodeDelete,
    updateLayoutAfterNodeIdChange,
} from "./utils";
import { ValidationResult } from "../../types";
import NodeUtils from "../../components/graph/NodeUtils";
import { batchGroupBy } from "./batchGroupBy";
import { NestedKeyOf } from "./nestedKeyOf";
import ProcessUtils from "../../common/ProcessUtils";
import { correctFetchedDetails } from "./correctFetchedDetails";

//TODO: We should change namespace from graphReducer to currentlyDisplayedProcess

const emptyGraphState: GraphState = {
    scenarioLoading: false,
    scenario: null,
    layout: [],
    testCapabilities: null,
    testFormParameters: null,
    selectionState: [],
    processCounts: {},
    testResults: null,
    unsavedNewName: null,
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
            const { scenario } = action;
            return {
                ...state,
                scenario,
                scenarioLoading: false,
                layout: LayoutUtils.fromMeta(scenario.scenarioGraph),
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
            const { history, lastDeployedAction, lastAction } = action;
            return {
                ...state,
                scenario: {
                    ...state.scenario,
                    history: history,
                    lastDeployedAction: lastDeployedAction,
                    lastAction: lastAction,
                },
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
        case "PROCESS_RENAME": {
            return {
                ...state,
                unsavedNewName: action.name,
            };
        }
        case "DELETE_NODES": {
            return action.ids.reduce((state, idToDelete) => {
                const stateAfterNodeDelete = updateAfterNodeDelete(state, idToDelete);
                const scenarioGraph = GraphUtils.deleteNode(stateAfterNodeDelete.scenario.scenarioGraph, idToDelete);
                return {
                    ...stateAfterNodeDelete,
                    scenario: {
                        ...stateAfterNodeDelete.scenario,
                        scenarioGraph: scenarioGraph,
                    },
                };
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
            const nodeWithPosition = {
                node: action.node,
                position: action.position,
            };
            const { uniqueIds, nodes, layout } = prepareNewNodesWithLayout(state, [nodeWithPosition], false);
            return {
                ...addNodesWithLayout(state, { nodes, layout }),
                selectionState: uniqueIds,
            };
        }
        case "NODES_WITH_EDGES_ADDED": {
            const { nodes, layout, uniqueIds } = prepareNewNodesWithLayout(state, action.nodesWithPositions, true);

            const idToUniqueId = zipObject(
                action.nodesWithPositions.map((n) => n.node.id),
                uniqueIds,
            );
            const edgesWithValidIds = action.edges.map((edge) => ({
                ...edge,
                from: idToUniqueId[edge.from],
                to: idToUniqueId[edge.to],
            }));

            const updatedEdges = edgesWithValidIds.reduce((edges, edge) => {
                const fromNode = nodes.find((n) => n.id === edge.from);
                const toNode = nodes.find((n) => n.id === edge.to);
                const currentNodeEdges = NodeUtils.getOutputEdges(fromNode.id, edges);
                const newEdge = createEdge(fromNode, toNode, edge.edgeType, currentNodeEdges, action.processDefinitionData);
                return edges.concat(newEdge);
            }, state.scenario.scenarioGraph.edges);

            const stateWithNodesAdded = addNodesWithLayout(state, { nodes, layout });
            return {
                ...stateWithNodesAdded,
                scenario: {
                    ...stateWithNodesAdded.scenario,
                    scenarioGraph: {
                        ...stateWithNodesAdded.scenario.scenarioGraph,
                        edges: updatedEdges,
                    },
                },
                selectionState: uniqueIds,
            };
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
            };
        }
        case "DISPLAY_TEST_RESULTS_DETAILS": {
            return {
                ...state,
                testResults: action.testResults,
                scenarioLoading: false,
            };
        }
        case "HIDE_RUN_PROCESS_DETAILS": {
            return {
                ...state,
                testResults: null,
                processCounts: null,
            };
        }
        case "EXPAND_SELECTION": {
            return {
                ...state,
                selectionState: uniq([...state.selectionState, ...action.nodeIds]),
            };
        }
        case "TOGGLE_SELECTION": {
            return {
                ...state,
                selectionState: xor(state.selectionState, action.nodeIds),
            };
        }
        case "RESET_SELECTION": {
            return {
                ...state,
                selectionState: action.nodeIds ? action.nodeIds : [],
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
});

const pick = <T extends NonNullable<unknown>>(object: T, props: NestedKeyOf<T>[]) => _pick(object, props);
const omit = <T extends NonNullable<unknown>>(object: T, props: NestedKeyOf<T>[]) => _omit(object, props);

const pickKeys: NestedKeyOf<GraphState>[] = ["scenario", "unsavedNewName", "layout", "selectionState"];
const omitKeys: NestedKeyOf<GraphState>[] = [
    "scenario.validationResult",
    "scenario.lastDeployedAction",
    "scenario.lastAction",
    "scenario.history",
];

const getUndoableState = (state: GraphState) => omit(pick(state, pickKeys), omitKeys.concat(["scenario.validationResult"]));
const getNonUndoableState = (state: GraphState) => defaultsDeep(omit(state, pickKeys), pick(state, omitKeys));

const undoableReducer = undoable<GraphState, Action>(reducer, {
    ignoreInitialState: true,
    clearHistoryType: [UndoActionTypes.CLEAR_HISTORY, "PROCESS_FETCH"],
    groupBy: batchGroupBy.init(),
    filter: combineFilters((action, nextState, prevState) => {
        return !isEqual(getUndoableState(nextState), getUndoableState(prevState._latestUnfiltered));
    }, excludeAction(["VALIDATION_RESULT", "UPDATE_IMPORTED_PROCESS", "PROCESS_STATE_LOADED", "UPDATE_TEST_CAPABILITIES", "UPDATE_BACKEND_NOTIFICATIONS", "PROCESS_DEFINITION_DATA", "PROCESS_TOOLBARS_CONFIGURATION_LOADED", "CORRECT_INVALID_SCENARIO", "DISPLAY_PROCESS_ACTIVITY", "LOGGED_USER", "REGISTER_TOOLBARS", "UI_SETTINGS"])),
});

// apply only undoable changes for undo actions
function fixUndoableHistory(state: StateWithHistory<GraphState>, action: Action): StateWithHistory<GraphState> {
    const nextState = undoableReducer(state, action);

    if (Object.values(UndoActionTypes).includes(action.type)) {
        const present = defaultsDeep(getUndoableState(nextState.present), getNonUndoableState(state?.present));
        return { ...nextState, present };
    }

    return nextState;
}

//TODO: replace this with use of selectors everywhere
export const reducerWithUndo: Reducer<GraphState & { history: StateWithHistory<GraphState> }> = (state, action) => {
    const history = fixUndoableHistory(state?.history, action);
    return { ...history.present, history };
};
