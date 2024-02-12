import { css, cx } from "@emotion/css";
import React, { useCallback, useEffect } from "react";
import { useSelector } from "react-redux";
import { getWritableCategories } from "../reducers/selectors/settings";
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

export type FormValue = { processName: string; processCategory: string; processingMode: string };

enum ProcessingMode {
    "streaming" = "streaming",
    "requestResponse" = "requestResponse",
    "batch" = "batch",
}

interface AddProcessFormProps extends ChangeableValue<FormValue> {
    fieldErrors: FieldError[];
}

export function AddProcessForm({ value, onChange, fieldErrors }: AddProcessFormProps): JSX.Element {
    const { t } = useTranslation();
    const categories = useSelector(getWritableCategories);

    const onFieldChange = useCallback((field: keyof FormValue, next: string) => onChange({ ...value, [field]: next }), [onChange, value]);

    useEffect(() => {
        if (!value.processCategory) {
            onFieldChange("processCategory", categories[0]);
        }
    }, [categories, onFieldChange, value.processCategory]);

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
                                label={t("addProcessForm.label.streaming", "Streaming")}
                                value={ProcessingMode.streaming}
                                Icon={StreamingIcon}
                                active={value.processingMode === ProcessingMode.streaming}
                            />
                            <CustomRadio
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
                        <Typography component={"div"} variant={"overline"} mt={1}>
                            <Trans i18nKey={"addProcessForm.helperText.processingMode"}>
                                Processing mode defines how scenario deployed on an engine interacts with the outside world. Click here to
                                <Link
                                    sx={{ cursor: "pointer", ml: 0.5 }}
                                    href="https://nussknacker.io/documentation/about/ProcessingModes/"
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
                            onChange={(e) => onFieldChange("processCategory", e.target.value)}
                        >
                            {categories.map((cat, index) => (
                                <option key={index} value={cat}>
                                    {cat}
                                </option>
                            ))}
                        </SelectNodeWithFocus>
                    </div>
                </FormControl>
            </NodeTable>
        </div>
    );
}
