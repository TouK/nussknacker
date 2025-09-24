import { parseQuery, stringifyQuery } from "../src/containers/hooks/useSearchQuery";

describe("Search query parsing", () => {
    it("should encode query params", () => {
        const queryParams = {
            booleanParam: true,
            nodeIds: ["[ABC] node 1", "node 2", "node 3 \\ , & ?", "node 4 👍", "node5"],
            numberParams: [35, 20.24],
        };
        expect(stringifyQuery(queryParams)).toEqual(
            "booleanParam=true&nodeIds=%5BABC%5D%20node%201,node%202,node%203%20%5C%20%2C%20%26%20%3F,node%204%20%F0%9F%91%8D,node5&numberParams=35,20.24",
        );
    });

    it("should decode query params", () => {
        const searchString =
            "booleanParam=true&nodeIds=%5BABC%5D%20node%201,node%202,node%203%20%5C%20%2C%20%26%20%3F,node%204%20%F0%9F%91%8D,node5&numberParams=35,20.24";
        expect(parseQuery(searchString)).toEqual({
            booleanParam: true,
            nodeIds: ["[ABC] node 1", "node 2", "node 3 \\ , & ?", "node 4 👍", "node5"],
            numberParams: [35, 20.24],
        });
    });
});
