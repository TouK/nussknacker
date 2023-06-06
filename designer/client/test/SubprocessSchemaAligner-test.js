import * as FragmentSchemaAligner from "../src/components/graph/FragmentSchemaAligner";
import _ from "lodash";

const fragmentProcessDefinitionData = {
    componentGroups: [
        {
            name: "fragments",
            components: [
                {
                    type: "fragment",
                    label: "subproc1",
                    node: {
                        type: "FragmentInput",
                        id: "",
                        ref: {
                            id: "subproc1",
                            parameters: [
                                { name: "param1", expression: { language: "spel", expression: "''" } },
                                { name: "param2", expression: { language: "spel", expression: "''" } },
                            ],
                        },
                    },
                },
            ],
        },
    ],
    processDefinition: {},
};

describe("fragment schema aligner test", () => {
    it("should remove redundant and add missing parameters according to schema", () => {
        const fragmentNode = {
            type: "FragmentInput",
            id: "node4",
            ref: {
                id: "subproc1",
                parameters: [
                    { name: "oldParam1", expression: { language: "spel", expression: "'abc'" } },
                    { name: "param2", expression: { language: "spel", expression: "'cde'" } },
                ],
            },
            outputs: {},
        };

        const alignedFragment = FragmentSchemaAligner.alignFragmentWithSchema(fragmentProcessDefinitionData, fragmentNode);

        expect(alignedFragment.ref.parameters).toEqual([
            { name: "param1", expression: { language: "spel", expression: "''" } },
            { name: "param2", expression: { language: "spel", expression: "'cde'" } },
        ]);
        expect(_.omit(alignedFragment, "ref")).toEqual(_.omit(fragmentNode, "ref"));
    });

    it("should not change anything if fragment is valid with schema", () => {
        const fragmentNode = {
            type: "FragmentInput",
            id: "node4",
            ref: {
                id: "subproc1",
                parameters: [
                    { name: "param1", expression: { language: "spel", expression: "'abc'" } },
                    { name: "param2", expression: { language: "spel", expression: "'cde'" } },
                ],
            },
            outputs: {},
        };

        const alignedFragment = FragmentSchemaAligner.alignFragmentWithSchema(fragmentProcessDefinitionData, fragmentNode);

        expect(alignedFragment).toEqual(fragmentNode);
    });
});
