# lithium-crdt Interactive Demo

An interactive web demo showing how lithium-crdt performs field-level CRDT conflict resolution on Protocol Buffer messages.

## Running

```bash
git clone https://github.com/atoms-co/lithium-crdt.git
cd lithium-crdt
./gradlew :examples:interactive-demo:run
```

Your browser will open [http://localhost:8080](http://localhost:8080) automatically.

## What it demonstrates

- **Multiple nodes**: Three simulated devices (Node A, B, C), each maintaining their own copy of a `CollaborativeDocument`
- **Independent edits**: Modify different fields on different nodes while they're disconnected
- **Sync & merge**: Sync nodes to see how field-level Last-Write-Wins resolution merges changes
- **Version tree**: Inspect the parallel `VersionNode` tree that tracks per-field modification timestamps
- **Resolution strategies**: See the outcome of each sync — `NO_CHANGE`, `LOCAL`, `INCOMING`, or `MERGED_VALUES`

## How to use

1. Edit document fields on **Node A** (title, content, author, etc.) and click "Apply Changes"
2. Edit *different* fields on **Node B**
3. Click a sync button (e.g., "A → B") to merge Node A's state into Node B
4. Observe that both changes survive — the version tree shows each field's independent version
5. Try editing the *same* field on two nodes and syncing — the higher timestamp wins
6. Toggle nodes offline to simulate network partitions
7. Expand the "Version Tree" section to see the parallel version metadata structure
