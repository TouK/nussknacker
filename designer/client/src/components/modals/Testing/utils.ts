import moment from "moment/moment";

import type { TestingEventParameters } from "./TestingEventsTable";

export const mapEventsToRunTestsFormat = (event: TestingEventParameters) => {
    let parsedVariables: Record<string, unknown> | string;
    try {
        parsedVariables = JSON.parse(event.variables);
    } catch {
        // Fallback: keep original string if not valid JSON
        parsedVariables = event.variables;
    }

    let epochString: string | undefined;
    if (event.timestamp) {
        const m = moment(event.timestamp);
        if (m.isValid()) {
            epochString = String(m.valueOf());
        }
    }

    return {
        sourceId: event.sourceId,
        variables: parsedVariables,
        timestamp: epochString,
    };
};

export const mapGeneratedTestingDataToTableFormat = (events: TestingEventParameters) => {
    events.variables = JSON.stringify(events.variables, null, 2);

    return events;
};
