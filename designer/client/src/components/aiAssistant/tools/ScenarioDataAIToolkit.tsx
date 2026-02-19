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
        description: `Modifies scenario by replacing values at paths using dot-notation with array indexes. MUST retrieve scenario data first to get correct array indexes. Use for any scenario modifications: single nodes, bulk edits, properties, edges. CRITICAL ATOMIC: if ANY change fails, ENTIRE batch rejected and NO changes apply. Validation runs automatically - errors returned in validationResult but changes still apply (non-blocking validation).`,
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
                            path: z
                                .string()
                                .describe(
                                    "Dot-notation path with integer indexes from scenario data. Examples: 'nodes[0].id', 'nodes[2].value', 'edges[1].from', 'edges[3].to', 'properties.parallelism'. NEVER guess indexes - always use actual values from retrieved scenario.",
                                ),
                            value: z
                                // TODO: more types or any on BE
                                .string()
                                .describe(
                                    `New value to set. String for names/ids, SpEL expression for logic (e.g., "#input.value", "42").`,
                                ),
                        })
                        .describe(`Single path+value modification.`),
                )
                .describe(
                    "Array of changes applied atomically in ONE call. This tool modifies EXISTING nodes/edges/properties only - do NOT add/remove nodes. When changing nodes (e.g., renaming nodeId, modifying outputVariableNames), related edges and branch parameters are corrected automatically - do NOT manually update edge references (from/to) or edgeTypes. Only change nodes properties, let the system handle edge updates. NEVER split related changes into separate calls - entire batch must succeed or all changes are rejected.",
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
            const { validationResult } = result;
            return { nodes, edges, validationResult };
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
