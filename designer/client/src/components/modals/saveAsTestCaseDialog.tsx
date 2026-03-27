import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { v4 as uuidv4 } from "uuid";

import { addTestCase } from "../../actions/nk/testCasesActions";
import { getActiveTestCase, getTestCases } from "../../reducers/selectors/testCases";
import { useAppDispatch, useAppSelector } from "../../store/storeHelpers";
import { LoadingButtonTypes } from "../../windowManager/LoadingButton";
import { WindowContent } from "../../windowManager/WindowContent";
import Input from "../graph/node-modal/editors/field/Input";
import { NodeRow } from "../graph/node-modal/node/NodeRow";
import { NodeValue } from "../graph/node-modal/node/NodeValue";
import { NodeTable } from "../graph/node-modal/NodeDetailsContent/NodeTable";

const SaveAsTestCaseDialog = (props: WindowContentProps) => {
    const { t } = useTranslation();
    const activeTestCase = useAppSelector(getActiveTestCase);

    const [testCaseName, setTestCaseName] = useState(`Copy: ${activeTestCase?.name || "Test Case"}`);
    const dispatch = useAppDispatch();
    const { nameErrors, isValid } = useSaveAsTestCaseValidation(testCaseName);
    const { close } = props;
    const cancelButton = useMemo<WindowButtonProps | false>(() => {
        return {
            title: t("dialog.button.cancel", "cancel"),
            action: () => close(),
            className: LoadingButtonTypes.secondaryButton,
        };
    }, [close, t]);

    const saveAsButton = useMemo<WindowButtonProps | false>(() => {
        return {
            title: t("dialog.button.saveAs", "Save as"),
            action: () => {
                if (!activeTestCase) return;
                dispatch(addTestCase({ ...activeTestCase, id: uuidv4(), name: testCaseName.trim() }));
                props.close();
            },
            disabled: !isValid,
        };
    }, [t, dispatch, activeTestCase, testCaseName, isValid, props]);
    const handleNameChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
        setTestCaseName(e.target.value);
    }, []);

    return (
        <WindowContent {...props} buttons={[cancelButton, saveAsButton]}>
            <NodeTable>
                <NodeRow label={t("saveAsTestCaseDialog.label.name", "Name")}>
                    <NodeValue>
                        <Input value={testCaseName} onChange={handleNameChange} fieldErrors={nameErrors} showValidation />
                    </NodeValue>
                </NodeRow>
            </NodeTable>
        </WindowContent>
    );
};

const useSaveAsTestCaseValidation = (testCaseName: string) => {
    const { t } = useTranslation();
    const testCases = useAppSelector(getTestCases);

    const nameErrors = [
        testCaseName.trim().length === 0 && t("saveAsTestCaseDialog.error.nameEmpty", "Name cannot be empty"),
        testCases.some((tc) => tc.name === testCaseName.trim()) &&
            t("saveAsTestCaseDialog.error.nameExists", "Test case with this name already exists"),
    ]
        .filter((e): e is string => Boolean(e))
        .map((message) => ({ message, description: "" }));

    return { nameErrors, isValid: nameErrors.length === 0 };
};

export default SaveAsTestCaseDialog;
