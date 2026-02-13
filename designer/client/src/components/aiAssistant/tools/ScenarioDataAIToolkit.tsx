import { Typography } from "@mui/material";
import React from "react";
import z from "zod";

import { unsafe_applyScenarioChanges } from "../../../actions/nk/process";
import ProcessUtils from "../../../common/ProcessUtils";
import { getSavedScenario, getScenario, getScenarioGraph } from "../../../reducers/selectors/graph";
import { getProcessState } from "../../../reducers/selectors/scenarioState";
import { useAppDispatch } from "../../../store/storeHelpers";
import {
    getComponentsDefinition,
    getDynamicParameterDefinitions,
    getFindAvailableVariables,
} from "../../graph/node-modal/NodeDetailsContent/selectors";
import { DefaultToolComponent } from "../components/DefaultToolComponent";
import { rejectToolCall, useFrontendAiTool } from "../useFrontendAiTool";

export const ScenarioDataAIToolkit = () => {
    const dispatch = useAppDispatch();

    useFrontendAiTool({
        toolName: "get_scenario",
        description: `Use this tool to get the raw data of the currently opened scenario graph. It returns the nodes, edges, and properties of the scenario. The data is returned as a JSON object, with nodes and edges as flat arrays. Each node in the array is an object characterized by a unique 'id' (string) and a 'type' (string) and each edge connects two nodes by their IDs. This data can change frequently as the user edits the scenario. CRITICAL: When using this data to modify the scenario, pay close attention to array indexes (e.g., in the 'nodes' array). Always verify the array length before accessing an index to avoid errors.`,
        render: (props) => (
            <DefaultToolComponent {...props}>
                <Typography>Get scenario data</Typography>
            </DefaultToolComponent>
        ),
        parameters: z.object({
            draft: z
                .boolean()
                .optional()
                .describe(
                    "Set to true to get the current draft of the scenario, including any unsaved changes. Set to false to get the last saved version of the scenario.",
                ),
        }),
        execute: async ({ draft }) => {
            return dispatch((_, getState) => {
                const state = getState();
                if (draft) {
                    const { nodes, edges, properties } = getScenarioGraph(state);
                    return { nodes, edges, properties };
                } else {
                    const {
                        scenarioGraph: { nodes, edges, properties },
                    } = getSavedScenario(state);
                    return { nodes, edges, properties };
                }
            });
        },
    });

    useFrontendAiTool({
        toolName: "change_scenario_values",
        description: `Use this tool to modify the raw data of the currently opened scenario. This tool is powerful but requires careful use. It applies changes by replacing values at specified paths in the scenario's JSON data. The tool enforces multiple validation layers, including schema validation, value validation, and JSON integrity checks. If any validation fails, no changes will be applied. You must provide a list of changes that fully complies with all validation rules. IMPORTANT: If this tool returns an error, it typically indicates an issue with the change itself, most often an incorrect 'path' in one of the change objects.`,
        render: (props) => (
            <DefaultToolComponent {...props}>
                <Typography>Change scenario graph</Typography>
            </DefaultToolComponent>
        ),
        parameters: z.object({
            changes: z
                .array(
                    z
                        .object({
                            path: z
                                .string()
                                .describe(
                                    `A dot-notation path to the field to be modified within the scenario's JSON data. Use '.' for nested objects and '[index]' for array elements (e.g., 'nodes[2].expression.expression'). The index in array elements MUST be a number, not an expression (like SpEL). Before using an index for an array like 'nodes', ALWAYS confirm the number of elements in that array to prevent out-of-bounds errors. For example, if 'nodes' has 3 elements, the valid indexes are 0, 1, and 2.`,
                                ),
                            value: z
                                // TODO: more types or any on BE
                                .string()
                                .describe(`The new value to be set at the specified path. This must be the final, serialized value.`),
                        })
                        .describe(`Represents a single, atomic modification to the raw data of the currently opened scenario.`),
                )
                .describe(
                    `An array of change objects. Each object must represent exactly one field modification. Do not include duplicate paths or unchanged values. It is recommended to split complex modifications into multiple, atomic, end-value edits.`,
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
        description: `Use this tool to get the current validation status of the scenario. It returns a list of errors, warnings, and other validation problems. It also provides the overall scenario state, which can be useful for understanding if the scenario is ready for deployment.`,
        parameters: z.object({}),
        execute: () => {
            return dispatch((_, getState) => {
                const state = getState();
                const scenario = getScenario(state);
                const scenarioState = getProcessState(state);
                const { errors, validationErrors, validationWarnings, warnings } = ProcessUtils.getValidationResult(scenario);
                return { errors, validationErrors, validationWarnings, warnings, scenarioState };
            });
        },
    });

    // TODO: useless for now
    // useFrontendAiTool({
    //     toolName: "get_node_context",
    //     description: `Get variables context and definitons available in node fields`,
    //     parameters: z.object({
    //         nodeId: z.string().describe("id/name of edited node"),
    //     }),
    //     execute: async ({ nodeId }) => {
    //         return dispatch((_, getState) => {
    //             const state = getState();
    //
    //             const before = getScenarioGraph(state)?.nodes.find((n) => n.id === nodeId);
    //             if (!before) return rejectToolCall("no such node!");
    //
    //             const dynamicParameterDefinitions = getDynamicParameterDefinitions(state, before);
    //             const availableVariables = getFindAvailableVariables(state)?.(before.id);
    //             const componentsDefinition = getComponentsDefinition(state);
    //             const componentDefinition = ProcessUtils.extractComponentDefinition(before, componentsDefinition);
    //             return { dynamicParameterDefinitions, componentDefinition, availableVariables };
    //         });
    //     },
    // });

    return null;
};
