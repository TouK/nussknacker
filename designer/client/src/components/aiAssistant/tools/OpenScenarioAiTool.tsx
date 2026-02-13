import { useNavigate } from "react-router-dom";
import { z } from "zod";

import { visualizationUrl } from "../../../common/VisualizationUrl";
import { useFrontendAiTool } from "../useFrontendAiTool";

export const OpenScenarioAiTool = () => {
    const navigate = useNavigate();

    useFrontendAiTool({
        toolName: "open_scenario",
        description: `Opens a scenario in the designer and navigates to its editor view. Requires the exact scenario name.`,
        parameters: z.object({
            name: z.string().describe("The exact name of the scenario to open. Get available names using another tool."),
        }),
        execute: ({ name }) => {
            navigate(visualizationUrl(name));
        },
    });

    return null;
};
