# flame-lab

Constant-pressure adiabatic flame temperature balance service. Given a fuel,
an equivalence ratio and an intake temperature, it solves for the product
composition and the adiabatic flame temperature, recording the enthalpy
residual of every iteration. Each solve is persisted as one job in an
in-process SQLite database and can be retrieved later by id, residual curve
included.

The service also solves the inverse problem: given a fuel, an intake
temperature and a *target* adiabatic flame temperature, it finds the
equivalence ratio(s) that reach it. The inverse solve is an outer loop
wrapped around the same forward balance — same fuel catalog, same air
composition, same thermodynamic coefficients, same temperature iteration
kernel; no separate thermodynamics, no lookup tables, no fitted curves.
Because the flame-temperature curve is not monotone in phi (it rises toward
a peak near stoichiometric and falls off rich of it), the service first
locates the peak itself, then searches each branch separately: a target
below the peak yields a lean root and a rich root, a target above the peak
is reported unreachable with the estimated peak attached. Inverse jobs are
persisted in the same SQLite database and retrieved by id, outer convergence
sequences included.

Scope is deliberately narrow: constant-pressure adiabatic balance only, HTTP
in/out only, no frontend, no accounts, no batch endpoint (one job = one
balance iteration, one inverse job = one target temperature).

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
- **Inverse solve**: the peak of T(phi) is located by golden-section
  maximization over the forward balance. Each requested branch (lean, rich)
  is then solved by a bracketing walk away from the peak plus bisection,
  recording every trial phi with its temperature deviation in order. A root
  is accepted only when |T_forward(phi) - target| <= 0.5 K (pinned
  temperature tolerance) within 80 outer steps (pinned); every accepted root
  is re-checked through the forward balance (temperature within tolerance,
  phi > 0, atom residuals below threshold) before it is reported. A side
  that runs out of steps fails and reports no ratio.

## Layout (one responsibility per module)

| package | responsibility |
| --- | --- |
| `balance` | element balance, product composition, atom residuals |
| `thermo`  | species, NASA Cp polynomials, enthalpy/Cp integrals, validity window |
| `solver`  | temperature iteration, convergence decision, residual curve |
| `inverse` | peak location, two-sided outer root find, forward re-check, inverse job store/validation |
| `job`     | job records, SQLite store, input validation, demo seeding |
| `api`     | HTTP endpoints and typed error mapping |

## API

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
- `POST /api/inverse-jobs` — body `{"fuel":"methane","intakeTemperature":298.15,"targetTemperature":2200.0,"side":"BOTH"}`
  with `side` one of `LEAN`, `RICH`, `BOTH` (case-insensitive). Returns `201`
  with the full inverse job. Invalid input (unknown fuel, non-positive
  intake or target temperature, illegal side, missing or non-finite values)
  is rejected with `400` and a typed error before the outer search starts.
- `GET /api/inverse-jobs/{id}` — full inverse job record: inputs, pinned
  criteria (`temperatureTolerance`, `maxOuterSteps`), the located peak
  (`peakEquivalenceRatio`, `peakTemperature`), each requested side's found
  ratio with its forward re-check (flame temperature, product mole
  fractions, atom residuals) and its own outer convergence sequence
  `(step, equivalenceRatio, temperatureDeviation)`, typed error if failed.
  A target above the peak fails with `TARGET_UNREACHABLE` and the estimated
  peak attached.
- `GET /api/inverse-jobs` — list of all inverse jobs.

Two demo jobs are seeded at startup on a fresh database: job id 1
(`label=demo-stoich-methane`, methane, phi=1, 298.15 K) and inverse job id 1
(`label=demo-inverse-methane`, methane, intake 298.15 K, target 2200 K, both
sides).

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
curl -s -X POST localhost:8080/api/jobs \
  -H 'Content-Type: application/json' \
  -d '{"fuel":"methane","equivalenceRatio":1.0,"intakeTemperature":298.15}'

curl -s -X POST localhost:8080/api/inverse-jobs \
  -H 'Content-Type: application/json' \
  -d '{"fuel":"methane","intakeTemperature":298.15,"targetTemperature":2200.0,"side":"BOTH"}'
```

