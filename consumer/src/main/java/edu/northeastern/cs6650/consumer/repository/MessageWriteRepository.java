package edu.northeastern.cs6650.consumer.repository;

import edu.northeastern.cs6650.consumer.dto.QueueMessage;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MessageWriteRepository {

  private static final String INSERT_SQL = """
      INSERT INTO messages (
        message_id, room_id, user_id, username, message, message_type,
        server_id, client_ip, created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT (message_id) DO NOTHING
      """;

  private final JdbcTemplate jdbcTemplate;

  public MessageWriteRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public int insertBatch(List<QueueMessage> messages) {
    if (messages.isEmpty()) {
      return 0;
    }
    jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
      @Override
      public void setValues(PreparedStatement ps, int i) throws SQLException {
        QueueMessage m = messages.get(i);
        ps.setString(1, m.getMessageId());
        ps.setString(2, m.getRoomId());
        ps.setString(3, m.getUserId());
        ps.setString(4, m.getUsername());
        ps.setString(5, m.getMessage());
        ps.setString(6, m.getMessageType());
        ps.setString(7, m.getServerId());
        ps.setString(8, m.getClientIp());
        ps.setTimestamp(9, Timestamp.from(parseTimestamp(m.getTimestamp())));
      }

      @Override
      public int getBatchSize() {
        return messages.size();
      }
    });
    return messages.size();
  }

  private Instant parseTimestamp(String ts) {
    if (ts == null || ts.isEmpty()) {
      return Instant.now();
    }
    try {
      return Instant.parse(ts);
    } catch (Exception ignored) {
      return Instant.now();
    }
  }
}
