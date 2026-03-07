package com.prbotbenchmark.grading;

import com.prbotbenchmark.grading.domain.IssueType;
import com.prbotbenchmark.grading.dto.CreateIssueTypeRequest;
import com.prbotbenchmark.grading.dto.UpdateIssueTypeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class IssueTypeService {

    private final IssueTypeRepository issueTypeRepository;

    @Transactional(readOnly = true)
    public List<IssueType> listAll(boolean activeOnly) {
        if (activeOnly) {
            return issueTypeRepository.findByActiveTrue();
        }
        return issueTypeRepository.findAll();
    }

    @Transactional(readOnly = true)
    public IssueType getById(Long id) {
        return issueTypeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "IssueType not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public IssueType getByCode(String code) {
        return issueTypeRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "IssueType not found with code: " + code));
    }

    public IssueType create(CreateIssueTypeRequest req) {
        if (req.code() == null || !req.code().matches("[A-Z][A-Z0-9_]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Issue type code must match pattern [A-Z][A-Z0-9_]+ (uppercase letters, digits, and underscores only)");
        }

        if (issueTypeRepository.findByCode(req.code()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "IssueType with code '" + req.code() + "' already exists");
        }

        log.info("Creating issue type with code: {}", req.code());

        IssueType issueType = IssueType.builder()
                .code(req.code())
                .name(req.name())
                .description(req.description())
                .category(req.category())
                .severityHint(req.severityHint())
                .active(true)
                .build();

        return issueTypeRepository.save(issueType);
    }

    public IssueType update(Long id, UpdateIssueTypeRequest req) {
        IssueType issueType = getById(id);

        if (req.name() != null) {
            issueType.setName(req.name());
        }
        if (req.description() != null) {
            issueType.setDescription(req.description());
        }
        if (req.category() != null) {
            issueType.setCategory(req.category());
        }
        if (req.severityHint() != null) {
            issueType.setSeverityHint(req.severityHint());
        }
        if (req.active() != null) {
            issueType.setActive(req.active());
        }

        log.info("Updating issue type id: {}", id);
        return issueTypeRepository.save(issueType);
    }

    public void deactivate(Long id) {
        IssueType issueType = getById(id);
        issueType.setActive(false);
        issueTypeRepository.save(issueType);
        log.info("Deactivated issue type id: {}", id);
    }

    @Transactional(readOnly = true)
    public List<String> listCategories() {
        return issueTypeRepository.findAll().stream()
                .map(IssueType::getCategory)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}
