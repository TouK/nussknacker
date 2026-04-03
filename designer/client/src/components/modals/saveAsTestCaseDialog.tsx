import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { v4 as uuidv4 } from "uuid";

import { addTestCase } from "../../actions/nk/testCasesActions";
import { getActiveTestCase } from "../../reducers/selectors/testCases";
import { useAppDispatch, useAppSelector } from "../../store/storeHelpers";
import { LoadingButtonTypes } from "../../windowManager/LoadingButton";
import { WindowContent } from "../../windowManager/WindowContent";
import Input from "../graph/node-modal/editors/field/Input";
import { NodeRow } from "../graph/node-modal/node/NodeRow";
import { NodeValue } from "../graph/node-modal/node/NodeValue";
import { NodeTable } from "../graph/node-modal/NodeDetailsContent/NodeTable";
import { useTestCaseNameValidation } from "./useTestCaseNameValidation";

const SaveAsTestCaseDialog = (props: WindowContentProps) => {
    const { t } = useTranslation();
    const activeTestCase = useAppSelector(getActiveTestCase);

    const [testCaseName, setTestCaseName] = useState(`Copy: ${activeTestCase?.name || "Test Case"}`);
    const dispatch = useAppDispatch();
    const { nameErrors, isValid } = useTestCaseNameValidation(testCaseName);
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

export default SaveAsTestCaseDialog;
