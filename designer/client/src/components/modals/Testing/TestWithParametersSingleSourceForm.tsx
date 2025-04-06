import { css, cx } from "@emotion/css";
import { Box, Button } from "@mui/material";
import type { WindowType } from "@touk/window-manager";
import React, { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";

import type { WindowKind } from "../../../windowManager";
import { getValidationErrorsForField } from "../../graph/node-modal/editors/Validators";
import ValidationLabels from "../../modals/ValidationLabels";
import { AdhocTestingFormContext } from "../AdhocTesting/AdhocTestingFormContext";
import { MarkdownForm } from "../AdhocTesting/MarkdownForm";
import { useAdhocTestingAction } from "../AdhocTesting/useAdhocTestingAction";
import { useAdhocTestingParametersValidation } from "../AdhocTesting/useAdhocTestingParametersValidation";
import type { TestingData } from "./TestingDialog";

interface TestWithParametersSingleSourceFormProps {
    testingData: WindowType<WindowKind, TestingData>;
    closeDialog: () => void;
}

export function TestWithParametersSingleSourceForm({ testingData, closeDialog }: TestWithParametersSingleSourceFormProps): JSX.Element {
    const { t } = useTranslation();
    const {
        meta: { viewParams },
    } = testingData;

    const adhocTestingAction = useAdhocTestingAction();
    const { variableTypes, parameters = [], initialValues, onConfirmAction } = adhocTestingAction;
    const [adhocTestingCurrentValue, setAdhocTestingCurrentValue] = useState(initialValues);
    const { errors, isValid } = useAdhocTestingParametersValidation(adhocTestingAction, adhocTestingCurrentValue);
    const adhocTestingConfirm = useCallback(async () => {
        onConfirmAction(adhocTestingCurrentValue);
        closeDialog();
    }, [closeDialog, onConfirmAction, adhocTestingCurrentValue]);

    return (
        <div className={cx(css({ paddingTop: 10, paddingBottom: 20 }))}>
            <AdhocTestingFormContext.Provider
                value={{
                    value: adhocTestingCurrentValue,
                    setValue: setAdhocTestingCurrentValue,
                    parameters,
                    variableTypes,
                    errors,
                }}
            >
                <ValidationLabels fieldErrors={getValidationErrorsForField(errors, "testType")} />
                <MarkdownForm content={viewParams.markdownContent} />
                <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 1, width: "auto" }}>
                    <Button sx={{ width: "15%" }} size="medium" variant="outlined" onClick={() => closeDialog()}>
                        {t("testingForm.cancelButton.label", "Cancel")}
                    </Button>
                    <Button
                        sx={{ width: "15%" }}
                        size="medium"
                        variant="contained"
                        onClick={adhocTestingConfirm}
                        type="submit"
                        disabled={!isValid}
                    >
                        {t("testingForm.testButton.label", "Test")}
                    </Button>
                </Box>
            </AdhocTestingFormContext.Provider>
        </div>
    );
}
