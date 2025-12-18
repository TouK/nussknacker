import { Stack, Typography } from "@mui/material";
import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";

import { setTestingAssertions } from "../../../../../../actions/nk/displayTestResults";
import { getTestingAssertions } from "../../../../../../reducers/selectors/graph";
import { getUserSettings } from "../../../../../../reducers/selectors/userSettings";
import { useAppDispatch, useAppSelector } from "../../../../../../store/storeHelpers";
import { StyledButton } from "../../../../styledButton";
import { EditableEditor } from "../../../editors/EditableEditor";
import { EditorType } from "../../../editors/expression/types";
import { NodeTable } from "../../../NodeDetailsContent/NodeTable";

export const Assertions = () => {
    const settings = useAppSelector(getUserSettings);
    const { t } = useTranslation();
    const dispatch = useAppDispatch();
    const testingAssertions = useAppSelector(getTestingAssertions);

    const addAssertion = useCallback(() => {
        dispatch(setTestingAssertions((prev) => prev.concat({ expression: "", language: "spel" })));
    }, [dispatch]);

    const editAssertion = useCallback(
        (index: number, updated: Partial<{ expression: string; language: string }>) => {
            dispatch(setTestingAssertions((prev) => prev.map((item, i) => (i === index ? { ...item, ...updated } : item))));
        },
        [dispatch],
    );

    return (
        <Stack p={2} gap={2}>
            <Typography m={0} variant="h5">
                {t("testingDialog.label.assertions", "Assertions")}
            </Typography>
            {testingAssertions.map((expressionObj, index) => (
                <NodeTable key={index} sx={settings["node.showInputsAndOutputs"] ? { margin: "0 8px" } : undefined}>
                    <EditableEditor
                        editors={[{ type: EditorType.SPEL_PARAMETER_EDITOR }]}
                        expressionObj={expressionObj}
                        variableTypes={{}}
                        onValueChange={(expression) => editAssertion(index, expression)}
                        fieldErrors={[]}
                    />
                </NodeTable>
            ))}

            <StyledButton title={t("node.row.add.title", "Add field")} onClick={addAssertion} sx={{ mt: 2 }}>
                {t("node.row.add.text", "+")}
            </StyledButton>
        </Stack>
    );
};
