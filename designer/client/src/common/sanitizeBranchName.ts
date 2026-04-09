export function sanitizeBranchName(branchId: string): string {
    return branchId
        .split("")
        .map((character, index) => {
            if (index === 0) {
                return /[A-Za-z_$]/.test(character) ? character : "_";
            }
            return /[A-Za-z0-9_$]/.test(character) ? character : "_";
        })
        .join("");
}

export function createNodeNameByContextKey(nodes: ReadonlyArray<{ id: string; name: string }>): Record<string, string> {
    return nodes.reduce<Record<string, string>>((acc, node) => {
        acc[node.id] = node.name;
        acc[sanitizeBranchName(node.id)] = node.name;
        return acc;
    }, {});
}
