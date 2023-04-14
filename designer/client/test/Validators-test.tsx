import {notBlankValueValidator} from "../src/components/graph/node-modal/editors/Validators"
import {describe, expect, it} from "@jest/globals"

describe("Validator", () => {
    describe("notBlankValueValidator", () => {
        it.each([
            ["''", false],
            [" '' ", false],
            ['""', false],
            [' "" ', false],
            ["'someString' ", true],
            ['"someString" ', true],
            ['"someString" + ""', true],
        ])('for expression: [%s] isValid should be %s', (expression: string, expected: boolean) => {
            return expect(notBlankValueValidator.isValid(expression)).toEqual(expected)
        })
    })
})
