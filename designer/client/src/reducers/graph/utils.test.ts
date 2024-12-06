import { prepareNewNodesWithLayout } from "./utils";
import { nodesWithPositions, state } from "./utils.fixtures";

describe("GraphUtils", () => {
    it("prepareNewNodesWithLayout should update union output expression parameter with an updated node name when new unique node ids created", () => {
        const { scenarioGraph } = state.scenario;
        expect(prepareNewNodesWithLayout(scenarioGraph.nodes, nodesWithPositions, true)).toMatchSnapshot();
    });
});
