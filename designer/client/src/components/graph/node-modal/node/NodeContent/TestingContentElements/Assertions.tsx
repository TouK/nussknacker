import { Box, Typography } from "@mui/material";
import { omit } from "lodash";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import type { AssertionOperator } from "../../../../../../actions/nk/testCasesActions";
import { clearTestCaseNodeAssertionResult, setTestCaseAssertions } from "../../../../../../actions/nk/testCasesActions";
import httpService from "../../../../../../http/HttpService/instance";
import { getTestCaseAssertionsForNode } from "../../../../../../reducers/selectors/testCases";
import { getTestAssertionResultsForNode } from "../../../../../../reducers/selectors/testing";
import { useAppDispatch, useAppSelector } from "../../../../../../store/storeHelpers";
import type { Edge } from "../../../../../../types/edge";
import type { NodeType } from "../../../../../../types/node";
import type { VariableTypes } from "../../../../../../types/validation";
import type { ContextData } from "../../../../../dataMapper/DataMapper";
import { withUuid } from "../../../appendUuid";
import { useHelpText } from "../../../editors/expression/helpText";
import { ExpressionLang } from "../../../editors/expression/types";
import { InfoTooltip } from "../../../editors/InfoTooltip/InfoTooltip";
import { ParamKeyProvider } from "../../../editors/ParamKeyProvider";
import { useInputOutputContext } from "../../../io/InputOutputContext";
import { NodeRowFieldsProvider } from "../../../node-row-fields-provider/NodeRowFieldsProvider";
import { NodeTable } from "../../../NodeDetailsContent/NodeTable";
import { getProcessName, getProcessProperties } from "../../../NodeDetailsContent/selectors";
import { OverrideKeys } from "../../../parameterHelpers";
import { useGetNodeTestCasesErrors, useValidation, useVariableTypes } from "../../../useNodeTypeDetailsContentLogic";
import { AssertionItem } from "./AssertionItem";
import { StyledStack } from "./components/Styled";
import { TestingExpandable } from "./components/TestingExpandable";

function AssertionHelpTooltip() {
    return (
        <InfoTooltip
            variant="hover"
            customComponentsProps={{ tooltip: { sx: { maxWidth: 400 } } }}
            title={useHelpText(ExpressionLang.SpEL)}
        />
    );
}

interface Props {
    node: NodeType;
    edges: Edge[];
}

export const Assertions = ({ node, edges }: Props) => {
    const { t } = useTranslation();
    const [isExpanded, setIsExpanded] = useState(true);
    const dispatch = useAppDispatch();
    const testCaseAssertions = useAppSelector((state) => getTestCaseAssertionsForNode(state, node.id));
    const testAssertionResults = useAppSelector((state) => getTestAssertionResultsForNode(state, node.id));
    const ioContext = useInputOutputContext();
    const getAvailableContexts = ioContext?.getAvailableContexts;
    const MAX_RECORDS_CONTEXT = 20;
    const recordsContextData = useMemo<ContextData | undefined>(() => {
        const [inputContexts] = getAvailableContexts?.("input") ?? [[]];
        if (!inputContexts.length) return undefined;
        const records = inputContexts
            .slice(0, MAX_RECORDS_CONTEXT)
            .map((ctx) => Object.fromEntries(Object.keys(ctx.variables).map((k) => [k, ctx.variables[k].pretty])));
        return { records };
    }, [getAvailableContexts]);
    const testCasesErrors = useGetNodeTestCasesErrors(node);

    useValidation({ node, showValidation: true, edges });
    const scenarioName = useAppSelector(getProcessName);
    const processProperties = useAppSelector(getProcessProperties);
    const nodeVariableTypes = useVariableTypes({ node });
    const [assertionVariableTypes, setAssertionVariableTypes] = useState<VariableTypes>({});

    useEffect(() => {
        const fetchAssertionVariableTypes = async () => {
            const response = await httpService.fetchTestCaseNodeAdditionalVariables(scenarioName, {
                variableTypes: nodeVariableTypes,
                nodeData: node,
                scenarioProperties: processProperties,
            });
            setAssertionVariableTypes(omit(response.assertionsAdditionalVariables, "TESTS") || {});
        };

        fetchAssertionVariableTypes();
    }, [scenarioName, nodeVariableTypes, node, processProperties]);

    const addAssertion = useCallback(() => {
        dispatch(
            setTestCaseAssertions(node.id, (prev) =>
                prev.concat(
                    withUuid({
                        expected: { expression: "", language: "spel" },
                        operator: "equals" as const,
                        actual: { expression: "", language: "spel" },
                    } as const),
                ),
            ),
        );
    }, [dispatch, node.id]);

    const removeAssertion = useCallback(
        (_, uuid: string) => {
            const assertionIndex = testCaseAssertions.findIndex((item) => item.uuid === uuid);
            dispatch(setTestCaseAssertions(node.id, (prev) => prev.filter((item) => item.uuid !== uuid)));
            if (assertionIndex !== -1) {
                dispatch(clearTestCaseNodeAssertionResult(node.id, assertionIndex));
            }
        },
        [dispatch, node.id, testCaseAssertions],
    );

    const editAssertion = useCallback(
        (
            uuid: string,
            updated: Partial<{
                description: string;
                expected: { expression: string; language: string };
                operator: AssertionOperator;
                actual: { expression: string; language: string };
            }>,
        ) => {
            dispatch(setTestCaseAssertions(node.id, (prev) => prev.map((item) => (item.uuid === uuid ? { ...item, ...updated } : item))));
        },
        [dispatch, node.id],
    );

    return (
        <StyledStack>
            <TestingExpandable
                componentId={"Assertions"}
                expandableTitle={
                    <Box display={"flex"} gap={2} alignItems={"center"}>
                        <Typography variant={"body2"} color={"text.secondary"}>
                            {t("testingDialog.label.assertions", "Assertions")}
                        </Typography>
                        <ParamKeyProvider custom={OverrideKeys.AssertionActual}>
                            <AssertionHelpTooltip />
                        </ParamKeyProvider>
                    </Box>
                }
                expanded={isExpanded}
                onChange={setIsExpanded}
            >
                {testCaseAssertions.length === 0 && (
                    <Typography variant={"body2"}>{t("assertions.noAssertionsDefined", "No assertions defined")}</Typography>
                )}
                <NodeTable sx={{ mx: 0 }}>
                    <NodeRowFieldsProvider
                        path={null}
                        label=""
                        onFieldRemove={removeAssertion}
                        onFieldAdd={addAssertion}
                        readOnly={false}
                        errors={[]}
                    >
                        {testCaseAssertions.map(({ description, expected, operator, actual, uuid }, index) => (
                            <AssertionItem
                                key={uuid}
                                uuid={uuid}
                                onChange={editAssertion}
                                description={description}
                                expected={expected}
                                operator={operator}
                                actual={actual}
                                node={node}
                                variableTypes={assertionVariableTypes}
                                contextData={recordsContextData}
                                testAssertionResult={testAssertionResults?.[index]}
                                index={index}
                                errors={testCasesErrors.assertionsErrors[index.toString()]}
                            />
                        ))}
                    </NodeRowFieldsProvider>
                </NodeTable>
            </TestingExpandable>
        </StyledStack>
    );
};
