import { createSelector } from "reselect";

import { getProcessName, getScenarioGraph, isFragment } from "./graph";
import { getProcessState } from "./scenarioState";
import { getUi } from "./ui";

const getStoredContextRawData = createSelector([getUi], ({ aiContextRawData: prevData }) => {
    return prevData || "";
});

export const getContextRawData = createSelector(
    [getProcessName, isFragment, getScenarioGraph, getProcessState],
    (name, isFragment, scenario, state) => {
        if (!name) return "";
        const value = isFragment ? { name, scenario, isFragment } : { name, scenario, state };
        return JSON.stringify(value);
    },
);

export const getContextPrompt = createSelector([getContextRawData, getStoredContextRawData], (data, prevData) => {
    if (!data) return "";
    const context = prevData
        ? prevData === data
            ? [`Nothing changed in raw data since last time I asked you.`]
            : [
                  `This time I have this raw internal data: ${data}.`,
                  `Watch out for changes, it might be different scenario or just value change.`,
              ]
        : [
              `Here you have some more raw internal data: ${data}.`,
              `Remember that I can't fully see that data. I only edit some of it directly, while other parts are displayed differently.`,
              `This data is provided so you can better understand the context, all my expressions, nodes and connections should be here.`,
              `I mainly edit expressions in leaf values that are sometimes wrapped in a more user-friendly layer.`,
              `Please always answer briefly. Never repeat yourself.`,
          ];
    return context.join("\n");
});
