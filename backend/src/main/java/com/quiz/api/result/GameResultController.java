package com.quiz.api.result;

import com.quiz.common.security.CurrentUser;
import com.quiz.domain.result.GameResult;
import com.quiz.domain.result.GameResultService;
import com.quiz.domain.room.RoomService;
import com.quiz.infra.pdf.PdfGenerator;
import com.quiz.infra.websocket.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@RestController
@RequestMapping("/api/rooms/{roomId}")
@RequiredArgsConstructor
public class GameResultController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter FILENAME_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String CACHE_PREFIX = "pdf:result:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final GameResultService gameResultService;
    private final PdfGenerator pdfGenerator;
    private final RoomService roomService;
    private final RedisTemplate<String, byte[]> redisTemplate;

    @GetMapping(value = "/result.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasRole('HOST')")
    public ResponseEntity<byte[]> download(
        @PathVariable Long roomId,
        @CurrentUser AuthPrincipal principal
    ) {
        log.debug("GET /api/rooms/{}/result.pdf userId={}", roomId, principal.userId());
        roomService.validateHost(roomId, principal.userId());

        // FINISHED 방 결과는 불변 — Redis 캐시 활용
        String cacheKey = CACHE_PREFIX + roomId;
        byte[] pdf = redisTemplate.opsForValue().get(cacheKey);
        String roomCode;
        if (pdf == null) {
            GameResult result = gameResultService.build(roomId);
            pdf = pdfGenerator.generate(result);
            roomCode = result.roomCode();
            redisTemplate.opsForValue().set(cacheKey, pdf, CACHE_TTL);
            log.debug("PDF 생성 및 캐시 저장 roomId={}", roomId);
        } else {
            roomCode = roomService.findById(roomId).code();
            log.debug("PDF 캐시 히트 roomId={}", roomId);
        }

        String filename = "quiz-result-" + roomCode + "-"
            + LocalDate.now(ZONE).format(FILENAME_DATE) + ".pdf";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded
            )
            .body(pdf);
    }
}
