import { createToken, EmbeddedActionsParser, Lexer } from "chevrotain";

const MapOpen = createToken({
    name: "MapOpen",
    pattern: /(#AGG\.map\()?{/,
});
const MapClose = createToken({
    name: "MapClose",
    pattern: /}(\))?/,
});
const Comma = createToken({
    name: "Comma",
    pattern: /,/,
});
const Colon = createToken({
    name: "Colon",
    pattern: /:/,
});
const Identifier = createToken({
    name: "Identifier",
    pattern: /([a-zA-Z]\w+|"[^"]+")/,
});
const Number = createToken({
    name: "Number",
    pattern: /\d+/,
});
const Spel = createToken({
    name: "Spel",
    pattern: /#([^{},:\n\r])+/,
});
const WhiteSpace = createToken({
    name: "WhiteSpace",
    pattern: /\s+/,
    group: Lexer.SKIPPED,
});

const aggMapTokens = [MapClose, MapOpen, Comma, Spel, Identifier, Number, Colon, WhiteSpace];

export const AggMapLikeLexer = new Lexer(aggMapTokens);

export class AggMapLikeParser extends EmbeddedActionsParser {
    constructor() {
        super(aggMapTokens, { recoveryEnabled: true });

        this.performSelfAnalysis();
    }

    object = this.RULE("object", () => {
        const obj = {};

        this.CONSUME(MapOpen);
        this.MANY_SEP({
            SEP: Comma,
            DEF: () => {
                Object.assign(obj, this.SUBRULE(this.objectItem));
            },
        });
        this.CONSUME(MapClose);

        return obj;
    });

    objectItem = this.RULE("objectItem", () => {
        const obj = {};

        const lit = this.CONSUME(Identifier);
        this.CONSUME(Colon);

        const value = this.OR([
            { ALT: () => this.CONSUME(Spel) },
            { ALT: () => this.CONSUME2(Identifier) },
            { ALT: () => this.CONSUME(Number) },
        ]);

        const key = lit.isInsertedInRecovery ? "BAD_KEY" : lit.image.replaceAll(/"/g, "");
        obj[key] = value.image;

        return obj;
    });
}
