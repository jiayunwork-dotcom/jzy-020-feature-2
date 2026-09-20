# flame-lab

Constant-pressure adiabatic flame temperature balance service. Given a fuel,
an equivalence ratio and an intake temperature, it solves for the product
composition and the adiabatic flame temperature, recording the enthalpy
residual of every iteration. It also runs the inverse direction: given a
wanted adiabatic flame temperature, it finds the equivalence ratio(s) that
reach it. Each solve (either direction) is persisted as one job in an
in-process SQLite database and can be retrieved later by id, convergence
traces included.

Scope is deliberately narrow: constant-pressure adiabatic balance only, HTTP
in/out only, no frontend, no accounts, no batch endpoint (one job = one
balance iteration, one inverse job = one target-temperature search).

## Model

- **Fuels**: methane (CH4) and ethane (C2H6). Oxidizer is air pinned at
  O2 0.21 / N2 0.79 by mole.
- **Equivalence ratio** phi > 0; phi = 1 is stoichiometric. Per mole of fuel
  the oxidizer demand is `c + h/4` mol O2; actual O2 is demand/phi.
- **Products** are pinned to CO2, H2O, O2, N2 — no CO/H2 dissociation. Lean
  mixtures are diluted by excess O2, rich mixtures by unburned fuel (only the
  fraction 1/phi of the fuel finds oxygen). N2 is inert: product N2 equals
  intake N2. Total mole numbers are summed from the species, never assumed
  constant.
- **Element closure**: C/H/O/N atoms in minus out must stay below the pinned
  threshold (1e-9 mol); the residuals are stored on the job.
- **Enthalpy**: h(T) = h_f(298.15 K) + integral of Cp dT, with built-in NASA
  Cp polynomials. Reactants and products share the same coefficient set.
  Evaluating Cp outside the pinned window [250, 5000] K fails the job instead
  of extrapolating.
- **Iteration**: damped Newton on R(T) = H_products(T) - H_reactants. The
  initial guess overshoots the root on purpose; every accepted (and recorded)
  step strictly reduces |R|, so the residual curve is monotone. The loop
  stops as soon as |R| <= 1e-3 J/mol (pinned tolerance) and fails the job if
  50 iterations (pinned) are not enough — it never fabricates a temperature.

## Inverse balance (target temperature -> equivalence ratio)

Flame temperature is **not monotonic** in equivalence ratio: from very lean
to very rich it rises, peaks near stoichiometric, then falls again as excess
rich-side fuel absorbs heat. A target below the peak therefore usually has
two phis — a lean one and a rich one; exactly at the peak there is one;
above it the target is unreachable.

The inverse search is purely a wrapper around the unchanged forward balance
— there is no second thermodynamics core, no lookup table, no fitted
polynomial. Every temperature observation is a genuine forward solve
(same catalog, same air, same NASA coefficients, same temperature kernel):

1. **Peak location** (`PeakLocator`): a geometric scan over phi brackets the
   hottest point (extending outward if the hottest sample sits on an edge),
   then a golden-section search refines the peak phi and peak temperature.
2. **Branch root-finding** (`SideRootFinder`): on each requested branch it
   walks outward from the peak to bracket the target (retracting past probes
   whose flame root leaves the pinned Cp window), then bisects on the
   temperature deviation. Probes are budgeted at 60 per side; if the
   deviation cannot be pressed into the pinned **1 K** tolerance, that side
   fails instead of returning a phi that never met the target.
3. **Forward re-check** (`ForwardEvaluator`): every reported phi is run
   through the forward balance once more; its flame temperature must land
   within 1 K of the target, phi must be positive, and the atom residual
   must stay below the 1e-9 threshold.

Lean and rich branches each accumulate their **own** ordered outer sequence
of `(step, equivalenceRatio, temperatureDeviation)`; they are never merged.
A target above the located peak fails the job as `TARGET_UNREACHABLE` and
still records the peak phi/temperature so the caller knows the ceiling.

## Layout (one responsibility per module)

