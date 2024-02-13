import { css, cx } from "@emotion/css";
import React, { useCallback, useEffect, useState } from "react";
import { ChangeableValue } from "./ChangeableValue";
import ValidationLabels from "./modals/ValidationLabels";
import { NodeTable } from "./graph/node-modal/NodeDetailsContent/NodeTable";
import { NodeInput, SelectNodeWithFocus } from "./withFocus";
import { FieldError } from "./graph/node-modal/editors/Validators";
import { FormControl, FormGroup, FormLabel, Link, Typography } from "@mui/material";
import { Trans, useTranslation } from "react-i18next";
import StreamingIcon from "../assets/img/streaming.svg";
import RequestResponseIcon from "../assets/img/request-response.svg";
import BatchIcon from "../assets/img/batch.svg";
import { CustomRadio } from "./customRadio/CustomRadio";
import HttpService, { ProcessingMode } from "../http/HttpService";
import { useProcessFormDataOptions } from "./useProcessFormDataOptions";

export type FormValue = { processName: string; processCategory: string; processingMode: string; processEngine: string };

interface AddProcessFormProps extends ChangeableValue<FormValue> {
    fieldErrors: FieldError[];
}

export function AddProcessForm({ value, onChange, fieldErrors }: AddProcessFormProps): JSX.Element {
    const { t } = useTranslation();
    const [allCombinations, setAllCombinations] = useState([]);
    const onFieldChange = useCallback((field: keyof FormValue, next: string) => onChange({ ...value, [field]: next }), [onChange, value]);
    const { categories, engines, processingModes } = useProcessFormDataOptions({ allCombinations, value });

    useEffect(() => {
        HttpService.fetchScenarioParametersCombinations().then((response) => {
            const combinations = response.data.combinations;
            setAllCombinations(combinations);
        });
    }, []);

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
                    <FormLabel>{t("addProcessForm.label.processingMode", "Processing mode")}</FormLabel>
                    <span className="node-value">
                        <FormGroup
                            row
                            sx={(theme) => ({ flexWrap: "nowrap", gap: theme.spacing(1.5) })}
                            onChange={(event) => {
                                const target = event.target as HTMLInputElement;
                                if (!target.checked) {
                                    onFieldChange("processingMode", "");
                                    return;
                                }

                                onFieldChange("processingMode", target.value);
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
                                disabled={processingModes.every((processingMode) => processingMode !== ProcessingMode.batch)}
                                label={t("addProcessForm.label.batch", "Batch")}
                                value={ProcessingMode.batch}
                                Icon={BatchIcon}
                                active={value.processingMode === ProcessingMode.batch}
                            />
                        </FormGroup>
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
                    <FormLabel>{t("addProcessForm.label.name", "Name")}</FormLabel>
                    <div className="node-value">
                        <NodeInput
                            type="text"
                            id="newProcessName"
                            value={value.processName}
                            onChange={(e) => onFieldChange("processName", e.target.value)}
                        />
                        <ValidationLabels fieldErrors={fieldErrors} />
                    </div>
                </FormControl>
                <FormControl>
                    <FormLabel>{t("addProcessForm.label.category", "Category")}</FormLabel>
                    <div className="node-value">
                        <SelectNodeWithFocus
                            id="processCategory"
                            value={value.processCategory}
                            onChange={(e) => {
                                onFieldChange("processCategory", e.target.value);
                            }}
                        >
                            <>
                                <option value={""}></option>
                                {categories.map((category, index) => (
                                    <option key={index} value={category}>
                                        {category}
                                    </option>
                                ))}
                            </>
                        </SelectNodeWithFocus>
                        <Typography component={"div"} variant={"overline"} mt={1}>
                            <Trans i18nKey={"addProcessForm.helperText.category"}>
                                To read more about categories,
                                <Link
                                    sx={{ cursor: "pointer", ml: 0.5 }}
                                    href="https://nussknacker.io/documentation/docs/1.10/installation_configuration_guide/DesignerConfiguration/#scenario-type-categories"
                                    target="_blank"
                                    rel="noopener noreferrer"
                                >
                                    click here.
                                </Link>
                            </Trans>
                        </Typography>
                    </div>
                </FormControl>
                <FormControl>
                    <FormLabel>{t("addProcessForm.label.engine", "Engine")}</FormLabel>
                    <div className="node-value">
                        <SelectNodeWithFocus
                            id="processEngine"
                            value={value.processEngine}
                            onChange={(e) => {
                                onFieldChange("processEngine", e.target.value);
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
                        </SelectNodeWithFocus>
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
            </NodeTable>
        </div>
    );
}
