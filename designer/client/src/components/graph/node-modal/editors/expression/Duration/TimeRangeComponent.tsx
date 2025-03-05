import React from "react";
import { cx } from "@emotion/css";
import { UnknownFunction } from "../../../../../../types/common";
import { Duration } from "./DurationEditor";
import { Period } from "./PeriodEditor";
import { nodeInput, nodeInputWithError } from "../../../NodeDetailsContent/NodeTableStyled";

export type TimeRangeComponentType = {
    label: string;
    fieldName: string;
};

export enum TimeRange {
    Years = "YEARS",
    Months = "MONTHS",
    Days = "DAYS",
    Hours = "HOURS",
    Minutes = "MINUTES",
    Seconds = "SECONDS",
}

const components: Record<string, TimeRangeComponentType> = {
    [TimeRange.Years]: {
        label: "years",
        fieldName: "years",
    },
    [TimeRange.Months]: {
        label: "months",
        fieldName: "months",
    },
    [TimeRange.Days]: {
        label: "days",
        fieldName: "days",
    },
    [TimeRange.Hours]: {
        label: "hours",
        fieldName: "hours",
    },
    [TimeRange.Minutes]: {
        label: "minutes",
        fieldName: "minutes",
    },
    [TimeRange.Seconds]: {
        label: "seconds",
        fieldName: "seconds",
    },
};

type Props = {
    timeRangeComponentName: TimeRange;
    onChange: UnknownFunction;
    value: Duration | Period;
    readOnly: boolean;
    showValidation: boolean;
    isValid: boolean;
    isMarked: boolean;
};

export default function TimeRangeComponent(props: Props) {
    const { timeRangeComponentName, onChange, value, readOnly, showValidation, isValid, isMarked } = props;
    const component = components[timeRangeComponentName];

    return (
        <div className={"time-range-component"}>
            <input
                readOnly={readOnly}
                value={value[component.fieldName] || ""}
                onChange={(event) => onChange(component.fieldName, event.target.value)}
                className={cx([
                    nodeInput,
                    "time-range-input",
                    showValidation && !isValid && nodeInputWithError,
                    isMarked && "marked",
                    readOnly && "read-only",
                ])}
            />
            <span className={"time-range-component-label"}>{component.label}</span>
        </div>
    );
}
