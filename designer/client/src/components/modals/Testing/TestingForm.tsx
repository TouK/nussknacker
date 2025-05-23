import { Box, FormGroup, FormLabel, Link, Typography } from "@mui/material";
import type { WindowType } from "@touk/window-manager";
import React, { useCallback, useMemo, useState } from "react";
import { Trans, useTranslation } from "react-i18next";
import { useDispatch } from "react-redux";

import { updateTestType } from "../../../actions/nk/displayTestResults";
import type { WindowKind } from "../../../windowManager";
import { CustomRadio } from "../../customRadio/CustomRadio";
import { NodeTable } from "../../graph/node-modal/NodeDetailsContent/NodeTable";
import { nodeValue } from "../../graph/node-modal/NodeDetailsContent/NodeTableStyled";
import { useTestingContext } from "./TestingContext";
import type { TestingData } from "./TestingDialog";
import { TestVariantForm } from "./TestVariantForm";

export type FormValue = { testType: string };

export type TouchedValue = Record<keyof FormValue, boolean>;

interface TestingFormProps {
    testingData: WindowType<WindowKind, TestingData>;
    closeDialog: () => void;
}

export function TestingForm({ testingData, closeDialog }: TestingFormProps): JSX.Element {
    const { t } = useTranslation();
    const dispatch = useDispatch();

    const { options, testType } = useTestingContext();

    const formValue = useMemo<FormValue>(
        () => ({
            testType,
        }),
        [testType],
    );

    const [touched, setTouched] = useState<TouchedValue>({
        testType: false,
    });
    const onChange = useCallback(
        (value: FormValue) => {
            dispatch(updateTestType(value.testType));
        },
        [dispatch],
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
        <Box pt={1.5}>
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
                        {options.map(({ disableReason, ...props }) => (
                            <CustomRadio
                                key={props.value}
                                title={props.disabled ? disableReason : props.title}
                                active={formValue.testType === props.value}
                                {...props}
                            />
                        ))}
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
                <Box pt={1.5}>
                    <TestVariantForm testType={formValue.testType} testingData={testingData} closeDialog={closeDialog} />
                </Box>
            </NodeTable>
        </Box>
    );
}
