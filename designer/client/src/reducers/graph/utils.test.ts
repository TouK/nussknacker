import { prepareNewNodesWithLayout } from "./utils";
import { scenarioGraph, nodesWithPositions } from "./utils.fixtures";

jest.mock("uuid", () => {
    let counter = 0;
    const uuids = ["e7a058ef-126b-4959-ae1f-d1bfd91a6529", "a493b327-b57f-4ef6-bd61-eb008b83d241", "1960a6d0-ee25-4c38-a81b-3720738dfccf"];
    return { v4: () => uuids[counter++] ?? `uuid-${counter}` };
});

describe("GraphUtils", () => {
    it("prepareNewNodesWithLayout should update union output expression parameter with an updated node name when new unique node ids created", () => {
        expect(prepareNewNodesWithLayout(scenarioGraph.nodes, nodesWithPositions, true)).toMatchSnapshot();
    });
});
