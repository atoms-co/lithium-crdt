package co.atoms.protobuf.crdt.resolver.descriptor

enum class MessageFieldMergeStrategy {
    /**
     * Recursively merge into child fields using their strategies.
     * For primitives, behaves like REPLACE.
     * Conflicts resolved by version comparator (default: last-write-wins).
     */
    MERGE,
    /**
     * Replace entire field value atomically without merging child fields.
     * Conflicts resolved by version comparator (default: last-write-wins).
     */
    REPLACE,
    /**
     * Increment/decrement operations that commute.
     * Only valid for int type value fields.
     */
    INT_COUNTER,
    /**
     * Increment/decrement operations that commute.
     * Only valid for long type value fields.
     */
    LONG_COUNTER
}
