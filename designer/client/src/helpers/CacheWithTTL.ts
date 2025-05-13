import { ArrayKeyedMapLike } from "./ArrayKeyedMapLike";

type Entry<V> = {
    value: V;
    lastUsed: number;
};

export class CacheWithTTL<K extends any[], V> {
    private map = new ArrayKeyedMapLike<K, Entry<V>>();

    constructor(private ttl = 60000, private maxSize = 10) {}

    private now(): number {
        return Date.now();
    }

    private isExpired(entry: Entry<V>): boolean {
        return this.now() - entry.lastUsed >= this.ttl;
    }

    private cleanup(): void {
        this.map.forEach((entry, key) => {
            if (this.isExpired(entry)) {
                this.map.delete(key);
            }
        });
    }

    get(key: K): V {
        this.cleanup();
        const entry = this.map.get(key);
        if (!entry) return undefined;

        entry.lastUsed = this.now();
        return entry.value;
    }

    set(key: K, value: V): this {
        this.cleanup();
        if (this.map.size >= this.maxSize) {
            const oldestKey = this.map.keys().next().value;
            this.map.delete(oldestKey);
        }

        this.map.set(key, {
            value,
            lastUsed: this.now(),
        });
        return this;
    }

    has(key: K): boolean {
        this.cleanup();
        const entry = this.map.get(key);
        return entry && !this.isExpired(entry);
    }

    delete(key: K): boolean {
        return this.map.delete(key);
    }

    clear(): void {
        this.map.clear();
    }
}
