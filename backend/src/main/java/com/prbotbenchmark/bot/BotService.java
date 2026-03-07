package com.prbotbenchmark.bot;

import com.prbotbenchmark.bot.domain.Bot;
import com.prbotbenchmark.bot.dto.CreateBotRequest;
import com.prbotbenchmark.bot.dto.UpdateBotRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class BotService {

    private final BotRepository botRepository;

    public BotService(BotRepository botRepository) {
        this.botRepository = botRepository;
    }

    @Transactional(readOnly = true)
    public List<Bot> listAll() {
        return botRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Bot getById(Long id) {
        return botRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bot not found with id: " + id));
    }

    public Bot create(CreateBotRequest req) {
        Bot bot = new Bot();
        bot.setName(req.name());
        bot.setDescription(req.description());
        bot.setWorkflowFileName(req.workflowFileName());
        bot.setWorkflowContent(req.workflowContent());
        bot.setWaitStrategy(req.waitStrategy());
        bot.setTimeoutSeconds(req.timeoutSeconds());
        bot.setCreatedAt(Instant.now());
        bot.setUpdatedAt(Instant.now());

        return botRepository.save(bot);
    }

    public Bot update(Long id, UpdateBotRequest req) {
        Bot bot = getById(id);

        if (req.name() != null) {
            bot.setName(req.name());
        }
        if (req.description() != null) {
            bot.setDescription(req.description());
        }
        if (req.workflowFileName() != null) {
            bot.setWorkflowFileName(req.workflowFileName());
        }
        if (req.workflowContent() != null) {
            bot.setWorkflowContent(req.workflowContent());
        }
        if (req.waitStrategy() != null) {
            bot.setWaitStrategy(req.waitStrategy());
        }
        if (req.timeoutSeconds() != null) {
            bot.setTimeoutSeconds(req.timeoutSeconds());
        }
        bot.setUpdatedAt(Instant.now());

        return botRepository.save(bot);
    }

    public void delete(Long id) {
        Bot bot = getById(id);
        botRepository.delete(bot);
    }
}
