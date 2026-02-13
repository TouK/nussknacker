import { Typography } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";
import z from "zod";

import { unsafe_applyScenarioChanges } from "../../../actions/nk/process";
import ProcessUtils from "../../../common/ProcessUtils";
import { getSavedScenario, getScenario, getScenarioGraph } from "../../../reducers/selectors/graph";
import { getProcessState } from "../../../reducers/selectors/scenarioState";
import { useAppDispatch } from "../../../store/storeHelpers";
import { DefaultToolComponent } from "../components/DefaultToolComponent";
import { rejectToolCall, useFrontendAiTool } from "../useFrontendAiTool";

export const ScenarioDataAIToolkit = () => {
    const dispatch = useAppDispatch();
    const { t } = useTranslation();

    useFrontendAiTool({
        toolName: "get_scenario",
        description: `Returns the raw JSON data of the currently opened scenario. The response contains 'nodes' array, 'edges' array, and 'properties' object. Each node has an 'id' field. IMPORTANT: Always check the actual array length in the response before using array indexes - do not assume or estimate array sizes.`,
        render: (props) => (
            <DefaultToolComponent {...props}>
                <Typography>{t("aiAssistant.tools.getScenarioData", "Get scenario data")}</Typography>
            </DefaultToolComponent>
        ),
        parameters: z.object({
            draft: z
                .boolean()
                .optional()
                .describe("If true, returns current draft with unsaved changes. If false, returns last saved version."),
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
        description: `Modifies the currently opened scenario by replacing values at specified paths. You can target specific nodes using nodeId or use full paths. BEFORE using this tool, you MUST call get_scenario to read the current data and count array lengths. All changes are validated - if any change fails, none will be applied.`,
        render: (props) => (
            <DefaultToolComponent {...props}>
                <Typography>{t("aiAssistant.tools.changeScenarioGraph", "Change scenario graph")}</Typography>
            </DefaultToolComponent>
        ),
        parameters: z.object({
            changes: z
                .array(
                    z
                        .object({
                            nodeId: z.string().optional().describe("if this is specified path should root from node with this id"),
                            path: z
                                .string()
                                .describe(
                                    [
                                        `Dot-notation path with numeric array indexes.`,
                                        `If nodeId is provided, path is relative to that node (e.g., 'value' or 'expression.expression').`,
                                        `If nodeId is NOT provided, use full path with array indexes (e.g., 'nodes[0].value', 'properties.name').`,
                                        `WRONG: 'nodes[#nodeId].value', 'nodes[someExpression].value', 'nodes.0.value'.`,
                                        `Array indexes must be plain integers. Count items in get_scenario response to determine valid indexes.`,
                                    ].join(" "),
                                ),
                            value: z
                                // TODO: more types or any on BE
                                .string()
                                .describe(
                                    `The string value to set. Can be a plain string or a SpEL expression string (e.g., "#input.value" or "42").`,
                                ),
                        })
                        .describe(`A single field modification.`),
                )
                .describe(`List of changes to apply atomically. No duplicate paths, no unchanged values.`),
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
        description: `Returns current validation status including errors, warnings, and scenario state. Use this to check if the scenario is ready for deployment.`,
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

    return null;
};
