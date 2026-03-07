package com.prbotbenchmark.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prbotbenchmark.collection.ReviewCommentRepository;
import com.prbotbenchmark.collection.domain.ReviewComment;
import com.prbotbenchmark.grading.GoldenDatasetRepository;
import com.prbotbenchmark.grading.GradingRepository;
import com.prbotbenchmark.grading.domain.GoldenDatasetEntry;
import com.prbotbenchmark.grading.domain.Grading;
import com.prbotbenchmark.runner.BenchmarkRunRepository;
import com.prbotbenchmark.runner.ReplayPrRepository;
import com.prbotbenchmark.runner.domain.BenchmarkRun;
import com.prbotbenchmark.runner.domain.ReplayPr;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExportService {

    private final BenchmarkRunRepository benchmarkRunRepository;
    private final ReplayPrRepository replayPrRepository;
    private final ReviewCommentRepository reviewCommentRepository;
    private final GradingRepository gradingRepository;
    private final GoldenDatasetRepository goldenDatasetRepository;
    private final ReportingService reportingService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public String exportRunAsCsv(Long runId) {
        BenchmarkRun run = benchmarkRunRepository.findById(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "BenchmarkRun not found with id: " + runId));

        List<ReviewComment> comments = reviewCommentRepository.findByReplayPrRunId(runId);

        StringBuilder sb = new StringBuilder();
        sb.append("runId,runName,botName,suitePrOriginalNumber,suitePrTitle,replayPrId,mirrorPrUrl," +
                "commentType,filePath,lineNumber,body,verdict,severity,stars,flagged\n");

        for (ReviewComment comment : comments) {
            List<Grading> gradings = gradingRepository.findByCommentIdAndCommentTableType(
                    comment.getId(), "REVIEW");
            Grading grading = gradings.isEmpty() ? null : gradings.get(gradings.size() - 1);

            ReplayPr replayPr = comment.getReplayPr();
            String suitePrOriginalNumber = "";
            String suitePrTitle = "";
            String replayPrId = "";
            String mirrorPrUrl = "";

            if (replayPr != null) {
                replayPrId = String.valueOf(replayPr.getId());
                mirrorPrUrl = replayPr.getMirrorPrUrl() != null ? replayPr.getMirrorPrUrl() : "";
                if (replayPr.getSuitePr() != null) {
                    suitePrOriginalNumber = replayPr.getSuitePr().getOriginalPrNumber() != null
                            ? String.valueOf(replayPr.getSuitePr().getOriginalPrNumber()) : "";
                    suitePrTitle = replayPr.getSuitePr().getTitle() != null
                            ? replayPr.getSuitePr().getTitle() : "";
                }
            }

            String botName = comment.getBot() != null ? comment.getBot().getName() : "";
            String commentType = comment.getCommentType() != null ? comment.getCommentType().name() : "";
            String filePath = comment.getFilePath() != null ? comment.getFilePath() : "";
            String lineNumber = comment.getLineNumber() != null ? String.valueOf(comment.getLineNumber()) : "";

            String body = comment.getBody();
            body = body != null && body.length() > 500 ? body.substring(0, 497) + "..." : body;
            body = body != null ? body : "";

            String verdict = "";
            String severity = "";
            String stars = "";
            String flagged = "";

            if (grading != null) {
                verdict = grading.getVerdict() != null ? grading.getVerdict() : "";
                severity = grading.getSeverity() != null ? grading.getSeverity() : "";
                stars = grading.getStars() != null ? String.valueOf(grading.getStars()) : "";
                flagged = grading.getFlagged() != null ? String.valueOf(grading.getFlagged()) : "";
            }

            sb.append(csvField(String.valueOf(run.getId()))).append(",");
            sb.append(csvField(run.getName() != null ? run.getName() : "")).append(",");
            sb.append(csvField(botName)).append(",");
            sb.append(csvField(suitePrOriginalNumber)).append(",");
            sb.append(csvField(suitePrTitle)).append(",");
            sb.append(csvField(replayPrId)).append(",");
            sb.append(csvField(mirrorPrUrl)).append(",");
            sb.append(csvField(commentType)).append(",");
            sb.append(csvField(filePath)).append(",");
            sb.append(csvField(lineNumber)).append(",");
            sb.append(csvField(body)).append(",");
            sb.append(csvField(verdict)).append(",");
            sb.append(csvField(severity)).append(",");
            sb.append(csvField(stars)).append(",");
            sb.append(csvField(flagged)).append("\n");
        }

        log.debug("Exported {} review comments for run {}", comments.size(), runId);
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public String exportGoldenDatasetAsCsv() {
        List<GoldenDatasetEntry> entries = goldenDatasetRepository.findByActiveTrue();

        StringBuilder sb = new StringBuilder();
        sb.append("id,suitePrOriginalNumber,filePath,lineNumber,issueType,description,canonicalBody,active,includedByDefault\n");

        for (GoldenDatasetEntry entry : entries) {
            String suitePrOriginalNumber = "";
            if (entry.getSuitePr() != null && entry.getSuitePr().getOriginalPrNumber() != null) {
                suitePrOriginalNumber = String.valueOf(entry.getSuitePr().getOriginalPrNumber());
            }

            String canonicalBody = entry.getCanonicalBody();
            canonicalBody = canonicalBody != null && canonicalBody.length() > 500
                    ? canonicalBody.substring(0, 497) + "..." : canonicalBody;
            canonicalBody = canonicalBody != null ? canonicalBody : "";

            sb.append(csvField(String.valueOf(entry.getId()))).append(",");
            sb.append(csvField(suitePrOriginalNumber)).append(",");
            sb.append(csvField(entry.getFilePath() != null ? entry.getFilePath() : "")).append(",");
            sb.append(csvField(entry.getLineNumber() != null ? String.valueOf(entry.getLineNumber()) : "")).append(",");
            sb.append(csvField(entry.getIssueType() != null ? entry.getIssueType() : "")).append(",");
            sb.append(csvField(entry.getDescription() != null ? entry.getDescription() : "")).append(",");
            sb.append(csvField(canonicalBody)).append(",");
            sb.append(csvField(entry.getActive() != null ? String.valueOf(entry.getActive()) : "")).append(",");
            sb.append(csvField(entry.getIncludedByDefault() != null ? String.valueOf(entry.getIncludedByDefault()) : "")).append("\n");
        }

        log.debug("Exported {} golden dataset entries", entries.size());
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public String exportRunAsSarif(Long runId) {
        BenchmarkRun run = benchmarkRunRepository.findById(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "BenchmarkRun not found with id: " + runId));

        List<ReviewComment> comments = reviewCommentRepository.findByReplayPrRunId(runId);

        // Collect valid graded comments and unique rule IDs
        record ValidComment(ReviewComment comment, Grading grading) {}

        java.util.List<ValidComment> validComments = new java.util.ArrayList<>();
        Set<String> ruleIds = new LinkedHashSet<>();

        for (ReviewComment comment : comments) {
            List<Grading> gradings = gradingRepository.findByCommentIdAndCommentTableType(
                    comment.getId(), "REVIEW");
            if (gradings.isEmpty()) continue;
            Grading latest = gradings.get(gradings.size() - 1);
            if (!"VALID".equals(latest.getVerdict())) continue;

            validComments.add(new ValidComment(comment, latest));
            if (latest.getVerdict() != null) {
                ruleIds.add(latest.getVerdict());
            }
        }

        // Build SARIF JSON tree
        ObjectNode root = objectMapper.createObjectNode();
        root.put("version", "2.1.0");
        root.put("$schema", "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json");

        ArrayNode runs = root.putArray("runs");
        ObjectNode sarifRun = runs.addObject();

        // Tool driver
        ObjectNode tool = sarifRun.putObject("tool");
        ObjectNode driver = tool.putObject("driver");
        driver.put("name", "PR Bot Benchmark");
        driver.put("version", "1.0.0");

        ArrayNode rules = driver.putArray("rules");
        for (String ruleId : ruleIds) {
            ObjectNode rule = rules.addObject();
            rule.put("id", ruleId);
        }

        // Results
        ArrayNode results = sarifRun.putArray("results");
        for (ValidComment vc : validComments) {
            ReviewComment comment = vc.comment();
            Grading grading = vc.grading();

            ObjectNode result = results.addObject();
            result.put("ruleId", grading.getVerdict() != null ? grading.getVerdict() : "UNKNOWN");
            result.put("level", mapSeverityToLevel(grading.getSeverity()));

            ObjectNode message = result.putObject("message");
            String body = comment.getBody() != null ? comment.getBody() : "";
            message.put("text", body);

            ArrayNode locations = result.putArray("locations");
            ObjectNode location = locations.addObject();
            ObjectNode physicalLocation = location.putObject("physicalLocation");

            ObjectNode artifactLocation = physicalLocation.putObject("artifactLocation");
            artifactLocation.put("uri", comment.getFilePath() != null ? comment.getFilePath() : "");

            ObjectNode region = physicalLocation.putObject("region");
            region.put("startLine", comment.getLineNumber() != null ? comment.getLineNumber() : 1);

            ObjectNode properties = result.putObject("properties");
            String botName = comment.getBot() != null ? comment.getBot().getName() : "";
            properties.put("bot", botName);
            properties.put("runId", runId);
            properties.put("stars", grading.getStars() != null ? grading.getStars() : 0);
        }

        try {
            String sarif = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            log.debug("Exported {} valid comments as SARIF for run {}", validComments.size(), runId);
            return sarif;
        } catch (Exception e) {
            log.error("Failed to serialize SARIF for run {}", runId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate SARIF output");
        }
    }

    private String csvField(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\n") || value.contains("\r") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String mapSeverityToLevel(String severity) {
        if (severity == null) return "note";
        return switch (severity.toUpperCase()) {
            case "CRITICAL", "MAJOR" -> "error";
            case "MINOR" -> "warning";
            case "NITPICK" -> "note";
            default -> "note";
        };
    }
}
