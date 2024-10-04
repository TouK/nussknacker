import { css, cx } from "@emotion/css";
import React, { useCallback } from "react";
import { ChangeableValue } from "./ChangeableValue";
import ValidationLabels from "./modals/ValidationLabels";
import { NodeTable } from "./graph/node-modal/NodeDetailsContent/NodeTable";
import { getValidationErrorsForField } from "./graph/node-modal/editors/Validators";
import { Box, FormControl, FormGroup, FormLabel, Link, Typography } from "@mui/material";
import { Trans, useTranslation } from "react-i18next";
import StreamingIcon from "../assets/img/streaming.svg";
import RequestResponseIcon from "../assets/img/request-response.svg";
import BatchIcon from "../assets/img/batch.svg";
import { CustomRadio } from "./customRadio/CustomRadio";
import { ProcessingMode } from "../http/HttpService";
import { NodeValidationError } from "../types";
import { isEmpty } from "lodash";
import { Option, TypeSelect } from "./graph/node-modal/fragment-input-definition/TypeSelect";
import { nodeValue } from "./graph/node-modal/NodeDetailsContent/NodeTableStyled";
import { InfoOutlined } from "@mui/icons-material";
import Input from "./graph/node-modal/editors/field/Input";
import { formLabelWidth } from "../containers/theme/styles";

export type FormValue = { processName: string; processCategory: string; processingMode: string; processEngine: string };

export type TouchedValue = Record<keyof FormValue, boolean>;

interface AddProcessFormProps extends ChangeableValue<FormValue> {
    validationErrors: NodeValidationError[];
    categories: { value: string; disabled: boolean }[];
    processingModes: ProcessingMode[];
    engines: string[];
    handleSetTouched: (touched: TouchedValue) => void;
    touched: TouchedValue;
    displayContactSupportMessage?: boolean;
}

