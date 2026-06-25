package com.alexsoft.smarthouse.interviewer;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/sections")
    public ResponseEntity<List<String>> getSections() {
        String sql = "SELECT DISTINCT section FROM interview_topic WHERE section IS NOT NULL ORDER BY section";
        return ResponseEntity.ok(jdbcTemplate.queryForList(sql, String.class));
    }

    @GetMapping("/topics")
    public ResponseEntity<List<Map<String, Object>>> getTopics(@RequestParam(required = false) String section) {
        String sql = "SELECT id, section, topic_name as \"topicName\", jd_relevance as \"jdRelevance\", " +
                "total_questions as \"totalQuestions\", attempted_questions as \"attemptedQuestions\", " +
                "avg_score as \"avgScore\", status FROM v_topic_score ";
        List<Object> args = new ArrayList<>();
        if (section != null && !section.isEmpty()) {
            sql += "WHERE section = ? ";
            args.add(section);
        }
        sql += "ORDER BY CASE WHEN status = 'Not Started' THEN 0 WHEN status = 'In Progress' THEN 1 ELSE 2 END, COALESCE(avg_score, -1) ASC";

        return ResponseEntity.ok(jdbcTemplate.queryForList(sql, args.toArray()));
    }

    @PostMapping("/topics")
    public ResponseEntity<Integer> createTopic(@RequestBody TopicRequest req) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO interview_topic (section, topic_name, jd_relevance, file_path) VALUES (?, ?, ?, ?)",
                    new String[]{"id"});
            ps.setString(1, req.getSection());
            ps.setString(2, req.getTopicName());
            ps.setString(3, req.getJdRelevance() != null ? String.valueOf(req.getJdRelevance()) : null);
            ps.setString(4, req.getFilePath());
            return ps;
        }, keyHolder);
        return ResponseEntity.status(201).body(keyHolder.getKey().intValue());
    }

    @PatchMapping("/topics/{id}")
    public ResponseEntity<Void> updateTopic(@PathVariable int id, @RequestBody TopicRequest req) {
        if (req.getSection() != null) jdbcTemplate.update("UPDATE interview_topic SET section = ? WHERE id = ?", req.getSection(), id);
        if (req.getTopicName() != null) jdbcTemplate.update("UPDATE interview_topic SET topic_name = ? WHERE id = ?", req.getTopicName(), id);
        if (req.getJdRelevance() != null) jdbcTemplate.update("UPDATE interview_topic SET jd_relevance = ? WHERE id = ?", String.valueOf(req.getJdRelevance()), id);
        if (req.getFilePath() != null) jdbcTemplate.update("UPDATE interview_topic SET file_path = ? WHERE id = ?", req.getFilePath(), id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/questions")
    public ResponseEntity<List<Map<String, Object>>> getQuestions(@RequestParam(required = false) String section, @RequestParam(required = false) Integer topicId) {
        String sql = "SELECT q.id, q.question_text as \"questionText\", q.difficulty, t.topic_name as \"topicName\", q.topic_id as \"topicId\", qs.score, qs.attempt_notes as \"notes\" " +
                "FROM interview_question q " +
                "JOIN interview_topic t ON t.id = q.topic_id " +
                "LEFT JOIN v_question_score qs ON qs.question_id = q.id WHERE 1=1 ";
        List<Object> args = new ArrayList<>();
        if (section != null && !section.isEmpty()) {
            sql += "AND t.section = ? ";
            args.add(section);
        }
        if (topicId != null) {
            sql += "AND q.topic_id = ? ";
            args.add(topicId);
        }
        sql += "ORDER BY q.id ASC";
        return ResponseEntity.ok(jdbcTemplate.queryForList(sql, args.toArray()));
    }

    @PostMapping("/questions")
    public ResponseEntity<Integer> createQuestion(@RequestBody QuestionRequest req) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO interview_question (topic_id, question_text, difficulty) VALUES (?, ?, ?)",
                    new String[]{"id"});
            ps.setInt(1, req.getTopicId());
            ps.setString(2, req.getQuestionText());
            ps.setString(3, req.getDifficulty());
            return ps;
        }, keyHolder);
        return ResponseEntity.status(201).body(keyHolder.getKey().intValue());
    }

    @PatchMapping("/questions/{id}")
    public ResponseEntity<Void> updateQuestion(@PathVariable int id, @RequestBody QuestionRequest req) {
        if (req.getTopicId() != null) jdbcTemplate.update("UPDATE interview_question SET topic_id = ? WHERE id = ?", req.getTopicId(), id);
        if (req.getQuestionText() != null) jdbcTemplate.update("UPDATE interview_question SET question_text = ? WHERE id = ?", req.getQuestionText(), id);
        if (req.getDifficulty() != null) jdbcTemplate.update("UPDATE interview_question SET difficulty = ? WHERE id = ?", req.getDifficulty(), id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/questions/next")
    public ResponseEntity<Map<String, Object>> getNextQuestion(@RequestParam String section) {
        String sql = "SELECT q.id, q.question_text as \"questionText\", q.difficulty, t.topic_name as \"topicName\", q.topic_id as \"topicId\" " +
                "FROM interview_question q " +
                "JOIN interview_topic t ON t.id = q.topic_id " +
                "LEFT JOIN v_question_score qs ON qs.question_id = q.id " +
                "WHERE t.section = ? AND COALESCE(qs.score, 0) < 80 " +
                "ORDER BY COALESCE(qs.score, -1) ASC, q.id ASC LIMIT 1";

        List<Map<String, Object>> res = jdbcTemplate.queryForList(sql, section);
        if (res.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(res.get(0));
    }

    @GetMapping("/questions/{id}/attempts")
    public ResponseEntity<List<Map<String, Object>>> getAttempts(@PathVariable int id) {
        String sql = "SELECT id, score, notes FROM interview_attempt WHERE question_id = ? ORDER BY id DESC";
        return ResponseEntity.ok(jdbcTemplate.queryForList(sql, id));
    }

    @PostMapping("/questions/{id}/attempts")
    public ResponseEntity<Map<String, Object>> submitAttempt(@PathVariable int id, @RequestBody AttemptRequest req) {
        jdbcTemplate.update("INSERT INTO interview_attempt (question_id, score, notes) VALUES (?, ?, ?)",
                id, req.getScore(), req.getNotes());

        Integer topicId = jdbcTemplate.queryForObject("SELECT topic_id FROM interview_question WHERE id = ?", Integer.class, id);

        String sql = "SELECT id, section, topic_name as \"topicName\", jd_relevance as \"jdRelevance\", " +
                "total_questions as \"totalQuestions\", attempted_questions as \"attemptedQuestions\", " +
                "avg_score as \"avgScore\", status FROM v_topic_score WHERE id = ?";
        return ResponseEntity.ok(jdbcTemplate.queryForMap(sql, topicId));
    }

    @Data
    public static class TopicRequest {
        private String section;
        private String topicName;
        private Integer jdRelevance;
        private String filePath;
    }

    @Data
    public static class QuestionRequest {
        private Integer topicId;
        private String questionText;
        private String difficulty;
    }

    @Data
    public static class AttemptRequest {
        private Integer score;
        private String notes;
    }
}
