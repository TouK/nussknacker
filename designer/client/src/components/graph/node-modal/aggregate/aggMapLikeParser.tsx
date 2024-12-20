import { parse } from "ts-spel";
import { Ast } from "ts-spel/lib/lib/Ast";

export class AggMapLikeParser {
    parseList = (input: string) => {
        const ast = this.#parse(input);
        return this.#parseList(input, ast);
    };

    parseObject = (input: string) => {
        const ast = this.#parse(input);
        return this.#parseObject(input, ast);
    };

    #parse(input: string) {
        try {
            return parse(input, true);
        } catch (e) {
            return null;
        }
    }

    #parseList(input: string, ast?: Ast) {
        if (ast?.type !== "CompoundExpression") return null;
        if (ast.expressionComponents[0].type !== "InlineList") return null;
        if (ast.expressionComponents[1].type !== "PropertyReference") return null;
        if (ast.expressionComponents[1].propertyName !== "toString") return null;
        return ast.expressionComponents[0].elements.map((el) => this.#printFragment(input, el));
    }

    #printFragment(
        text: string,
        el: {
            start: number;
            end: number;
        },
    ) {
        return text.slice(el.start, el.end).trim();
    }

    #parseObject(input: string, ast?: Ast) {
        switch (ast?.type) {
            case "InlineList":
                return ast.elements.length < 1 ? {} : null;
            case "InlineMap":
                return Object.fromEntries(Object.entries(ast.elements).map(([key, el]) => [key, this.#printFragment(input, el)]));
            case "CompoundExpression":
                if (ast.expressionComponents[0].type !== "VariableReference") return null;
                if (ast.expressionComponents[0].variableName !== "AGG") return null;
                if (ast.expressionComponents[1].type !== "MethodReference") return null;
                if (ast.expressionComponents[1].methodName !== "map") return null;
                return this.#parseObject(input, ast.expressionComponents[1].args[0]);
            default:
                return null;
        }
    }
}
