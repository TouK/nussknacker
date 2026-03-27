import { Box, Typography } from "@mui/material";
import { omit } from "lodash";
import React, { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import type { AssertionOperator } from "../../../../../../actions/nk/testCasesActions";
import { setTestCaseAssertions } from "../../../../../../actions/nk/testCasesActions";
import httpService from "../../../../../../http/HttpService/instance";
import { getProcessingType } from "../../../../../../reducers/selectors/graph";
import { getTestCaseAssertionsForNode } from "../../../../../../reducers/selectors/testCases";
import { getTestAssertionResultsForNode } from "../../../../../../reducers/selectors/testing";
import { useAppDispatch, useAppSelector } from "../../../../../../store/storeHelpers";
import type { Edge } from "../../../../../../types/edge";
import type { NodeType } from "../../../../../../types/node";
import type { VariableTypes } from "../../../../../../types/validation";
import { Expandable } from "../../../../../common/Expandable";
import { withUuid } from "../../../appendUuid";
import { InfoTooltip } from "../../../editors/InfoTooltip/InfoTooltip";
import { NodeRowFieldsProvider } from "../../../node-row-fields-provider/NodeRowFieldsProvider";
import { NodeTable } from "../../../NodeDetailsContent/NodeTable";
import { useGetNodeTestCasesErrors, useValidation, useVariableTypes } from "../../../useNodeTypeDetailsContentLogic";
import { AssertionItem } from "./AssertionItem";
import { StyledStack } from "./components/Styled";

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
    const testCasesErrors = useGetNodeTestCasesErrors(node);

    useValidation({ node, showValidation: true, edges });
    const processingType = useAppSelector(getProcessingType);
    const nodeVariableTypes = useVariableTypes({ node });
    const [assertionVariableTypes, setAssertionVariableTypes] = useState<VariableTypes>({});

    useEffect(() => {
        const fetchAssertionVariableTypes = async () => {
            const response = await httpService.fetchTestCaseNodeAdditionalVariables(processingType, { variableTypes: nodeVariableTypes });
            setAssertionVariableTypes(omit(response.assertionsAdditionalVariables, "TESTS") || {});
        };

        fetchAssertionVariableTypes();
    }, [processingType, nodeVariableTypes]);

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
            dispatch(setTestCaseAssertions(node.id, (prev) => prev.filter((item) => item.uuid !== uuid)));
        },
        [dispatch, node.id],
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
            <Expandable
                componentId={"Assertions"}
                expandableTitle={
                    <Box display={"flex"} gap={2} alignItems={"center"}>
                        <Typography variant={"body2"} color={"text.secondary"}>
                            {t("testingDialog.label.assertions", "Assertions")}
                        </Typography>
                        <InfoTooltip
                            variant={"hover"}
                            customComponentsProps={{ tooltip: { sx: { maxWidth: 400 } } }}
                            title={t(
                                "testingDialog.assertions.hint",
                                "Use **#records** to access all events that passed through this node.\n\ncount `#records.size()`  \nfield access `#records[0].status`  \nfilter `#records.?[#this.amount > 100].size()`",
                            )}
                        />
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
                                variableTypes={assertionVariableTypes}
                                testAssertionResult={testAssertionResults?.[index]}
                                index={index}
                                errors={testCasesErrors.assertionsErrors[index.toString()]}
                            />
                        ))}
                    </NodeRowFieldsProvider>
                </NodeTable>
            </Expandable>
        </StyledStack>
    );
};
