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
