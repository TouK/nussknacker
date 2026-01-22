export interface AssertionParts {
    assertion: string;
    expected: string;
    actual: string;
}

export const decodeAssertionExpression = (expression: string): AssertionParts | null => {
    if (!expression || typeof expression !== "string") {
        return null;
    }

    const assertionMatch = expression.match(/#TESTS\.(\w+)\s*\((.*)\)\s*$/);
    if (!assertionMatch) {
        return null;
    }

    const assertion = assertionMatch[1];
    const argsString = assertionMatch[2].trim();

    // Parse the arguments - handle quoted strings and nested expressions
    const args = parseArguments(argsString);

    if (args.length < 1) {
        return null;
    }

    return {
        assertion,
        expected: args[0],
        actual: args[1] || "",
    };
};

export const encodeAssertionExpression = (parts: AssertionParts): string => {
    const { assertion, expected, actual } = parts;
    return `#TESTS.${assertion}(${formatArgument(expected)}, ${formatArgument(actual)})`;
};

const parseArguments = (argsString: string): string[] => {
    const args: string[] = [];
    let current = "";
    let inQuotes = false;
    let quoteChar = "";
    let parenDepth = 0;
    let braceDepth = 0;

    for (let i = 0; i < argsString.length; i++) {
        const char = argsString[i];
        const prevChar = i > 0 ? argsString[i - 1] : "";

        // Handle quotes
        if ((char === '"' || char === "'") && prevChar !== "\\") {
            if (!inQuotes) {
                inQuotes = true;
                quoteChar = char;
            } else if (char === quoteChar) {
                inQuotes = false;
            }
        }

        // Handle parentheses depth
        if (!inQuotes) {
            if (char === "(") parenDepth++;
            if (char === ")") parenDepth--;
        }

        // Handle curly braces depth
        if (!inQuotes) {
            if (char === "{") braceDepth++;
            if (char === "}") braceDepth--;
        }

        // Split on comma at depth 0 and outside quotes
        if (char === "," && !inQuotes && parenDepth === 0 && braceDepth === 0) {
            args.push(current.trim());
            current = "";
        } else {
            current += char;
        }
    }

    if (current.trim()) {
        args.push(current.trim());
    }

    return args.map(unquoteString);
};

const unquoteString = (str: string): string => {
    if ((str.startsWith("'") && str.endsWith("'")) || (str.startsWith('"') && str.endsWith('"'))) {
        return str.slice(1, -1);
    }
    return str;
};

const formatArgument = (arg: string): string => {
    // If the argument already looks like an expression or contains special chars, don't quote it
    if (arg.includes("{") || arg.includes("(") || arg.startsWith("#")) {
        return arg;
    }
    // If the argument is a number (integer or float), don't quote it
    if (!isNaN(Number(arg)) && arg.trim() !== "") {
        return arg;
    }
    // Otherwise wrap in single quotes
    return `'${arg}'`;
};
