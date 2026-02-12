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
        description: `Get full raw data of opened scenario graph. Returns nodes and edges. Returned data changes frequently.`,
        render: (props) => (
            <DefaultToolComponent {...props}>
                <Typography>Get scenario data</Typography>
            </DefaultToolComponent>
        ),
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
        toolName: "change_scenario_values",
        description: `Replace values in raw data of opened scenario. Multiple validation layers are enforced, including schema validation, value validation, and full JSON integrity checks. If any validation fails, no changes will be applied. You must return a corrected change list that fully complies with all validation rules.`,
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
                                    "Dot-notation path to the modified field inside the original JSON. Use . for objects and [index] for array elements (e.g., edges[2].from).",
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
                    "Each array item must represent exactly one field change. Do not include duplicate paths. Do not include unchanged values. Split changes to multiple atomic end-value edits.",
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
        description: `Get scenario problems, validation state and status`,
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
