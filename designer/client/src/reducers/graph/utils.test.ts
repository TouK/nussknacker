import { prepareNewNodesWithLayout } from "./utils";
import { scenarioGraph, nodesWithPositions } from "./utils.fixtures";

describe("GraphUtils", () => {
    it("prepareNewNodesWithLayout should update union output expression parameter with an updated node name when new unique node ids created", () => {
        expect(prepareNewNodesWithLayout(scenarioGraph.nodes, nodesWithPositions, true)).toMatchSnapshot();
    });
});