export function AddProcessForm({
    value,
    touched,
    onChange,
    handleSetTouched,
    validationErrors,
    categories,
    engines,
    processingModes,
    displayContactSupportMessage,
}: AddProcessFormProps): JSX.Element {
    const { t } = useTranslation();
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

    const categoryOptions: Option[] = [
        { value: "", label: "" },
        ...categories.map((category) => ({ value: category.value, label: category.value, isDisabled: category.disabled })),
    ];

    const engineOptions: Option[] = [{ value: "", label: "" }, ...engines.map((engine) => ({ value: engine, label: engine }))];

    return (
        <div
            className={cx(
                css({
                    paddingTop: 10,
                    paddingBottom: 20,
                }),
            )}
        >
            <NodeTable>
                <FormControl>
                    <FormLabel required>{t("addProcessForm.label.processingMode", "Processing mode")}</FormLabel>
                    <span className={nodeValue}>
                        <FormGroup
                            row
                            sx={(theme) => ({ flexWrap: "nowrap", gap: theme.spacing(1.5) })}
                            onChange={(event) => {
                                const target = event.target as HTMLInputElement;

                                onFieldChange("processingMode", target.checked ? target.value : "");
                            }}
                            onBlur={() => {
                                onBlurChange("processingMode", true);
                            }}
                        >
                            <CustomRadio
                                disabled={processingModes.every((processingMode) => processingMode !== ProcessingMode.streaming)}
                                label={t("addProcessForm.label.streaming", "Streaming")}
                                value={ProcessingMode.streaming}
                                Icon={StreamingIcon}
                                active={value.processingMode === ProcessingMode.streaming}
                            />
                            <CustomRadio
                                disabled={processingModes.every((processingMode) => processingMode !== ProcessingMode.requestResponse)}
                                label={t("addProcessForm.label.requestResponse", "Request-response")}
                                value={ProcessingMode.requestResponse}
                                Icon={RequestResponseIcon}
                                active={value.processingMode === ProcessingMode.requestResponse}
                            />
                            <CustomRadio
                                label={t("addProcessForm.label.batch", "Batch")}
                                value={ProcessingMode.batch}
                                Icon={BatchIcon}
                                active={value.processingMode === ProcessingMode.batch}
                            />
                        </FormGroup>
                        <ValidationLabels
                            fieldErrors={touched.processingMode ? getValidationErrorsForField(validationErrors, "processingMode") : []}
                        />
                        <Typography component={"div"} variant={"overline"} mt={1}>
                            <Trans i18nKey={"addProcessForm.helperText.processingMode"}>
                                Processing mode defines how scenario deployed on an engine interacts with the outside world. Click here to
                                <Link
                                    sx={{ cursor: "pointer", ml: 0.5 }}
                                    href="https://nussknacker.io/documentation/docs/about/ProcessingModes"
                                    target="_blank"
                                    rel="noopener noreferrer"
                                >
                                    learn more.
                                </Link>
                            </Trans>
                        </Typography>
                    </span>
                </FormControl>
                {displayContactSupportMessage ? (
                    <Box ml={formLabelWidth} display={"flex"} mt={3}>
                        <InfoOutlined />
                        <Typography ml={1} variant={"body2"}>
                            <Trans i18nKey={"addProcessForm.displayContactSupportMessage"}>
                                Batch processing mode hasn&apos;t been set up yet. If you’d like to configuring it, please don’t hesitate to
                                contact our team at <Link href={"mailto:enterprise@nussknacker.io"}>enterprise@nussknacker.io</Link>
                            </Trans>
                        </Typography>
                    </Box>
                ) : (
                    <>
                        <FormControl>
                            <FormLabel required>{t("addProcessForm.label.name", "Name")}</FormLabel>
                            <div className={nodeValue}>
                                <Input
                                    type="text"
                                    id="newProcessName"
                                    value={value.processName}
                                    onChange={(e) => onFieldChange("processName", e.target.value)}
                                    onBlur={() => {
                                        onBlurChange("processName", true);
                                    }}
                                    fieldErrors={touched.processName ? getValidationErrorsForField(validationErrors, "processName") : []}
                                    showValidation={true}
                                />
                            </div>
                        </FormControl>
                        {!isEmpty(categories) && (
                            <FormControl>
                                <FormLabel required>{t("addProcessForm.label.category", "Category")}</FormLabel>
                                <Box flex={1} width="100%">
                                    <TypeSelect
                                        id="processCategory"
                                        onChange={(value) => {
                                            onFieldChange("processCategory", value);
                                        }}
                                        onBlur={() => {
                                            onBlurChange("processCategory", true);
                                        }}
                                        value={categoryOptions.find((option) => option.value === value.processCategory)}
                                        options={categoryOptions}
                                        fieldErrors={
                                            touched.processCategory ? getValidationErrorsForField(validationErrors, "processCategory") : []
                                        }
                                    />
                                    <Typography component={"div"} variant={"overline"} mt={1}>
                                        <Trans i18nKey={"addProcessForm.helperText.category"}>
                                            To read more about categories,
                                            <Link
                                                sx={{ cursor: "pointer", ml: 0.5 }}
                                                href="https://nussknacker.io/documentation/docs/installation_configuration_guide/DesignerConfiguration/#scenario-type-categories"
                                                target="_blank"
                                                rel="noopener noreferrer"
                                            >
                                                click here.
                                            </Link>
                                        </Trans>
                                    </Typography>
                                </Box>
                            </FormControl>
                        )}
                        {!isEmpty(engines) && (
                            <FormControl>
                                <FormLabel required>{t("addProcessForm.label.engine", "Engine")}</FormLabel>
                                <Box flex={1} width="100%">
                                    <TypeSelect
                                        id="processEngine"
                                        onChange={(value) => {
                                            onFieldChange("processEngine", value);
                                        }}
                                        onBlur={() => {
                                            onBlurChange("processEngine", true);
                                        }}
                                        value={engineOptions.find((option) => option.value === value.processEngine)}
                                        options={engineOptions}
                                        fieldErrors={
                                            touched.processEngine ? getValidationErrorsForField(validationErrors, "processEngine") : []
                                        }
                                    />
                                    <Typography component={"div"} variant={"overline"} mt={1}>
                                        <Trans i18nKey={"addProcessForm.helperText.engine"}>
                                            To read more about engines,
                                            <Link
                                                sx={{ cursor: "pointer", ml: 0.5 }}
                                                href="https://nussknacker.io/documentation/docs/about/engines"
                                                target="_blank"
                                                rel="noopener noreferrer"
                                            >
                                                click here.
                                            </Link>
                                        </Trans>
                                    </Typography>
                                </Box>
                            </FormControl>
                        )}
                    </>
                )}
            </NodeTable>
        </div>
    );
}
