package com.flamelab.job;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flamelab.solver.IterationPoint;

/**
 * Job persistence in the in-process SQLite database. Iteration sequences and
 * mole-fraction maps travel as JSON columns; every job row owns its own
 * residual curve, so concurrently running jobs never overwrite each other.
 */
@Repository
public class JobStore {

    private static final String COLUMNS = "id, label, fuel, equivalence_ratio, intake_temperature, status, "
            + "final_temperature, product_mole_fractions, atom_residuals, max_atom_residual, "
            + "atom_balance_threshold, enthalpy_tolerance, max_iterations, iterations, "
            + "error_type, error_message, created_at";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JobStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public FlameJob insert(FlameJob job) {
        String sql = "INSERT INTO jobs (label, fuel, equivalence_ratio, intake_temperature, status, "
                + "final_temperature, product_mole_fractions, atom_residuals, max_atom_residual, "
                + "atom_balance_threshold, enthalpy_tolerance, max_iterations, iterations, "
                + "error_type, error_message, created_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, job.getLabel());
            ps.setString(2, job.getFuel());
            ps.setDouble(3, job.getEquivalenceRatio());
            ps.setDouble(4, job.getIntakeTemperature());
            ps.setString(5, job.getStatus().name());
            if (job.getFinalTemperature() == null) {
                ps.setNull(6, java.sql.Types.REAL);
            } else {
                ps.setDouble(6, job.getFinalTemperature());
            }
            ps.setString(7, writeJson(job.getProductMoleFractions()));
            ps.setString(8, writeJson(job.getAtomResiduals()));
            ps.setDouble(9, job.getMaxAtomResidual());
            ps.setDouble(10, job.getAtomBalanceThreshold());
            ps.setDouble(11, job.getEnthalpyTolerance());
            ps.setInt(12, job.getMaxIterations());
            ps.setString(13, writeJson(job.getIterations()));
            ps.setString(14, job.getError() == null ? null : job.getError().type().name());
            ps.setString(15, job.getError() == null ? null : job.getError().message());
            ps.setString(16, job.getCreatedAt());
            return ps;
        }, keys);
        Number key = keys.getKey();
        if (key != null) {
            job.setId(key.longValue());
        }
        return job;
    }

    public Optional<FlameJob> findById(long id) {
        List<FlameJob> found = jdbc.query("SELECT " + COLUMNS + " FROM jobs WHERE id = ?", mapper(), id);
        return found.stream().findFirst();
    }

    public Optional<FlameJob> findByLabel(String label) {
        List<FlameJob> found = jdbc.query("SELECT " + COLUMNS + " FROM jobs WHERE label = ?", mapper(), label);
        return found.stream().findFirst();
    }

    public List<FlameJob> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM jobs ORDER BY id", mapper());
    }

    private RowMapper<FlameJob> mapper() {
        return (rs, rowNum) -> {
            FlameJob job = new FlameJob();
            job.setId(rs.getLong("id"));
            job.setLabel(rs.getString("label"));
            job.setFuel(rs.getString("fuel"));
            job.setEquivalenceRatio(rs.getDouble("equivalence_ratio"));
            job.setIntakeTemperature(rs.getDouble("intake_temperature"));
            job.setStatus(JobStatus.valueOf(rs.getString("status")));
            double finalTemperature = rs.getDouble("final_temperature");
            job.setFinalTemperature(rs.wasNull() ? null : finalTemperature);
            job.setProductMoleFractions(readJsonMap(rs.getString("product_mole_fractions")));
            job.setAtomResiduals(readJsonMap(rs.getString("atom_residuals")));
            job.setMaxAtomResidual(rs.getDouble("max_atom_residual"));
            job.setAtomBalanceThreshold(rs.getDouble("atom_balance_threshold"));
            job.setEnthalpyTolerance(rs.getDouble("enthalpy_tolerance"));
            job.setMaxIterations(rs.getInt("max_iterations"));
            job.setIterations(readJson(rs.getString("iterations"), new TypeReference<>() {}));
            String errorType = rs.getString("error_type");
            if (errorType != null) {
                job.setError(new ErrorInfo(ErrorType.valueOf(errorType), rs.getString("error_message")));
            }
            job.setCreatedAt(rs.getString("created_at"));
            return job;
        };
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("could not serialize job field", e);
        }
    }

    private java.util.Map<String, Double> readJsonMap(String raw) {
        return raw == null ? null : readJson(raw, new TypeReference<>() {});
    }

    private <T> T readJson(String raw, TypeReference<T> type) {
        try {
            return json.readValue(raw, type);
        } catch (Exception e) {
            throw new IllegalStateException("could not deserialize job field", e);
        }
    }
}
