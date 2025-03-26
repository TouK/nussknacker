import { css, cx } from "@emotion/css";
import loadable from "@loadable/component";
import { Box, Button, FormGroup, FormLabel, Link, Typography } from "@mui/material";
import type { WindowType } from "@touk/window-manager";
import { flow, isEmpty } from "lodash";
import React, { useCallback, useState } from "react";
import { Trans, useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";

import { testScenarioWithGeneratedData } from "../../../actions/nk/displayTestResults";
import { getScenarioGraph, getTestParameters } from "../../../reducers/selectors/graph";
import { getFeatureSettings } from "../../../reducers/selectors/settings";
import type { WindowKind } from "../../../windowManager";
import type { ChangeableValue } from "../../ChangeableValue";
import { CustomRadio } from "../../customRadio/CustomRadio";
import { NodeInput } from "../../FormElements";
import {
    extendErrors,
    getValidationErrorsForField,
    literalIntegerValueValidator,
    mandatoryValueValidator,
    maximalNumberValidator,
    minimalNumberValidator,
} from "../../graph/node-modal/editors/Validators";
import { NodeTable } from "../../graph/node-modal/NodeDetailsContent/NodeTable";
import { nodeInput, nodeValue } from "../../graph/node-modal/NodeDetailsContent/NodeTableStyled";
import { getProcessName } from "../../graph/node-modal/NodeDetailsContent/selectors";
import ValidationLabels from "../../modals/ValidationLabels";
import { AdhocTestingFormContext } from "../AdhocTesting/AdhocTestingFormContext";
import { MarkdownForm } from "../AdhocTesting/MarkdownForm";
import { useAdhocTestingAction } from "../AdhocTesting/useAdhocTestingAction";
import { useAdhocTestingParametersValidation } from "../AdhocTesting/useAdhocTestingParametersValidation";
import type { TestingData } from "./TestingDialog";

export enum TestType {
    "withParameters" = "Dry run with parameters",
    "withGeneratedData" = "With generated data",
}

export type FormValue = { testType: string };

export type TouchedValue = Record<keyof FormValue, boolean>;

interface TestingFormProps extends ChangeableValue<FormValue> {
    handleSetTouched: (touched: TouchedValue) => void;
    touched: TouchedValue;
    testingData: WindowType<WindowKind, TestingData>;
    closeDialog: () => void;
}

const DryRunTestingIcon = loadable(() => import("../../../assets/img/icons/test-dry-run.svg"));
const GenerateAndTestIcon = loadable(() => import("../../../assets/img/icons/test-using-generated-data.svg"));

export function TestingForm({ value, touched, onChange, handleSetTouched, testingData, closeDialog }: TestingFormProps): JSX.Element {
    const { t } = useTranslation();
    const dispatch = useDispatch();
    const onFieldChange = useCallback(
        (field: keyof FormValue, next: string) => {
            onChange({ ...value, [field]: next });
            handleSetTouched({ ...touched, [field]: true });
        },
        [handleSetTouched, onChange, touched, value],
    );
    const onBlurChange = useCallback(
        (field: keyof TouchedValue, next: boolean) => handleSetTouched({ ...touched, [field]: next }),
        [handleSetTouched, touched],
    );

    const {
        meta: { view },
    } = testingData;

    // Test with parameters
    const adhocTestingAction = useAdhocTestingAction();
    const { variableTypes, parameters = [], initialValues, onConfirmAction } = adhocTestingAction;
    const [adhocTestingCurrentValue, setAdhocTestingCurrentValue] = useState(initialValues);
    const { adhocTestingErrors, adhocTestingIsValid } = useAdhocTestingParametersValidation(adhocTestingAction, adhocTestingCurrentValue);
    const adhocTestingValidationErrors = flow((errors) => extendErrors(errors, value.testType, "testType", []))(adhocTestingErrors);
    const adhocTestingConfirm = useCallback(async () => {
        onConfirmAction(adhocTestingCurrentValue);
        closeDialog();
    }, [closeDialog, onConfirmAction, adhocTestingCurrentValue]);

    // Test with generated data
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

    // Use the appropriate actions for buttons
    const testParameters = useSelector(getTestParameters);
    const sourcesFound = testParameters.length;
    const thereAreMultipleSources = sourcesFound > 1;
    const formIsValid =
        value.testType === TestType.withParameters ? adhocTestingIsValid && !thereAreMultipleSources : generatedDataTestingIsValid;
    const confirmForm = value.testType === TestType.withParameters ? adhocTestingConfirm : generatedDataTestingConfirm;

    const testWithParametersElementWhenSingleSource =
        value.testType === TestType.withParameters && !thereAreMultipleSources ? <MarkdownForm content={view.markdownContent} /> : <></>;

    const testWithParametersElementWhenMultipleSources =
        value.testType === TestType.withParameters && thereAreMultipleSources ? (
            <Box
                sx={{ display: "flex", flexWrap: "wrap", justifyContent: "center", gap: 2, width: "100%" }}
                style={{ marginBottom: "12px", marginTop: "12px" }}
            >
                <Typography component="span" variant={"subtitle1"} noWrap={false} align={"center"}>
                    {`Test with form is supported only for scenario with single source. Your scenario has ${sourcesFound} sources.`}
                </Typography>
            </Box>
        ) : (
            <></>
        );

    const testWithGeneratedDataElement =
        value.testType === TestType.withGeneratedData ? (
            <div style={{ marginBottom: "16px", marginTop: "28px" }}>
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
            </div>
        ) : (
            <></>
        );

    const buttons = (
        <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 1, width: "auto" }}>
            <Button sx={{ width: "15%" }} size="medium" variant="outlined" onClick={() => closeDialog()}>
                {t("testingForm.cancelButton.label", "Cancel")}
            </Button>
            <Button sx={{ width: "15%" }} size="medium" variant="contained" onClick={confirmForm} type="submit" disabled={!formIsValid}>
                {t("testingForm.testButton.label", "Test")}
            </Button>
        </Box>
    );

    return (
        <div className={cx(css({ paddingTop: 10, paddingBottom: 20 }))}>
            <AdhocTestingFormContext.Provider
                value={{
                    value: adhocTestingCurrentValue,
                    setValue: setAdhocTestingCurrentValue,
                    parameters,
                    variableTypes,
                    errors: adhocTestingErrors,
                }}
            >
                <NodeTable>
                    <FormLabel required>{t("addProcessForm.label.testMode", "Data used in sources")}</FormLabel>
                    <span className={nodeValue}>
                        <FormGroup
                            row
                            sx={(theme) => ({ flexWrap: "flex", alignItems: "center", gap: theme.spacing(1.5) })}
                            onChange={(event) => {
                                const target = event.target as HTMLInputElement;
                                onFieldChange("testType", target.checked ? target.value : target.value);
                            }}
                            onBlur={() => {
                                onBlurChange("testType", true);
                            }}
                        >
                            <CustomRadio
                                label={t("testingForm.label.withParameters", "Form")}
                                value={TestType.withParameters}
                                Icon={DryRunTestingIcon}
                                active={value.testType === TestType.withParameters}
                            />
                            <CustomRadio
                                label={t("testingForm.label.withGeneratedData", "Live samples")}
                                value={TestType.withGeneratedData}
                                Icon={GenerateAndTestIcon}
                                active={value.testType === TestType.withGeneratedData}
                            />
                        </FormGroup>
                        <ValidationLabels
                            fieldErrors={touched.testType ? getValidationErrorsForField(adhocTestingValidationErrors, "testType") : []}
                        />
                        <Typography component={"div"} variant={"overline"} mt={1}>
                            <Trans i18nKey={"testingForm.helperText.testType"}>
                                Determines how the input data is provided for the test run. Click here{" "}
                                <Link
                                    sx={{ cursor: "pointer", ml: 0.5 }}
                                    href="https://nussknacker.io/documentation/docs/scenarios_authoring/TestingAndDebugging/"
                                    target="_blank"
                                    rel="noopener noreferrer"
                                >
                                    to learn more.
                                </Link>
                            </Trans>
                        </Typography>
                    </span>
                    {testWithParametersElementWhenSingleSource}
                    {testWithParametersElementWhenMultipleSources}
                    {testWithGeneratedDataElement}
                    {buttons}
                </NodeTable>
            </AdhocTestingFormContext.Provider>
        </div>
    );
}
