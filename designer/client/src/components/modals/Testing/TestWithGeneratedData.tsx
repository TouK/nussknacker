import { Box, Button, FormLabel } from "@mui/material";
import { isEmpty } from "lodash";
import React, { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";

import { testScenarioWithGeneratedData } from "../../../actions/nk/displayTestResults";
import { getScenarioGraph } from "../../../reducers/selectors/graph";
import { getFeatureSettings } from "../../../reducers/selectors/settings";
import { NodeInput } from "../../FormElements";
import {
    extendErrors,
    getValidationErrorsForField,
    literalIntegerValueValidator,
    mandatoryValueValidator,
    maximalNumberValidator,
    minimalNumberValidator,
} from "../../graph/node-modal/editors/Validators";
import { nodeInput, nodeValue } from "../../graph/node-modal/NodeDetailsContent/NodeTableStyled";
import { getProcessName } from "../../graph/node-modal/NodeDetailsContent/selectors";
import ValidationLabels from "../../modals/ValidationLabels";

interface TestWithGeneratedDataFormProps {
    closeDialog: () => void;
}

export function TestWithGeneratedDataForm({ closeDialog }: TestWithGeneratedDataFormProps): JSX.Element {
    const { t } = useTranslation();
    const dispatch = useDispatch();

    const processName = useSelector(getProcessName);
    const scenarioGraph = useSelector(getScenarioGraph);
    const generatedDataMaxSamples = useSelector(getFeatureSettings).testDataSettings.maxSamplesCount;

    const [{ generatedDataTestSampleSize }, setState] = useState({
        generatedDataTestSampleSize: "10",
    });
    const generatedDataTestingConfirm = useCallback(async () => {
        dispatch(testScenarioWithGeneratedData(generatedDataTestSampleSize, processName, scenarioGraph));
        closeDialog();
    }, [dispatch, processName, scenarioGraph, generatedDataTestSampleSize, closeDialog]);
    const generatedDataNumberOfSamplesValidators = [
        literalIntegerValueValidator,
        minimalNumberValidator(0),
        maximalNumberValidator(generatedDataMaxSamples),
        mandatoryValueValidator,
    ];

    const generatedDataTestingErrors = extendErrors([], generatedDataTestSampleSize, "testData", generatedDataNumberOfSamplesValidators);
    const generatedDataTestingIsValid = isEmpty(generatedDataTestingErrors);

    return (
        <Box mt={1.5}>
            <FormLabel required>{t("testingForm.withGeneratedData.numberOfSamples.label", "Specify number of samples")}</FormLabel>
            <div className={nodeValue} style={{ marginTop: "4px" }}>
                <NodeInput
                    value={generatedDataTestSampleSize}
                    onChange={(event) => setState({ generatedDataTestSampleSize: event.target.value })}
                    className={nodeInput}
                    autoFocus
                />
            </div>
            <ValidationLabels fieldErrors={getValidationErrorsForField(generatedDataTestingErrors, "testData")} />
            <Box sx={(theme) => ({ display: "flex", justifyContent: "flex-end", gap: 1, width: "auto", marginTop: theme.spacing(5) })}>
                <Button sx={{ width: "15%" }} size="medium" variant="outlined" onClick={() => closeDialog()}>
                    {t("testingForm.cancelButton.label", "Cancel")}
                </Button>
                <Button
                    sx={{ width: "15%" }}
                    size="medium"
                    variant="contained"
                    onClick={generatedDataTestingConfirm}
                    type="submit"
                    disabled={!generatedDataTestingIsValid}
                >
                    {t("testingForm.testButton.label", "Test")}
                </Button>
            </Box>
        </Box>
    );
}
