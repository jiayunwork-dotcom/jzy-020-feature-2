package com.flamelab.inverse;

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
import com.flamelab.job.ErrorInfo;
import com.flamelab.job.ErrorType;
import com.flamelab.job.JobStatus;

/**
 * Persistence for inverse jobs in the same in-process SQLite database as the
 * forward jobs (a separate table, the same file — no second database process).
 * Each branch solution and each of the two convergence sequences travels in
 * its own JSON column, so concurrent inverse jobs never contaminate one
 * another's traces.
 */
@Repository
public class InverseJobStore {

    private static final String COLUMNS = "id, label, fuel, intake_temperature, target_temperature, side, status, "
            + "peak_equivalence_ratio, peak_temperature, lean_solution, rich_solution, "
            + "lean_sequence, rich_sequence, temperature_tolerance, max_outer_steps, "
            + "error_type, error_message, created_at";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public InverseJobStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public InverseFlameJob insert(InverseFlameJob job) {
        String sql = "INSERT INTO inverse_jobs (label, fuel, intake_temperature, target_temperature, side, status, "
                + "peak_equivalence_ratio, peak_temperature, lean_solution, rich_solution, "
                + "lean_sequence, rich_sequence, temperature_tolerance, max_outer_steps, "
                + "error_type, error_message, created_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, job.getLabel());
            ps.setString(2, job.getFuel());
            ps.setDouble(3, job.getIntakeTemperature());
            ps.setDouble(4, job.getTargetTemperature());
            ps.setString(5, job.getSide().name());
            ps.setString(6, job.getStatus().name());
            setNullableDouble(ps, 7, job.getPeakEquivalenceRatio());
            setNullableDouble(ps, 8, job.getPeakTemperature());
            ps.setString(9, writeJson(job.getLeanSolution()));
            ps.setString(10, writeJson(job.getRichSolution()));
            ps.setString(11, writeJson(job.getLeanSequence()));
            ps.setString(12, writeJson(job.getRichSequence()));
            ps.setDouble(13, job.getTemperatureTolerance());
            ps.setInt(14, job.getMaxOuterSteps());
            ps.setString(15, job.getError() == null ? null : job.getError().type().name());
            ps.setString(16, job.getError() == null ? null : job.getError().message());
            ps.setString(17, job.getCreatedAt());
            return ps;
        }, keys);
        Number key = keys.getKey();
        if (key != null) {
            job.setId(key.longValue());
        }
        return job;
    }

    public Optional<InverseFlameJob> findById(long id) {
        List<InverseFlameJob> found = jdbc.query(
                "SELECT " + COLUMNS + " FROM inverse_jobs WHERE id = ?", mapper(), id);
        return found.stream().findFirst();
    }

    public Optional<InverseFlameJob> findByLabel(String label) {
        List<InverseFlameJob> found = jdbc.query(
                "SELECT " + COLUMNS + " FROM inverse_jobs WHERE label = ?", mapper(), label);
        return found.stream().findFirst();
    }

    public List<InverseFlameJob> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM inverse_jobs ORDER BY id", mapper());
    }

    private void setNullableDouble(PreparedStatement ps, int index, Double value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.REAL);
        } else {
            ps.setDouble(index, value);
        }
    }

    private RowMapper<InverseFlameJob> mapper() {
        return (rs, rowNum) -> {
            InverseFlameJob job = new InverseFlameJob();
            job.setId(rs.getLong("id"));
            job.setLabel(rs.getString("label"));
            job.setFuel(rs.getString("fuel"));
            job.setIntakeTemperature(rs.getDouble("intake_temperature"));
            job.setTargetTemperature(rs.getDouble("target_temperature"));
            job.setSide(RequestSide.valueOf(rs.getString("side")));
            job.setStatus(JobStatus.valueOf(rs.getString("status")));
            double peakPhi = rs.getDouble("peak_equivalence_ratio");
            job.setPeakEquivalenceRatio(rs.wasNull() ? null : peakPhi);
            double peakTemp = rs.getDouble("peak_temperature");
            job.setPeakTemperature(rs.wasNull() ? null : peakTemp);
            job.setLeanSolution(readJson(rs.getString("lean_solution"),
                    new TypeReference<SideSolution>() {}));
            job.setRichSolution(readJson(rs.getString("rich_solution"),
                    new TypeReference<SideSolution>() {}));
            job.setLeanSequence(readJson(rs.getString("lean_sequence"),
                    new TypeReference<List<OuterStep>>() {}));
            job.setRichSequence(readJson(rs.getString("rich_sequence"),
                    new TypeReference<List<OuterStep>>() {}));
            job.setTemperatureTolerance(rs.getDouble("temperature_tolerance"));
            job.setMaxOuterSteps(rs.getInt("max_outer_steps"));
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
            return value == null ? null : json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("could not serialize inverse job field", e);
        }
    }

    private <T> T readJson(String raw, TypeReference<T> type) {
        try {
            return raw == null ? null : json.readValue(raw, type);
        } catch (Exception e) {
            throw new IllegalStateException("could not deserialize inverse job field", e);
        }
    }
}
