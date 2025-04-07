import { css, cx } from "@emotion/css";
import loadable from "@loadable/component";
import { FormGroup, FormLabel, Link, Typography } from "@mui/material";
import type { WindowType } from "@touk/window-manager";
import React, { useCallback, useMemo, useState } from "react";
import { Trans, useTranslation } from "react-i18next";
import { useSelector } from "react-redux";

import { getTestCapabilities } from "../../../reducers/selectors/graph";
import type { WindowKind } from "../../../windowManager";
import { CustomRadio } from "../../customRadio/CustomRadio";
import { NodeTable } from "../../graph/node-modal/NodeDetailsContent/NodeTable";
import { nodeValue } from "../../graph/node-modal/NodeDetailsContent/NodeTableStyled";
import type { TestingData } from "./TestingDialog";
import { TestVariantForm } from "./TestVariantForm";
import { useValidatedLocalStorage } from "./useValidatedLocalStorage";

export enum TestType {
    withParameters = "withParameters",
    withGeneratedData = "withGeneratedData",
}

export type FormValue = { testType: string };

export type TouchedValue = Record<keyof FormValue, boolean>;

interface TestingFormProps {
    testingData: WindowType<WindowKind, TestingData>;
    closeDialog: () => void;
}

const DryRunTestingIcon = loadable(() => import("../../../assets/img/icons/test-dry-run.svg"));
const GenerateAndTestIcon = loadable(() => import("../../../assets/img/icons/test-using-generated-data.svg"));

export function TestingForm({ testingData, closeDialog }: TestingFormProps): JSX.Element {
    const { t } = useTranslation();

    const testCapabilities = useSelector(getTestCapabilities);
    const testWithParametersIsAvailable = useMemo(() => testCapabilities.canTestWithForm, [testCapabilities]);
    const testWithGeneratedDataIsAvailable = useMemo(
        () => testCapabilities.canGenerateTestData && testCapabilities.canBeTested,
        [testCapabilities],
    );

    const availabilityMap: Record<TestType, boolean> = useMemo(() => {
        return {
            [TestType.withParameters]: testWithParametersIsAvailable,
            [TestType.withGeneratedData]: testWithGeneratedDataIsAvailable,
        };
    }, [testWithParametersIsAvailable, testWithGeneratedDataIsAvailable]);
    const availableTestTypes = Object.entries(availabilityMap)
        .filter(([_, isAvailable]) => isAvailable)
        .map(([key]) => key as TestType);

    const [formValue, setState] = useValidatedLocalStorage<FormValue>(
        "selectedTestTypeState",
        { testType: availableTestTypes[0] },
        (stored: FormValue) => availableTestTypes.includes(TestType[stored.testType]),
    );
    const [touched, setTouched] = useState<TouchedValue>({
        testType: false,
    });
    const onChange = useCallback(
        (value: FormValue) => {
            setState(value);
        },
        [setState],
    );
    const handleSetTouched = useCallback(
        (touched: TouchedValue) => {
            setTouched(touched);
        },
        [setTouched],
    );
    const onFieldChange = useCallback(
        (field: keyof FormValue, next: string) => {
            onChange({ ...formValue, [field]: next });
            handleSetTouched({ ...touched, [field]: true });
        },
        [handleSetTouched, onChange, touched, formValue],
    );
    const onBlurChange = useCallback(
        (field: keyof TouchedValue, next: boolean) => handleSetTouched({ ...touched, [field]: next }),
        [handleSetTouched, touched],
    );

    return (
        <div className={cx(css({ paddingTop: 10, paddingBottom: 20 }))}>
            <NodeTable>
                <FormLabel required>{t("addProcessForm.label.testMode", "Data used in scenario sources")}</FormLabel>
                <span className={nodeValue}>
                    <FormGroup
                        row
                        sx={(theme) => ({ flexWrap: "flex", alignItems: "center", gap: theme.spacing(1.5) })}
                        onChange={(event) => {
                            const target = event.target as HTMLInputElement;
                            onFieldChange("testType", target.value);
                        }}
                        onBlur={() => {
                            onBlurChange("testType", true);
                        }}
                    >
                        <CustomRadio
                            label={t("testingForm.label.withParameters", "Form")}
                            value={TestType.withParameters}
                            Icon={DryRunTestingIcon}
                            active={formValue.testType === TestType.withParameters}
                            disabled={!testWithParametersIsAvailable}
                        />
                        <CustomRadio
                            label={t("testingForm.label.withGeneratedData", "Live samples")}
                            value={TestType.withGeneratedData}
                            Icon={GenerateAndTestIcon}
                            active={formValue.testType === TestType.withGeneratedData}
                            disabled={!testWithGeneratedDataIsAvailable}
                        />
                    </FormGroup>
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
                <div className={cx(css({ paddingTop: 10, paddingBottom: 20 }))}>
                    <TestVariantForm testType={formValue.testType} testingData={testingData} closeDialog={closeDialog}></TestVariantForm>
                </div>
            </NodeTable>
        </div>
    );
}
