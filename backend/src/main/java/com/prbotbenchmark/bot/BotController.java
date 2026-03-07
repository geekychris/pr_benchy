package com.prbotbenchmark.bot;

import com.prbotbenchmark.bot.domain.Bot;
import com.prbotbenchmark.bot.dto.CreateBotRequest;
import com.prbotbenchmark.bot.dto.UpdateBotRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bots")
public class BotController {

    private final BotService botService;

    public BotController(BotService botService) {
        this.botService = botService;
    }

    @GetMapping
    public ResponseEntity<List<Bot>> listAll() {
        return ResponseEntity.ok(botService.listAll());
    }

    @PostMapping
    public ResponseEntity<Bot> create(@RequestBody CreateBotRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(botService.create(req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Bot> getById(@PathVariable Long id) {
        return ResponseEntity.ok(botService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Bot> update(@PathVariable Long id, @RequestBody UpdateBotRequest req) {
        return ResponseEntity.ok(botService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        botService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
