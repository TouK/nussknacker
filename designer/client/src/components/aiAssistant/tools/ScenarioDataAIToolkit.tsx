import z from "zod";

import { unsafe_applyScenarioChanges } from "../../../actions/nk/process";
import ProcessUtils from "../../../common/ProcessUtils";
import { getSavedScenario, getScenarioGraph } from "../../../reducers/selectors/graph";
import { useAppDispatch } from "../../../store/storeHelpers";
import {
    getComponentsDefinition,
    getDynamicParameterDefinitions,
    getFindAvailableVariables,
} from "../../graph/node-modal/NodeDetailsContent/selectors";
import { rejectToolCall, useFrontendAiTool } from "../useFrontendAiTool";

export const ScenarioDataAIToolkit = () => {
    const dispatch = useAppDispatch();

    useFrontendAiTool({
        toolName: "get_scenario",
        description: `Get full raw data of opened scenario graph. Returns nodes and edges. Returned data changes frequently.`,
        parameters: z.object({ draft: z.boolean().optional().describe("get draft or saved version without draft changes") }),
        execute: async ({ draft }) => {
            return dispatch((_, getState) => {
                const state = getState();
                if (draft) {
                    const { nodes, edges } = getScenarioGraph(state);
                    return { nodes, edges };
                } else {
                    const {
                        scenarioGraph: { nodes, edges },
                    } = getSavedScenario(state);
                    return { nodes, edges };
                }
            });
        },
    });

    useFrontendAiTool({
        toolName: "change_scenario_graph",
        description: `Replace values in raw data of opened scenario. Multiple validation layers are enforced, including schema validation, value validation, and full JSON integrity checks. If any validation fails, no changes will be applied. You must return a corrected change list that fully complies with all validation rules.`,
        parameters: z.object({
            changes: z
                .array(
                    z
                        .object({
                            path: z
                                .string()
                                .describe(
                                    "Dot-notation path to the modified field inside the original JSON. Use . for objects and [index] for array elements (e.g., a.b[0].c).",
                                ),
                            value: z
                                // TODO: more types or any on BE
                                .string()
                                .describe(
                                    "The new value that should replace the value at the specified path. Must be the final value after modification.",
                                ),
                        })
                        .describe("Represents a single value modification in raw data of opened scenario."),
                )
                .describe(
                    "Each array item must represent exactly one field change. Do not include duplicate paths. Do not include unchanged values.",
                ),
        }),
        execute: async ({ changes }) => {
            const result = await dispatch(unsafe_applyScenarioChanges(changes));
            if (typeof result === "string") {
                return rejectToolCall(JSON.stringify(result));
            }
            if ("errors" in result) {
                return rejectToolCall(JSON.stringify(result.errors));
            }
            const { nodes, edges } = result.scenario;
            return { nodes, edges };
        },
    });

    useFrontendAiTool({
        toolName: "get_validation_results",
        description: `not implemented!`,
        parameters: z.object({}),
        execute: () => {
            throw "not implemented!";
        },
    });

    useFrontendAiTool({
        toolName: "get_components",
        description: `not implemented!`,
        parameters: z.object({}),
        execute: () => {
            throw "not implemented!";
        },
    });

    useFrontendAiTool({
        toolName: "add_new_node",
        description: `not implemented!`,
        parameters: z.object({}),
        execute: () => {
            throw "not implemented!";
        },
    });

    useFrontendAiTool({
        toolName: "get_node_context",
        description: `Get variables context and definitons available in node fields`,
        parameters: z.object({
            nodeId: z.string().describe("id/name of edited node"),
        }),
        execute: async ({ nodeId }) => {
            return dispatch((_, getState) => {
                const state = getState();
                const before = getScenarioGraph(state)?.nodes.find((n) => n.id === nodeId);
                const dynamicParameterDefinitions = getDynamicParameterDefinitions(state, before);
                const availableVariables = getFindAvailableVariables(state)?.(before.id);
                const componentsDefinition = getComponentsDefinition(state);
                const componentDefinition = ProcessUtils.extractComponentDefinition(before, componentsDefinition);
                return { dynamicParameterDefinitions, componentDefinition, availableVariables };
            });
        },
    });

    return null;
};
