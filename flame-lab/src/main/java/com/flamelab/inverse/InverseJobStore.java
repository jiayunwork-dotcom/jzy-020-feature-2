package com.flamelab.inverse;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
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
 * Inverse-job persistence in the same in-process SQLite database as the
 * forward jobs (separate table). Side results — each with its own outer
 * convergence sequence — travel as JSON columns, so concurrently running
 * inverse jobs never overwrite each other's traces.
 */
@Repository
public class InverseJobStore {

    private static final String COLUMNS = "id, label, fuel, intake_temperature, target_temperature, "
            + "requested_side, status, temperature_tolerance, max_outer_steps, "
            + "peak_equivalence_ratio, peak_temperature, lean_result, rich_result, "
            + "error_type, error_message, created_at";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public InverseJobStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public InverseJob insert(InverseJob job) {
        String sql = "INSERT INTO inverse_jobs (label, fuel, intake_temperature, target_temperature, "
                + "requested_side, status, temperature_tolerance, max_outer_steps, "
                + "peak_equivalence_ratio, peak_temperature, lean_result, rich_result, "
                + "error_type, error_message, created_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, job.getLabel());
            ps.setString(2, job.getFuel());
            ps.setDouble(3, job.getIntakeTemperature());
            ps.setDouble(4, job.getTargetTemperature());
            ps.setString(5, job.getRequestedSide().name());
            ps.setString(6, job.getStatus().name());
            ps.setDouble(7, job.getTemperatureTolerance());
            ps.setInt(8, job.getMaxOuterSteps());
            setNullableDouble(ps, 9, job.getPeakEquivalenceRatio());
            setNullableDouble(ps, 10, job.getPeakTemperature());
            ps.setString(11, writeJson(job.getLean()));
            ps.setString(12, writeJson(job.getRich()));
            ps.setString(13, job.getError() == null ? null : job.getError().type().name());
            ps.setString(14, job.getError() == null ? null : job.getError().message());
            ps.setString(15, job.getCreatedAt());
            return ps;
        }, keys);
        Number key = keys.getKey();
        if (key != null) {
            job.setId(key.longValue());
        }
        return job;
    }

    public Optional<InverseJob> findById(long id) {
        List<InverseJob> found = jdbc.query("SELECT " + COLUMNS + " FROM inverse_jobs WHERE id = ?", mapper(), id);
        return found.stream().findFirst();
    }

    public Optional<InverseJob> findByLabel(String label) {
        List<InverseJob> found = jdbc.query("SELECT " + COLUMNS + " FROM inverse_jobs WHERE label = ?", mapper(), label);
        return found.stream().findFirst();
    }

    public List<InverseJob> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM inverse_jobs ORDER BY id", mapper());
    }

    private RowMapper<InverseJob> mapper() {
        return (rs, rowNum) -> {
            InverseJob job = new InverseJob();
            job.setId(rs.getLong("id"));
            job.setLabel(rs.getString("label"));
            job.setFuel(rs.getString("fuel"));
            job.setIntakeTemperature(rs.getDouble("intake_temperature"));
            job.setTargetTemperature(rs.getDouble("target_temperature"));
            job.setRequestedSide(RequestedSide.valueOf(rs.getString("requested_side")));
            job.setStatus(JobStatus.valueOf(rs.getString("status")));
            job.setTemperatureTolerance(rs.getDouble("temperature_tolerance"));
            job.setMaxOuterSteps(rs.getInt("max_outer_steps"));
            double peakPhi = rs.getDouble("peak_equivalence_ratio");
            job.setPeakEquivalenceRatio(rs.wasNull() ? null : peakPhi);
            double peakTemperature = rs.getDouble("peak_temperature");
            job.setPeakTemperature(rs.wasNull() ? null : peakTemperature);
            job.setLean(readSide(rs.getString("lean_result")));
            job.setRich(readSide(rs.getString("rich_result")));
            String errorType = rs.getString("error_type");
            if (errorType != null) {
                job.setError(new ErrorInfo(ErrorType.valueOf(errorType), rs.getString("error_message")));
            }
            job.setCreatedAt(rs.getString("created_at"));
            return job;
        };
    }

    private void setNullableDouble(PreparedStatement ps, int index, Double value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, Types.REAL);
        } else {
            ps.setDouble(index, value);
        }
    }

    private SideResult readSide(String raw) {
        return raw == null ? null : readJson(raw, new TypeReference<>() {});
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("could not serialize inverse job field", e);
        }
    }

    private <T> T readJson(String raw, TypeReference<T> type) {
        try {
            return json.readValue(raw, type);
        } catch (Exception e) {
            throw new IllegalStateException("could not deserialize inverse job field", e);
        }
    }
}
