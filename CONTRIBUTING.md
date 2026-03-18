# Contributing to protobuf-crdt

Thank you for your interest in contributing to protobuf-crdt! This document provides guidelines and information for contributors.

## Code of Conduct

This project adheres to a code of conduct. By participating, you are expected to uphold this code. Please report unacceptable behavior to the project maintainers.

## How to Contribute

### Reporting Bugs

Before creating a bug report, please check existing issues to avoid duplicates. When creating a bug report, include:

- A clear, descriptive title
- Steps to reproduce the issue
- Expected behavior vs actual behavior
- Your environment (OS, Java version, Kotlin version)
- Relevant code snippets or error messages
- If possible, a minimal reproduction case

### Suggesting Features

Feature requests are welcome! Please provide:

- A clear description of the feature
- The use case / problem it solves
- Example usage if applicable
- Any implementation ideas you have

### Pull Requests

1. **Fork and clone** the repository
2. **Create a branch** for your changes:
   ```bash
   git checkout -b feature/your-feature-name
   # or
   git checkout -b fix/issue-description
   ```
3. **Make your changes** following the code style guidelines below
4. **Write tests** for new functionality
5. **Run the test suite** to ensure nothing is broken:
   ```bash
   ./gradlew test
   ```
6. **Commit your changes** with a clear commit message
7. **Push** to your fork and open a Pull Request

## Development Setup

### Prerequisites

- JDK 17 or higher
- Gradle 9.0+ (included via wrapper)

### Building

```bash
# Build all modules
./gradlew build

# Build a specific module
./gradlew :resolver:build
./gradlew :wire:build
./gradlew :protoc:build
```

### Running Tests

```bash
# Run all tests
./gradlew test

# Run tests for a specific module
./gradlew :resolver:test
./gradlew :wire:test
./gradlew :protoc:test

# Run tests with detailed output
./gradlew test --info
```

### Generating Proto Classes

```bash
# Wire compilation
./gradlew :wire-data:generateWireProtos
./gradlew :wire-test:generateWireTestProtos

# Protoc compilation
./gradlew :protoc-data:generateProto
```

## Code Style Guidelines

### Kotlin

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Prefer immutability (`val` over `var`, immutable collections)
- Use expression bodies for simple functions
- Document public APIs with KDoc

### Documentation

- All public classes, functions, and properties should have KDoc documentation
- Include `@param`, `@return`, and `@throws` tags where applicable
- Provide code examples for complex APIs

### Testing

- Write unit tests for all new functionality
- Use descriptive test names that explain what is being tested
- Follow the Arrange-Act-Assert pattern
- Mock external dependencies appropriately

## Project Structure

Understanding the module structure helps you know where to make changes:

```
protobuf-crdt/
├── resolver/       # Core algorithms - pure Kotlin, no protobuf deps
├── data/           # Proto schema definitions (*.proto files)
├── wire-data/      # Wire-generated data classes from data/
├── wire/           # Wire CRDT implementation
├── protoc-data/    # Protoc-generated data classes from data/
├── protoc/         # Protoc CRDT implementation
└── fixtures/       # Shared test utilities
```

### Where to Make Changes

| Change Type | Module(s) |
|------------|-----------|
| Core resolution algorithm | `resolver` |
| Proto schema changes | `data`, then regenerate `wire-data` and `protoc-data` |
| Wire-specific behavior | `wire` |
| Protoc-specific behavior | `protoc` |
| New collection strategy | `resolver` (algorithm), `wire`/`protoc` (implementation) |

## Commit Messages

Write clear, concise commit messages:

- Use the imperative mood ("Add feature" not "Added feature")
- Keep the first line under 72 characters
- Reference issues when applicable (`Fixes #123`)

Example:
```
Add tombstone TTL cleanup for map resolver

- Implement time-based tombstone expiration
- Add crdt_tombstone_ttl proto option
- Update MapResolver to clean expired tombstones during writes

Fixes #42
```

## Review Process

1. A maintainer will review your PR
2. They may request changes or ask questions
3. Once approved, a maintainer will merge your PR
4. Your contribution will be included in the next release

## Questions?

If you have questions about contributing, feel free to:

- Open an issue with your question
- Start a discussion in the repository

Thank you for contributing!
