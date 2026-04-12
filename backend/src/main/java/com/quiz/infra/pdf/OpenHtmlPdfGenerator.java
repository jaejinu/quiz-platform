package com.quiz.infra.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.quiz.common.exception.QuizException;
import com.quiz.domain.result.GameResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;

/**
 * OpenHtmlToPdf 기반 PDF 생성기. Noto Sans KR 폰트를 classpath에서 로드한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenHtmlPdfGenerator implements PdfGenerator {

    private static final String FONT_PATH = "/fonts/NotoSansKR-Regular.otf";
    private static final String FONT_FAMILY = "Noto Sans KR";

    private final GameResultHtmlRenderer htmlRenderer;

    @Override
    public byte[] generate(GameResult result) {
        String html = htmlRenderer.render(result);
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            // OpenHtmlToPdf가 내부적으로 폰트 스트림을 여러 번 열 수 있으므로 매번 새 스트림을 공급한다.
            builder.useFont(() -> getClass().getResourceAsStream(FONT_PATH), FONT_FAMILY);
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            byte[] pdf = os.toByteArray();
            log.debug("PDF generated roomId={} bytes={}", result.roomId(), pdf.length);
            return pdf;
        } catch (Exception e) {
            log.error("PDF 생성 실패 roomId={}", result.roomId(), e);
            throw new QuizException("PDF_GENERATION_FAILED", "PDF 생성 실패: " + e.getMessage());
        }
    }
}
