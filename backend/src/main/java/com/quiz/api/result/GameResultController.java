package com.quiz.api.result;

import com.quiz.common.security.CurrentUser;
import com.quiz.domain.result.GameResult;
import com.quiz.domain.result.GameResultService;
import com.quiz.domain.room.RoomService;
import com.quiz.infra.pdf.PdfGenerator;
import com.quiz.infra.websocket.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final GameResultService gameResultService;
    private final PdfGenerator pdfGenerator;
    private final RoomService roomService;

    @GetMapping(value = "/result.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasRole('HOST')")
    public ResponseEntity<byte[]> download(
        @PathVariable Long roomId,
        @CurrentUser AuthPrincipal principal
    ) {
        log.debug("GET /api/rooms/{}/result.pdf userId={}", roomId, principal.userId());
        roomService.validateHost(roomId, principal.userId());

        GameResult result = gameResultService.build(roomId);
        byte[] pdf = pdfGenerator.generate(result);

        String filename = "quiz-result-" + result.roomCode() + "-"
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
