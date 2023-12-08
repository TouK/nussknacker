import { UnknownFunction } from "../../../../../../types/common";
import TimeRangeComponent, { TimeRange } from "./TimeRangeComponent";
import ValidationLabels from "../../../../../modals/ValidationLabels";
import React from "react";
import { Duration } from "./DurationEditor";
import { Period } from "./PeriodEditor";
import { TimeRangeStyled } from "./TimeRangeStyled";
import { FieldError } from "../../Validators";

type Props = {
    components: Array<TimeRange>;
    onComponentValueChange: UnknownFunction;
    readOnly: boolean;
    showValidation: boolean;
    fieldError: FieldError;
    value: Duration | Period;
    expression: string;
    isMarked: boolean;
};

export default function TimeRangeSection(props: Props): JSX.Element {
    const { components, onComponentValueChange, readOnly, showValidation, fieldError, value, isMarked } = props;

    return (
        <TimeRangeStyled>
            <div className={"time-range-components"}>
                {components.map((componentName) => (
                    <TimeRangeComponent
                        key={componentName}
                        timeRangeComponentName={componentName}
                        onChange={onComponentValueChange}
                        value={value}
                        readOnly={readOnly}
                        isMarked={isMarked}
                        isValid={!fieldError}
                        showValidation={showValidation}
                    />
                ))}
            </div>
            {showValidation && <ValidationLabels fieldError={fieldError} />}
        </TimeRangeStyled>
    );
}
