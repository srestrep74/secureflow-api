# Security Context and Concurrency

**Status: Phase 0 approved on 2026-11-15; later implementation remains phase-gated.**

The Servlet security context normally uses thread-local execution state; lifecycle infrastructure clears it. Never retain mutable principal state in singletons. Arbitrary asynchronous execution requires explicit propagation or immutable actor data. Verify isolation through real requests.

See the [consolidated Phase 0 design](phase-0-design.md) for the complete contract, trade-offs and approval gate.
