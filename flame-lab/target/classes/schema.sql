CREATE TABLE IF NOT EXISTS jobs (
    id                     INTEGER PRIMARY KEY,
    label                  TEXT,
    fuel                   TEXT    NOT NULL,
    equivalence_ratio      REAL    NOT NULL,
    intake_temperature     REAL    NOT NULL,
    status                 TEXT    NOT NULL,
    final_temperature      REAL,
    product_mole_fractions TEXT,
    atom_residuals         TEXT,
    max_atom_residual      REAL,
    atom_balance_threshold REAL    NOT NULL,
    enthalpy_tolerance     REAL    NOT NULL,
    max_iterations         INTEGER NOT NULL,
    iterations             TEXT    NOT NULL,
    error_type             TEXT,
    error_message          TEXT,
    created_at             TEXT    NOT NULL
);

CREATE TABLE IF NOT EXISTS inverse_jobs (
    id                     INTEGER PRIMARY KEY,
    label                  TEXT,
    fuel                   TEXT    NOT NULL,
    intake_temperature     REAL    NOT NULL,
    target_temperature     REAL    NOT NULL,
    requested_side         TEXT    NOT NULL,
    status                 TEXT    NOT NULL,
    temperature_tolerance  REAL    NOT NULL,
    max_outer_steps        INTEGER NOT NULL,
    peak_equivalence_ratio REAL,
    peak_temperature       REAL,
    lean_result            TEXT,
    rich_result            TEXT,
    error_type             TEXT,
    error_message          TEXT,
    created_at             TEXT    NOT NULL
);
