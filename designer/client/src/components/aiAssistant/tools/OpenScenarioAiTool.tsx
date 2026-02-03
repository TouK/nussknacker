import { useNavigate } from "react-router-dom";
import { z } from "zod";

import { visualizationUrl } from "../../../common/VisualizationUrl";
import { useFrontendAiTool } from "../useFrontendAiTool";

export const OpenScenarioAiTool = () => {
    const navigate = useNavigate();

    useFrontendAiTool({
        toolName: "open_scenario",
        description: `Open scenario`,
        parameters: z.object({ name: z.string().describe("scenario name") }),
        execute: ({ name }) => {
            navigate(visualizationUrl(name));
        },
    });

    return null;
};
