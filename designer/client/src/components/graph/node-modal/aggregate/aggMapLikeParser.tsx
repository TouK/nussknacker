import { createToken, EmbeddedActionsParser, Lexer } from "chevrotain";
import { withLogs } from "../../../../devHelpers";

const LCurly = createToken({ name: "LCurly", pattern: /\{/, push_mode: "inside" });
const RCurly = createToken({ name: "RCurly", pattern: /}/, pop_mode: true });

const CollectionOpen = createToken({
    name: "CollectionOpen",
    pattern: /#COLLECTION.join\(\{/,
    push_mode: "inside",
});
const CollectionClose = createToken({
    name: "CollectionClose",
    pattern: /}, "|"\)/,
    pop_mode: true,
});
const ListClose = createToken({
    name: "ListClose",
    pattern: /}\.toString/,
    pop_mode: true,
});
const MapOpen = createToken({
    name: "MapOpen",
    pattern: /#AGG\.map\(\{/,
    push_mode: "inside",
});
const MapClose = createToken({
    name: "MapClose",
    pattern: /}\)/,
    pop_mode: true,
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
    pattern: /([a-zA-Z0-9]([.\s]?\w+)*)/,
});

const SingleQuoted = createToken({
    name: "SingleQuoted",
    pattern: /'[^']*'/,
});
const DoubleQuoted = createToken({
    name: "DoubleQuoted",
    pattern: /"[^"]*"/,
});
const Wrapped = createToken({
    name: "Wrapped",
    pattern: /\{[^}]*}/,
    pop_mode: true,
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

const aggMapTokens = {
    modes: {
        outside: [
            SingleQuoted,
            DoubleQuoted,
            CollectionOpen,
            MapOpen,
            CollectionClose,
            MapClose,
            ListClose,
            LCurly,
            RCurly,
            Comma,
            Colon,
            WhiteSpace,
            Spel,
            Identifier,
            Number,
        ],
        inside: [
            Wrapped,
            SingleQuoted,
            DoubleQuoted,
            CollectionClose,
            MapClose,
            ListClose,
            RCurly,
            Comma,
            Colon,
            WhiteSpace,
            Spel,
            Identifier,
            Number,
        ],
    },
    defaultMode: "outside",
};

export class AggMapLikeParser extends EmbeddedActionsParser {
    private lexer: Lexer;
    private quoted = this.RULE("quoted", () => {
        return this.OR([{ ALT: () => this.CONSUME(SingleQuoted) }, { ALT: () => this.CONSUME(DoubleQuoted) }]);
    });
    private collectionItem = this.RULE("collectionItem", () => {
        return this.OR([
            { ALT: () => this.CONSUME(Wrapped).image },
            { ALT: () => this.SUBRULE(this.quoted).image },
            { ALT: () => this.CONSUME(Spel).image?.trim() },
            { ALT: () => this.CONSUME(Identifier).image?.trim() },
            { ALT: () => this.CONSUME(Number).image },
        ]);
    });
    private objectItem = this.RULE("objectItem", () => {
        const obj = {};
        const lit = this.OR([
            { ALT: () => this.CONSUME(Identifier).image },
            {
                ALT: () => {
                    const { image } = this.SUBRULE(this.quoted);
                    return image?.substring?.(1, image.length - 1);
                },
            },
        ]);
        const colon = this.CONSUME(Colon);
        const value = this.SUBRULE(this.collectionItem);

        if (colon.isInsertedInRecovery) {
            return null;
        }
        obj[lit] = value;
        return obj;
    });
    private mapItems = this.RULE("mapItems", () => {
        const obj = {};
        this.MANY_SEP({
            SEP: Comma,
            DEF: () => {
                Object.assign(obj, this.SUBRULE(this.objectItem));
            },
        });
        return obj;
    });
    private aggMap = this.RULE("aggMap", () => {
        const opening = this.CONSUME(MapOpen);
        const obj = this.SUBRULE(this.mapItems);
        this.CONSUME(MapClose);

        if (!opening.image || opening.isInsertedInRecovery) {
            return null;
        }

        return obj;
    });
    private plainMap = this.RULE("plainMap", () => {
        const opening = this.CONSUME(LCurly);
        const obj = this.SUBRULE(this.mapItems);
        this.CONSUME(RCurly);

        if (!opening.image || opening.isInsertedInRecovery) {
            return null;
        }

        return obj;
    });
    private object = this.RULE("object", () => {
        return this.OR([{ ALT: () => this.SUBRULE(this.aggMap) }, { ALT: () => this.SUBRULE(this.plainMap) }]);
    });
    private collection = this.RULE("collection", () => {
        const arr = [];
        this.CONSUME(CollectionOpen);
        this.MANY_SEP({
            SEP: Comma,
            DEF: () => {
                const item = this.SUBRULE(this.collectionItem);
                if (!item) return;
                arr.push(item);
            },
        });
        this.CONSUME(CollectionClose);
        return arr;
    });
    private list = this.RULE("list", () => {
        const arr = [];
        this.CONSUME(LCurly);
        this.MANY_SEP({
            SEP: Comma,
            DEF: () => {
                const item = this.SUBRULE(this.collectionItem);
                if (!item) return;
                arr.push(item);
            },
        });
        this.CONSUME(ListClose);
        return arr;
    });
    private groupBy = this.RULE("groupBy", () => {
        return this.OR([{ ALT: () => this.SUBRULE(this.collection) }, { ALT: () => this.SUBRULE(this.list) }]);
    });
    private fullText = "";

    constructor(tokens = aggMapTokens) {
        super(tokens, { recoveryEnabled: true });
        this.lexer = new Lexer(tokens);
        this.parseObject = withLogs(this.parseObject.bind(this));
        this.parseList = withLogs(this.parseList.bind(this));
        this.performSelfAnalysis();
    }

    parseList(input: string): Array<string> {
        this.tokenizeInput(input);
        return this.groupBy() || null;
    }

    parseObject(input: string): Record<string, string> {
        this.tokenizeInput(input);
        return this.object() || null;
    }

    private tokenizeInput(input: string) {
        const lexResult = this.lexer.tokenize(input);
        this.fullText = input;
        this.input = lexResult.tokens;
    }
}
