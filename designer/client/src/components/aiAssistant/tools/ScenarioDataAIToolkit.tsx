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
        description: `Modifies scenario by replacing values at paths. Use nodeId for node-relative paths or full paths with indexes. MUST call get_scenario first to count array lengths. CRITICAL ATOMIC BEHAVIOR: If ANY change fails (invalid path/nodeId/value), ENTIRE batch is rejected and NO changes apply. On retry, resend ALL changes, not just the fixed one. Changes apply sequentially in array order.`,
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
                            nodeId: z
                                .string()
                                .optional()
                                .describe(
                                    "Makes path relative to this node. CRITICAL: ALL nodeIds resolved to indexes BEFORE changes apply, using current state. When renaming node: ALL changes targeting it MUST use OLD id (new id doesn't exist during resolution). Cannot mix old/new ids in same batch.",
                                ),
                            path: z
                                .string()
                                .describe(
                                    "Dot-notation with integer indexes. With nodeId: relative path like 'value', 'expression.expression', 'id'. Without nodeId: full path like 'nodes[0].value', 'edges[2].from'. Count array lengths in get_scenario response. When renaming node: update ALL referencing edges in SAME batch with NEW id value, or entire batch fails.",
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
                .describe(
                    "Array of changes applied atomically. No duplicate paths. When renaming node: use OLD id in nodeId param for all changes to that node, use NEW id as value in edge paths like 'edges[i].from'.",
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
