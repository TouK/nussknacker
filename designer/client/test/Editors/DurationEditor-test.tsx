import * as React from "react";

import { render, screen } from "@testing-library/react";
import { DurationEditor, duration } from "../../src/components/graph/node-modal/editors/expression/Duration/DurationEditor";
import { TimeRange } from "../../src/components/graph/node-modal/editors/expression/Duration/TimeRangeComponent";
import { mockFormatter, mockFieldErrors, mockValueChange } from "./helpers";
import { NuThemeProvider } from "../../src/containers/theme/nuThemeProvider";
import { FormatterType, typeFormatters } from "../../src/components/graph/node-modal/editors/expression/Formatter";
import type { Duration } from "../../src/components/graph/node-modal/editors/expression/Duration/DurationEditor";
import { EditorType } from "../../src/components/graph/node-modal/editors/expression/types";

describe(DurationEditor.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <NuThemeProvider>
                <DurationEditor
                    readOnly={false}
                    isMarked={false}
                    onValueChange={mockValueChange}
                    fieldErrors={mockFieldErrors}
                    editorConfig={{
                        type: EditorType.CRON_EDITOR,
                        timeRangeComponents: [TimeRange.Years],
                    }}
                    expressionObj={{ language: "spel", expression: "" }}
                    showValidation={true}
                    formatter={mockFormatter}
                />
            </NuThemeProvider>,
        );

        expect(screen.getByText("validation error")).toBeInTheDocument();
    });
});

describe(`${duration.name} function`, () => {
    it("should parse duration without loosing days if more than 31", () => {
        const emptyDuration: Duration = { days: 0, hours: 0, minutes: 0, seconds: 0, milliseconds: 0 };
        const formatter = typeFormatters[FormatterType.Duration];
        const oneMinute = "PT1M";
        const oneHour = "PT1H";
        const almostOneDay = "PT23H";
        const oneDayOneHour = "PT25H";
        const oneDay = "P1D";
        const fortyDays = "P40D";
        const mix = "P1DT1H1M0.10S";

        const times = [oneMinute, oneHour, almostOneDay, oneDayOneHour, oneDay, fortyDays, mix];
        const results = [
            { ...emptyDuration, minutes: 1 },
            { ...emptyDuration, hours: 1 },
            { ...emptyDuration, hours: 23 },
            { ...emptyDuration, days: 1, hours: 1 },
            { ...emptyDuration, days: 1 },
            { ...emptyDuration, days: 40 },
            { days: 1, hours: 1, minutes: 1, seconds: 0, milliseconds: 100 },
        ];
        expect(times.map(formatter.decode).map(duration)).toStrictEqual(results);
    });
});