| package | responsibility |
| --- | --- |
| `balance` | element balance, product composition, atom residuals |
| `thermo`  | species, NASA Cp polynomials, enthalpy/Cp integrals, validity window |
| `solver`  | forward temperature iteration, convergence decision, residual curve |
| `inverse` | peak location, per-branch outer root-finding, forward re-check, inverse jobs (store/validation/seeding) |
| `job`     | forward job records, SQLite store, input validation, demo seeding |
| `api`     | HTTP endpoints and typed error mapping |

## API

### Forward

- `POST /api/jobs` — body `{"fuel":"methane","equivalenceRatio":1.0,"intakeTemperature":298.15}`.
  Returns `201` with the full job (status `CONVERGED` or `FAILED`). Invalid
  input (unknown fuel, non-positive phi or intake temperature, missing or
  non-finite values) is rejected with `400` and a typed error before any
  iteration starts.
- `GET /api/jobs/{id}` — full job record: inputs, pinned criteria
  (`enthalpyTolerance`, `maxIterations`, `atomBalanceThreshold`), the
  iteration sequence `(step, temperature, enthalpyResidual)`, final
  temperature, product mole fractions, atom residuals, typed error if failed.
- `GET /api/jobs` — list of all jobs.

A demo job (`label=demo-stoich-methane`, methane, phi=1, 298.15 K) is seeded
at startup as job id 1 on a fresh database.

### Inverse

- `POST /api/inverse-jobs` — body
  `{"fuel":"methane","intakeTemperature":298.15,"targetTemperature":2150.0,"side":"BOTH"}`.
  `side` is `LEAN`, `RICH` or `BOTH`. Returns `201` with the full inverse
  job: inputs, the located `peakEquivalenceRatio`/`peakTemperature`, the
  forward-re-checked `leanSolution` and `richSolution` (each with
  `equivalenceRatio`, `flameTemperature`, `temperatureDeviation`, product
  mole fractions and atom residuals), the two independently accumulated
  sequences `leanSequence`/`richSequence` of
  `(step, equivalenceRatio, temperatureDeviation)`, and the pinned
  `temperatureTolerance`/`maxOuterSteps`. Unknown fuel, missing or
  non-finite numbers, non-positive intake or target temperature, or an
  illegal side are rejected with `400` typed errors before the outer search
  starts. A target above the peak is persisted as `FAILED` with
  `TARGET_UNREACHABLE`, the peak written into the record, and no fabricated
  solution; a branch that cannot close inside tolerance fails as
  `SIDE_NOT_CONVERGED` with the trace it walked.
- `GET /api/inverse-jobs/{id}` — full inverse job record by number.
- `GET /api/inverse-jobs` — list of all inverse jobs.

A demo inverse job (`label=demo-inverse-methane-2150`, methane, intake
298.15 K, target 2150 K, `BOTH`) is seeded at startup as inverse job id 1 on
a fresh database; both its lean and rich phi converge and pass their own
forward re-check (2150 K is a notch below the ~2325 K methane peak).

## Build, test, run

```bash
mvn test          # regression suite
mvn package       # executable jar
java -jar target/flame-lab-1.0.0.jar
```

Container (one command, uses a locally available JDK 17 image as base):

```bash
./start.sh        # mvn package + docker build + docker run on :8080
```

If the local JDK 17 image has a different tag:

```bash
docker build --build-arg BASE_IMAGE=<your-local-jdk17-image> -t flame-lab .
docker run --rm -p 8080:8080 flame-lab
```

The SQLite file defaults to `./flamelab.db` (`/data/flamelab.db` in the
container); override with `FLAMELAB_DB_PATH`.

## Example

```bash
# forward: phi -> flame temperature
curl -s -X POST localhost:8080/api/jobs \
  -H 'Content-Type: application/json' \
  -d '{"fuel":"methane","equivalenceRatio":1.0,"intakeTemperature":298.15}'

# inverse: wanted flame temperature -> lean and rich equivalence ratios
curl -s -X POST localhost:8080/api/inverse-jobs \
  -H 'Content-Type: application/json' \
  -d '{"fuel":"methane","intakeTemperature":298.15,"targetTemperature":2150.0,"side":"BOTH"}'
```
