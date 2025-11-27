import { isEmpty } from "lodash";
import React from "react";

import type { UnknownFunction } from "../../../../../../types/common";
import ValidationLabels from "../../../../../modals/ValidationLabels";
import type { FieldError } from "../../Validators";
import type { Duration } from "./DurationEditor";
import type { Period } from "./PeriodEditor";
import type { TimeRange } from "./TimeRangeComponent";
import TimeRangeComponent from "./TimeRangeComponent";
import { TimeRangeStyled } from "./TimeRangeStyled";

type Props = {
    components: Array<TimeRange>;
    onComponentValueChange: UnknownFunction;
    readOnly: boolean;
    showValidation: boolean;
    fieldErrors: FieldError[];
    value: Duration | Period;
    expression: string;
    isMarked: boolean;
};

export default function TimeRangeSection(props: Props): React.JSX.Element {
    const { components, onComponentValueChange, readOnly, showValidation, fieldErrors, value, isMarked } = props;

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
                        isValid={isEmpty(fieldErrors)}
                        showValidation={showValidation}
                    />
                ))}
            </div>
            {showValidation && <ValidationLabels fieldErrors={fieldErrors} />}
        </TimeRangeStyled>
    );
}
