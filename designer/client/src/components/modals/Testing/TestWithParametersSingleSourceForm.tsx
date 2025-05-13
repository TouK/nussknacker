import { Box } from "@mui/material";
import type { WindowType } from "@touk/window-manager";
import React, { useCallback, useEffect, useState } from "react";

import type { WindowKind } from "../../../windowManager";
import { getValidationErrorsForField } from "../../graph/node-modal/editors/Validators";
import ValidationLabels from "../../modals/ValidationLabels";
import { AdhocTestingFormContext } from "../AdhocTesting/AdhocTestingFormContext";
import { MarkdownForm } from "../AdhocTesting/MarkdownForm";
import { useAdhocTestingAction } from "../AdhocTesting/useAdhocTestingAction";
import { useAdhocTestingParametersValidation } from "../AdhocTesting/useAdhocTestingParametersValidation";
import { useTestingContext } from "./TestingContext";
import type { TestingData } from "./TestingDialog";

interface TestWithParametersSingleSourceFormProps {
    testingData: WindowType<WindowKind, TestingData>;
    closeDialog: () => void;
}

export function TestWithParametersSingleSourceForm({ testingData, closeDialog }: TestWithParametersSingleSourceFormProps): JSX.Element {
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
    const { handleSetAction, handleIsValid } = useTestingContext();

    useEffect(() => {
        handleIsValid(isValid);
        handleSetAction(adhocTestingConfirm);
    }, [adhocTestingConfirm, handleIsValid, handleSetAction, isValid]);

    return (
        <Box mt={1.5}>
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
            </AdhocTestingFormContext.Provider>
        </Box>
    );
}
