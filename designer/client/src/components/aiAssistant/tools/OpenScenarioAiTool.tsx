import { useNavigate } from "react-router-dom";
import { z } from "zod";

import { visualizationUrl } from "../../../common/VisualizationUrl";
import { useFrontendAiTool } from "../useFrontendAiTool";

export const OpenScenarioAiTool = () => {
    const navigate = useNavigate();

    useFrontendAiTool({
        toolName: "open_scenario",
        description: `Use this tool to open a scenario (process) in the designer. You need to provide the exact name of the scenario to open.`,
        parameters: z.object({
            name: z
                .string()
                .describe("The exact name of the scenario to open. You can get a list of available scenarios by using another tool."),
        }),
        execute: ({ name }) => {
            navigate(visualizationUrl(name));
        },
    });

    return null;
};
