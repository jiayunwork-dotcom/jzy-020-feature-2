# flame-lab

Constant-pressure adiabatic flame temperature balance service. Given a fuel,
an equivalence ratio and an intake temperature, it solves for the product
composition and the adiabatic flame temperature, recording the enthalpy
residual of every iteration. Each solve is persisted as one job in an
in-process SQLite database and can be retrieved later by id, residual curve
included.

Scope is deliberately narrow: constant-pressure adiabatic balance only, HTTP
in/out only, no frontend, no accounts, no batch endpoint (one job = one
balance iteration).

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

## Layout (one responsibility per module)

| package | responsibility |
| --- | --- |
| `balance` | element balance, product composition, atom residuals |
| `thermo`  | species, NASA Cp polynomials, enthalpy/Cp integrals, validity window |
| `solver`  | temperature iteration, convergence decision, residual curve |
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

A demo job (`label=demo-stoich-methane`, methane, phi=1, 298.15 K) is seeded
at startup as job id 1 on a fresh database.

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
```
