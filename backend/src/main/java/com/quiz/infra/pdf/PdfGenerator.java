package com.quiz.infra.pdf;

import com.quiz.domain.result.GameResult;

/**
 * 게임 결과(GameResult)를 PDF 바이트 배열로 렌더링하는 어댑터.
 */
public interface PdfGenerator {

    byte[] generate(GameResult result);
}
