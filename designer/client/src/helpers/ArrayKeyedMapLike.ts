type Entry<K, V> = {
    key: K;
    value: V;
};

export class ArrayKeyedMapLike<K extends any[], V> {
    private entries: Entry<K, V>[] = [];

    get(key: K): V | undefined {
        const entry = this.entries.find((e) => this.compareKeys(e.key, key));
        return entry?.value;
    }

    set(key: K, value: V): this {
        const existing = this.entries.find((e) => this.compareKeys(e.key, key));
        if (existing) {
            existing.value = value;
        } else {
            this.entries.push({
                key,
                value,
            });
        }
        return this;
    }

    has(key: K): boolean {
        return this.entries.some((e) => this.compareKeys(e.key, key));
    }

    delete(key: K): boolean {
        const index = this.entries.findIndex((e) => this.compareKeys(e.key, key));
        if (index >= 0) {
            this.entries.splice(index, 1);
            return true;
        }
        return false;
    }

    clear(): void {
        this.entries = [];
    }

    forEach(callback: (value: V, key: K, cache: this) => void): void {
        this.entries.forEach(({ key, value }) => {
            callback(value, key, this);
        });
    }

    get size(): number {
        return this.entries.length;
    }

    keys(): IterableIterator<K> {
        return this.entries.map((entry) => entry.key).values();
    }

    private compareKeys(a: K, b: K): boolean {
        if (a.length !== b.length) return false;
        return a.every((entry, i) => entry === b[i]);
    }
}
