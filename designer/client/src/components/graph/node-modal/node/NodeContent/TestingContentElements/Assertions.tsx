import { Typography } from "@mui/material";
import React, { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";

import { setTestCaseAssertions } from "../../../../../../actions/nk/testCasesActions";
import { getTestCaseAssertionsForNode } from "../../../../../../reducers/selectors/testCases";
import { getTestAssertionResultsForNode } from "../../../../../../reducers/selectors/testing";
import { useAppDispatch, useAppSelector } from "../../../../../../store/storeHelpers";
import type { NodeType } from "../../../../../../types/node";
import { Expandable } from "../../../../../common/Expandable";
import { withUuid } from "../../../appendUuid";
import { NodeRowFieldsProvider } from "../../../node-row-fields-provider/NodeRowFieldsProvider";
import { NodeTable } from "../../../NodeDetailsContent/NodeTable";
import { useVariableTypes } from "../../../useNodeTypeDetailsContentLogic";
import { AssertionItem } from "./AssertionItem";
import { StyledStack } from "./components/Styled";

interface Props {
    node: NodeType;
}

export const Assertions = ({ node }: Props) => {
    const { t } = useTranslation();
    const [isExpanded, setIsExpanded] = useState(true);
    const dispatch = useAppDispatch();
    const testCaseAssertions = useAppSelector((state) => getTestCaseAssertionsForNode(state, node.id));
    const testAssertionResults = useAppSelector((state) => getTestAssertionResultsForNode(state, node.id));
    const variableTypes = useVariableTypes({ node });

    const addAssertion = useCallback(() => {
        dispatch(
            setTestCaseAssertions(node.id, (prev) =>
                prev.concat(withUuid({ expression: { expression: "#TESTS.assertEquals()", language: "spel" } })),
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
        (uuid: string, updated: Partial<{ expression: { expression: string; language: string } }>) => {
            dispatch(setTestCaseAssertions(node.id, (prev) => prev.map((item) => (item.uuid === uuid ? { ...item, ...updated } : item))));
        },
        [dispatch, node.id],
    );

    return (
        <StyledStack>
            <Expandable componentId={"Assertions"} expandableTitle={"Assertions"} expanded={isExpanded} onChange={setIsExpanded}>
                <NodeTable sx={{ mx: 0 }}>
                    <NodeRowFieldsProvider
                        path={null}
                        label=""
                        onFieldRemove={removeAssertion}
                        onFieldAdd={addAssertion}
                        readOnly={false}
                        errors={[]}
                    >
                        <>
                            {testCaseAssertions.length === 0 && (
                                <Typography>{t("assertions.noAssertionsDefined", "No assertions defined")}</Typography>
                            )}

                            {testCaseAssertions.map(({ expression: expressionObj, uuid }, index) => (
                                <AssertionItem
                                    key={uuid}
                                    uuid={uuid}
                                    onChange={editAssertion}
                                    expressionObj={expressionObj}
                                    variableTypes={variableTypes}
                                    testAssertionResult={testAssertionResults?.[index]}
                                    index={index}
                                />
                            ))}
                        </>
                    </NodeRowFieldsProvider>
                </NodeTable>
            </Expandable>
        </StyledStack>
    );
};
