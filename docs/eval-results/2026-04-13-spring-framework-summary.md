# Spring Framework Eval Summary

Latest completed run:

- Command: `PRINCE_EVAL_ROOTS=/Users/gus/dev/projects/spring-framework ./gradlew :core:evalTest`
- Raw report: `docs/eval-results/2026-04-14.md`
- Project: `spring-framework @ 1787d3e885`

## Current status

- The latest completed Spring run is past the old runtime-crash phase.
- **Parse errors: `0` in all 9 configs.**
- The only remaining hard failures are **idempotency failures**.
- Each config scanned and attempted `9198` files.
- Each config reformatted `8760` files and found `438` already clean.

## Progress snapshot

The evaluation is now in a much better place than the earlier `NoSuchElementException` / indent-underflow era:

- Earlier runs were blocked by large numbers of formatter runtime failures.
- The latest completed run reports **no parse/runtime failures at all**.
- Remaining work is now entirely about making already-formatted files stable on the second pass.

## Idempotency counts by config

| Config | Idempotency failures |
|---|---:|
| `aggressive-wide` | `71` |
| `aggressive-balanced` | `53` |
| `aggressive-narrow` | `52` |
| `moderate-wide` | `72` |
| `moderate-balanced` | `54` |
| `moderate-narrow` | `53` |
| `default-wide` | `76` |
| `default-balanced` | `55` |
| `default-narrow` | `54` |

Totals:

- **Total remaining idempotency failures across all configs:** `540`
- **Best configs:** `aggressive-narrow` with `52`
- **Worst config:** `default-wide` with `76`

## What this means

- Spring is no longer blocked by parser support or formatter exceptions.
- The formatter now completes a full pass over the Spring corpus in every config.
- The remaining gap is second-pass stability, especially in `wide` wrapping modes.

## Current leading files

From the latest completed run, the first remaining aggressive-wide failures are:

1. `spring-r2dbc/src/test/java/org/springframework/r2dbc/connection/init/AbstractDatabasePopulatorTests.java`
2. `spring-r2dbc/src/main/java/org/springframework/r2dbc/connection/init/DatabasePopulator.java`
3. `spring-r2dbc/src/main/java/org/springframework/r2dbc/connection/ConnectionFactoryUtils.java`
4. `spring-oxm/src/main/java/org/springframework/oxm/jaxb/Jaxb2Marshaller.java`
5. `spring-orm/src/main/java/org/springframework/orm/jpa/SharedEntityManagerCreator.java`

## Recent wins reflected in this state

The latest runs have already eliminated several earlier front-of-list failures, including:

- `spring-webflux/src/test/java/org/springframework/web/reactive/FlushingIntegrationTests.java`
- `spring-webflux/src/test/java/org/springframework/web/reactive/function/server/RouterFunctionsTests.java`
- protobuf generated interfaces that were drifting comments onto declaration headers
- several earlier comment-placement and chain/lambda runtime-failure clusters that no longer appear as parse/runtime blockers

## Bottom line

Spring evaluation is now in the **final stabilization phase**:

- `0` parse/runtime failures
- `540` remaining idempotency failures across 9 configs
- failures are concentrated in formatting stability, not formatter crashes
