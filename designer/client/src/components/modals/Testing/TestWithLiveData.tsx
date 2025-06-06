import { Box, FormLabel } from "@mui/material";
import { isEmpty } from "lodash";
import React, { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";

import { testScenarioWithGeneratedData } from "../../../actions/nk/displayTestResults";
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
import ValidationLabels from "../../modals/ValidationLabels";
import { useTestingContext } from "./TestingContext";

interface TestWithLiveDataFormProps {
    closeDialog: () => void;
}

export function TestWithLiveDataForm({ closeDialog }: TestWithLiveDataFormProps): JSX.Element {
    const { t } = useTranslation();
    const dispatch = useDispatch();

    const { handleSetAction, handleIsValid } = useTestingContext();

    const liveDataMaxSamples = useSelector(getFeatureSettings).testDataSettings.maxSamplesCount;

    const [{ liveDataTestSampleSize }, setState] = useState({
        liveDataTestSampleSize: "10",
    });
    const liveDataTestingConfirm = useCallback(async () => {
        dispatch(testScenarioWithGeneratedData(liveDataTestSampleSize));
        closeDialog();
    }, [dispatch, liveDataTestSampleSize, closeDialog]);
    const liveDataNumberOfSamplesValidators = [
        literalIntegerValueValidator,
        minimalNumberValidator(0),
        maximalNumberValidator(liveDataMaxSamples),
        mandatoryValueValidator,
    ];

    const liveDataTestingErrors = extendErrors([], liveDataTestSampleSize, "testData", liveDataNumberOfSamplesValidators);
    const liveDataTestingIsValid = isEmpty(liveDataTestingErrors);

    useEffect(() => {
        handleIsValid(liveDataTestingIsValid);
        handleSetAction(liveDataTestingConfirm);
    }, [liveDataTestingConfirm, liveDataTestingIsValid, handleIsValid, handleSetAction]);

    return (
        <Box mt={1.5}>
            <FormLabel required>{t("testingForm.withLiveData.numberOfSamples.label", "Specify number of samples")}</FormLabel>
            <div className={nodeValue} style={{ marginTop: "4px" }}>
                <NodeInput
                    value={liveDataTestSampleSize}
                    onChange={(event) => setState({ liveDataTestSampleSize: event.target.value })}
                    className={nodeInput}
                    autoFocus
                />
            </div>
            <ValidationLabels fieldErrors={getValidationErrorsForField(liveDataTestingErrors, "testData")} />
        </Box>
    );
}
