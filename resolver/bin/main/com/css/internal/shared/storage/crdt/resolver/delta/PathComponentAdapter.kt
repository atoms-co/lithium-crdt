package com.css.internal.shared.storage.crdt.resolver.delta

interface PathComponentAdapter<C> {
    // Factory methods for creating path components
    fun createPathComponentField(field: Int): C
    fun createPathComponentBooleanKey(key: Boolean): C
    fun createPathComponentIntKey(key: Int): C
    fun createPathComponentLongKey(key: Long): C
    fun createPathComponentStringKey(key: String): C
    fun createPathComponentRepeatedIndex(index: Int): C

    // Getter methods for extracting values from path components
    /**
     * Extracts the field number from a path component, if present.
     * @return The field number, or null if this component doesn't represent a field
     */
    val C.fieldNumber: Int?

    /**
     * Extracts the boolean key from a path component, if present.
     * @return The boolean key, or null if this component doesn't represent a boolean map key
     */
    val C.booleanKey: Boolean?

    /**
     * Extracts the int key from a path component, if present.
     * @return The int key, or null if this component doesn't represent an int map key
     */
    val C.intKey: Int?

    /**
     * Extracts the long key from a path component, if present.
     * @return The long key, or null if this component doesn't represent a long map key
     */
    val C.longKey: Long?

    /**
     * Extracts the string key from a path component, if present.
     * @return The string key, or null if this component doesn't represent a string map key
     */
    val C.stringKey: String?

    /**
     * Extracts the repeated index from a path component, if present.
     * @return The index, or null if this component doesn't represent a list index
     */
    val C.repeatedIndex: Int?
}
