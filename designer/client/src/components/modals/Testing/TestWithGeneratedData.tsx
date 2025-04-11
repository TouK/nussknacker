import { Box, FormLabel } from "@mui/material";
import { isEmpty } from "lodash";
import React, { useCallback, useEffect, useState } from "react";
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
import { useTesting } from "./TestingContext";

interface TestWithGeneratedDataFormProps {
    closeDialog: () => void;
}

export function TestWithGeneratedDataForm({ closeDialog }: TestWithGeneratedDataFormProps): JSX.Element {
    const { t } = useTranslation();
    const dispatch = useDispatch();

    const { handleSetAction, handleIsValid } = useTesting();

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

    useEffect(() => {
        handleIsValid(generatedDataTestingIsValid);
        handleSetAction(generatedDataTestingConfirm);
    }, [generatedDataTestingConfirm, generatedDataTestingIsValid, handleIsValid, handleSetAction]);

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
        </Box>
    );
}
