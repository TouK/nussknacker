import { Box, Stack, Typography } from "@mui/material";
import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";

import { setTestingAssertions } from "../../../../../../actions/nk/displayTestResults";
import { getTestingAssertionForNode } from "../../../../../../reducers/selectors/graph";
import { useAppDispatch, useAppSelector } from "../../../../../../store/storeHelpers";
import type { NodeType } from "../../../../../../types/node";
import { StyledButton } from "../../../../styledButton";
import { EditableEditor } from "../../../editors/EditableEditor";
import { EditorType } from "../../../editors/expression/types";
import { NodeTable } from "../../../NodeDetailsContent/NodeTable";
import { AssertionStatus } from "./AssertionStatus";

interface Props {
    node: NodeType;
}

export const Assertions = ({ node }: Props) => {
    const { t } = useTranslation();
    const dispatch = useAppDispatch();
    const testingAssertions = useAppSelector((state) => getTestingAssertionForNode(state, node.id));

    const addAssertion = useCallback(() => {
        dispatch(setTestingAssertions(node.id, (prev) => prev.concat({ expression: "", language: "spel" })));
    }, [dispatch, node.id]);

    const removeAssertion = useCallback(
        (index: number) => {
            dispatch(setTestingAssertions(node.id, (prev) => prev.filter((_, i) => i !== index)));
        },
        [dispatch, node.id],
    );

    const editAssertion = useCallback(
        (index: number, updated: Partial<{ expression: string; language: string }>) => {
            dispatch(setTestingAssertions(node.id, (prev) => prev.map((item, i) => (i === index ? { ...item, ...updated } : item))));
        },
        [dispatch, node.id],
    );

    return (
        <Stack p={3} gap={2}>
            <Typography m={0} variant="h5">
                {t("testingDialog.label.assertions", "Assertions")}
            </Typography>
            {testingAssertions.map((expressionObj, index) => (
                <Box key={index} display={"flex"} alignItems={"end"}>
                    <NodeTable sx={{ flex: 1, m: 0 }}>
                        <EditableEditor
                            editors={[{ type: EditorType.SPEL_PARAMETER_EDITOR }]}
                            expressionObj={expressionObj}
                            variableTypes={{}}
                            onValueChange={(expression) => editAssertion(index, expression)}
                            fieldErrors={[]}
                        />
                    </NodeTable>
                    <StyledButton title={t("node.row.remove.title", "Remove field")} onClick={() => removeAssertion(index)} sx={{ ml: 1 }}>
                        {t("node.row.remove.text", "-")}
                    </StyledButton>
                    <Box sx={{ mb: 0.5, ml: 1, display: "flex", alignItems: "center" }}>
                        <AssertionStatus status={"error"} message={"message"} />
                    </Box>
                </Box>
            ))}

            <StyledButton title={t("node.row.add.title", "Add field")} onClick={addAssertion} sx={{ mt: 2 }}>
                {t("node.row.add.text", "+")}
            </StyledButton>
        </Stack>
    );
};
