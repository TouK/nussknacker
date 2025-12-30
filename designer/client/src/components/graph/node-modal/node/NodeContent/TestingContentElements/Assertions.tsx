import { Box, Typography } from "@mui/material";
import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";

import { setTestCaseAssertions } from "../../../../../../actions/nk/testCasesActions";
import { getTestCaseAssertionsForNode } from "../../../../../../reducers/selectors/testCases";
import { getTestAssertionResultsForNode } from "../../../../../../reducers/selectors/testing";
import { useAppDispatch, useAppSelector } from "../../../../../../store/storeHelpers";
import type { NodeType } from "../../../../../../types/node";
import { StyledButton } from "../../../../styledButton";
import { EditableEditor } from "../../../editors/EditableEditor";
import { EditorType } from "../../../editors/expression/types";
import { NodeTable } from "../../../NodeDetailsContent/NodeTable";
import { AssertionStatus } from "./AssertionStatus";
import { StyledStack } from "./components/Styled";

interface Props {
    node: NodeType;
}

export const Assertions = ({ node }: Props) => {
    const { t } = useTranslation();
    const dispatch = useAppDispatch();
    const testCaseAssertions = useAppSelector((state) => getTestCaseAssertionsForNode(state, node.id));
    const testAssertionResults = useAppSelector((state) => getTestAssertionResultsForNode(state, node.id));

    const addAssertion = useCallback(() => {
        dispatch(
            setTestCaseAssertions(node.id, (prev) =>
                prev.concat({ expression: { expression: "#TESTS.assertEquals()", language: "spel" } }),
            ),
        );
    }, [dispatch, node.id]);

    const removeAssertion = useCallback(
        (index: number) => {
            dispatch(setTestCaseAssertions(node.id, (prev) => prev.filter((_, i) => i !== index)));
        },
        [dispatch, node.id],
    );

    const editAssertion = useCallback(
        (index: number, updated: Partial<{ expression: { expression: string; language: string } }>) => {
            dispatch(setTestCaseAssertions(node.id, (prev) => prev.map((item, i) => (i === index ? { ...item, ...updated } : item))));
        },
        [dispatch, node.id],
    );

    return (
        <StyledStack>
            <Typography m={0} variant="h5">
                {t("testingDialog.label.assertions", "Assertions")}
            </Typography>
            {testCaseAssertions.map(({ expression: expressionObj }, index) => {
                const testAssertionResult = testAssertionResults?.[index];
                return (
                    <Box key={index} display={"flex"} alignItems={"end"}>
                        <NodeTable sx={{ flex: 1, m: 0 }}>
                            <EditableEditor
                                editors={[{ type: EditorType.SPEL_PARAMETER_EDITOR }]}
                                expressionObj={expressionObj}
                                variableTypes={{}}
                                onValueChange={(expression) => editAssertion(index, { expression })}
                                fieldErrors={[]}
                            />
                        </NodeTable>
                        <StyledButton
                            title={t("node.row.remove.title", "Remove field")}
                            onClick={() => removeAssertion(index)}
                            sx={{ ml: 1 }}
                        >
                            {t("node.row.remove.text", "-")}
                        </StyledButton>
                        <Box sx={{ mb: 0.5, ml: 1, display: "flex", alignItems: "center" }}>
                            {testAssertionResult && (
                                <AssertionStatus
                                    status={testAssertionResult.type === "SuccessfulAssertion" ? "success" : "error"}
                                    message={testAssertionResult.type === "FailedAssertion" ? testAssertionResult.message : undefined}
                                />
                            )}
                        </Box>
                    </Box>
                );
            })}

            <StyledButton title={t("node.row.add.title", "Add field")} onClick={addAssertion} sx={{ mt: 2 }}>
                {t("node.row.add.text", "+")}
            </StyledButton>
        </StyledStack>
    );
};
