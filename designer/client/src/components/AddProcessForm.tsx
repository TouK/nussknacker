import { css, cx } from "@emotion/css";
import React, { useCallback } from "react";
import { ChangeableValue } from "./ChangeableValue";
import ValidationLabels from "./modals/ValidationLabels";
import { NodeTable } from "./graph/node-modal/NodeDetailsContent/NodeTable";
import { NodeInput, SelectNode } from "./FormElements";
import { getValidationErrorsForField } from "./graph/node-modal/editors/Validators";
import { FormControl, FormGroup, FormHelperText, FormLabel, Link, Typography } from "@mui/material";
import { Trans, useTranslation } from "react-i18next";
import StreamingIcon from "../assets/img/streaming.svg";
import RequestResponseIcon from "../assets/img/request-response.svg";
import BatchIcon from "../assets/img/batch.svg";
import { CustomRadio } from "./customRadio/CustomRadio";
import { ProcessingMode } from "../http/HttpService";
import { NodeValidationError } from "../types";
import { isEmpty } from "lodash";

export type FormValue = { processName: string; processCategory: string; processingMode: string; processEngine: string };

export type TouchedValue = Record<keyof FormValue, boolean>;

interface AddProcessFormProps extends ChangeableValue<FormValue> {
    validationErrors: NodeValidationError[];
    categories: { value: string; disabled: boolean }[];
    processingModes: ProcessingMode[];
    engines: string[];
    handleSetTouched: (touched: TouchedValue) => void;
    touched: TouchedValue;
    isProcessingModeBatchAvailable?: boolean;
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
    isProcessingModeBatchAvailable,
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
                    <span className="node-value">
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
                            {/*TODO: Remove condition when batch processing mode ready */}
                            {isProcessingModeBatchAvailable && (
                                <CustomRadio
                                    disabled={processingModes.every((processingMode) => processingMode !== ProcessingMode.batch)}
                                    label={t("addProcessForm.label.batch", "Batch")}
                                    value={ProcessingMode.batch}
                                    Icon={BatchIcon}
                                    active={value.processingMode === ProcessingMode.batch}
                                />
                            )}
                        </FormGroup>
                        <ValidationLabels
                            fieldErrors={touched.processingMode ? getValidationErrorsForField(validationErrors, "processingMode") : []}
                        />
                        <Typography component={"div"} variant={"overline"} mt={1}>
                            <Trans i18nKey={"addProcessForm.helperText.processingMode"}>
                                Processing mode defines how scenario deployed on an engine interacts with the outside world. Click here to
                                <Link
                                    sx={{ cursor: "pointer", ml: 0.5 }}
                                    href="https://nussknacker.io/documentation/about/ProcessingModes"
                                    target="_blank"
                                    rel="noopener noreferrer"
                                >
                                    learn more.
                                </Link>
                            </Trans>
                        </Typography>
                    </span>
                </FormControl>
                <FormControl>
                    <FormLabel required>{t("addProcessForm.label.name", "Name")}</FormLabel>
                    <div className="node-value">
                        <NodeInput
                            type="text"
                            id="newProcessName"
                            value={value.processName}
                            className={"node-input"}
                            onChange={(e) => onFieldChange("processName", e.target.value)}
                            onBlur={() => {
                                onBlurChange("processName", true);
                            }}
                        />
                        <ValidationLabels
                            fieldErrors={touched.processName ? getValidationErrorsForField(validationErrors, "processName") : []}
                        />
                    </div>
                </FormControl>
                {!isEmpty(categories) && (
                    <FormControl>
                        <FormLabel required>{t("addProcessForm.label.category", "Category")}</FormLabel>
                        <div className="node-value">
                            <SelectNode
                                id="processCategory"
                                className={"node-input"}
                                value={value.processCategory}
                                onChange={(e) => {
                                    onFieldChange("processCategory", e.target.value);
                                }}
                                onBlur={() => {
                                    onBlurChange("processCategory", true);
                                }}
                            >
                                <>
                                    <option value={""}></option>
                                    {categories.map(({ value, disabled }, index) => (
                                        <option key={index} value={value} disabled={disabled}>
                                            {value}
                                        </option>
                                    ))}
                                </>
                            </SelectNode>
                            <ValidationLabels
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
                        </div>
                    </FormControl>
                )}
                {!isEmpty(engines) && (
                    <FormControl>
                        <FormLabel required>{t("addProcessForm.label.engine", "Engine")}</FormLabel>
                        <div className="node-value">
                            <SelectNode
                                id="processEngine"
                                value={value.processEngine}
                                className={"node-input"}
                                onChange={(e) => {
                                    onFieldChange("processEngine", e.target.value);
                                }}
                                onBlur={() => {
                                    onBlurChange("processEngine", true);
                                }}
                            >
                                <>
                                    <option value={""}></option>
                                    {engines.map((engine, index) => (
                                        <option key={index} value={engine}>
                                            {engine}
                                        </option>
                                    ))}
                                </>
                            </SelectNode>
                            {touched.processEngine
                                ? getValidationErrorsForField(validationErrors, "processEngine").map((engineError, index) => (
                                      <FormHelperText key={index} error>
                                          {engineError.message}
                                      </FormHelperText>
                                  ))
                                : []}
                            <Typography component={"div"} variant={"overline"} mt={1}>
                                <Trans i18nKey={"addProcessForm.helperText.engine"}>
                                    To read more about engines,
                                    <Link
                                        sx={{ cursor: "pointer", ml: 0.5 }}
                                        href="https://nussknacker.io/documentation/about/engines"
                                        target="_blank"
                                        rel="noopener noreferrer"
                                    >
                                        click here.
                                    </Link>
                                </Trans>
                            </Typography>
                        </div>
                    </FormControl>
                )}
            </NodeTable>
        </div>
    );
}
