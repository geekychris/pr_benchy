package com.prbotbenchmark.grading;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prbotbenchmark.benchmark.SuitePrRepository;
import com.prbotbenchmark.benchmark.domain.SuitePr;
import com.prbotbenchmark.collection.OriginalCommentRepository;
import com.prbotbenchmark.collection.ReviewCommentRepository;
import com.prbotbenchmark.collection.domain.OriginalComment;
import com.prbotbenchmark.collection.domain.ReviewComment;
import com.prbotbenchmark.grading.domain.GoldenDatasetEntry;
import com.prbotbenchmark.grading.domain.Grading;
import com.prbotbenchmark.grading.dto.BulkGradeRequest;
import com.prbotbenchmark.grading.dto.BulkGradeResult;
import com.prbotbenchmark.grading.dto.CreateGradingRequest;
import com.prbotbenchmark.grading.dto.GradingProgressDto;
import com.prbotbenchmark.grading.dto.PromoteToGoldenRequest;
import com.prbotbenchmark.grading.dto.UngradedCommentDto;
import com.prbotbenchmark.grading.dto.UpdateGoldenEntryRequest;
import com.prbotbenchmark.grading.dto.UpdateGradingRequest;
import com.prbotbenchmark.runner.ReplayPrRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class GradingService {

    private static final Set<String> VALID_VERDICTS = Set.of("VALID", "INVALID", "DUPLICATE", "NEEDS_REVIEW");

    private final GradingRepository gradingRepository;
    private final GoldenDatasetRepository goldenDatasetRepository;
    private final SuitePrRepository suitePrRepository;
    private final ReviewCommentRepository reviewCommentRepository;
    private final OriginalCommentRepository originalCommentRepository;
    private final ReplayPrRepository replayPrRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<Grading> getGradingsForComment(Long commentId, String commentTableType) {
        return gradingRepository.findByCommentIdAndCommentTableType(commentId, commentTableType);
    }

    public Grading createGrading(CreateGradingRequest req) {
        if (req.verdict() != null && !VALID_VERDICTS.contains(req.verdict())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid verdict: " + req.verdict() + ". Must be one of: " + VALID_VERDICTS);
        }
        if (req.stars() != null && (req.stars() < 0 || req.stars() > 5)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Stars must be between 0 and 5");
        }

        Grading grading = Grading.builder()
                .commentId(req.commentId())
                .commentTableType(req.commentTableType())
                .graderType(req.graderType())
                .graderId(req.graderId())
                .verdict(req.verdict())
                .severity(req.severity())
                .stars(req.stars() != null ? req.stars() : 0)
                .flagged(req.flagged() != null ? req.flagged() : false)
                .notes(req.notes())
                .build();

        return gradingRepository.save(grading);
    }

    public Grading updateGrading(Long id, UpdateGradingRequest req) {
        Grading grading = gradingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Grading not found with id: " + id));

        if (req.verdict() != null) {
            if (!VALID_VERDICTS.contains(req.verdict())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid verdict: " + req.verdict() + ". Must be one of: " + VALID_VERDICTS);
            }
            grading.setVerdict(req.verdict());
        }
        if (req.severity() != null) {
            grading.setSeverity(req.severity());
        }
        if (req.stars() != null) {
            if (req.stars() < 0 || req.stars() > 5) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Stars must be between 0 and 5");
            }
            grading.setStars(req.stars());
        }
        if (req.flagged() != null) {
            grading.setFlagged(req.flagged());
        }
        if (req.notes() != null) {
            grading.setNotes(req.notes());
        }

        return gradingRepository.save(grading);
    }

    public void deleteGrading(Long id) {
        if (!gradingRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Grading not found with id: " + id);
        }
        gradingRepository.deleteById(id);
    }

    public GoldenDatasetEntry promoteToGoldenDataset(PromoteToGoldenRequest req) {
        SuitePr suitePr;
        String filePath;
        Integer lineNumber;
        String body;

        if ("REVIEW".equals(req.commentTableType())) {
            ReviewComment comment = reviewCommentRepository.findById(req.commentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "ReviewComment not found with id: " + req.commentId()));
            suitePr = comment.getReplayPr().getSuitePr();
            filePath = comment.getFilePath();
            lineNumber = comment.getLineNumber();
            body = comment.getBody();
        } else if ("ORIGINAL".equals(req.commentTableType())) {
            OriginalComment comment = originalCommentRepository.findById(req.commentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "OriginalComment not found with id: " + req.commentId()));
            suitePr = comment.getSuitePr();
            filePath = comment.getFilePath();
            lineNumber = comment.getLineNumber();
            body = comment.getBody();
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "commentTableType must be REVIEW or ORIGINAL");
        }

        GoldenDatasetEntry entry = GoldenDatasetEntry.builder()
                .suitePr(suitePr)
                .sourceCommentId(req.commentId())
                .sourceCommentType(req.commentTableType())
                .filePath(filePath)
                .lineNumber(lineNumber)
                .issueType(req.issueType())
                .description(req.description())
                .canonicalBody(body)
                .active(true)
                .includedByDefault(true)
                .build();

        return goldenDatasetRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<GoldenDatasetEntry> listGoldenDataset(boolean activeOnly) {
        if (activeOnly) {
            return goldenDatasetRepository.findByActiveTrue();
        }
        return goldenDatasetRepository.findAll();
    }

    public GoldenDatasetEntry updateGoldenEntry(Long id, UpdateGoldenEntryRequest req) {
        GoldenDatasetEntry entry = goldenDatasetRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "GoldenDatasetEntry not found with id: " + id));

        if (req.issueType() != null) {
            entry.setIssueType(req.issueType());
        }
        if (req.description() != null) {
            entry.setDescription(req.description());
        }
        if (req.canonicalBody() != null) {
            entry.setCanonicalBody(req.canonicalBody());
        }
        if (req.active() != null) {
            entry.setActive(req.active());
        }
        if (req.includedByDefault() != null) {
            entry.setIncludedByDefault(req.includedByDefault());
        }

        return goldenDatasetRepository.save(entry);
    }

    public void deactivateGoldenEntry(Long id) {
        GoldenDatasetEntry entry = goldenDatasetRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "GoldenDatasetEntry not found with id: " + id));
        entry.setActive(false);
        goldenDatasetRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public String exportToJson() {
        List<GoldenDatasetEntry> entries = goldenDatasetRepository.findByActiveTrue();
        List<Map<String, Object>> exportList = entries.stream()
                .map(entry -> {
                    Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("id", entry.getId());
                    map.put("suitePrId", entry.getSuitePr() != null ? entry.getSuitePr().getId() : null);
                    map.put("originalPrNumber", entry.getSuitePr() != null ? entry.getSuitePr().getOriginalPrNumber() : null);
                    map.put("prTitle", entry.getSuitePr() != null ? entry.getSuitePr().getTitle() : null);
                    map.put("sourceCommentId", entry.getSourceCommentId());
                    map.put("sourceCommentType", entry.getSourceCommentType());
                    map.put("filePath", entry.getFilePath());
                    map.put("lineNumber", entry.getLineNumber());
                    map.put("issueType", entry.getIssueType());
                    map.put("description", entry.getDescription());
                    map.put("canonicalBody", entry.getCanonicalBody());
                    map.put("active", entry.getActive());
                    map.put("includedByDefault", entry.getIncludedByDefault());
                    map.put("createdAt", entry.getCreatedAt() != null ? entry.getCreatedAt().toString() : null);
                    map.put("updatedAt", entry.getUpdatedAt() != null ? entry.getUpdatedAt().toString() : null);
                    return map;
                })
                .toList();

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(exportList);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to serialize golden dataset to JSON", e);
        }
    }

    public void importFromJson(String json) {
        List<Map<String, Object>> importList;
        try {
            importList = objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Failed to parse JSON: " + e.getMessage());
        }

        for (Map<String, Object> item : importList) {
            Long suitePrId = item.get("suitePrId") != null ? ((Number) item.get("suitePrId")).longValue() : null;
            String filePath = (String) item.get("filePath");
            Integer lineNumber = item.get("lineNumber") != null ? ((Number) item.get("lineNumber")).intValue() : null;
            String issueType = (String) item.get("issueType");

            // Try to find existing entry by (suitePrId, filePath, lineNumber, issueType)
            GoldenDatasetEntry entry = null;
            if (suitePrId != null) {
                List<GoldenDatasetEntry> existing = goldenDatasetRepository.findBySuitePrId(suitePrId);
                entry = existing.stream()
                        .filter(e -> matches(e, filePath, lineNumber, issueType))
                        .findFirst()
                        .orElse(null);
            }

            if (entry == null) {
                // Create new entry
                entry = new GoldenDatasetEntry();
                if (suitePrId != null) {
                    suitePrRepository.findById(suitePrId).ifPresent(entry::setSuitePr);
                }
            }

            if (item.get("sourceCommentId") != null) {
                entry.setSourceCommentId(((Number) item.get("sourceCommentId")).longValue());
            }
            if (item.get("sourceCommentType") != null) {
                entry.setSourceCommentType((String) item.get("sourceCommentType"));
            }
            entry.setFilePath(filePath);
            entry.setLineNumber(lineNumber);
            entry.setIssueType(issueType);
            if (item.get("description") != null) {
                entry.setDescription((String) item.get("description"));
            }
            if (item.get("canonicalBody") != null) {
                entry.setCanonicalBody((String) item.get("canonicalBody"));
            }
            Boolean active = item.get("active") != null ? (Boolean) item.get("active") : true;
            entry.setActive(active);
            Boolean includedByDefault = item.get("includedByDefault") != null ? (Boolean) item.get("includedByDefault") : true;
            entry.setIncludedByDefault(includedByDefault);

            goldenDatasetRepository.save(entry);
        }

        log.info("Imported {} golden dataset entries from JSON", importList.size());
    }

    @Transactional(readOnly = true)
    public List<UngradedCommentDto> getGradingQueue(Long runId, Long suiteId, int limit) {
        List<UngradedCommentDto> queue = new ArrayList<>();

        if (runId != null) {
            List<ReviewComment> comments = reviewCommentRepository.findByReplayPrRunId(runId);
            for (ReviewComment c : comments) {
                List<Grading> gradings = gradingRepository.findByCommentIdAndCommentTableType(c.getId(), "REVIEW");
                if (gradings.isEmpty()) {
                    SuitePr suitePr = c.getReplayPr() != null ? c.getReplayPr().getSuitePr() : null;
                    String botName = c.getBot() != null ? c.getBot().getName() : null;
                    Long replayPrId = c.getReplayPr() != null ? c.getReplayPr().getId() : null;
                    Long suitePrId2 = suitePr != null ? suitePr.getId() : null;
                    Integer originalPrNumber = suitePr != null ? suitePr.getOriginalPrNumber() : null;
                    String prTitle = suitePr != null ? suitePr.getTitle() : null;
                    String commentType = c.getCommentType() != null ? c.getCommentType().name() : null;
                    queue.add(new UngradedCommentDto(
                            c.getId(), "REVIEW", replayPrId, suitePrId2,
                            originalPrNumber, prTitle, botName, commentType,
                            c.getFilePath(), c.getLineNumber(), c.getBody(),
                            null, null));
                }
            }
        } else if (suiteId != null) {
            List<SuitePr> suitePrs = suitePrRepository.findBySuiteId(suiteId);
            for (SuitePr suitePr : suitePrs) {
                List<OriginalComment> comments = originalCommentRepository.findBySuitePrId(suitePr.getId());
                for (OriginalComment c : comments) {
                    List<Grading> gradings = gradingRepository.findByCommentIdAndCommentTableType(c.getId(), "ORIGINAL");
                    if (gradings.isEmpty()) {
                        String commentType = c.getCommentType() != null ? c.getCommentType().name() : null;
                        queue.add(new UngradedCommentDto(
                                c.getId(), "ORIGINAL", null, suitePr.getId(),
                                suitePr.getOriginalPrNumber(), suitePr.getTitle(), null, commentType,
                                c.getFilePath(), c.getLineNumber(), c.getBody(),
                                null, null));
                    }
                }
            }
        }

        // Order: flagged desc, stars desc, then by creation time (nulls last for stars/flagged)
        queue.sort(Comparator
                .comparing((UngradedCommentDto d) -> d.flagged() != null && d.flagged() ? 0 : 1)
                .thenComparing(d -> d.stars() != null ? -d.stars() : 0)
        );

        return limit > 0 && queue.size() > limit ? queue.subList(0, limit) : queue;
    }

    @Transactional
    public BulkGradeResult bulkGrade(BulkGradeRequest req) {
        if (req.verdict() != null && !VALID_VERDICTS.contains(req.verdict())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid verdict: " + req.verdict() + ". Must be one of: " + VALID_VERDICTS);
        }

        String effectiveGraderId = req.graderId() != null ? req.graderId() : "bulk-human";
        int successCount = 0;
        int skippedCount = 0;
        List<String> errors = new ArrayList<>();

        for (Long commentId : req.commentIds()) {
            try {
                List<Grading> existing = gradingRepository.findByCommentIdAndCommentTableType(commentId, req.commentTableType());
                boolean alreadyGraded = existing.stream()
                        .anyMatch(g -> "HUMAN".equals(g.getGraderType()) && effectiveGraderId.equals(g.getGraderId()));
                if (alreadyGraded) {
                    skippedCount++;
                    continue;
                }

                Grading grading = Grading.builder()
                        .commentId(commentId)
                        .commentTableType(req.commentTableType())
                        .graderType("HUMAN")
                        .graderId(effectiveGraderId)
                        .verdict(req.verdict())
                        .severity(req.severity())
                        .stars(req.stars() != null ? req.stars() : 0)
                        .flagged(req.flagged() != null ? req.flagged() : false)
                        .notes(req.notes())
                        .build();
                gradingRepository.save(grading);
                successCount++;
            } catch (Exception e) {
                log.warn("Failed to grade commentId={}: {}", commentId, e.getMessage());
                errors.add("commentId=" + commentId + ": " + e.getMessage());
            }
        }

        return new BulkGradeResult(successCount, skippedCount, errors);
    }

    @Transactional(readOnly = true)
    public GradingProgressDto getProgress(Long runId, Long suiteId) {
        List<Long> commentIds = new ArrayList<>();
        String tableType;

        if (runId != null) {
            tableType = "REVIEW";
            List<ReviewComment> comments = reviewCommentRepository.findByReplayPrRunId(runId);
            comments.forEach(c -> commentIds.add(c.getId()));
        } else {
            tableType = "ORIGINAL";
            List<SuitePr> suitePrs = suitePrRepository.findBySuiteId(suiteId);
            for (SuitePr sp : suitePrs) {
                originalCommentRepository.findBySuitePrId(sp.getId())
                        .forEach(c -> commentIds.add(c.getId()));
            }
        }

        int totalComments = commentIds.size();
        int gradedComments = 0;
        int validCount = 0;
        int invalidCount = 0;
        int duplicateCount = 0;
        int needsReviewCount = 0;
        int flaggedCount = 0;

        for (Long commentId : commentIds) {
            List<Grading> gradings = gradingRepository.findByCommentIdAndCommentTableType(commentId, tableType);
            if (!gradings.isEmpty()) {
                gradedComments++;
                // Use the first grading for verdict breakdown
                Grading g = gradings.get(0);
                if ("VALID".equals(g.getVerdict())) validCount++;
                else if ("INVALID".equals(g.getVerdict())) invalidCount++;
                else if ("DUPLICATE".equals(g.getVerdict())) duplicateCount++;
                else if ("NEEDS_REVIEW".equals(g.getVerdict())) needsReviewCount++;
            }
            boolean anyFlagged = gradings.stream().anyMatch(g -> Boolean.TRUE.equals(g.getFlagged()));
            if (anyFlagged) flaggedCount++;
        }

        int ungradedComments = totalComments - gradedComments;
        double percentComplete = totalComments > 0 ? (double) gradedComments / totalComments * 100 : 0.0;

        return new GradingProgressDto(
                runId, suiteId,
                totalComments, gradedComments, ungradedComments, percentComplete,
                validCount, invalidCount, duplicateCount, needsReviewCount, flaggedCount);
    }

    private boolean matches(GoldenDatasetEntry entry, String filePath, Integer lineNumber, String issueType) {
        boolean fileMatch = filePath != null ? filePath.equals(entry.getFilePath()) : entry.getFilePath() == null;
        boolean lineMatch = lineNumber != null ? lineNumber.equals(entry.getLineNumber()) : entry.getLineNumber() == null;
        boolean typeMatch = issueType != null ? issueType.equals(entry.getIssueType()) : entry.getIssueType() == null;
        return fileMatch && lineMatch && typeMatch;
    }
}
